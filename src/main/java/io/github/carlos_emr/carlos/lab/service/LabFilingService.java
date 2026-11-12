/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.lab.ca.all.util.LabVersionChain;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Files existing NEW HL7 routes for consenting colleagues in one audited transaction. */
@Service
public class LabFilingService {
    private final ProviderLabRoutingDao routingDao;
    private final PatientLabRoutingDao patientRoutingDao;
    private final PropertyDao propertyDao;
    private final ProviderDao providerDao;
    private final SecurityInfoManager security;
    private final TransactionTemplate transaction;

    public LabFilingService(ProviderLabRoutingDao routingDao, PatientLabRoutingDao patientRoutingDao, PropertyDao propertyDao,
            ProviderDao providerDao, SecurityInfoManager security, PlatformTransactionManager transactions) {
        this.routingDao = routingDao;
        this.patientRoutingDao = patientRoutingDao;
        this.propertyDao = propertyDao;
        this.providerDao = providerDao;
        this.security = security;
        transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * The caller may file only for other providers already routed to the reviewed result.
     * With upToReviewedVersion, also files existing routes on older versions, never newer ones.
     * All target consent/routing checks precede all writes. Missing older routes are left absent.
     * Existing comments are retained and the server-generated filing attribution is appended.
     * HL7 routes have no document queue entries; no document queue is changed here.
     * @return number of routing rows changed from NEW to filed
     */
    public int fileForProviders(LoggedInInfo info, int labNo, String labType, List<String> providerNos,
            String comment, boolean upToReviewedVersion) {
        if (!security.hasPrivilege(info, "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }
        if (info == null || info.getLoggedInProvider() == null || info.getLoggedInProviderNo() == null) {
            throw new SecurityException("Missing authenticated provider");
        }
        if (labNo <= 0 || !"HL7".equals(labType) || providerNos == null || providerNos.isEmpty()
                || providerNos.size() > 100 || (comment != null && comment.length() > 2000)) {
            throw new IllegalArgumentException("Invalid filing selection");
        }
        List<String> targets = new ArrayList<>(new LinkedHashSet<>(providerNos));
        for (String target : targets) {
            if (target == null || target.isBlank() || target.length() > 6 || "0".equals(target)
                    || "-1".equals(target) || info.getLoggedInProviderNo().equals(target)) {
                throw new SecurityException("Invalid filing target");
            }
        }
        return transaction.execute(status -> {
            var versions = new TreeSet<Integer>();
            if (upToReviewedVersion) versions.addAll(olderVersions(labNo));
            versions.add(labNo);
            // A consistent numeric lock order prevents deadlocks; selection uses VERSION order.
            for (int version : versions) routingDao.lockRoutingReport(version);
            // Patient-specific restrictions, including patient locks, override global write access.
            // Resolve every affected version on the server, after acquiring the report locks and
            // before any mutation.
            var patients = new LinkedHashSet<Integer>();
            for (int version : versions) {
                for (var route : patientRoutingDao.findByLabNoAndLabType(version, labType)) {
                    Integer patient = route.getDemographicNo();
                    if (patient != null && patient > 0) patients.add(patient);
                }
            }
            for (int patient : patients) {
                if (!security.hasPrivilege(info, "_lab", "w", String.valueOf(patient))) {
                    throw new SecurityException("Missing patient-specific lab write access");
                }
                // _lab$N alone misses a chart lock (_demographic$N / _eChart$N) on the same patient.
                if (!security.isAllowedAccessToPatientRecord(info, patient)) {
                    throw new SecurityException("access to patient record is restricted");
                }
            }
            List<Provider> providers = new ArrayList<>();
            for (String target : targets) {
                Provider provider = providerDao.getProvider(target);
                if (provider == null || !propertyDao.isActiveBooleanProperty(
                        Property.PROPERTY_KEY.hl7_allow_others_file_for_you, target)
                        || routingDao.findRoutingForUpdate(labNo, labType, target).isEmpty()) {
                    throw new SecurityException("Filing target is not routed or has not consented");
                }
                providers.add(provider);
            }
            int changed = 0;
            String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd @ h:mma z"));
            for (int p = 0; p < targets.size(); p++) {
                String target = targets.get(p);
                String attribution = "Filed by " + info.getLoggedInProvider().getFormattedName()
                        + " on behalf of " + providers.get(p).getFormattedName() + " on " + timestamp;
                if (comment != null && !comment.isBlank()) attribution += "\n" + comment;
                for (int version : versions) {
                    List<ProviderLabRoutingModel> rows = routingDao.findRoutingForUpdate(version, labType, target);
                    List<ProviderLabRoutingModel> newRows = rows.stream()
                            .filter(row -> "N".equals(row.getStatus())).toList();
                    if (newRows.isEmpty()) continue;
                    int transitioned = routingDao.transitionNewRoutingRows(version, labType, target, 'F');
                    if (transitioned != newRows.size()) throw new IllegalStateException("Routing transition changed unexpectedly");
                    for (ProviderLabRoutingModel row : newRows) {
                        String previous = row.getComment();
                        row.setStatus("F");
                        row.setComment(previous == null || previous.isEmpty() ? attribution : previous + "\n" + attribution);
                        row.setTimestamp(new Date());
                        routingDao.merge(row);
                        // Strict audit joins this transaction: audit failure rolls back the filing.
                        LogAction.addLogStrict(info, "lab.fileOnBehalf", LogSafe.sanitize(String.valueOf(version)),
                                null, null, "filedFor=" + LogSafe.sanitize(target));
                    }
                    changed += transitioned;
                }
            }
            return changed;
        });
    }

    List<Integer> olderVersions(int labNo) {
        return LabVersionChain.olderThan(labNo,
                new CommonLabResultData().getMatchingLabs(String.valueOf(labNo), "HL7"));
    }
}
