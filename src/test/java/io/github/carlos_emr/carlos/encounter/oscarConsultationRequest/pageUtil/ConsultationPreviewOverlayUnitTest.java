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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the consultation print preview shows when the clinician has typed but not saved.
 *
 * <p>The Print button POSTs the whole form by AJAX so the clinician keeps their edits and stays on
 * the page; the PDF then rebuilt the form from the database and the typed text was missing from the
 * preview entirely (issue #3721). These tests pin which fields the posted values now reach, and —
 * just as important — which they must not: patient identity on a consultation document has to come
 * from the record, never from the request that asked for the render.
 *
 * <p>Extends {@link CarlosUnitTestBase} because {@code EctConsultationFormRequestUtil}'s
 * constructor reaches for Spring beans through {@code SpringUtils}; the overlay itself needs no
 * container.</p>
 *
 * @since 2026-09-25
 */
@DisplayName("Consultation print preview overlay")
@Tag("unit")
class ConsultationPreviewOverlayUnitTest extends CarlosUnitTestBase {

    private static final String STORED = "stored value";
    private static final String TYPED = "typed but not saved";

    /** A deployment without the appointment-instruction lookup: nothing to resolve a label from. */
    private static final java.util.function.UnaryOperator<String> NO_LOOKUP = null;

    /**
     * EctConsultationFormRequestUtil pulls seven collaborators in its field initializers, none of
     * which the overlay touches. They are registered so the object can be constructed at all.
     */
    @BeforeEach
    void registerFormCollaborators() {
        registerMock(ConsultationRequestDao.class, mock(ConsultationRequestDao.class));
        registerMock(ConsultationRequestExtDao.class, mock(ConsultationRequestExtDao.class));
        registerMock(ConsultationServiceDao.class, mock(ConsultationServiceDao.class));
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(ContactDao.class, mock(ContactDao.class));
        registerMock(FaxJobDao.class, mock(FaxJobDao.class));
        registerMock(FaxClientLogDao.class, mock(FaxClientLogDao.class));
    }

    /** A request that answers only the parameters given, as a real POST would. */
    private static HttpServletRequest postOf(Map<String, String> parameters) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(anyString())).thenAnswer(call -> parameters.get(call.getArgument(0)));
        when(request.getAttribute(ConsultationPreviewOverlay.PREVIEW_USES_UNSAVED_VALUES_ATTRIBUTE))
                .thenReturn(Boolean.TRUE);
        return request;
    }

    /** A form object as estRequestFromId leaves it: every field holding what the record holds. */
    private static EctConsultationFormRequestUtil storedForm() {
        EctConsultationFormRequestUtil form = new EctConsultationFormRequestUtil();
        form.reasonForConsultation = STORED;
        form.clinicalInformation = STORED;
        form.concurrentProblems = STORED;
        form.currentMedications = STORED;
        form.allergies = STORED;
        form.urgency = "2";
        form.appointmentNotes = STORED;
        form.pwb = "0";
        form.patientName = "STORED, Patient";
        form.patientHealthNum = "1111111111";
        form.patientAddress = "1 Stored Street";
        form.providerNo = "999998";
        form.letterheadName = "Stored Clinic";
        form.specialist = "7";
        return form;
    }

    @Test
    @DisplayName("should show the typed clinical text rather than the stored text")
    void shouldShowTypedText_whenFieldsWerePosted() {
        EctConsultationFormRequestUtil form = storedForm();

        ConsultationPreviewOverlay.apply(form, postOf(Map.of(
                "reasonForConsultation", TYPED,
                "clinicalInformation", TYPED,
                "concurrentProblems", TYPED,
                "currentMedications", TYPED,
                "allergies", TYPED,
                "urgency", "1",
                "appointmentNotes", TYPED)), NO_LOOKUP);

        assertThat(form.reasonForConsultation).isEqualTo(TYPED);
        assertThat(form.clinicalInformation).isEqualTo(TYPED);
        assertThat(form.concurrentProblems).isEqualTo(TYPED);
        assertThat(form.currentMedications).isEqualTo(TYPED);
        assertThat(form.allergies).isEqualTo(TYPED);
        assertThat(form.urgency).isEqualTo("1");
        assertThat(form.appointmentNotes).isEqualTo(TYPED);
    }

    @Test
    @DisplayName("should keep the stored value for a field the form did not post")
    void shouldKeepStoredValue_whenFieldAbsentFromPost() {
        EctConsultationFormRequestUtil form = storedForm();

        ConsultationPreviewOverlay.apply(form, postOf(Map.of("reasonForConsultation", TYPED)), NO_LOOKUP);

        assertThat(form.reasonForConsultation).isEqualTo(TYPED);
        assertThat(form.clinicalInformation)
                .as("a field the POST did not carry is not an edit, so the record still speaks for it")
                .isEqualTo(STORED);
        assertThat(form.allergies).isEqualTo(STORED);
    }

    @Test
    @DisplayName("should show a cleared box as cleared")
    void shouldShowEmptyValue_whenFieldWasClearedBeforePrinting() {
        EctConsultationFormRequestUtil form = storedForm();

        // Emptying a box and printing is the same kind of edit as typing into one: the preview has
        // to show what is on the screen, not what the record still holds.
        ConsultationPreviewOverlay.apply(form, postOf(Map.of("clinicalInformation", "")), NO_LOOKUP);

        assertThat(form.clinicalInformation).isEmpty();
    }

    @Test
    @DisplayName("should read the patient-will-book checkbox from its presence in the post")
    void shouldReadPatientWillBook_fromCheckboxPresence() {
        EctConsultationFormRequestUtil checked = storedForm();
        ConsultationPreviewOverlay.apply(checked, postOf(Map.of(
                "patientWillBookRendered", "1", "patientWillBook", "1")), NO_LOOKUP);
        assertThat(checked.pwb).isEqualTo("1");

        EctConsultationFormRequestUtil unchecked = storedForm();
        unchecked.pwb = "1";
        // An unchecked checkbox posts nothing at all, so absence is the value here — unlike every
        // other field, where absence means "not submitted".
        ConsultationPreviewOverlay.apply(unchecked, postOf(Map.of("patientWillBookRendered", "1")), NO_LOOKUP);
        assertThat(unchecked.pwb).isEqualTo("0");
    }

    @Test
    @DisplayName("should keep the stored patient-will-book when the checkbox was never rendered")
    void shouldKeepStoredPatientWillBook_whenCheckboxNotRendered() {
        EctConsultationFormRequestUtil form = storedForm();
        form.pwb = "1";

        // CONSULTATION_PATIENT_WILL_BOOK is false by default, so the form renders no checkbox at
        // all and the POST looks exactly like an unchecked one. Reading that as "not checked" would
        // clear the stored booking on EVERY preview and print an appointment date instead.
        ConsultationPreviewOverlay.apply(form, postOf(Map.of("reasonForConsultation", TYPED)), NO_LOOKUP);

        assertThat(form.pwb).isEqualTo("1");
    }

    @Test
    @DisplayName("should show the typed referral date rather than the stored one")
    void shouldShowTypedReferralDate_whenPosted() {
        EctConsultationFormRequestUtil form = storedForm();
        form.referalDate = "2020-01-01";

        ConsultationPreviewOverlay.apply(form, postOf(Map.of("referalDate", "2026-09-25")), NO_LOOKUP);

        assertThat(form.referalDate).isEqualTo("2026-09-25");
    }

    @Test
    @DisplayName("should move the appointment instruction and its label together")
    void shouldResolveAppointmentInstructionLabel_whenSelectionPosted() {
        EctConsultationFormRequestUtil form = storedForm();

        // The form posts a lookup VALUE while the PDF prints the matching LABEL, so copying the
        // value alone would leave the preview showing the stored instruction.
        ConsultationPreviewOverlay.apply(form, postOf(Map.of("appointmentInstructions", "CALL")),
                value -> "CALL".equals(value) ? "Call the office" : "");

        assertThat(form.getAppointmentInstructions()).isEqualTo("CALL");
        assertThat(form.getAppointmentInstructionsLabel()).isEqualTo("Call the office");
    }

    @Test
    @DisplayName("should never take patient or provider identity from the request")
    void shouldNeverOverlayIdentity_evenWhenPosted() {
        EctConsultationFormRequestUtil form = storedForm();

        Map<String, String> hostile = new HashMap<>();
        hostile.put("patientName", "SOMEONE, Else");
        hostile.put("patientHealthNum", "9999999999");
        hostile.put("patientAddress", "1 Injected Road");
        hostile.put("providerNo", "1");
        hostile.put("letterheadName", "Injected Clinic");
        hostile.put("specialist", "99");
        ConsultationPreviewOverlay.apply(form, postOf(hostile), NO_LOOKUP);

        // A consultation request is a document that looks official. Identity on it comes from the
        // record or not at all, so a POST cannot put a name, a health number or an address on one.
        assertThat(form.patientName).isEqualTo("STORED, Patient");
        assertThat(form.patientHealthNum).isEqualTo("1111111111");
        assertThat(form.patientAddress).isEqualTo("1 Stored Street");
        assertThat(form.providerNo).isEqualTo("999998");
        // The letterhead and the specialist are resolved server-side from an id; copying the id
        // alone would leave the resolved block describing a different office than the name above.
        assertThat(form.letterheadName).isEqualTo("Stored Clinic");
        assertThat(form.specialist).isEqualTo("7");
    }

    @Test
    @DisplayName("should apply only where the preview branch asked for it")
    void shouldApplyOnlyWhereRequested_forOtherRenderPaths() {
        HttpServletRequest fax = mock(HttpServletRequest.class);
        assertThat(ConsultationPreviewOverlay.requested(fax))
                .as("the fax and stored-record prints save first, so they read the record")
                .isFalse();

        HttpServletRequest preview = mock(HttpServletRequest.class);
        when(preview.getAttribute(ConsultationPreviewOverlay.PREVIEW_USES_UNSAVED_VALUES_ATTRIBUTE))
                .thenReturn(Boolean.TRUE);
        assertThat(ConsultationPreviewOverlay.requested(preview)).isTrue();

        assertThat(ConsultationPreviewOverlay.requested(null)).isFalse();
    }

    @Test
    @DisplayName("should do nothing when there is no form or no request")
    void shouldDoNothing_whenGivenNulls() {
        ConsultationPreviewOverlay.apply(null, postOf(Map.of("allergies", TYPED)), NO_LOOKUP);

        EctConsultationFormRequestUtil form = storedForm();
        ConsultationPreviewOverlay.apply(form, null, NO_LOOKUP);
        assertThat(form.allergies).isEqualTo(STORED);
    }
}
