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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * An eForm action template's {@code <consultRequest>} saves the status its {@code <status>} tag
 * names, and the urgency its {@code <urgency>} tag names (issue #4826).
 *
 * <p>Each test runs {@link EFormUtil#writeEformTemplate} on a template holding one consultation
 * request and reads back the request it persists. Status codes: 1 Nothing, 2 Pending Specialist
 * Callback, 3 Pending Patient Callback, 4 Completed. Urgency codes: 1 Urgent, 2 Non-urgent,
 * 3 Return.</p>
 */
@Tag("unit")
@DisplayName("eForm template consultation request status")
class EFormUtilTemplateConsultStatusUnitTest extends CarlosUnitTestBase {

    private static final int SPECIALIST = 9001;

    private ConsultationRequestDao consultationRequestDao;

    @BeforeEach
    void registerDaos() {
        consultationRequestDao = createAndRegisterMock(ConsultationRequestDao.class);
        ProfessionalSpecialistDao professionalSpecialistDao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        when(professionalSpecialistDao.find(SPECIALIST)).thenReturn(new ProfessionalSpecialist());
        // The letterhead falls back to the clinic's details, which an unconfigured clinic leaves empty.
        createAndRegisterMock(ClinicDAO.class);
    }

    /** Submits an eForm whose template is a consultation request with these extra tags, and returns it. */
    private ConsultationRequest submit(String tags) {
        EForm eForm = new EForm();
        eForm.setFormName("FAKE consult template form");
        eForm.setDemographicNo("7");
        eForm.setProviderNo("111");
        eForm.setFormDate("2026-10-17");
        eForm.setFormHtml("<html><body><form></form><!-- <template><consultRequest>"
                + "<referredToService>12</referredToService><referredToSpecialist>" + SPECIALIST
                + "</referredToSpecialist><referredBy>111</referredBy><reason>FAKE reason</reason>" + tags
                + "</consultRequest></template> --></body></html>");

        EFormUtil.writeEformTemplate(null, new ArrayList<>(), new ArrayList<>(), eForm, "5", null, "/carlos");

        ArgumentCaptor<ConsultationRequest> request = ArgumentCaptor.forClass(ConsultationRequest.class);
        verify(consultationRequestDao).persist(request.capture());
        return request.getValue();
    }

    @Test
    @DisplayName("should save the status the <status> tag names, not the urgency's code")
    void shouldSaveTemplateStatus_whenTemplateNamesStatusAndUrgency() {
        ConsultationRequest request = submit("<urgency>3</urgency><status>2</status>");

        assertThat(request.getStatus()).isEqualTo("2");
        assertThat(request.getUrgency()).isEqualTo("3");
    }

    @Test
    @DisplayName("should save the status the <status> tag names when the template names no urgency")
    void shouldSaveTemplateStatus_whenTemplateNamesOnlyStatus() {
        ConsultationRequest request = submit("<status>4</status>");

        assertThat(request.getStatus()).isEqualTo("4");
        assertThat(request.getUrgency()).isEqualTo("2");
    }

    @Test
    @DisplayName("should save status 1 when the template names an urgency but no status")
    void shouldSaveDefaultStatus_whenTemplateNamesOnlyUrgency() {
        ConsultationRequest request = submit("<urgency>3</urgency>");

        assertThat(request.getStatus()).isEqualTo("1");
        assertThat(request.getUrgency()).isEqualTo("3");
    }

    @Test
    @DisplayName("should save status 1 and urgency 2 when the template names neither")
    void shouldSaveDefaults_whenTemplateNamesNeither() {
        ConsultationRequest request = submit("");

        assertThat(request.getStatus()).isEqualTo("1");
        assertThat(request.getUrgency()).isEqualTo("2");
    }
}
