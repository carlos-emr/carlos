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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportAccessService.Gate;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportAccessService.Question;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CdsExportPatientAuthorizationUnitTest {

    /**
     * Records every call that reaches the live database; {@code revoked} makes every later one
     * refuse, as a revoked right would.
     */
    private static final class Counting implements CdsExportPatientAuthorization.Fresh {
        final List<String> calls = new ArrayList<>();
        final List<List<CdsExportNoteAccessDto>> batches = new ArrayList<>();
        final List<CdsExportAccessService.PatientRecheck> rechecks = new ArrayList<>();
        final AtomicBoolean revoked = new AtomicBoolean();
        /** The gates the opening pass decides, and those of them it refuses. */
        final Set<Gate> decided =
                new LinkedHashSet<>(
                        List.of(
                                Gate.PATIENT,
                                Gate.PHARMACY,
                                Gate.CASE_MANAGEMENT,
                                Gate.category(CdsCategory.CLINICAL_NOTES, false),
                                Gate.category(CdsCategory.LABORATORY_RESULTS, false),
                                Gate.category(CdsCategory.LABORATORY_RESULTS, true)));
        final Set<Gate> refused = new LinkedHashSet<>();
        int decidesFor = 0;
        boolean programAnswer = true;

        private void check(String call) {
            calls.add(call);
            if (revoked.get()) throw new SecurityException("CDS_EXPORT_AUTHORIZATION_DENIED");
        }

        @Override
        public CdsExportAccessService.PatientDecisions decide(int patient) {
            check("decide " + patient);
            var decisions =
                    new CdsExportAccessService.PatientDecisions(
                            decidesFor == 0 ? patient : decidesFor);
            for (Gate gate : decided)
                if (refused.contains(gate)) decisions.refuse(gate, "CDS_EXPORT_AUTHORIZATION_DENIED");
                else decisions.grant(gate);
            decisions.answer(Question.record("_con"), true);
            decisions.answer(Question.record("_form"), false);
            return decisions;
        }

        @Override
        public void gate(int patient, Gate gate) {
            check("gate " + gate.kind() + " " + gate.category());
        }

        @Override
        public boolean answer(int patient, Question question) {
            check("answer " + question.kind() + " " + question.value());
            return programAnswer;
        }

        @Override
        public void note(
                CdsCategory category,
                CdsExportNoteAccessDto scope,
                CdsExportAccessService.PatientGrants grants) {
            assertThat(grants.patient()).isEqualTo(scope.patient());
            check("note " + category + " " + scope.noteId());
        }

        @Override
        public void notes(CdsCategory category, List<CdsExportNoteAccessDto> scopes) {
            check("notes " + category + " " + scopes.size());
            batches.add(scopes);
        }

        @Override
        public void recheck(CdsExportAccessService.PatientRecheck request) {
            check("recheck " + request.patient());
            rechecks.add(request);
        }
    }

    private static CdsExportNoteAccessDto note(long id) {
        return new CdsExportNoteAccessDto(id, 27, "17", "1");
    }

    @Test
    void shouldDecideTheFixedGatesInOneOpeningPass_whileOnePatientIsCaptured() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        for (int step = 0; step < 15; step++) {
            session.patient(27);
            session.pharmacy(27);
            session.category(27, CdsCategory.CLINICAL_NOTES, false);
            session.category(27, CdsCategory.LABORATORY_RESULTS, true);
            session.caseManagement(27);
            session.note(CdsCategory.CLINICAL_NOTES, note(91));
            session.note(CdsCategory.CLINICAL_NOTES, note(92));
            assertThat(session.restrictedProgram(27, CdsCategory.REPORTS, "10034")).isTrue();
            assertThat(session.record(27, "_con")).isTrue();
            assertThat(session.record(27, "_form")).isFalse();
        }

        // One opening pass; only what depends on the chart's own rows is asked afterwards, once.
        assertThat(fresh.calls)
                .containsExactly(
                        "decide 27",
                        "note CLINICAL_NOTES 91",
                        "note CLINICAL_NOTES 92",
                        "answer RESTRICTED_PROGRAM 10034");
    }

    @Test
    void shouldCheckEveryGrantAgainInOneClosingPass_whenThePatientIsFinished() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        session.patient(27);
        session.pharmacy(27);
        session.category(27, CdsCategory.LABORATORY_RESULTS, true);
        session.note(CdsCategory.CLINICAL_NOTES, note(92));
        session.note(CdsCategory.CLINICAL_NOTES, note(91));
        session.note(CdsCategory.PAST_HEALTH, note(93));
        session.issueProgram(27, CdsCategory.PROBLEM_LIST, "10035");
        session.record(27, "_con");
        session.record(27, "_form");
        fresh.calls.clear();

        session.finish();

        assertThat(fresh.calls).containsExactly("recheck 27");
        var request = fresh.rechecks.getFirst();
        assertThat(request.patient()).isEqualTo(27);
        // Exactly the gates that were asked and granted, never one that was only decided.
        assertThat(request.gates())
                .containsExactly(
                        Gate.PATIENT,
                        Gate.PHARMACY,
                        Gate.category(CdsCategory.LABORATORY_RESULTS, true));
        // The uncached batch check needs ascending note order.
        assertThat(request.notes().get(CdsCategory.CLINICAL_NOTES))
                .containsExactly(note(91), note(92));
        assertThat(request.notes().get(CdsCategory.PAST_HEALTH)).containsExactly(note(93));
        // A "no" grants nothing, so only the questions answered yes are asked again.
        assertThat(request.questions())
                .containsExactly(
                        Question.issueProgram(CdsCategory.PROBLEM_LIST, "10035"),
                        Question.record("_con"));
    }

    @Test
    void shouldRefuseThePatient_whenAccessIsRevokedWhileItIsCaptured() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        session.patient(27);
        session.note(CdsCategory.CLINICAL_NOTES, note(91));
        assertThat(session.restrictedProgram(27, CdsCategory.REPORTS, "10034")).isTrue();
        fresh.revoked.set(true);
        // Later steps of the same patient still answer from the session ...
        session.patient(27);
        session.note(CdsCategory.CLINICAL_NOTES, note(91));

        // ... but the closing pass refuses before the patient's output is accepted.
        assertThatThrownBy(session::finish).isInstanceOf(SecurityException.class);
        assertThat(fresh.calls).last().isEqualTo("recheck 27");
    }

    @Test
    void shouldDecideTheNextPatientFromTheDatabaseAgain_whenAccessIsRevokedBetweenTwoPatients() {
        var fresh = new Counting();
        var first = new CdsExportPatientAuthorization(27, fresh);
        first.patient(27);
        first.finish();
        fresh.revoked.set(true);

        // Nothing of the first patient's session answers for the next patient.
        assertThatThrownBy(() -> new CdsExportPatientAuthorization(28, fresh))
                .isInstanceOf(SecurityException.class);
        assertThat(fresh.calls).containsExactly("decide 27", "recheck 27", "decide 28");
    }

    @Test
    void shouldRefuseAGateTheOpeningPassRefused_everyTimeItIsAsked() {
        var fresh = new Counting();
        fresh.refused.add(Gate.category(CdsCategory.LABORATORY_RESULTS, true));
        var session = new CdsExportPatientAuthorization(27, fresh);
        session.category(27, CdsCategory.LABORATORY_RESULTS, false);

        for (int attempt = 0; attempt < 2; attempt++)
            assertThatThrownBy(() -> session.category(27, CdsCategory.LABORATORY_RESULTS, true))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");

        // A refusal is never remembered as a grant.
        session.finish();
        assertThat(fresh.rechecks.getFirst().gates())
                .containsExactly(Gate.category(CdsCategory.LABORATORY_RESULTS, false));
    }

    @Test
    void shouldAskAGateTheOpeningPassDidNotDecide_fromTheDatabaseOnce() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);

        session.category(27, CdsCategory.REPORTS, false);
        session.category(27, CdsCategory.REPORTS, false);

        assertThat(fresh.calls).containsExactly("decide 27", "gate CATEGORY REPORTS");
        session.finish();
        assertThat(fresh.rechecks.getFirst().gates())
                .containsExactly(Gate.category(CdsCategory.REPORTS, false));
    }

    @Test
    void shouldRefuseAnUndecidedGateThatFails_withoutRememberingIt() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        fresh.revoked.set(true);
        assertThatThrownBy(() -> session.category(27, CdsCategory.REPORTS, false))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> session.category(27, CdsCategory.REPORTS, false))
                .isInstanceOf(SecurityException.class);

        assertThat(fresh.calls)
                .containsExactly("decide 27", "gate CATEGORY REPORTS", "gate CATEGORY REPORTS");
    }

    @Test
    void shouldAskAYesAnswerAgain_whenThePatientIsFinished() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        assertThat(session.restrictedProgram(27, CdsCategory.REPORTS, "10034")).isTrue();

        session.finish();

        assertThat(fresh.rechecks.getFirst().questions())
                .containsExactly(Question.restrictedProgram(CdsCategory.REPORTS, "10034"));
    }

    @Test
    void shouldNotAskARefusedProgramAgain_becauseNothingDependsOnIt() {
        var fresh = new Counting();
        fresh.programAnswer = false;
        var session = new CdsExportPatientAuthorization(27, fresh);
        assertThat(session.issueProgram(27, CdsCategory.PROBLEM_LIST, "10035")).isFalse();
        assertThat(session.issueProgram(27, CdsCategory.PROBLEM_LIST, "10035")).isFalse();
        assertThat(fresh.calls).containsExactly("decide 27", "answer ISSUE_PROGRAM 10035");

        session.finish();

        assertThat(fresh.rechecks.getFirst().questions()).isEmpty();
    }

    @Test
    void shouldHoldThePatient_whenTheOpeningPassIsRefused() {
        var fresh = new Counting();
        fresh.revoked.set(true);

        assertThatThrownBy(() -> new CdsExportPatientAuthorization(27, fresh))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseAChangedScopeOfOneNote_insteadOfReusingTheFirstAnswer() {
        var session = new CdsExportPatientAuthorization(27, new Counting());
        session.note(CdsCategory.CLINICAL_NOTES, note(91));

        assertThatThrownBy(
                        () ->
                                session.note(
                                        CdsCategory.CLINICAL_NOTES,
                                        new CdsExportNoteAccessDto(91, 27, "18", "1")))
                .isInstanceOf(SecurityException.class)
                .hasMessage("CDS_EXPORT_ACCESS_CHANGED");
    }

    @Test
    void shouldNeverAnswerForAnotherPatient_fromThisSession() {
        var session = new CdsExportPatientAuthorization(27, new Counting());

        assertThatThrownBy(() -> session.patient(28))
                .hasMessage("CDS_EXPORT_SESSION_PATIENT_MISMATCH");
        assertThatThrownBy(
                        () ->
                                session.note(
                                        CdsCategory.CLINICAL_NOTES,
                                        new CdsExportNoteAccessDto(91, 28, "17", "1")))
                .hasMessage("CDS_EXPORT_SESSION_PATIENT_MISMATCH");
    }

    @Test
    void shouldRefuseDecisionsOfAnotherPatient_whenTheSessionStarts() {
        var fresh = new Counting();
        fresh.decidesFor = 28;

        assertThatThrownBy(() -> new CdsExportPatientAuthorization(27, fresh))
                .hasMessage("CDS_EXPORT_SESSION_PATIENT_MISMATCH");
    }

    @Test
    void shouldHandEveryNoteToTheClosingPass_whenThePatientHasMany() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        int count = CdsExportPatientAuthorization.NOTE_BATCH + 5;
        for (long id = 1; id <= count; id++) session.note(CdsCategory.CLINICAL_NOTES, note(id));

        session.finish();

        var notes = fresh.rechecks.getFirst().notes().get(CdsCategory.CLINICAL_NOTES);
        assertThat(notes).hasSize(count);
        assertThat(notes.getFirst().noteId()).isEqualTo(1);
        assertThat(notes.getLast().noteId()).isEqualTo(count);
    }

    @Test
    void shouldAnswerAReauthorizationPassFromItsOwnOpeningPass_withoutAClosingCheck() {
        var fresh = new Counting();
        var pass = CdsExportPatientAuthorization.pass(27, fresh);
        assertThat(pass.session()).isFalse();
        for (int step = 0; step < 3; step++) {
            pass.patient(27);
            pass.pharmacy(27);
            pass.caseManagement(27);
            pass.category(27, CdsCategory.CLINICAL_NOTES, false);
        }
        pass.notes(CdsCategory.CLINICAL_NOTES, List.of(note(91), note(92)));

        // The notes of a pass are checked in one uncached batch, never one transaction each.
        assertThat(fresh.calls).containsExactly("decide 27", "notes CLINICAL_NOTES 2");
        assertThat(fresh.batches.getFirst()).containsExactly(note(91), note(92));
        // A pass reads no patient data and so has nothing to close; it is not a session.
        assertThatThrownBy(pass::finish).hasMessage("CDS_EXPORT_SESSION_NOT_STARTED");
        assertThat(fresh.rechecks).isEmpty();
    }

    @Test
    void shouldRefuseAReauthorizationPass_whenAccessWasRevokedBeforeIt() {
        var fresh = new Counting();
        CdsExportPatientAuthorization.pass(27, fresh).patient(27);
        fresh.revoked.set(true);

        // Each pass starts from the database; the earlier pass answers nothing.
        assertThatThrownBy(() -> CdsExportPatientAuthorization.pass(27, fresh))
                .isInstanceOf(SecurityException.class);
    }
}
