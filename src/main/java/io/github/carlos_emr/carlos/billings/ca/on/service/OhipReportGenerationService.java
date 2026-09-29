/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.DateRange;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
/**
 * Shared mutation service for the three OHIP-extract action entry points:
 * {@code ViewGenReport2Action} (SOLO_REPORT), {@code ViewGenGroupReport2Action}
 * (GROUP_REPORT), and {@code ViewGenSimulation2Action} (SIMULATION).
 *
 * <p>Iterates billable providers, runs {@link OhipClaimExtractService} per
 * provider to generate the OHIP claim file / HTML preview, and (in report
 * modes) persists a {@link BillActivity} row. Per-provider work runs inside
 * a {@code REQUIRES_NEW} transaction (see {@code perProviderTx}) so a single
 * provider's failure rolls back only that provider's writes — earlier
 * providers' commits stay durable.</p>
 *
 * <p>Three modes:
 * <ul>
 *   <li>{@link Mode#GROUP_REPORT}: for hybrid-billing group submissions —
 *       writes a group-keyed file with {@code H{monthCode}{groupNo}} naming.</li>
 *   <li>{@link Mode#SOLO_REPORT}: for solo-provider submissions —
 *       writes provider-keyed files; skips group-billing providers.</li>
 *   <li>{@link Mode#SIMULATION}: dry run with {@code eFlag="0"} — no
 *       BillActivity persist, no file write; just builds an HTML preview
 *       returned via {@link SimulationResult}.</li>
 * </ul>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class OhipReportGenerationService {

    public enum Mode { GROUP_REPORT, SOLO_REPORT, SIMULATION }

    private static final int PROVIDER_BILLINGNO_LENGTH = 6;
    private static final int PROVIDER_SPECIALTYCODE_LENGTH = 2;
    private static final int PROVIDER_GROUPNO_LENGTH = 4;

    private final BillActivityDao billActivityDao;
    private final ProviderDao providerDao;
    private final BillingDao billingDao;
    private final BillingDetailDao billingDetailDao;
    /**
     * ObjectFactory yields a fresh prototype-scoped {@link OhipClaimExtractService}
     * per invocation — needed because OhipClaimExtractService carries per-claim
     * mutating state across its dbQuery / writeFile / writeHtml methods. The
     * factory ensures Spring assembles each instance through DI rather than
     * via {@code new}, so the @Service / @Scope("prototype") wiring on
     * OhipClaimExtractService is honoured.
     */
    private final org.springframework.beans.factory.ObjectFactory<OhipClaimExtractService> ohipClaimExtractFactory;
    /**
     * Per-provider transactional boundary. Wraps {@code dbQuery} (which
     * sets bills as billed) and {@code persistBillActivity} so a failure
     * inside one provider's iteration rolls back only that provider's
     * DB writes — earlier providers' files + BillActivity rows stay
     * committed. AOP self-invocation cannot achieve this on a class-level
     * {@code @Transactional} method, so we drive the boundary
     * programmatically.
     */
    private final org.springframework.transaction.support.TransactionTemplate perProviderTx;

    OhipReportGenerationService(BillActivityDao billActivityDao,
                                ProviderDao providerDao,
                                BillingDao billingDao,
                                BillingDetailDao billingDetailDao,
                                org.springframework.beans.factory.ObjectFactory<OhipClaimExtractService> ohipClaimExtractFactory,
                                org.springframework.transaction.PlatformTransactionManager txManager) {
        this.billActivityDao = billActivityDao;
        this.providerDao = providerDao;
        this.billingDao = billingDao;
        this.billingDetailDao = billingDetailDao;
        this.ohipClaimExtractFactory = ohipClaimExtractFactory;
        this.perProviderTx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.perProviderTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Result returned to the simulation mode — captures the HTML preview
     * the legacy JSP stashed on the request via {@code request.setAttribute}.
     */
    public record SimulationResult(String htmlPreview, String errorMsg, String dateBeginStr, String dateEndStr) {}

    /**
     * One row per provider whose per-provider transaction rolled back.
     * The action layer surfaces these via {@code skippedProviders} on the
     * request so the operator's success page can banner "N providers were
     * skipped — see error log" rather than rendering as if every selected
     * provider's report was generated.
     *
     * <p>{@code causeClass} is the simple class name of the throwable so
     * the JSP can render a coarse category (file vs data vs unknown) without
     * leaking stack-trace detail; the full cause is in the server log.</p>
     */
    public record FailedProvider(String providerNo, String ohipNo,
                                 String causeClass, String causeMessage) {
        public FailedProvider {
            // Coalesce nulls into safe defaults at the type boundary so the
            // JSP never renders the literal word "null" through SafeEncode,
            // and so a future caller / deserializer / test fixture can't
            // bypass the producer-side coalescing.
            providerNo = providerNo == null ? "" : providerNo;
            ohipNo = ohipNo == null ? "" : ohipNo;
            causeClass = causeClass == null ? "Unknown" : causeClass;
            causeMessage = (causeMessage == null || causeMessage.isEmpty())
                    ? "<no message; check server log>" : causeMessage;
        }
    }

    /**
     * Run the OHIP extract for a group/solo submission. Persists
     * BillActivity rows and writes the OHIP+HTML files for each
     * eligible provider.
     *
     * @return list of providers whose per-provider tx rolled back; empty
     *         when all selected providers were processed cleanly. Caller
     *         should stash this on the request for the JSP banner.
     */
    public java.util.List<FailedProvider> generateReport(HttpServletRequest request, Mode mode) {
        if (mode == Mode.SIMULATION) {
            throw new IllegalArgumentException("Use generateSimulation for SIMULATION mode");
        }

        java.util.List<FailedProvider> skipped = new ArrayList<>();

        String monthCode = request.getParameter("monthCode");
        if (monthCode == null || monthCode.isEmpty()) {
            // Empty skipped list otherwise reads as "everything ran clean"
            // on the success page; log so the operator/oncall can tell why
            // the page rendered with no banner.
            MiscUtils.getLogger().warn(
                    "OhipReportGeneration.generateReport: missing monthCode parameter; nothing processed");
            return skipped;
        }

        String providerParam = request.getParameter("providers");
        if (providerParam == null || providerParam.trim().isEmpty()) {
            MiscUtils.getLogger().warn(
                    "OhipReportGeneration.generateReport: missing providers parameter; nothing processed");
            return skipped;
        }
        providerParam = providerParam.trim();

        boolean hybridBilling = isHybridBilling();
        List<String> hybridProviders = hybridProviderList();
        int curYear = new GregorianCalendar().get(Calendar.YEAR);

        List<Provider> providersToProcess = resolveProviders(providerParam);

        int batchOrdinal = 1;
        for (Provider p : providersToProcess) {
            if (p.getOhipNo() == null || p.getOhipNo().isEmpty()) continue;

            // Hybrid filter: GROUP_REPORT processes only group providers;
            // SOLO_REPORT processes only solo (non-group) providers.
            boolean inHybridList = hybridBilling && hybridProviders.contains(p.getProviderNo());
            if (mode == Mode.GROUP_REPORT && hybridBilling && !inHybridList) continue;
            if (mode == Mode.SOLO_REPORT && hybridBilling && inHybridList) continue;

            String proOHIP = p.getOhipNo();
            String groupNo = sanitizeGroupNo(SxmlMisc.getXmlContent(p.getComments(),
                    "<xml_p_billinggroup_no>", "</xml_p_billinggroup_no>"));
            String specialty = sanitizeSpecialty(SxmlMisc.getXmlContent(p.getComments(),
                    "<xml_p_specialty_code>", "</xml_p_specialty_code>"));

            String groupKey = mode == Mode.GROUP_REPORT ? groupNo : proOHIP;
            String batchCount = nextBatchCount(monthCode, groupKey, curYear);

            OhipClaimExtractService extract = ohipClaimExtractFactory.getObject();
            extract.seteFlag("1");
            extract.setOhipVer(request.getParameter("verCode"));
            extract.setProviderNo(proOHIP);
            extract.setOhipCenter(request.getParameter("billcenter"));
            extract.setGroupNo(groupNo);
            extract.setSpecialty(specialty);
            extract.setBatchCount(String.valueOf(batchOrdinal));

            String[] filenames = buildFilenames(monthCode, groupKey, proOHIP, batchCount,
                    mode == Mode.GROUP_REPORT);
            extract.setHtmlFilename(filenames[1]);
            extract.setOhipFilename(filenames[0]);

            // Per-provider tx boundary: dbQuery (setAsBilled), file writes,
            // and persistBillActivity all live in one REQUIRES_NEW
            // transaction. A throw rolls back THIS provider's DB writes
            // (no setAsBilled remains, no BillActivity row); earlier
            // providers' commits stay durable. File writes happen INSIDE
            // the tx so a thrown BillingFileWriteException after writeFile
            // succeeds correctly tears down setAsBilled but leaves the
            // file on disk — the operator finds the orphan via the
            // missing BillActivity row and re-runs for that provider.
            String curUser = request.getParameter("curUser");
            try {
                perProviderTx.executeWithoutResult(status -> {
                    // Throwing a RuntimeException out of the lambda is the
                    // load-bearing rollback trigger; TransactionTemplate calls
                    // rollback automatically on RuntimeException propagation.
                    extract.dbQuery();
                    extract.writeFile(extract.getValue());
                    extract.writeHtml(extract.getHtmlCode());
                    persistBillActivity(monthCode, batchCount, filenames[0], filenames[1],
                            proOHIP, groupNo, curUser, extract);
                });
            } catch (RuntimeException ex) {
                // BillingFileWriteException, BillingDataLoadException, and
                // every other unchecked failure all roll back the
                // per-provider tx and surface the same shape of FailedProvider
                // record; multi-catching them adds no information at this
                // point. The cause class is captured so the JSP can branch
                // file-vs-data-vs-other if it needs to.
                String causeClass = ex.getClass().getSimpleName();
                // Default null exception messages (e.g. NPE with no message)
                // to a placeholder so the JSP banner doesn't render the
                // literal word "null" — operators need to know the message
                // is absent and to look at the server log.
                String causeMessage = ex.getMessage() == null
                        ? "<no message; check server log>"
                        : ex.getMessage();
                MiscUtils.getLogger().error(
                        "OhipReportGeneration: per-provider failure for provider {} (ohip={}, cause={}); tx rolled back, prior providers stay committed",
                        LogSafe.sanitize(p.getProviderNo()),
                        LogSafe.sanitize(proOHIP),
                        causeClass,
                        ex);
                skipped.add(new FailedProvider(
                        p.getProviderNo(), proOHIP, causeClass, causeMessage));
                continue;
            }

            batchOrdinal++;
        }
        return skipped;
    }

    /**
     * Run the OHIP simulation (eFlag="0") — produces an HTML preview
     * for {@code genSimulation.jsp}. No persistence.
     */
    public SimulationResult generateSimulation(HttpServletRequest request) {
        StringBuilder errorMsg = new StringBuilder();
        String provider = request.getParameter("providers");
        if (provider == null || provider.length() != PROVIDER_BILLINGNO_LENGTH) {
            errorMsg.append("The providers's billing code is not correct!<br>");
        }

        Date dateBegin = ConversionUtils.fromDateString(request.getParameter("xml_vdate"));
        Date dateEnd = ConversionUtils.fromDateString(request.getParameter("xml_appointment_date"));
        if (dateEnd == null) {
            dateEnd = ConversionUtils.fromDateString(request.getParameter("curDate"));
        }
        DateRange dateRange = new DateRange(dateBegin, dateEnd);

        StringBuilder htmlValue = new StringBuilder();
        for (Provider p : providerDao.getActiveProviders()) {
            if (p.getOhipNo() == null || p.getOhipNo().isEmpty()) continue;

            String proOHIP = p.getOhipNo();
            String groupNo = SxmlMisc.getXmlContent(p.getComments(),
                    "<xml_p_billinggroup_no>", "</xml_p_billinggroup_no>");
            String specialty = SxmlMisc.getXmlContent(p.getComments(),
                    "<xml_p_specialty_code>", "</xml_p_specialty_code>");

            if (specialty == null || specialty.isEmpty() || "null".equals(specialty)
                    || specialty.length() != PROVIDER_SPECIALTYCODE_LENGTH) {
                errorMsg.append("The providers's specialty code is not correct!<br>");
                specialty = "00";
            }
            if (groupNo == null || groupNo.isEmpty() || "null".equals(groupNo)
                    || groupNo.length() != PROVIDER_GROUPNO_LENGTH) {
                errorMsg.append("The providers's group no is not correct!<br>");
                groupNo = "0000";
            }

            OhipClaimExtractService extract = ohipClaimExtractFactory.getObject();
            extract.seteFlag("0");
            extract.setDateRange(dateRange);
            extract.setOhipVer(request.getParameter("verCode"));
            extract.setProviderNo(proOHIP);
            extract.setOhipCenter(request.getParameter("billcenter"));
            extract.setGroupNo(groupNo);
            extract.setSpecialty(specialty);
            extract.setBatchCount("1");
            extract.dbQuery();

            htmlValue.append(extract.getHtmlValue());
        }
        return new SimulationResult(htmlValue.toString(), errorMsg.toString(),
                dateBegin == null ? "" : dateBegin.toString(),
                dateEnd == null ? "" : dateEnd.toString());
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private List<Provider> resolveProviders(String providerParam) {
        if ("all".equalsIgnoreCase(providerParam)) {
            return providerDao.getActiveProviders();
        }
        // Single provider — first 6 chars of the param value.
        String pn = providerParam.length() > PROVIDER_BILLINGNO_LENGTH
                ? providerParam.substring(0, PROVIDER_BILLINGNO_LENGTH) : providerParam;
        Provider p = providerDao.getProvider(pn);
        List<Provider> out = new ArrayList<>();
        if (p != null) out.add(p);
        return out;
    }

    /**
     * Whether the {@code hybrid_billing} property is enabled. Callers
     * use this to decide whether to chain a SOLO_REPORT into a follow-up
     * GROUP_REPORT pass.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public boolean isHybridBilling() {
        String v = CarlosProperties.getInstance().getProperty("hybrid_billing", "");
        return "on".equalsIgnoreCase(v);
    }

    private List<String> hybridProviderList() {
        List<String> out = new ArrayList<>();
        if (!isHybridBilling()) return out;
        String list = CarlosProperties.getInstance().getProperty("group_billing_providerNo", "");
        for (String pn : list.split(",")) {
            String trimmed = pn.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private String nextBatchCount(String monthCode, String key, int curYear) {
        // JPA Query.getResultList() does not produce null elements (per the
        // EntityManager contract), so the previous null-row guard was an
        // unreachable defence. BillActivity.getBatchCount() returns int.
        int max = 0;
        for (BillActivity ba : billActivityDao.findCurrentByMonthCodeAndGroupNo(
                monthCode, key, ConversionUtils.fromDateString(curYear + "-01-01"))) {
            int bc = ba.getBatchCount();
            if (bc > max) max = bc;
        }
        return String.valueOf(max + 1);
    }

    private String[] buildFilenames(String monthCode, String groupKey, String proOHIP,
                                     String batchCount, boolean isGroup) {
        int padLen = 3 - batchCount.length();
        String pad = padLen == 1 ? "0" : padLen == 2 ? "00" : "";
        String htmlFilename;
        String ohipFilename;
        if (isGroup) {
            htmlFilename = "H" + monthCode + groupKey + "_" + proOHIP + "_" + pad + batchCount + ".htm";
            ohipFilename = "H" + monthCode + groupKey + "." + pad + batchCount;
        } else {
            htmlFilename = "H" + monthCode + proOHIP + "_" + pad + batchCount + ".htm";
            ohipFilename = "H" + monthCode + proOHIP + "." + pad + batchCount;
        }
        return new String[]{ohipFilename, htmlFilename};
    }

    private void persistBillActivity(String monthCode, String batchCount, String ohipFilename,
                                      String htmlFilename, String proOHIP, String groupNo,
                                      String creator, OhipClaimExtractService extract) {
        BillActivity ba = new BillActivity();
        ba.setMonthCode(monthCode);
        ba.setBatchCount(parseStrictBatchCount(batchCount));
        ba.setHtmlFilename(htmlFilename);
        ba.setOhipFilename(ohipFilename);
        ba.setProviderOhipNo(proOHIP);
        ba.setGroupNo(groupNo);
        ba.setCreator(creator);
        ba.setHtmlContext(extract.getHtmlCode());
        ba.setOhipContext(extract.getValue());
        ba.setClaimRecord(extract.getOhipClaim() + "/" + extract.getOhipRecord());
        ba.setUpdateDateTime(new Date());
        ba.setStatus("A");
        ba.setTotal(extract.getTotalAmount());
        billActivityDao.persist(ba);
    }

    private static String sanitizeGroupNo(String raw) {
        return (raw == null || raw.isEmpty() || "null".equals(raw)) ? "0000" : raw;
    }

    private static String sanitizeSpecialty(String raw) {
        return (raw == null || raw.isEmpty() || "null".equals(raw)) ? "00" : raw;
    }

    /**
     * Parse a batch-count string for {@link BillActivity#setBatchCount(int)}. The
     * batchCount feeds {@code nextBatchCount} (max+1) and the OHIP-filename
     * counter ({@code H{monthCode}.{batchCount}}); a silent zero on a malformed
     * value would risk colliding filenames and duplicate-claim submission. Throw
     * a typed exception so the caller's per-provider {@link
     * org.springframework.transaction.support.TransactionTemplate} rolls back
     * this provider's writes and surfaces the bad input.
     */
    private static int parseStrictBatchCount(String s) {
        if (s == null) {
            throw new BillingValidationException("OHIP batch count is null");
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new BillingValidationException(
                    "OHIP batch count is not a valid integer; see logs", e);
        }
    }
}
