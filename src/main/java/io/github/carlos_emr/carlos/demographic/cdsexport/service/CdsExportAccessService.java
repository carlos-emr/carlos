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

    /**
     * The authorization rows already read by the pass running on this thread, or nothing.
     *
     * <p>A pass is one public call of this service, so one fresh read-committed transaction, that
     * makes many checks for one actor, one command and one patient ({@link #decidePatient},
     * {@link #recheckPatient}, {@link #requireNotesRead}). Each check is the ordinary one: the
     * same method, the same {@link SecurityInfoManager} evaluation. Only the rows behind them are
     * read once instead of once per check: each identity's roles and security-object rows
     * ({@link SecurityInfoManager#readOnlySnapshot}), the provider, account and facility
     * membership rows of the identity, and each program and reporter-role note grant. The rows
     * are bound here by {@link #inPass} for the duration of that one call and removed before it
     * returns, whatever its outcome. They never reach another transaction, patient, job or
     * thread, and no decision is derived from a pass that has ended.
     */
    private final ThreadLocal<Pass> pass = new ThreadLocal<>();

    private static final class Pass {
        private final java.util.List<String> objects;
        private final java.util.Map<String, SecurityInfoManager.PrivilegeSnapshot> snapshots =
                new java.util.HashMap<>();
        private final java.util.Map<java.util.List<Object>, Boolean> noteGrants =
                new java.util.HashMap<>();
        private CdsExportActorDto actor;
        private CdsExportCommand command;
        private CurrentIdentity identity;

        private Pass(java.util.List<String> objects) {
            this.objects = objects;
        }
    }

    /** Every security object a check of this command may ask about, for one read ahead. */
    private static java.util.List<String> objects(CdsExportCommand command, Integer patient) {
        var names =
                new java.util.LinkedHashSet<>(
                        java.util.List.of(
                                "_admin",
                                "_cds_export",
                                "_demographic",
                                "_lab_sensitive",
                                PHARMACY_READ,
                                CHART_READ,
                                DISEASE_REGISTRY_READ,
                                EFORM_READ));
        names.addAll(new java.util.TreeSet<>(RECORD_OBJECTS));
        for (CdsCategory category : command.categories())
            names.add(
                    io.github.carlos_emr.carlos.demographic.cds.security.CdsCategoryReadPermission
                            .object(category));
        var all = new java.util.ArrayList<>(names);
        if (patient != null) for (String name : names) all.add(name + "$" + patient);
        return java.util.List.copyOf(all);
    }

    /** Runs {@code work} as one pass; a pass already running on this thread is continued. */
    private <T> T inPass(
            CdsExportCommand command, Integer patient, java.util.function.Supplier<T> work) {
        if (command == null || pass.get() != null) return work.get();
        pass.set(new Pass(objects(command, patient)));
        try {
            return work.get();
        } finally {
            pass.remove();
        }
    }

    /** {@link SecurityInfoManager#hasPrivilegeReadOnly}, from the running pass's rows if any. */
    private boolean privilege(LoggedInInfo context, String object, String right, String target) {
        var snapshot = snapshot(context);
        return snapshot == null
                ? security.hasPrivilegeReadOnly(context, object, right, target)
                : snapshot.hasPrivilege(object, right, target);
    }

    /** {@link SecurityInfoManager#isAllowedAccessToPatientRecord}, likewise. */
    private boolean patientRecordAllowed(LoggedInInfo context, Integer patient) {
        var snapshot = snapshot(context);
        return snapshot == null
                ? security.isAllowedAccessToPatientRecord(context, patient)
                : snapshot.isAllowedAccessToPatientRecord(patient);
    }

    private SecurityInfoManager.PrivilegeSnapshot snapshot(LoggedInInfo context) {
        var current = pass.get();
        if (current == null || context == null || context.getLoggedInProviderNo() == null)
            return null;
        // Roles and privileges are those of the provider number; one snapshot per provider.
        return current.snapshots.computeIfAbsent(
                context.getLoggedInProviderNo(),
                ignored -> security.readOnlySnapshot(context, current.objects));
    }

    /** The program and reporter-role grant of a note, asked once per pass for each provider. */
    private boolean noteReadable(
            String provider,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto scope,
            Integer facility,
            boolean filter) {
        var current = pass.get();
        if (current == null || provider == null)
            return notes.canRead(provider, scope, facility, filter);
        // The grant depends on these values only, never on which note of the patient carries them.
        var key =
                java.util.Arrays.<Object>asList(
                        provider,
                        scope.patient(),
                        scope.program(),
                        scope.reporterRole(),
                        facility,
                        filter);
        Boolean known = current.noteGrants.get(key);
        if (known == null) {
            known = notes.canRead(provider, scope, facility, filter);
            current.noteGrants.put(key, known);
        }
        return known;
    }

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
     * The identity an eForm of this patient is rendered as: the job's effective provider, after
     * the same live checks as any Reports read of the patient (including {@code _eform} read, for
     * both identities). The renderer re-checks {@code _eform} itself; it never runs as a system
     * identity with wider rights.
     */
    public LoggedInInfo requireEformRendering(
            CdsExportActorDto actor, CdsExportCommand command, int patient) {
        return requireCategory(actor, command, patient, CdsCategory.REPORTS, false);
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
        requireNoteCategory(command, category, expected);
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

    private static void requireNoteCategory(
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
    }

    /**
     * One patient's note grants inside one export worker's authorization session
     * ({@link CdsExportPatientAuthorization}). It lives only while that worker captures and emits
     * that one patient, is never shared with another patient, job or thread, and is discarded
     * once the session ends; the session's closing check runs every grant again, fresh
     * ({@link #requireNotesRead}).
     */
    public static final class PatientGrants {
        private final int patient;
        private final java.util.Map<CdsCategory, CurrentIdentity> chart =
                new java.util.EnumMap<>(CdsCategory.class);
        private final java.util.Set<String> noteGrants = new java.util.HashSet<>();

        public PatientGrants(int patient) {
            if (patient < 1) deny();
            this.patient = patient;
        }

        public int patient() {
            return patient;
        }

        @Override
        public String toString() {
            return "CdsExportAccessService.PatientGrants[protected]";
        }
    }

    /**
     * {@link #requireNoteRead}, inside one patient's authorization session. The note's exact live
     * scope is still read for every note, but the chart gates of the category and the program and
     * reporter-role grant are evaluated once per patient: they are the same for every note of that
     * patient with the same program and role, as {@link #requireNotesRead} already treats them.
     * Freshness is restored by the session's closing check, which re-runs every grant uncached
     * before the patient's output is accepted.
     */
    public void requireNoteRead(
            CdsExportActorDto actor,
            CdsExportCommand command,
            CdsCategory category,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto expected,
            PatientGrants grants) {
        requireNoteCategory(command, category, expected);
        if (grants == null || expected.patient() != grants.patient) deny();
        CurrentIdentity current = grants.chart.get(category);
        if (current == null) {
            current = chartIdentity(actor, command, expected.patient(), category);
            grants.chart.put(category, current);
        }
        if (!expected.equals(notes.findScope(expected.patient(), expected.noteId()))) deny();
        String grant =
                category.name() + '\u0000' + expected.program() + '\u0000' + expected.reporterRole();
        if (grants.noteGrants.contains(grant)) return;
        boolean filter = configuration.filterNotesOnFacility();
        if (!notes.canRead(
                        current.effective().getLoggedInProviderNo(),
                        expected,
                        command.facilityId(),
                        filter)
                || current.operator() != null
                        && !notes.canRead(
                                current.operator().getLoggedInProviderNo(),
                                expected,
                                command.facilityId(),
                                filter)) deny();
        grants.noteGrants.add(grant);
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
        inPass(
                command,
                patient,
                () -> {
                    CurrentIdentity current = chartIdentity(actor, command, patient, category);
                    var actual =
                            notes.findScopes(
                                    patient, batch.stream().map(scope -> scope.noteId()).toList());
                    if (!batch.equals(actual)) deny();
                    record Grant(String program, String reporterRole) {}
                    var checked = new java.util.HashSet<Grant>();
                    boolean filter = configuration.filterNotesOnFacility();
                    for (var scope : batch) {
                        if (!checked.add(new Grant(scope.program(), scope.reporterRole())))
                            continue;
                        if (!noteReadable(
                                        current.effective().getLoggedInProviderNo(),
                                        scope,
                                        command.facilityId(),
                                        filter)
                                || current.operator() != null
                                        && !noteReadable(
                                                current.operator().getLoggedInProviderNo(),
                                                scope,
                                                command.facilityId(),
                                                filter)) deny();
                    }
                    return null;
                });
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
     * The categories whose records may be restricted to a program: a document (Reports) and a
     * prevention (Immunizations, PastHealth screenings).
     */
    static final java.util.Set<CdsCategory> RESTRICTED_PROGRAM_CATEGORIES =
            java.util.Set.of(CdsCategory.REPORTS, CdsCategory.IMMUNIZATIONS, CdsCategory.PAST_HEALTH);

    /**
     * Fresh program check for a record restricted to a program: a document, or a legacy
     * prevention row carrying OSCAR 19/CAISI {@code restrictToProgram}/{@code programNo}. The
     * chart shows a document restricted to a program only to a provider who works in it, and the
     * same rule is applied to such a prevention, so every current identity (the effective
     * provider and any operator) must work in the program; the category's ordinary chart rights
     * are required as well. A false answer holds the patient; nothing here grants a record the
     * chart would not show.
     */
    public boolean canReadRestrictedProgram(
            CdsExportActorDto actor,
            CdsExportCommand command,
            CdsCategory category,
            int patient,
            String program) {
        if (program == null
                || !program.matches("-?[0-9]{1,10}")
                || command == null
                || category == null
                || !RESTRICTED_PROGRAM_CATEGORIES.contains(category)
                || !command.categories().contains(category)) deny();
        long value = Long.parseLong(program);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) deny();
        CurrentIdentity current = chartIdentity(actor, command, patient, category);
        return notes.canReadRestrictedProgram(
                        current.effective().getLoggedInProviderNo(), (int) value)
                && (current.operator() == null
                        || notes.canReadRestrictedProgram(
                                current.operator().getLoggedInProviderNo(), (int) value));
    }

    /** The record security objects a printed form may require ({@link #canReadRecordObject}). */
    static final java.util.Set<String> RECORD_OBJECTS = java.util.Set.of("_con", "_form");

    /**
     * Fresh check that every current identity (the effective provider and any operator) may read
     * a record's own security object for the patient, at role and patient level, after the
     * ordinary Reports chart gates (which still throw). The chart requires it before it shows or
     * prints a consultation ({@code _con}) or an encounter form ({@code _form}); a false answer
     * leaves that record exported as its data summary only and grants nothing.
     */
    public boolean canReadRecordObject(
            CdsExportActorDto actor, CdsExportCommand command, int patient, String object) {
        if (object == null
                || !RECORD_OBJECTS.contains(object)
                || command == null
                || !command.categories().contains(CdsCategory.REPORTS)) deny();
        CurrentIdentity current = chartIdentity(actor, command, patient, CdsCategory.REPORTS);
        for (var context :
                current.operator() == null
                        ? java.util.List.of(current.effective())
                        : java.util.List.of(current.effective(), current.operator()))
            if (!privilege(context, object, "r", null)
                    || !privilege(context, object, "r", Integer.toString(patient)))
                return false;
        return true;
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

    /**
     * A fixed gate of one patient's export: the patient gate ({@link #requirePatient}), the
     * preferred-pharmacy gate ({@link #requirePharmacyRead}), the case-management gate
     * ({@link #requireCaseManagementRead}) or one category's gate ({@link #requireCategory}).
     */
    public record Gate(Kind kind, CdsCategory category, boolean sensitiveLaboratory) {
        /** Which check a gate is. */
        public enum Kind {
            PATIENT,
            PHARMACY,
            CASE_MANAGEMENT,
            CATEGORY
        }

        public static final Gate PATIENT = new Gate(Kind.PATIENT, null, false);
        public static final Gate PHARMACY = new Gate(Kind.PHARMACY, null, false);
        public static final Gate CASE_MANAGEMENT = new Gate(Kind.CASE_MANAGEMENT, null, false);

        public Gate {
            Objects.requireNonNull(kind);
            if ((kind == Kind.CATEGORY) != (category != null)
                    || sensitiveLaboratory && kind != Kind.CATEGORY)
                throw new IllegalArgumentException("CDS_EXPORT_AUTHORIZATION_GATE_INVALID");
        }

        public static Gate category(CdsCategory category, boolean sensitiveLaboratory) {
            return new Gate(Kind.CATEGORY, Objects.requireNonNull(category), sensitiveLaboratory);
        }

        @Override
        public String toString() {
            return "CdsExportAccessService.Gate[protected]";
        }
    }

    /**
     * A yes/no question about one patient whose "yes" a record depends on: whether the program
     * of a patient issue is readable ({@link #canReadIssueProgram}), whether a program a record is
     * restricted to is readable ({@link #canReadRestrictedProgram}) or whether a record's own
     * security object is readable ({@link #canReadRecordObject}).
     */
    public record Question(Kind kind, CdsCategory category, String value) {
        /** Which check a question is. */
        public enum Kind {
            ISSUE_PROGRAM,
            RESTRICTED_PROGRAM,
            RECORD
        }

        public Question {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(value);
            if ((kind == Kind.RECORD) != (category == null))
                throw new IllegalArgumentException("CDS_EXPORT_AUTHORIZATION_QUESTION_INVALID");
        }

        public static Question issueProgram(CdsCategory category, String program) {
            return new Question(Kind.ISSUE_PROGRAM, Objects.requireNonNull(category), program);
        }

        public static Question restrictedProgram(CdsCategory category, String program) {
            return new Question(
                    Kind.RESTRICTED_PROGRAM, Objects.requireNonNull(category), program);
        }

        public static Question record(String object) {
            return new Question(Kind.RECORD, null, object);
        }

        @Override
        public String toString() {
            return "CdsExportAccessService.Question[protected]";
        }
    }

    /**
     * What {@link #decidePatient} decided for one patient in one pass: every fixed gate and
     * every record-object question of the command, each granted or refused by its ordinary
     * check, and the chart identities the patient's note checks start from. It answers for that
     * one patient only. Its owner (one export worker's authorization session or one
     * re-authorization pass) discards it when that patient is finished; a session's grants are
     * all checked again, from the database, by {@link #recheckPatient} before the patient's
     * output is accepted.
     */
    public static final class PatientDecisions {
        private final int patient;
        private final java.util.Map<Gate, String> gates = new java.util.HashMap<>();
        private final java.util.Map<Question, Object> answers = new java.util.HashMap<>();
        private final PatientGrants grants;

        PatientDecisions(int patient) {
            this.grants = new PatientGrants(patient);
            this.patient = patient;
        }

        public int patient() {
            return patient;
        }

        /** Whether this pass decided the gate; an undecided gate must be checked on its own. */
        public boolean decided(Gate gate) {
            return gates.containsKey(gate);
        }

        /**
         * Passes a granted gate and refuses a refused one with the refusal its check gave.
         *
         * @throws SecurityException when the gate's check refused
         * @throws IllegalStateException when the gate was not decided by this pass
         */
        public void require(Gate gate) {
            String refusal = gates.get(gate);
            if (refusal == null)
                throw new IllegalStateException("CDS_EXPORT_AUTHORIZATION_GATE_UNDECIDED");
            if (!refusal.isEmpty()) throw new SecurityException(refusal);
        }

        /**
         * The answer this pass gave the question, or null when it did not ask it.
         *
         * @throws SecurityException when the question's own chart gates refused
         */
        public Boolean answer(Question question) {
            Object answer = answers.get(question);
            if (answer instanceof String refusal) throw new SecurityException(refusal);
            return (Boolean) answer;
        }

        /** The patient's note grants, starting from the chart identities this pass established. */
        public PatientGrants grants() {
            return grants;
        }

        void grant(Gate gate) {
            gates.put(Objects.requireNonNull(gate), "");
        }

        void refuse(Gate gate, String refusal) {
            gates.put(Objects.requireNonNull(gate), refusal(refusal));
        }

        void answer(Question question, boolean answer) {
            answers.put(Objects.requireNonNull(question), answer);
        }

        void refuse(Question question, String refusal) {
            answers.put(Objects.requireNonNull(question), refusal(refusal));
        }

        private static String refusal(String message) {
            return message == null || message.isEmpty()
                    ? "CDS_EXPORT_AUTHORIZATION_DENIED"
                    : message;
        }

        @Override
        public String toString() {
            return "CdsExportAccessService.PatientDecisions[protected]";
        }
    }

    /**
     * Everything one patient's authorization session granted, for {@link #recheckPatient}: the
     * gates it passed, every note it authorized (per category, in ascending note order) and
     * every question it answered yes.
     */
    public record PatientRecheck(
            int patient,
            java.util.Set<Gate> gates,
            java.util.Map<
                            CdsCategory,
                            java.util.List<
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportNoteAccessDto>>
                    notes,
            java.util.Set<Question> questions) {
        public PatientRecheck {
            gates = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(gates));
            var copy =
                    new java.util.EnumMap<
                            CdsCategory,
                            java.util.List<
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportNoteAccessDto>>(CdsCategory.class);
            notes.forEach((category, scopes) -> copy.put(category, java.util.List.copyOf(scopes)));
            notes = java.util.Collections.unmodifiableMap(copy);
            questions =
                    java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(questions));
        }

        @Override
        public String toString() {
            return "CdsExportAccessService.PatientRecheck[protected]";
        }
    }

    /** One fixed gate by its ordinary check, for a caller that holds no decisions for it. */
    public void requireGate(
            CdsExportActorDto actor, CdsExportCommand command, int patient, Gate gate) {
        switch (Objects.requireNonNull(gate).kind()) {
            case PATIENT -> requirePatient(actor, command, patient);
            case PHARMACY -> requirePharmacyRead(actor, command, patient);
            case CASE_MANAGEMENT -> requireCaseManagementRead(actor, command, patient);
            case CATEGORY ->
                    requireCategory(
                            actor, command, patient, gate.category(), gate.sensitiveLaboratory());
            default -> deny();
        }
    }

    /** One question by its ordinary check, for a caller that holds no decisions for it. */
    public boolean answerQuestion(
            CdsExportActorDto actor, CdsExportCommand command, int patient, Question question) {
        return switch (Objects.requireNonNull(question).kind()) {
            case ISSUE_PROGRAM ->
                    canReadIssueProgram(
                            actor, command, question.category(), patient, question.value());
            case RESTRICTED_PROGRAM ->
                    canReadRestrictedProgram(
                            actor, command, question.category(), patient, question.value());
            case RECORD -> canReadRecordObject(actor, command, patient, question.value());
        };
    }

    /**
     * The opening pass of one patient: decides every fixed gate and record-object question of
     * the command for this patient, each by its ordinary check, from one fresh read-committed
     * transaction. A check that refuses is recorded as refused and does not stop the others, so
     * a right the chart never needs (for example a record's printed form) cannot hold the
     * patient. Who is exporting is established first and refuses the whole pass.
     *
     * <p>The decisions are current as of this call. They are for the one patient session or
     * re-authorization pass that asked, and that owner must discard them when the patient is
     * finished; a session must end with {@link #recheckPatient}.
     *
     * @throws SecurityException when the patient is not selected or the exporting identities are
     *     no longer valid
     */
    public PatientDecisions decidePatient(
            CdsExportActorDto actor, CdsExportCommand command, int patient) {
        requireSelectedPatient(command, patient);
        return inPass(
                command,
                patient,
                () -> {
                    identity(actor, command);
                    var decisions = new PatientDecisions(patient);
                    var gates = new java.util.ArrayList<Gate>();
                    gates.add(Gate.PATIENT);
                    gates.add(Gate.PHARMACY);
                    if (command.categories().containsAll(CdsExportNoteSourceReader.CATEGORIES))
                        gates.add(Gate.CASE_MANAGEMENT);
                    for (CdsCategory category : command.categories())
                        gates.add(Gate.category(category, false));
                    if (command.categories().contains(CdsCategory.LABORATORY_RESULTS))
                        gates.add(Gate.category(CdsCategory.LABORATORY_RESULTS, true));
                    for (Gate gate : gates) {
                        try {
                            requireGate(actor, command, patient, gate);
                            decisions.grant(gate);
                        } catch (SecurityException refused) {
                            decisions.refuse(gate, refused.getMessage());
                        }
                    }
                    if (command.categories().contains(CdsCategory.REPORTS))
                        for (String object : new java.util.TreeSet<>(RECORD_OBJECTS)) {
                            var question = Question.record(object);
                            try {
                                decisions.answer(
                                        question,
                                        answerQuestion(actor, command, patient, question));
                            } catch (SecurityException refused) {
                                decisions.refuse(question, refused.getMessage());
                            }
                        }
                    for (CdsCategory category : command.categories()) {
                        if (!CdsExportNoteSourceReader.CATEGORIES.contains(category)) continue;
                        try {
                            decisions.grants.chart.put(
                                    category, chartIdentity(actor, command, patient, category));
                        } catch (SecurityException refused) {
                            // The first note check of this category establishes it, and is refused.
                        }
                    }
                    // A read that failed under one of these checks was answered as a refusal, but
                    // it also condemned this transaction: refuse the whole pass rather than let the
                    // commit fail as an infrastructure error after the decisions were returned.
                    if (org.springframework.transaction.support.TransactionSynchronizationManager
                                    .isActualTransactionActive()
                            && org.springframework.transaction.interceptor.TransactionAspectSupport
                                    .currentTransactionStatus()
                                    .isRollbackOnly()) deny();
                    return decisions;
                });
    }

    /**
     * The closing pass of one patient's authorization session: every gate the session passed,
     * every note it authorized and every question it answered yes, checked again by its ordinary
     * check from one fresh read-committed transaction. Nothing of the session is trusted; only
     * the list of what to check comes from it.
     *
     * @throws SecurityException when any check refuses, or a question no longer answers yes
     *     ({@code CDS_EXPORT_ACCESS_CHANGED})
     */
    public void recheckPatient(
            CdsExportActorDto actor, CdsExportCommand command, PatientRecheck request) {
        Objects.requireNonNull(request);
        int patient = request.patient();
        requireSelectedPatient(command, patient);
        inPass(
                command,
                patient,
                () -> {
                    identity(actor, command);
                    for (Gate gate : request.gates()) requireGate(actor, command, patient, gate);
                    for (var entry : request.notes().entrySet()) {
                        var scopes = entry.getValue();
                        for (int start = 0; start < scopes.size(); start += 8192)
                            requireNotesRead(
                                    actor,
                                    command,
                                    entry.getKey(),
                                    scopes.subList(start, Math.min(scopes.size(), start + 8192)));
                    }
                    for (Question question : request.questions())
                        if (!answerQuestion(actor, command, patient, question))
                            throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
                    return null;
                });
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
            if (!privilege(context, "_eChart", "r", null)
                    || !privilege(
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
                && !privilege(current, "_admin", "w", null)) deny();
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
        if (!privilege(current, "_demographic", "r", null)) deny();
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
                && (!privilege(
                                current.effective(), additionalReadObject, "r", null)
                        || !privilege(
                                current.effective(),
                                additionalReadObject,
                                "r",
                                patient.toString()))) deny();
        if (current.operator() != null) {
            requireReads(current.operator(), command, patient, only, sensitive);
            if (additionalReadObject != null
                    && (!privilege(
                                    current.operator(), additionalReadObject, "r", null)
                            || !privilege(
                                    current.operator(),
                                    additionalReadObject,
                                    "r",
                                    patient.toString()))) deny();
        }
        return current.effective();
    }

    private CurrentIdentity identity(CdsExportActorDto actor, CdsExportCommand command) {
        var current = pass.get();
        if (current == null) return liveIdentity(actor, command);
        // One pass is one actor and one command; any other pair is established on its own.
        if (current.identity == null
                || !Objects.equals(current.actor, actor)
                || !Objects.equals(current.command, command)) {
            var established = liveIdentity(actor, command);
            if (current.identity != null) return established;
            current.actor = actor;
            current.command = command;
            current.identity = established;
        }
        return current.identity;
    }

    private CurrentIdentity liveIdentity(CdsExportActorDto actor, CdsExportCommand command) {
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
        if (!privilege(context, "_demographic", "r", target))
            refuse(patient, "_demographic");
        if (patient != null && !patientRecordAllowed(context, patient)) deny();
        for (CdsCategory category : command.categories()) {
            if (only != null && category != only) continue;
            String object =
                    io.github.carlos_emr.carlos.demographic.cds.security.CdsCategoryReadPermission
                            .object(category);
            if (!privilege(context, object, "r", target))
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
                && !privilege(context, "_lab_sensitive", "r", target))
            refuse(patient, "_lab_sensitive");
        // ProblemList carries the Disease Registry, whose own screen needs _dxresearch read. It
        // is required whenever ProblemList is selected, by name at admission and again for each
        // patient, whether or not the chart holds a registry entry.
        if (command.categories().contains(CdsCategory.PROBLEM_LIST)
                && (only == null || only == CdsCategory.PROBLEM_LIST)
                && !privilege(context, DISEASE_REGISTRY_READ, "r", target))
            refuse(patient, DISEASE_REGISTRY_READ);
        // Reports carry every current eForm (its stored values and the form as the chart shows
        // it), so they need the eForm screen's own read right, by name at admission and again for
        // each patient, whether or not the chart holds an eForm (maintainer decision 6).
        if (command.categories().contains(CdsCategory.REPORTS)
                && (only == null || only == CdsCategory.REPORTS)
                && !privilege(context, EFORM_READ, "r", target))
            refuse(patient, EFORM_READ);
        // Every chart's capture reads its preferred pharmacy under _rx (Demographics is always
        // exported) and, with notes or history selected, its case management under _eChart,
        // whether or not the chart holds such a row. Name a missing role-level right at admission
        // and worker entry instead of stopping the job at its first patient as changed access.
        if (patient == null && only == null) {
            if (!privilege(context, PHARMACY_READ, "r", null))
                refuse(null, PHARMACY_READ);
            if (!java.util.Collections.disjoint(
                            command.categories(), CdsExportNoteSourceReader.CATEGORIES)
                    && !privilege(context, CHART_READ, "r", null))
                refuse(null, CHART_READ);
        }
    }

    /** The prescription module's read object, which every chart's preferred pharmacy needs. */
    static final String PHARMACY_READ = "_rx";

    /** The chart's read object, which every chart's case management (notes, history) needs. */
    static final String CHART_READ = "_eChart";

    /** The Disease Registry's own read object, required with ProblemList. */
    static final String DISEASE_REGISTRY_READ = "_dxresearch";

    /** The eForm read object, required with Reports, which carry every eForm of the chart. */
    static final String EFORM_READ = "_eform";

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
        if (!privilege(actor, "_admin", "w", null)
                && !privilege(actor, "_cds_export", "w", null)) deny();
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
        if (!privilege(context, "_demographic", "r", null)) deny();
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
