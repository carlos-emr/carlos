/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.ExtractBean;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the live legacy group export: durable files precede the single activity/log/status commit.
 * A database generation lock spans selection, commit and recovery, shared with the modern endpoint.
 * @since 2026-11-09
 */
@Service
public class LegacyBcTeleplanExportService {
    public record Selection(String providers, String monthCode, String version, String billingCenter) { }
    private final BcBillingService billing;
    private final ProviderDao providers;
    private final BillActivityDao activities;
    private final BillingmasterDAO masters;
    private final TransactionTemplate transactions;
    private final TeleplanExportCoordinator coordinator;
    @PersistenceContext private EntityManager entityManager;

    public LegacyBcTeleplanExportService(BcBillingService billing, ProviderDao providers, BillActivityDao activities,
            @Qualifier("billingmasterDAO") BillingmasterDAO masters,
            PlatformTransactionManager transactionManager, TeleplanExportCoordinator coordinator) {
        this.billing = billing;
        this.providers = providers;
        this.activities = activities;
        this.masters = masters;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.coordinator = coordinator;
    }

    public int generate(Selection selection, String creator) throws Exception {
        if (creator == null || creator.isBlank()) throw new SecurityException("Authenticated billing creator is required");
        if (selection.monthCode() == null || !selection.monthCode().matches("[A-L]")) {
            throw new IllegalArgumentException("A valid billing month is required");
        }
        if (selection.providers() == null || selection.providers().isBlank()) throw new IllegalArgumentException("Select a provider");
        return coordinator.generate(() -> {
            var files = new LegacyTeleplanFiles(Path.of(CarlosProperties.getInstance().getProperty("HOME_DIR")));
            files.recover(this::hasCommittedActivity);
            try {
                Integer generated = transactions.execute(status -> {
                    try { return generateInTransaction(selection, creator, files); }
                    catch (Exception failure) { throw new IllegalStateException("Teleplan export could not be completed", failure); }
                });
                // The callback has returned through Spring's commit, so recovery can retire the journal.
                files.recover(this::hasCommittedActivity);
                return generated == null ? 0 : generated;
            } catch (Exception failure) {
                // Never guess about a failed commit acknowledgement. Recovery first checks committed DB state.
                try { files.recover(this::hasCommittedActivity); }
                catch (Exception recoveryFailure) { failure.addSuppressed(recoveryFailure); }
                throw failure;
            }
        });
    }

    private int generateInTransaction(Selection selection, String creator, LegacyTeleplanFiles files) throws Exception {
        List<Provider> selected;
        if ("all".equals(selection.providers())) selected = providers.getActiveProviders();
        else {
            String insurance = selection.providers().split(",", 2)[0].trim();
            if (!insurance.matches("[0-9]{5}")) throw new IllegalArgumentException("Select a billing provider");
            selected = providers.getBillableProvidersByOHIPNo(insurance);
        }
        Map<String, List<Provider>> groups = new LinkedHashMap<>();
        java.util.Set<String> selectedInsurance = new java.util.HashSet<>();
        for (Provider provider : selected) {
            String insurance = provider.getOhipNo();
            if (insurance == null || insurance.isBlank()) continue;
            Number pending = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM billing b JOIN billingmaster bm "
                    + "ON bm.billing_no=b.billing_no WHERE b.provider_ohip_no=?1 AND b.status IN ('O','W') "
                    + "AND bm.billingstatus IN ('O','W') AND b.billingtype IN ('MSP','ICBC','WCB')")
                    .setParameter(1, insurance).getSingleResult();
            if (pending.longValue() == 0) continue;
            if (!insurance.matches("[0-9]{5}")) throw new IllegalArgumentException("A provider has an invalid practitioner number");
            // Duplicate insurance identities must not generate duplicate claims under different provider records.
            if (!selectedInsurance.add(insurance)) throw new IllegalArgumentException("Providers share a practitioner number; correct the billing setup");
            String group = defaultCode(provider.getBillingNo(), "0000");
            if (!group.matches("[0-9]{4,5}")) throw new IllegalArgumentException("A provider has an invalid billing group");
            groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(provider);
        }
        if (selected.isEmpty()) throw new IllegalArgumentException("No billing providers matched the selection");
        Map<String, String> artifacts = new LinkedHashMap<>();
        List<ExtractBean> extracts = new ArrayList<>();
        List<BillActivity> activity = new ArrayList<>();
        for (var group : groups.entrySet()) {
            int next = nextBatch(selection.monthCode(), group.getKey());
            // Legacy filenames omit the year. Retain older exports and skip any occupied
            // claim or provider-report name while the shared generation lock is held.
            while (next <= 999 && batchExists(files, selection.monthCode(), group.getKey(), group.getValue(), next)) next++;
            if (next > 999) throw new IllegalStateException("Monthly Teleplan batch capacity is exhausted");
            String suffix = String.format(java.util.Locale.ROOT, "%03d", next);
            String claimFilename = "H" + selection.monthCode() + group.getKey() + "." + suffix;
            StringBuilder claimFile = new StringBuilder();
            int providerBatch = 1;
            for (Provider provider : group.getValue()) {
                // Lock source rows on this transaction's connection before the legacy reader hydrates them.
                List<?> eligible = entityManager.createNativeQuery("SELECT b.billing_no,bm.billingmaster_no FROM billing b "
                        + "JOIN billingmaster bm ON bm.billing_no=b.billing_no WHERE b.provider_ohip_no=?1 "
                        + "AND b.status IN ('O','W') AND bm.billingstatus IN ('O','W') "
                        + "AND b.billingtype IN ('MSP','ICBC','WCB') ORDER BY b.billing_no,bm.billingmaster_no FOR UPDATE")
                        .setParameter(1, provider.getOhipNo()).getResultList();
                if (eligible.isEmpty()) continue;
                entityManager.createNativeQuery("SELECT id FROM wcb WHERE id IN (SELECT bm.wcb_id FROM billingmaster bm "
                        + "JOIN billing b ON b.billing_no=bm.billing_no WHERE b.provider_ohip_no=?1 "
                        + "AND b.status IN ('O','W') AND bm.billingstatus IN ('O','W')) "
                        + "OR billing_no IN (SELECT b.billing_no FROM billing b JOIN billingmaster bm ON bm.billing_no=b.billing_no "
                        + "WHERE b.provider_ohip_no=?1 AND b.billingtype='WCB' AND b.status IN ('O','W') "
                        + "AND bm.billingstatus IN ('O','W') AND (bm.wcb_id IS NULL OR bm.wcb_id=0)) FOR UPDATE")
                        .setParameter(1, provider.getOhipNo()).getResultList();
                ExtractBean extract = new ExtractBean();
                extract.seteFlag("1");
                extract.setVSFlag(providerBatch == 1 ? 0 : 1);
                extract.setOhipVer(selection.version());
                extract.setOhipCenter(selection.billingCenter());
                extract.setProviderNo(provider.getOhipNo());
                extract.setGroupNo(group.getKey());
                extract.setSpecialty(defaultCode(SxmlMisc.getXmlContent(provider.getComments(), "<xml_p_specialty_code>", "</xml_p_specialty_code>"), "00"));
                extract.setBatchCount(String.valueOf(providerBatch++));
                billing.generateExtract(extract);
                java.util.Set<Integer> marked = extract.getMasterIdsToMark();
                if (marked.isEmpty()) throw new IllegalStateException("Eligible claims produced no Teleplan records");
                Map<Integer, java.util.Set<Integer>> pendingByInvoice = new LinkedHashMap<>();
                for (Object row : eligible) {
                    Object[] ids = (Object[]) row;
                    pendingByInvoice.computeIfAbsent(((Number) ids[0]).intValue(), ignored -> new java.util.HashSet<>())
                            .add(((Number) ids[1]).intValue());
                }
                for (Integer billId : extract.getBillIdsToMark()) {
                    java.util.Set<Integer> pending = pendingByInvoice.get(billId);
                    if (pending == null || !marked.containsAll(pending)) {
                        throw new IllegalStateException("An invoice contains pending claims absent from the export");
                    }
                }
                extracts.add(extract);
                claimFile.append(extract.getValue());
                String htmlFilename = "H" + selection.monthCode() + provider.getOhipNo() + "_" + suffix + ".htm";
                artifacts.put(htmlFilename, extract.getHtmlCode());
                BillActivity batch = new BillActivity();
                batch.setMonthCode(selection.monthCode()); batch.setBatchCount(next);
                batch.setHtmlFilename(htmlFilename); batch.setOhipFilename(claimFilename);
                batch.setProviderOhipNo(provider.getOhipNo()); batch.setGroupNo(group.getKey());
                batch.setCreator(creator); batch.setHtmlContext(extract.getHtmlCode()); batch.setOhipContext(extract.getValue());
                batch.setClaimRecord(extract.getOhipClaim() + "/" + extract.getOhipRecord());
                batch.setUpdateDateTime(new java.util.Date()); batch.setStatus("A"); batch.setTotal(extract.getTotalAmount());
                activity.add(batch);
            }
            if (providerBatch > 1) artifacts.put(claimFilename, claimFile.toString());
        }
        if (activity.isEmpty()) return 0;
        // Allocate activity identities inside this transaction before publication. Recovery must never
        // mistake an older year's activity with the same filename for this commit's witness.
        for (BillActivity batch : activity) activities.persist(batch);
        entityManager.flush();
        Map<String, Integer> witnesses = new LinkedHashMap<>();
        for (BillActivity batch : activity) {
            witnesses.putIfAbsent(batch.getOhipFilename(), batch.getId());
            witnesses.put(batch.getHtmlFilename(), batch.getId());
        }
        Path staged = files.stage(artifacts, witnesses);
        files.publish(staged);
        for (ExtractBean extract : extracts) {
            for (Integer id : extract.getMasterIdsToMark()) masters.markBcMasterBilled(id);
            for (Integer id : extract.getBillIdsToMark()) masters.markBcBillBilled(id);
        }
        entityManager.flush();
        return activity.size();
    }

    private int nextBatch(String month, String group) {
        Number max = (Number) entityManager.createNativeQuery("SELECT MAX(batchcount) FROM billactivity "
                        + "WHERE monthCode=?1 AND groupno=?2 AND updatedatetime >= ?3")
                .setParameter(1, month).setParameter(2, group)
                .setParameter(3, java.sql.Date.valueOf(LocalDate.now(java.time.ZoneId.systemDefault()).withDayOfYear(1))).getSingleResult();
        return max == null ? 1 : Math.addExact(max.intValue(), 1);
    }

    private static boolean batchExists(LegacyTeleplanFiles files, String month, String group,
            List<Provider> providers, int batch) throws java.io.IOException {
        String suffix = String.format(java.util.Locale.ROOT, "%03d", batch);
        if (files.exists("H" + month + group + "." + suffix)) return true;
        for (Provider provider : providers) {
            if (files.exists("H" + month + provider.getOhipNo() + "_" + suffix + ".htm")) return true;
        }
        return false;
    }

    private boolean hasCommittedActivity(Integer activityId, String filename) {
        // Include deleted activities: a deleted batch was still committed, and its files must not be discarded.
        return Boolean.TRUE.equals(transactions.execute(status -> ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM billactivity WHERE id=?1 AND (ohipfilename=?2 OR htmlfilename=?2)")
                .setParameter(1, activityId).setParameter(2, filename).getSingleResult()).longValue() > 0));
    }

    private static String defaultCode(String value, String fallback) {
        return value == null || value.isBlank() || "null".equals(value) ? fallback : value.trim();
    }
}
