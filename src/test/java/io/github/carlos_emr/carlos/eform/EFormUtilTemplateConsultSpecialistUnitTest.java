/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * An eForm action template's {@code <consultRequest>} without a usable {@code <referredToSpecialist>}
 * is skipped, like one whose specialist is not found, with a warning naming the eForm's form id,
 * instead of stopping the submission (issue #4833).
 *
 * <p>Each test runs {@link EFormUtil#writeEformTemplate} on a template holding one consultation
 * request, with the DAOs mocked, and checks whether the request is saved.</p>
 */
@Tag("unit")
@DisplayName("eForm template consultation request specialist")
class EFormUtilTemplateConsultSpecialistUnitTest extends CarlosUnitTestBase {

    private static final int SPECIALIST = 9001;

    private ConsultationRequestDao consultationRequestDao;
    private ProfessionalSpecialistDao professionalSpecialistDao;

    @BeforeEach
    void registerDaos() {
        consultationRequestDao = createAndRegisterMock(ConsultationRequestDao.class);
        professionalSpecialistDao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        when(professionalSpecialistDao.find(SPECIALIST)).thenReturn(new ProfessionalSpecialist());
        // The letterhead falls back to the clinic's details, which an unconfigured clinic leaves empty.
        createAndRegisterMock(ClinicDAO.class);
    }

    /** Runs an eForm whose template is a consultation request with this specialist tag (or none). */
    private void submit(String specialistTag) {
        EForm eForm = new EForm();
        eForm.setFid("41");
        eForm.setFormName("FAKE consult template form");
        eForm.setDemographicNo("7");
        eForm.setProviderNo("111");
        eForm.setFormDate("2026-10-17");
        eForm.setFormHtml("<html><body><form></form><!-- <template><consultRequest>"
                + "<referredToService>12</referredToService>" + specialistTag
                + "<referredBy>111</referredBy><reason>FAKE reason</reason>"
                + "</consultRequest></template> --></body></html>");

        EFormUtil.writeEformTemplate(null, new ArrayList<>(), new ArrayList<>(), eForm, "5", null, "/carlos");
    }

    @Test
    @DisplayName("should skip the request without failing when the template names no specialist")
    void shouldSkipRequest_whenTemplateNamesNoSpecialist() {
        assertThatCode(() -> submit("")).doesNotThrowAnyException();

        verify(consultationRequestDao, never()).persist(any());
    }

    @Test
    @DisplayName("should log a warning naming only the eForm's form id when it skips the request")
    void shouldWarnWithFormId_whenSpecialistIsMissing() {
        try (LogCapture capture = LogCapture.forLogger(EFormUtil.class)) {
            submit("");

            assertThat(capture.messages())
                    .containsExactly("eForm template consultRequest skipped: specialist missing or not a number (eForm fid 41)");
        }
    }

    @Test
    @DisplayName("should skip the request without failing when the specialist is not a number")
    void shouldSkipRequest_whenSpecialistIsNotANumber() {
        assertThatCode(() -> submit("<referredToSpecialist>Dr FAKE</referredToSpecialist>")).doesNotThrowAnyException();

        verify(consultationRequestDao, never()).persist(any());
    }

    @Test
    @DisplayName("should skip the request when no specialist has the id")
    void shouldSkipRequest_whenSpecialistIsNotFound() {
        submit("<referredToSpecialist>9002</referredToSpecialist>");

        verify(consultationRequestDao, never()).persist(any());
    }

    @Test
    @DisplayName("should save the request for the specialist the template names")
    void shouldSaveRequest_whenSpecialistIsFound() {
        submit("<referredToSpecialist>" + SPECIALIST + "</referredToSpecialist>");

        ArgumentCaptor<ConsultationRequest> request = ArgumentCaptor.forClass(ConsultationRequest.class);
        verify(consultationRequestDao).persist(request.capture());
        assertThat(request.getValue().getServiceId()).isEqualTo(12);
        assertThat(request.getValue().getReasonForReferral()).isEqualTo("FAKE reason");
    }
}
