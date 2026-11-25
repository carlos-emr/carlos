/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ownership of the bytes behind Ontario's shared claim files and per-provider HTML reports. */
@Service
@Transactional(readOnly = true)
public class BillingOnFileAccessService {
    private static final int BATCH_SIZE = 256;
    private final SecurityInfoManager security;
    private final ProviderDataDao providers;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager em;

    public BillingOnFileAccessService(SecurityInfoManager security, ProviderDataDao providers) {
        this.security = security;
        this.providers = providers;
    }

    /** Every reference counts, including non-billable, inactive and deleted members of historical files. */
    public Map<String, Boolean> filePermissions(Collection<String> names, Set<String> allowedProviders) {
        List<String> distinct = names.stream().filter(n -> n != null && !n.isBlank()).distinct().toList();
        Map<String, Boolean> result = new HashMap<>();
        for (int start = 0; start < distinct.size(); start += BATCH_SIZE) {
            Set<String> batch = Set.copyOf(distinct.subList(start, Math.min(start + BATCH_SIZE, distinct.size())));
            List<Object[]> diskOwners = em.createQuery(
                    "select d.ohipFilename, f.providerNo from BillingONDiskName d "
                            + "left join BillingONFilename f on f.diskId=d.id where d.ohipFilename in :names", Object[].class)
                    .setParameter("names", batch).getResultList();
            List<Object[]> htmlOwners = em.createQuery(
                    "select f.htmlFilename, f.providerNo from BillingONFilename f where f.htmlFilename in :names", Object[].class)
                    .setParameter("names", batch).getResultList();
            for (Object[] row : diskOwners) recordOwner(result, batch, (String) row[0], (String) row[1], allowedProviders);
            for (Object[] row : htmlOwners) recordOwner(result, batch, (String) row[0], (String) row[1], allowedProviders);

            List<Object[]> legacy = em.createQuery(
                    "select b.ohipFilename, b.htmlFilename, b.providerOhipNo from BillActivity b "
                            + "where b.ohipFilename in :names or b.htmlFilename in :names", Object[].class)
                    .setParameter("names", batch).getResultList();
            Map<String, String> owners = uniqueLegacyOwners(legacy.stream().map(row -> (String) row[2]).toList());
            for (Object[] row : legacy) {
                String owner = owners.get(row[2]);
                recordOwner(result, batch, (String) row[0], owner, allowedProviders);
                recordOwner(result, batch, (String) row[1], owner, allowedProviders);
            }
        }
        return result;
    }

    /** BillActivity has no provider number. A reused or missing OHIP number cannot identify its owner. */
    public Map<String, String> uniqueLegacyOwners(Collection<String> ohipNumbers) {
        List<String> distinct = ohipNumbers.stream().filter(no -> no != null && !no.isBlank()).distinct().toList();
        Map<String, String> owners = new HashMap<>();
        for (int start = 0; start < distinct.size(); start += BATCH_SIZE) {
            Set<String> batch = Set.copyOf(distinct.subList(start, Math.min(start + BATCH_SIZE, distinct.size())));
            List<Object[]> matches = em.createQuery(
                    "select p.ohipNo, p.providerNo from Provider p where p.ohipNo in :numbers and p.providerNo <> '-1'",
                    Object[].class).setParameter("numbers", batch).getResultList();
            for (Object[] row : matches) {
                String ohip = (String) row[0];
                if (batch.contains(ohip)) owners.merge(ohip, (String) row[1], (a, b) -> "");
            }
        }
        return owners;
    }

    private static void recordOwner(Map<String, Boolean> results, Set<String> batch, String name, String owner,
                                    Set<String> allowedProviders) {
        // MariaDB collations can equate different Linux filenames. Only exact stored basenames participate.
        if (name != null && batch.contains(name)) {
            boolean allowed = owner != null && !owner.isBlank() && allowedProviders.contains(owner);
            results.merge(name, allowed, (previous, current) -> previous && current);
        }
    }

    public enum DownloadAccess { DENIED, GRANTED, VERIFY_OWNERS }

    private DownloadAccess billingOrReportAccess(LoggedInInfo login, String filename, boolean generatedObec) {
        if (login == null || filename == null || filename.isBlank()) return DownloadAccess.DENIED;
        if (generatedObec && security.hasPrivilege(login, "_report", SecurityInfoManager.READ, null)
                && !filePermissions(List.of(filename), Set.of()).containsKey(filename)) return DownloadAccess.GRANTED;
        if (!security.hasPrivilege(login, "_billing", SecurityInfoManager.READ, null)) return DownloadAccess.DENIED;
        return DownloadAccess.VERIFY_OWNERS;
    }

    /** A session-issued OBEC file or clinic-wide billing access can be authorized independently of a current disk. */
    public DownloadAccess downloadAccess(LoggedInInfo login, String filename, boolean generatedObec) {
        DownloadAccess access = billingOrReportAccess(login, filename, generatedObec);
        if (access != DownloadAccess.VERIFY_OWNERS) return access;
        return BillingOnProviderScope.forLogin(security, login).restricted() ? DownloadAccess.VERIFY_OWNERS : DownloadAccess.GRANTED;
    }

    public boolean canDownload(LoggedInInfo login, String filename, boolean generatedObec) {
        DownloadAccess access = billingOrReportAccess(login, filename, generatedObec);
        if (access != DownloadAccess.VERIFY_OWNERS) return access == DownloadAccess.GRANTED;
        var scope = BillingOnProviderScope.forLogin(security, login).historyProviderNumbers(providers, login.getLoggedInProviderNo());
        return scope.isEmpty() || Boolean.TRUE.equals(filePermissions(List.of(filename), scope.get()).get(filename));
    }

    /** Start the servlet's ownership queries after acquiring the publication guard, with no earlier transaction snapshot. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public <T extends AutoCloseable> T openScopedDownload(Supplier<T> open) {
        return BillingOutputLock.openDownload(open);
    }
}
