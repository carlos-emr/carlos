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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CdsExportPatientAuthorizationUnitTest {

    /** Records every uncached check; {@code revoked} makes every later check refuse. */
    private static final class Counting implements CdsExportPatientAuthorization.Fresh {
        final List<String> calls = new ArrayList<>();
        final List<List<CdsExportNoteAccessDto>> batches = new ArrayList<>();
        final AtomicBoolean revoked = new AtomicBoolean();
        boolean programAnswer = true;

        private void check(String call) {
            calls.add(call);
            if (revoked.get()) throw new SecurityException("CDS_EXPORT_AUTHORIZATION_DENIED");
        }

        @Override
        public void patient(int patient) {
            check("patient");
        }

        @Override
        public void pharmacy(int patient) {
            check("pharmacy");
        }

        @Override
        public void caseManagement(int patient) {
            check("case-management");
        }

        @Override
        public void category(int patient, CdsCategory category, boolean sensitiveLaboratory) {
            check("category " + category + " " + sensitiveLaboratory);
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
        public boolean issueProgram(int patient, CdsCategory category, String program) {
            check("issue " + program);
            return programAnswer && !revoked.get();
        }

        @Override
        public boolean restrictedProgram(int patient, CdsCategory category, String program) {
            check("restricted " + program);
            return programAnswer && !revoked.get();
        }

        @Override
        public boolean record(int patient, String object) {
            check("record " + object);
            return !revoked.get();
        }
    }

    private static CdsExportNoteAccessDto note(long id) {
        return new CdsExportNoteAccessDto(id, 27, "17", "1");
    }

    @Test
    void shouldAskEachCheckOnce_whileOnePatientIsCaptured() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        for (int step = 0; step < 15; step++) {
            session.patient(27);
            session.category(27, CdsCategory.CLINICAL_NOTES, false);
            session.caseManagement(27);
            session.note(CdsCategory.CLINICAL_NOTES, note(91));
            session.note(CdsCategory.CLINICAL_NOTES, note(92));
            assertThat(session.restrictedProgram(27, CdsCategory.REPORTS, "10034")).isTrue();
            assertThat(session.record(27, "_con")).isTrue();
        }

        assertThat(fresh.calls)
                .containsExactly(
                        "patient",
                        "category CLINICAL_NOTES false",
                        "case-management",
                        "note CLINICAL_NOTES 91",
                        "note CLINICAL_NOTES 92",
                        "restricted 10034",
                        "record _con");
    }

    @Test
    void shouldRunEveryGrantAgainUncached_whenThePatientIsFinished() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        session.patient(27);
        session.pharmacy(27);
        session.category(27, CdsCategory.LABORATORY_RESULTS, true);
        session.note(CdsCategory.CLINICAL_NOTES, note(92));
        session.note(CdsCategory.CLINICAL_NOTES, note(91));
        session.note(CdsCategory.PAST_HEALTH, note(93));
        session.issueProgram(27, CdsCategory.PROBLEM_LIST, "10035");
        session.record(27, "_form");
        fresh.calls.clear();

        session.finish();

        assertThat(fresh.calls)
                .containsExactly(
                        "patient",
                        "pharmacy",
                        "category LABORATORY_RESULTS true",
                        "notes PAST_HEALTH 1",
                        "notes CLINICAL_NOTES 2",
                        "issue 10035",
                        "record _form");
        // The uncached batch check needs ascending note order.
        assertThat(fresh.batches.get(1)).containsExactly(note(91), note(92));
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
        session.note(CdsCategory.CLINICAL_NOTES, note(91));

        // ... but the closing check refuses before the patient's output is accepted.
        assertThatThrownBy(session::finish).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseThePatient_whenAProgramGrantIsWithdrawnWhileItIsCaptured() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        assertThat(session.restrictedProgram(27, CdsCategory.REPORTS, "10034")).isTrue();
        fresh.programAnswer = false;

        assertThatThrownBy(session::finish)
                .isInstanceOf(SecurityException.class)
                .hasMessage("CDS_EXPORT_ACCESS_CHANGED");
    }

    @Test
    void shouldNotAskARefusedProgramAgain_becauseNothingDependsOnIt() {
        var fresh = new Counting();
        fresh.programAnswer = false;
        var session = new CdsExportPatientAuthorization(27, fresh);
        assertThat(session.issueProgram(27, CdsCategory.PROBLEM_LIST, "10035")).isFalse();
        assertThat(session.issueProgram(27, CdsCategory.PROBLEM_LIST, "10035")).isFalse();
        fresh.calls.clear();

        session.finish();

        assertThat(fresh.calls).isEmpty();
    }

    @Test
    void shouldRefuseAFirstCheckThatFails_withoutRememberingIt() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        fresh.revoked.set(true);
        assertThatThrownBy(() -> session.patient(27)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> session.patient(27)).isInstanceOf(SecurityException.class);

        assertThat(fresh.calls).containsExactly("patient", "patient");
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
    void shouldRecheckManyNotesInBoundedBatches_whenThePatientIsFinished() {
        var fresh = new Counting();
        var session = new CdsExportPatientAuthorization(27, fresh);
        int count = CdsExportPatientAuthorization.NOTE_BATCH + 5;
        for (long id = 1; id <= count; id++) session.note(CdsCategory.CLINICAL_NOTES, note(id));
        fresh.calls.clear();

        session.finish();

        assertThat(fresh.batches).hasSize(2);
        assertThat(fresh.batches.get(0)).hasSize(CdsExportPatientAuthorization.NOTE_BATCH);
        assertThat(fresh.batches.get(1)).hasSize(5);
        assertThat(fresh.batches.get(1).getFirst().noteId())
                .isEqualTo(CdsExportPatientAuthorization.NOTE_BATCH + 1L);
    }
}
