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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportAccessService.Gate;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportAccessService.Question;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * One patient's authorization inside one export worker: a session while the patient is captured
 * and emitted, or one short re-authorization pass afterwards.
 *
 * <p>Every capture, reconciliation, mapping and emission step asks the run control again whether
 * the exporting identities may read the patient, a category, a note, a program-restricted record
 * or a printed record. Answered fresh each time, a chart of a few hundred notes cost hundreds of
 * thousands of identity and privilege queries; answered once per step but one transaction per
 * check, every extra patient still cost about 180 transactions and 2,900 reads of the
 * authorization tables. Now one <em>opening pass</em> ({@link Fresh#decide}) decides every fixed
 * gate of the patient from the live database in one transaction when the session starts, before
 * any of the patient's data is read; the note, issue-program and restricted-program checks,
 * which depend on what the chart holds, are still made from the live database when first asked
 * and their first answer kept. {@link #finish()} then runs one <em>closing pass</em>
 * ({@link Fresh#recheck}): every check that granted access is made again, from the live database
 * in one fresh transaction, after the patient's last read and before its output is accepted. A
 * grant revoked at any point while the patient was being read is therefore still refused before
 * anything of that patient is accepted ({@code CDS_EXPORT_ACCESS_CHANGED}): the closing pass is
 * at least as late as the last per-step check used to be, and it trusts nothing but the list of
 * checks to repeat.
 *
 * <p>A gate decided by the opening pass is decided as of the session's start, not as of the step
 * that asks. A right granted only after the session started therefore does not help that patient
 * (it is refused and the patient held, as if the right were still missing); a right revoked after
 * the session started is caught by the closing pass.
 *
 * <p>The object belongs to exactly one patient and one worker thread. Checks for another patient
 * never use it, and the caller discards it when the patient is finished or held; the next
 * patient starts with its own opening pass. Answers that refused access are kept too: a refusal
 * holds the patient or leaves a record out, so no grant depends on it, and it is not repeated.
 *
 * <p>{@link #pass} opens the same thing without a closing check, for a caller that
 * re-authorizes an already captured patient in one go (the cohort passes before packaging,
 * publication and download). Every answer of such a pass comes from its own opening pass or a
 * check made while it is open; it reads no patient data, so there is nothing for a closing pass
 * to protect.
 */
final class CdsExportPatientAuthorization {

    /** The checks against the live database, bound to the job's actor and command. */
    interface Fresh {
        /** The opening pass: every fixed gate and record question, in one transaction. */
        CdsExportAccessService.PatientDecisions decide(int patient);

        /** One fixed gate the opening pass did not decide, uncached. */
        void gate(int patient, Gate gate);

        /** One question the opening pass did not ask, uncached. */
        boolean answer(int patient, Question question);

        /** One note inside a session (chart gates and grants kept in {@code grants}). */
        void note(
                CdsCategory category,
                CdsExportNoteAccessDto scope,
                CdsExportAccessService.PatientGrants grants);

        /** Every given note of one category, uncached, in ascending note order (at most 8192). */
        void notes(CdsCategory category, List<CdsExportNoteAccessDto> scopes);

        /** The closing pass: everything the session granted, uncached, in one transaction. */
        void recheck(CdsExportAccessService.PatientRecheck request);
    }

    /** The live checks of one job: the access service with the job's actor and command. */
    static final class Live implements Fresh {
        private final CdsExportAccessService access;
        private final CdsExportActorDto actor;
        private final CdsExportCommand command;

        Live(CdsExportAccessService access, CdsExportActorDto actor, CdsExportCommand command) {
            this.access = Objects.requireNonNull(access);
            this.actor = actor;
            this.command = command;
        }

        @Override
        public CdsExportAccessService.PatientDecisions decide(int patient) {
            return access.decidePatient(actor, command, patient);
        }

        @Override
        public void gate(int patient, Gate gate) {
            access.requireGate(actor, command, patient, gate);
        }

        @Override
        public boolean answer(int patient, Question question) {
            return access.answerQuestion(actor, command, patient, question);
        }

        @Override
        public void note(
                CdsCategory category,
                CdsExportNoteAccessDto scope,
                CdsExportAccessService.PatientGrants grants) {
            access.requireNoteRead(actor, command, category, scope, grants);
        }

        @Override
        public void notes(CdsCategory category, List<CdsExportNoteAccessDto> scopes) {
            access.requireNotesRead(actor, command, category, scopes);
        }

        @Override
        public void recheck(CdsExportAccessService.PatientRecheck request) {
            access.recheckPatient(actor, command, request);
        }

        @Override
        public String toString() {
            return "CdsExportPatientAuthorization.Live[protected]";
        }
    }

    /** The largest batch the uncached note check accepts. */
    static final int NOTE_BATCH = 8192;

    private final int patient;
    private final Fresh fresh;
    private final boolean session;
    private final CdsExportAccessService.PatientDecisions decisions;
    private final Set<Gate> granted = new LinkedHashSet<>();
    private final Map<Question, Boolean> answers = new LinkedHashMap<>();
    private final Map<CdsCategory, TreeMap<Long, CdsExportNoteAccessDto>> notes =
            new EnumMap<>(CdsCategory.class);

    /** Starts one patient's session with its opening pass. */
    CdsExportPatientAuthorization(int patient, Fresh fresh) {
        this(patient, fresh, true);
    }

    private CdsExportPatientAuthorization(int patient, Fresh fresh, boolean session) {
        this.fresh = Objects.requireNonNull(fresh);
        this.session = session;
        this.patient = patient;
        var decided = Objects.requireNonNull(fresh.decide(patient));
        // Decisions for another patient answer nothing here.
        if (decided.patient() != patient)
            throw new IllegalStateException("CDS_EXPORT_SESSION_PATIENT_MISMATCH");
        this.decisions = decided;
    }

    /** Starts one re-authorization pass of an already captured patient; it has no closing check. */
    static CdsExportPatientAuthorization pass(int patient, Fresh fresh) {
        return new CdsExportPatientAuthorization(patient, fresh, false);
    }

    int patient() {
        return patient;
    }

    /** True for a session, which must end with {@link #finish()}; false for a pass. */
    boolean session() {
        return session;
    }

    void patient(int patient) {
        gate(patient, Gate.PATIENT);
    }

    void pharmacy(int patient) {
        gate(patient, Gate.PHARMACY);
    }

    void caseManagement(int patient) {
        gate(patient, Gate.CASE_MANAGEMENT);
    }

    void category(int patient, CdsCategory category, boolean sensitiveLaboratory) {
        gate(patient, Gate.category(category, sensitiveLaboratory));
    }

    /** One note of a session; a pass authorizes notes in batches ({@link #notes}). */
    void note(CdsCategory category, CdsExportNoteAccessDto scope) {
        Objects.requireNonNull(category);
        own(scope.patient());
        var seen = notes.computeIfAbsent(category, ignored -> new TreeMap<>());
        var prior = seen.get(scope.noteId());
        if (prior != null) {
            // One note has one physical scope; a different one cannot both match the chart.
            if (!prior.equals(scope)) throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
            return;
        }
        if (session) fresh.note(category, scope, decisions.grants());
        else fresh.notes(category, List.of(scope));
        seen.put(scope.noteId(), scope);
    }

    /** One batch of a pass, uncached, in ascending note order; a session takes notes one by one. */
    void notes(CdsCategory category, List<CdsExportNoteAccessDto> scopes) {
        var batch = List.copyOf(scopes);
        for (var scope : batch) own(scope.patient());
        if (session) for (var scope : batch) note(category, scope);
        else fresh.notes(category, batch);
    }

    boolean issueProgram(int patient, CdsCategory category, String program) {
        return answer(patient, Question.issueProgram(category, program));
    }

    boolean restrictedProgram(int patient, CdsCategory category, String program) {
        return answer(patient, Question.restrictedProgram(category, program));
    }

    boolean record(int patient, String object) {
        return answer(patient, Question.record(object));
    }

    /**
     * The closing pass of a session: every check that granted access is made again from the live
     * database, in one transaction ({@link Fresh#recheck}): the patient, pharmacy, case
     * management and category gates, every note of every category, and every program and record
     * object that answered yes. Any refusal or changed answer is a {@link SecurityException}, so
     * the patient is held instead of accepted.
     */
    void finish() {
        if (!session) throw new IllegalStateException("CDS_EXPORT_SESSION_NOT_STARTED");
        var scopes = new EnumMap<CdsCategory, List<CdsExportNoteAccessDto>>(CdsCategory.class);
        notes.forEach((category, seen) -> scopes.put(category, List.copyOf(seen.values())));
        var yes = new LinkedHashSet<Question>();
        answers.forEach(
                (question, answer) -> {
                    if (answer) yes.add(question);
                });
        fresh.recheck(new CdsExportAccessService.PatientRecheck(patient, granted, scopes, yes));
    }

    private void gate(int patient, Gate gate) {
        own(patient);
        if (granted.contains(gate)) return;
        // A refusal throws here every time it is asked, and is never remembered as a grant.
        if (decisions.decided(gate)) decisions.require(gate);
        else fresh.gate(patient, gate);
        granted.add(gate);
    }

    private boolean answer(int patient, Question question) {
        own(patient);
        Boolean known = answers.get(question);
        if (known != null) return known;
        Boolean decided = decisions.answer(question);
        boolean answer = decided != null ? decided : fresh.answer(patient, question);
        answers.put(question, answer);
        return answer;
    }

    private void own(int patient) {
        if (patient != this.patient) throw new IllegalStateException("CDS_EXPORT_SESSION_PATIENT_MISMATCH");
    }

    @Override
    public String toString() {
        return "CdsExportPatientAuthorization[protected]";
    }
}
