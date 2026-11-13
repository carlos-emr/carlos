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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobOwnerDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconstructs current account/provider/facility and permissions at each disclosure boundary. A
 * saved job actor is identity, not a saved grant; logout alone does not cancel accepted work. Each
 * call owns a fresh read-committed transaction, so an enclosing captured source snapshot cannot
 * keep a revoked permission alive. Never invoke while holding a maintenance capability that would
 * block these ordinary clinical security reads.
 */
@Service
@Transactional(
        readOnly = true,
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED)
public class CdsExportAccessService {
    private final SecurityInfoManager security;
    private final CdsExportConfigurationService configuration;
    private final ProviderDao providers;
    private final SecurityDao accounts;
    private final ProviderFacilityDao memberships;
    private final io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao notes;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public CdsExportAccessService(
            SecurityInfoManager security,
            CdsExportConfigurationService configuration,
            ProviderDao providers,
            SecurityDao accounts,
            ProviderFacilityDao memberships,
            io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao notes) {
        this.security = Objects.requireNonNull(security);
        this.configuration = Objects.requireNonNull(configuration);
        this.providers = Objects.requireNonNull(providers);
        this.accounts = Objects.requireNonNull(accounts);
        this.memberships = Objects.requireNonNull(memberships);
        this.notes = Objects.requireNonNull(notes);
    }

    public CdsExportActorDto webActor(LoggedInInfo supplied, CdsExportCommand command) {
        Security account = sessionAccount(supplied);
        CdsExportActorDto actor =
                new CdsExportActorDto(
                        "web-" + account.getSecurityNo(),
                        command.providerNo(),
                        account.getSecurityNo(),
                        command.facilityId());
        context(actor, command, null, null, false);
        return actor;
    }

    /** Caller must first verify the immutable root-issued exact-argv delegation ticket. */
    public CdsExportActorDto delegatedActor(String verifiedOperator, CdsExportCommand command) {
        CdsExportActorDto actor =
                new CdsExportActorDto(
                        verifiedOperator, command.providerNo(), null, command.facilityId());
        context(actor, command, null, null, false);
        return actor;
    }

    /**
     * Worker entry rechecks the recorded actor's current global scope before any patient discovery.
     */
    public void requireScope(CdsExportActorDto actor, CdsExportCommand command) {
        context(actor, command, null, null, false);
    }

    /**
     * Current delegated metadata identity; the provider establishes live administration, not a
     * replacement disclosure scope.
     */
    public CdsExportActorDto delegatedSession(
            String verifiedOperator, String provider, Integer historicalFacilityHint) {
        Integer facility = configuration.metadataFacility(verifiedOperator, historicalFacilityHint);
        var actor = new CdsExportActorDto(verifiedOperator, provider, null, facility);
        metadataContext(actor);
        return actor;
    }

    /**
     * Admission/capture/publication/retrieval callers recheck every selected category and patient.
     */
    public LoggedInInfo requirePatient(
            CdsExportActorDto actor, CdsExportCommand command, int patient) {
        requireSelectedPatient(command, patient);
        return context(actor, command, patient, null, false);
    }

    /**
     * Source adapters must additionally flag actual sensitive laboratory occurrences; absence of
     * that flag is not proof.
     */
    public LoggedInInfo requireCategory(
            CdsExportActorDto actor,
            CdsExportCommand command,
            int patient,
            CdsCategory category,
            boolean sensitiveLaboratory) {
        requireSelectedPatient(command, patient);
        if (category == null
                || !command.categories().contains(category)
                || sensitiveLaboratory && category != CdsCategory.LABORATORY_RESULTS) deny();
        return context(actor, command, patient, category, sensitiveLaboratory);
    }

    /**
     * Native pharmacy rights apply even when MedicationsAndTreatments is not selected for the wire.
     */
    public LoggedInInfo requirePharmacyRead(
            CdsExportActorDto actor, CdsExportCommand command, int patient) {
        requireSelectedPatient(command, patient);
        if (!command.categories().contains(CdsCategory.DEMOGRAPHICS)) deny();
        return context(actor, command, patient, CdsCategory.DEMOGRAPHICS, false, "_rx");
    }

    /**
     * Rechecks the exact note's live program and reporter-role dependencies for both identities,
     * after the ordinary category/patient gates and before any note payload is read or emitted. A
     * caller-supplied captured scope is compared with the current physical row, never trusted.
     */
    public void requireNoteRead(
            CdsExportActorDto actor,
            CdsExportCommand command,
            CdsCategory category,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto expected) {
        if (expected == null
                || category == null
                || !java.util.Set.of(
                                CdsCategory.PERSONAL_HISTORY,
                                CdsCategory.FAMILY_HISTORY,
                                CdsCategory.PAST_HEALTH,
                                CdsCategory.PROBLEM_LIST,
                                CdsCategory.RISK_FACTORS,
                                CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                                CdsCategory.CLINICAL_NOTES)
                        .contains(category)
                || command == null
                || !command.categories().contains(category)) deny();
        CurrentIdentity current = chartIdentity(actor, command, expected.patient(), category);
        if (!expected.equals(notes.findScope(expected.patient(), expected.noteId()))
                || !notes.canRead(
                        current.effective().getLoggedInProviderNo(),
                        expected,
                        command.facilityId(),
                        configuration.filterNotesOnFacility())
                || current.operator() != null
                        && !notes.canRead(
                                current.operator().getLoggedInProviderNo(),
                                expected,
                                command.facilityId(),
                                configuration.filterNotesOnFacility())) deny();
    }

    /**
     * Rechecks every exact retained note in one fresh patient transaction. Identical
     * program/reporter-role grants share that transaction only; nothing survives a boundary.
     */
    public void requireNotesRead(
            CdsExportActorDto actor,
            CdsExportCommand command,
            CdsCategory category,
            java.util.List<
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                    .CdsExportNoteAccessDto>
                    expected) {
        var batch = java.util.List.copyOf(expected);
        if (batch.isEmpty()
                || batch.size() > 8192
                || command == null
                || category == null
                || !CdsExportNoteSourceReader.CATEGORIES.contains(category)
                || !command.categories().contains(category)) deny();
        int patient = batch.getFirst().patient();
        long previous = 0;
        for (var scope : batch) {
            if (scope.patient() != patient || scope.noteId() <= previous) deny();
            previous = scope.noteId();
        }
        CurrentIdentity current = chartIdentity(actor, command, patient, category);
        var actual =
                notes.findScopes(patient, batch.stream().map(scope -> scope.noteId()).toList());
        if (!batch.equals(actual)) deny();
        record Grant(String program, String reporterRole) {}
        var checked = new java.util.HashSet<Grant>();
        boolean filter = configuration.filterNotesOnFacility();
        for (var scope : batch) {
            if (!checked.add(new Grant(scope.program(), scope.reporterRole()))) continue;
            if (!notes.canRead(
                            current.effective().getLoggedInProviderNo(),
                            scope,
                            command.facilityId(),
                            filter)
                    || current.operator() != null
                            && !notes.canRead(
                                    current.operator().getLoggedInProviderNo(), scope,
                                    command.facilityId(), filter)) deny();
        }
    }

    /**
     * Whether both identities work in the program that owns a patient issue, under the program
     * part of the note rule (patient domain, facility boundary, one unambiguous membership).
     * Issues have no reporter role. A false answer is a review condition for the caller, not an
     * access failure; the ordinary category and chart gates still throw.
     */
    public boolean canReadIssueProgram(
            CdsExportActorDto actor,
            CdsExportCommand command,
            CdsCategory category,
            int patient,
            String program) {
        if (program == null
                || category == null
                || command == null
                || !CdsExportNoteSourceReader.CATEGORIES.contains(category)
                || !command.categories().contains(category)) deny();
        CurrentIdentity current = chartIdentity(actor, command, patient, category);
        boolean filter = configuration.filterNotesOnFacility();
        return notes.canReadProgram(
                        current.effective().getLoggedInProviderNo(),
                        patient,
                        program,
                        command.facilityId(),
                        filter)
                && (current.operator() == null
                        || notes.canReadProgram(
                                current.operator().getLoggedInProviderNo(),
                                patient,
                                program,
                                command.facilityId(),
                                filter));
    }

    /**
     * Fresh program check for a document restricted to a program. The chart shows such a document
     * only to a provider who works in its program, so every current identity (the effective
     * provider and any operator) must; the ordinary Reports chart rights are required as well. A
     * false answer holds the patient; nothing here grants a document the chart would not show.
     */
    public boolean canReadDocumentProgram(
            CdsExportActorDto actor, CdsExportCommand command, int patient, String program) {
        if (program == null
                || !program.matches("-?[0-9]{1,10}")
                || command == null
                || !command.categories().contains(CdsCategory.REPORTS)) deny();
        long value = Long.parseLong(program);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) deny();
        CurrentIdentity current = chartIdentity(actor, command, patient, CdsCategory.REPORTS);
        return notes.canReadDocumentProgram(
                        current.effective().getLoggedInProviderNo(), (int) value)
                && (current.operator() == null
                        || notes.canReadDocumentProgram(
                                current.operator().getLoggedInProviderNo(), (int) value));
    }

    /**
     * Complete issue/catalogue reads use the live chart gate even for a patient with no native
     * notes.
     */
    public void requireCaseManagementRead(
            CdsExportActorDto actor, CdsExportCommand command, int patient) {
        if (command == null
                || !command.categories().containsAll(CdsExportNoteSourceReader.CATEGORIES)) deny();
        CurrentIdentity current =
                chartIdentity(actor, command, patient, CdsCategory.CLINICAL_NOTES);
        if (!notes.canReadChart(current.effective().getLoggedInProviderNo(), patient)
                || current.operator() != null
                        && !notes.canReadChart(current.operator().getLoggedInProviderNo(), patient))
            deny();
    }

    private CurrentIdentity chartIdentity(
            CdsExportActorDto actor, CdsExportCommand command, int patient, CdsCategory category) {
        requireSelectedPatient(command, patient);
        CurrentIdentity current = identity(actor, command);
        requireReads(current.effective(), command, patient, category, false);
        if (current.operator() != null)
            requireReads(current.operator(), command, patient, category, false);
        for (var context :
                current.operator() == null
                        ? java.util.List.of(current.effective())
                        : java.util.List.of(current.effective(), current.operator())) {
            if (!security.hasPrivilegeReadOnly(context, "_eChart", "r", null)
                    || !security.hasPrivilegeReadOnly(
                            context, "_eChart", "r", Integer.toString(patient))) deny();
        }
        return current;
    }

    private static void requireSelectedPatient(CdsExportCommand command, int patient) {
        if (patient < 1
                || command == null
                || command.selection() == CdsExportCommand.Selection.SELECTED_PATIENTS
                        && java.util.Collections.binarySearch(command.patientNos(), patient) < 0)
            deny();
    }

    /**
     * Owner or current administrator may observe/control metadata; this never substitutes for
     * clinical publication/download authority.
     */
    public void requireObserve(CdsExportActorDto observer, CdsExportJobOwnerDto owner) {
        LoggedInInfo current = metadataContext(observer);
        if (!Objects.equals(observer.operatorKey(), owner.operatorKey())
                && !security.hasPrivilegeReadOnly(current, "_admin", "w", null)) deny();
    }

    /** Compatibility overload for trusted callers already holding a validated immutable intent. */
    public void requireObserve(CdsExportActorDto observer, CdsExportJobDocumentDto document) {
        requireObserve(
                observer,
                new CdsExportJobOwnerDto(
                        document.actor().operatorKey(),
                        document.command().providerNo(),
                        document.command().facilityId()));
    }

    private LoggedInInfo metadataContext(CdsExportActorDto actor) {
        if (actor == null) deny();
        Security account = actor.securityNo() == null ? null : activeAccount(actor.securityNo());
        if (account == null)
            configuration.requireDelegation(
                    actor.operator(), actor.providerNo(), actor.facilityId());
        else {
            if (!actor.operator().equals("web-" + account.getSecurityNo())) deny();
            // A disclosure actor can carry the explicitly delegated provider rather than
            // the account's own provider. Both identities remain live authorization gates.
            LoggedInInfo operator =
                    providerContext(account.getProviderNo(), actor.facilityId(), account);
            requireAdministration(operator);
            requireMetadataRead(operator);
            if (!Objects.equals(actor.providerNo(), account.getProviderNo()))
                configuration.requireDelegation(
                        actor.operator(), actor.providerNo(), actor.facilityId());
        }
        LoggedInInfo current = providerContext(actor.providerNo(), actor.facilityId(), account);
        requireAdministration(current);
        requireMetadataRead(current);
        return current;
    }

    /**
     * Metadata/history authorization without import admission, clinical writes, or a fabricated
     * export command.
     */
    public CdsExportActorDto operatorSession(LoggedInInfo supplied) {
        Security account = sessionAccount(supplied);
        Integer facility =
                supplied.getCurrentFacility() == null
                        ? null
                        : supplied.getCurrentFacility().getId();
        LoggedInInfo current = providerContext(account.getProviderNo(), facility, account);
        requireAdministration(current);
        if (!security.hasPrivilegeReadOnly(current, "_demographic", "r", null)) deny();
        return new CdsExportActorDto(
                "web-" + account.getSecurityNo(),
                account.getProviderNo(),
                account.getSecurityNo(),
                facility);
    }

    private LoggedInInfo context(
            CdsExportActorDto actor,
            CdsExportCommand command,
            Integer patient,
            CdsCategory only,
            boolean sensitive) {
        return context(actor, command, patient, only, sensitive, null);
    }

    private LoggedInInfo context(
            CdsExportActorDto actor,
            CdsExportCommand command,
            Integer patient,
            CdsCategory only,
            boolean sensitive,
            String additionalReadObject) {
        CurrentIdentity current = identity(actor, command);
        requireReads(current.effective(), command, patient, only, sensitive);
        if (additionalReadObject != null
                && (!security.hasPrivilegeReadOnly(
                                current.effective(), additionalReadObject, "r", null)
                        || !security.hasPrivilegeReadOnly(
                                current.effective(),
                                additionalReadObject,
                                "r",
                                patient.toString()))) deny();
        if (current.operator() != null) {
            requireReads(current.operator(), command, patient, only, sensitive);
            if (additionalReadObject != null
                    && (!security.hasPrivilegeReadOnly(
                                    current.operator(), additionalReadObject, "r", null)
                            || !security.hasPrivilegeReadOnly(
                                    current.operator(),
                                    additionalReadObject,
                                    "r",
                                    patient.toString()))) deny();
        }
        return current.effective();
    }

    private CurrentIdentity identity(CdsExportActorDto actor, CdsExportCommand command) {
        if (actor == null
                || !Objects.equals(actor.providerNo(), command.providerNo())
                || !Objects.equals(actor.facilityId(), command.facilityId())) deny();
        Security account = actor.securityNo() == null ? null : activeAccount(actor.securityNo());
        LoggedInInfo operator = null;
        if (account != null) {
            if (!actor.operator().equals("web-" + account.getSecurityNo())) deny();
            operator = providerContext(account.getProviderNo(), actor.facilityId(), account);
            requireAdministration(operator);
            if (!Objects.equals(actor.providerNo(), account.getProviderNo())) {
                configuration.requireDelegation(
                        actor.operator(), actor.providerNo(), actor.facilityId());
            }
        } else
            configuration.requireDelegation(
                    actor.operator(), actor.providerNo(), actor.facilityId());
        LoggedInInfo effective = providerContext(actor.providerNo(), actor.facilityId(), account);
        requireAdministration(effective);
        if (operator != null && Objects.equals(actor.providerNo(), account.getProviderNo()))
            operator = null;
        effective.setInitiatingCode("CdsExport/" + actor.operatorKey());
        effective.setIp(actor.securityNo() == null ? "cli/delegated" : "web/export-worker");
        return new CurrentIdentity(effective, operator);
    }

    private void requireReads(
            LoggedInInfo context,
            CdsExportCommand command,
            Integer patient,
            CdsCategory only,
            boolean sensitive) {
        String target = patient == null ? null : patient.toString();
        // A role-level (patient == null) refusal names the missing security object so an operator
        // can grant it before resubmitting; a patient-level refusal stays generic.
        if (!security.hasPrivilegeReadOnly(context, "_demographic", "r", target))
            refuse(patient, "_demographic");
        if (patient != null && !security.isAllowedAccessToPatientRecord(context, patient)) deny();
        for (CdsCategory category : command.categories()) {
            if (only != null && category != only) continue;
            String object =
                    io.github.carlos_emr.carlos.demographic.cds.security.CdsCategoryReadPermission
                            .object(category);
            if (!security.hasPrivilegeReadOnly(context, object, "r", target))
                refuse(patient, object);
        }
        // Laboratory capture requires the sensitive-result right for every selected patient
        // before any row is read, whether or not the chart holds a sensitive result. Check the
        // role-level right at admission and worker entry so a missing grant is refused up front
        // (authorization) instead of after acceptance as a changed-access review.
        boolean laboratoryScope =
                patient == null
                        && command.categories().contains(CdsCategory.LABORATORY_RESULTS)
                        && (only == null || only == CdsCategory.LABORATORY_RESULTS);
        if ((sensitive || laboratoryScope)
                && !security.hasPrivilegeReadOnly(context, "_lab_sensitive", "r", target))
            refuse(patient, "_lab_sensitive");
        // ProblemList carries the Disease Registry, whose own screen needs _dxresearch read. It
        // is required whenever ProblemList is selected, by name at admission and again for each
        // patient, whether or not the chart holds a registry entry.
        if (command.categories().contains(CdsCategory.PROBLEM_LIST)
                && (only == null || only == CdsCategory.PROBLEM_LIST)
                && !security.hasPrivilegeReadOnly(context, DISEASE_REGISTRY_READ, "r", target))
            refuse(patient, DISEASE_REGISTRY_READ);
    }

    /** The Disease Registry's own read object, required with ProblemList. */
    static final String DISEASE_REGISTRY_READ = "_dxresearch";

    private static void refuse(Integer patient, String object) {
        if (patient == null) throw new SecurityException("missing required sec object (" + object + ")");
        deny();
    }

    /**
     * The security object named by a role-level export refusal, or null. Only fixed object names
     * matching CARLOS's own pattern are returned, never request or patient data.
     */
    public static String missingObject(Throwable refused) {
        if (!(refused instanceof SecurityException) || refused.getMessage() == null) return null;
        var matcher =
                java.util.regex.Pattern.compile("missing required sec object \\((_[A-Za-z0-9_.]{1,64})\\)")
                        .matcher(refused.getMessage());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private void requireAdministration(LoggedInInfo actor) {
        if (!security.hasPrivilegeReadOnly(actor, "_admin", "w", null)
                && !security.hasPrivilegeReadOnly(actor, "_cds_export", "w", null)) deny();
    }

    private Security sessionAccount(LoggedInInfo supplied) {
        if (supplied == null
                || supplied.getLoggedInSecurity() == null
                || supplied.getLoggedInProviderNo() == null) deny();
        Security account = activeAccount(supplied.getLoggedInSecurity().getSecurityNo());
        if (!Objects.equals(account.getProviderNo(), supplied.getLoggedInProviderNo())) deny();
        return account;
    }

    private Security activeAccount(Integer number) {
        Security account = number == null ? null : accounts.find(number.intValue());
        if (account == null
                || Integer.valueOf(1).equals(account.getBExpireset())
                        && account.getDateExpiredate() != null
                        && account.getDateExpiredate().before(new Date())) deny();
        return account;
    }

    private LoggedInInfo providerContext(String number, Integer facilityId, Security account) {
        Provider provider = providers.getProvider(number);
        if (provider == null
                || !Objects.equals(number, provider.getProviderNo())
                || !"1".equals(provider.getStatus())) deny();
        LoggedInInfo context = new LoggedInInfo();
        context.setLoggedInProvider(provider);
        context.setLoggedInSecurity(account);
        if (facilityId != null) {
            Facility facility = entityManager.find(Facility.class, facilityId);
            if (facility == null || memberships.countByProviderAndFacility(number, facilityId) == 0)
                deny();
            context.setCurrentFacility(facility);
        }
        return context;
    }

    private void requireMetadataRead(LoggedInInfo context) {
        if (!security.hasPrivilegeReadOnly(context, "_demographic", "r", null)) deny();
    }

    private record CurrentIdentity(LoggedInInfo effective, LoggedInInfo operator) {
        @Override
        public String toString() {
            return "CurrentIdentity[protected]";
        }
    }

    private static void deny() {
        throw new SecurityException("CDS_EXPORT_AUTHORIZATION_DENIED");
    }
}
