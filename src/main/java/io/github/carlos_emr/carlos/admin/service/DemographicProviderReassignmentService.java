/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic, scoped bulk care-team reassignment.
 *
 * @since 2026-10-17
 */
@Service
public class DemographicProviderReassignmentService {
    private final DemographicManager demographics;
    private final ProviderManager2 providers;
    private final SecurityInfoManager security;

    public DemographicProviderReassignmentService(DemographicManager demographics, ProviderManager2 providers,
                                                  SecurityInfoManager security) {
        this.demographics = demographics;
        this.providers = providers;
        this.security = security;
    }

    @Transactional
    // FindSecBugs IMPROPER_UNICODE: maps a validated ASCII A-Z range, never a security identity. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Name-range endpoints are restricted to one ASCII A-Z/a-z character before Locale.ROOT case mapping; Unicode input is rejected")
    public int reassign(LoggedInInfo user, String operation, String oldProvider, String newProvider,
                        String from, String to) {
        if (user == null || !security.hasPrivilege(user, "_admin.misc", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.misc w)");
        }
        if (from == null || to == null || !from.matches("[A-Za-z]") || !to.matches("[A-Za-z]")
                || from.toUpperCase(Locale.ROOT).compareTo(to.toUpperCase(Locale.ROOT)) > 0
                || oldProvider == null || newProvider == null || !oldProvider.matches("[0-9]*")
                || !newProvider.matches("[0-9]*")) {
            throw new IllegalArgumentException("Select valid providers and an ordered A-Z name range.");
        }
        Provider old = oldProvider.isEmpty() ? null : providers.getProvider(user, oldProvider);
        if ((!oldProvider.isEmpty() && old == null)
                || (!newProvider.isEmpty() && providers.getProvider(user, newProvider) == null)) {
            throw new IllegalArgumentException("Select existing providers.");
        }
        String regex = "^[" + from.toUpperCase(Locale.ROOT) + "-" + to.toUpperCase(Locale.ROOT) + "]";
        if ("UpdateMrp".equals(operation)) {
            // The existing form offers "No Provider"; an empty replacement clears the MRP.
            // Its empty source selection historically matches no MRP records.
            if (old == null) return 0;
            List<Demographic> patients = demographics.getDemographicsNameRangeByProvider(user, old, regex);
            for (Demographic patient : patients) requirePatientWrite(user, patient.getDemographicNo());
            for (Demographic patient : patients) {
                // Keep the managed original intact until the manager archives it.
                Demographic replacement = new Demographic(patient);
                replacement.setProviderNo(newProvider);
                demographics.updateDemographic(user, replacement);
            }
            return patients.size();
        }
        List<Integer> ids = switch (operation) {
            case "UpdateResident" -> demographics.getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(user, oldProvider, regex);
            case "UpdateNurse" -> demographics.getDemographicNumbersByNurseNumberAndDemographicLastNameRegex(user, oldProvider, regex);
            case "UpdateMidwife" -> demographics.getDemographicNumbersByMidwifeNumberAndDemographicLastNameRegex(user, oldProvider, regex);
            default -> throw new IllegalArgumentException("Unknown reassignment operation.");
        };
        // Check every affected patient before modifying even an in-memory entity.
        for (Integer id : ids) requirePatientWrite(user, id);
        if (ids.isEmpty()) return 0;
        List<DemographicExt> extensions = switch (operation) {
            case "UpdateResident" -> demographics.getMultipleResidentForDemographicNumbersByProviderNumber(user, ids, oldProvider);
            case "UpdateNurse" -> demographics.getMultipleNurseForDemographicNumbersByProviderNumber(user, ids, oldProvider);
            default -> demographics.getMultipleMidwifeForDemographicNumbersByProviderNumber(user, ids, oldProvider);
        };
        for (DemographicExt extension : extensions) {
            if (!ids.contains(extension.getDemographicNo())) throw new SecurityException("Patient outside reassignment scope");
        }
        for (DemographicExt extension : extensions) {
            // updateExtension reads the original from the same persistence context to archive it.
            DemographicExt replacement = new DemographicExt(extension.getId(), extension.getProviderNo(),
                    extension.getDemographicNo(), extension.getKey(), newProvider);
            replacement.setDateCreated(extension.getDateCreated());
            replacement.setHidden(extension.isHidden());
            demographics.updateExtension(user, replacement);
        }
        return extensions.size();
    }

    private void requirePatientWrite(LoggedInInfo user, Integer id) {
        if (id == null || !security.hasPrivilege(user, "_demographic", "u", String.valueOf(id))
                || !security.isAllowedAccessToPatientRecord(user, id)) {
            throw new SecurityException("missing required patient access for reassignment");
        }
    }
}
