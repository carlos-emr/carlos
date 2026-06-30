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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.ConsultationSignatureService;
import io.github.carlos_emr.carlos.managers.ConsultationStampOutcome;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("EctConsultationFormRequest2Action")
@Tag("unit")
class EctConsultationFormRequest2ActionUnitTest extends CarlosUnitTestBase {

    private static final byte[] SIGNATURE_BYTES = new byte[]{1, 2, 3};
    private static final String PDF_BASE64 = "JVBERi0xLjQK";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;
    private Path pdfPath;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo loggedInInfo;
    private SecurityInfoManager securityInfoManager;
    private ConsultationSignatureService consultationSignatureService;
    private DocumentAttachmentManager documentAttachmentManager;
    private DemographicManager demographicManager;
    private DigitalSignatureManager digitalSignatureManager;
    private ConsultationRequestDao consultationRequestDao;
    private EctConsultationFormRequest2Action action;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest("POST", "/encounter/RequestConsultation");
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        consultationSignatureService = mock(ConsultationSignatureService.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        demographicManager = mock(DemographicManager.class);
        digitalSignatureManager = mock(DigitalSignatureManager.class);
        consultationRequestDao = mock(ConsultationRequestDao.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ConsultationManager.class, mock(ConsultationManager.class));
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(FaxManager.class, mock(FaxManager.class));
        registerMock(DigitalSignatureManager.class, digitalSignatureManager);
        registerMock(ConsultationSignatureService.class, consultationSignatureService);
        registerMock(ConsultationRequestDao.class, consultationRequestDao);
        registerMock(ConsultationRequestExtDao.class, mock(ConsultationRequestExtDao.class));
        registerMock(ProfessionalSpecialistDao.class, mock(ProfessionalSpecialistDao.class));
        registerMock(DemographicManager.class, demographicManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), isNull()))
                .thenReturn(true);
        when(consultationSignatureService.resolveManualSignatureRequestId("", "sig-request"))
                .thenReturn("sig-request");
        when(consultationSignatureService.resolveSignatureProviderNo("999998", "999998", "999998"))
                .thenReturn("999998");
        when(consultationSignatureService.resolvePreviewSignatureImage(loggedInInfo, false, "", "sig-request", "999998"))
                .thenReturn(SIGNATURE_BYTES);
        when(demographicManager.getDemographicFormattedName(loggedInInfo, 1)).thenReturn("Patient, Test");

        pdfPath = Files.createTempFile("consult-preview", ".pdf");
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response)).thenReturn(pdfPath);
        when(documentAttachmentManager.convertPDFToBase64(pdfPath)).thenReturn(PDF_BASE64);

        action = new EctConsultationFormRequest2Action();
        action.setSubmission("And Print Preview");
        action.setRequestId("9");
        action.setDemographicNo("1");
        action.setProviderNo("999998");
        action.setAppointmentHour("");
        action.setAppointmentPm("");
        action.setSignatureImg("");

        request.addParameter("newSignature", "false");
        request.addParameter("newSignatureImg", "sig-request");
        request.addParameter("signatureProviderNo", "999998");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
        if (pdfPath != null) {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    @DisplayName("returns the base64 PDF JSON and sets the signature override for a direct print preview")
    void shouldReturnJsonPdfWithSignatureOverride_forDirectPrintPreview() throws Exception {
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"consultPDF\":\"" + PDF_BASE64 + "\"");
        assertThat(response.getContentAsString()).contains("\"errorMessage\":null");
        assertThat(request.getAttribute(ConsultationSignatureService.SIGNATURE_IMAGE_OVERRIDE_ATTRIBUTE))
                .isEqualTo(SIGNATURE_BYTES);
        verify(documentAttachmentManager).renderConsultationFormWithAttachments(request, response);
    }

    @Test
    @DisplayName("keeps print preview errors generic when rendering fails")
    void shouldReturnGenericErrorMessage_whenDirectPrintPreviewFails() throws Exception {
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response))
                .thenThrow(new RuntimeException("sensitive internal path /var/lib/OscarDocument/consult.pdf"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString())
                .contains("A print preview of this consultation could not be generated. Please try again or contact support.")
                .doesNotContain("sensitive internal path")
                .doesNotContain("/var/lib/OscarDocument");
    }

    @Test
    @DisplayName("reuses the stored signature id when a manual re-sign produces no new signature on update")
    void shouldReuseStoredSignatureId_whenManualReSignReturnsNullOnUpdate() throws Exception {
        ConsultationRequest existing = new ConsultationRequest();
        existing.setSignatureImg("123"); // pre-existing DB-stored signature id
        // AbstractDao has find(Object) and find(int); the action calls find(Integer), so stub the Object overload.
        when(consultationRequestDao.find(Integer.valueOf(9))).thenReturn(existing);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("0");
        action.setSignatureImg("123"); // submitted form value — now untrusted for fallback
        request.setParameter("newSignature", "true");

        when(consultationSignatureService.resolveManualSignatureRequestId("123", "sig-request"))
                .thenReturn("sig-request");
        when(digitalSignatureManager.processAndSaveDigitalSignature(
                loggedInInfo, "sig-request", 1, ModuleType.CONSULTATION))
                .thenReturn(null);

        action.execute();

        assertThat(existing.getSignatureImg()).isEqualTo("123");
        verify(consultationRequestDao).merge(existing);
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
    }

    @Test
    @DisplayName("saves a stamp for the selected signature provider when an update has no stored signature")
    void shouldSaveStampForSelectedProvider_whenUpdateHasNoStoredSignature() throws Exception {
        ConsultationRequest existing = new ConsultationRequest();
        // AbstractDao has find(Object) and find(int); the action calls find(Integer), so stub the Object overload.
        when(consultationRequestDao.find(Integer.valueOf(9))).thenReturn(existing);

        DigitalSignature savedStamp = mock(DigitalSignature.class);
        when(savedStamp.getId()).thenReturn(55);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("0");
        action.setSignatureImg("");
        request.setParameter("newSignature", "false");

        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.SAVED, savedStamp));

        action.execute();

        assertThat(existing.getSignatureImg()).isEqualTo("55");
        verify(consultationSignatureService).saveConsultationStamp(loggedInInfo, "999998", 1);
        verify(consultationRequestDao).merge(existing);
    }

    /**
     * Pins the intended create/update asymmetry: unlike the update branch, a new consultation has no
     * stored-id fallback, so a manual re-sign that yields no DigitalSignature persists a null signatureImg
     * (and never falls back to a stamp).
     */
    @Test
    @DisplayName("persists a null signature without a stored-id fallback when a manual sign yields nothing on create")
    void shouldPersistNullSignature_whenManualSignYieldsNothingOnCreate() throws Exception {
        ConsultationRequest[] persisted = new ConsultationRequest[1];
        doAnswer(invocation -> {
            ConsultationRequest consult = invocation.getArgument(0);
            // ConsultationRequest#id is a generated @Id with no setter; assign it so the action's
            // post-persist Integer.parseInt(requestId) does not throw.
            ReflectionTestUtils.setField(consult, "id", 7);
            persisted[0] = consult;
            return null;
        }).when(consultationRequestDao).persist(org.mockito.ArgumentMatchers.any(ConsultationRequest.class));

        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("0");
        action.setSignatureImg("123");
        request.setParameter("newSignature", "true");

        when(consultationSignatureService.resolveManualSignatureRequestId("123", "sig-request"))
                .thenReturn("sig-request");
        when(digitalSignatureManager.processAndSaveDigitalSignature(
                loggedInInfo, "sig-request", 1, ModuleType.CONSULTATION))
                .thenReturn(null);

        action.execute();

        assertThat(persisted[0]).isNotNull();
        assertThat(persisted[0].getSignatureImg()).isNull();
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
    }

    @Test
    @DisplayName("rejects GET with 405 and performs no persistence")
    void shouldRejectGet_withMethodNotAllowed() throws Exception {
        request.setMethod("GET");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(consultationRequestDao, never()).persist(any());
        verify(consultationRequestDao, never()).merge(any());
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
        verify(documentAttachmentManager, never()).renderConsultationFormWithAttachments(any(), any());
    }

    @Test
    @DisplayName("warns the provider but still saves when a stamp cannot be applied on create")
    void shouldWarnButStillSave_whenStampFailsOnCreate() throws Exception {
        ConsultationRequest[] persisted = new ConsultationRequest[1];
        doAnswer(invocation -> {
            ConsultationRequest consult = invocation.getArgument(0);
            ReflectionTestUtils.setField(consult, "id", 7);
            persisted[0] = consult;
            return null;
        }).when(consultationRequestDao).persist(org.mockito.ArgumentMatchers.any(ConsultationRequest.class));

        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("0");
        action.setSignatureImg("");
        request.setParameter("newSignature", "false");

        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.STAMP_FILE_MISSING, null));

        action.execute();

        assertThat(persisted[0]).isNotNull();
        assertThat(persisted[0].getSignatureImg()).isNull();
        // The warning must survive the post-save redirect, so assert the redirect URL carries the flag
        // rather than the request attribute (which is discarded by the 302).
        assertThat(response.getRedirectedUrl()).contains("signatureNotApplied=1");
    }

    @Test
    @DisplayName("warns the provider but still saves when a manual signature cannot be persisted on create")
    void shouldWarnButStillSave_whenManualFailsOnCreate() throws Exception {
        ConsultationRequest[] persisted = new ConsultationRequest[1];
        doAnswer(invocation -> {
            ConsultationRequest consult = invocation.getArgument(0);
            ReflectionTestUtils.setField(consult, "id", 7);
            persisted[0] = consult;
            return null;
        }).when(consultationRequestDao).persist(org.mockito.ArgumentMatchers.any(ConsultationRequest.class));

        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("0");
        action.setSignatureImg("");
        request.setParameter("newSignature", "true");

        when(consultationSignatureService.resolveManualSignatureRequestId("", "sig-request"))
                .thenReturn("sig-request");
        when(consultationSignatureService.wasManualSignatureCaptured("sig-request")).thenReturn(true);
        when(digitalSignatureManager.processAndSaveDigitalSignature(
                loggedInInfo, "sig-request", 1, ModuleType.CONSULTATION))
                .thenReturn(null);

        action.execute();

        assertThat(persisted[0]).isNotNull();
        assertThat(response.getRedirectedUrl()).contains("signatureNotApplied=1");
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
    }

    @Test
    @DisplayName("does not warn when no manual signature was collected on create")
    void shouldNotWarn_whenNoManualSignatureCollectedOnCreate() throws Exception {
        ConsultationRequest[] persisted = new ConsultationRequest[1];
        doAnswer(invocation -> {
            ConsultationRequest consult = invocation.getArgument(0);
            ReflectionTestUtils.setField(consult, "id", 7);
            persisted[0] = consult;
            return null;
        }).when(consultationRequestDao).persist(org.mockito.ArgumentMatchers.any(ConsultationRequest.class));

        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("0");
        action.setSignatureImg("");
        request.setParameter("newSignature", "true");

        when(consultationSignatureService.resolveManualSignatureRequestId("", "sig-request"))
                .thenReturn("sig-request");
        when(consultationSignatureService.wasManualSignatureCaptured("sig-request")).thenReturn(false);
        when(digitalSignatureManager.processAndSaveDigitalSignature(
                loggedInInfo, "sig-request", 1, ModuleType.CONSULTATION))
                .thenReturn(null);

        action.execute();

        assertThat(persisted[0]).isNotNull();
        assertThat(persisted[0].getSignatureImg()).isNull();
        // Benign unsigned save: the redirect must NOT carry the warning flag.
        assertThat(response.getRedirectedUrl()).doesNotContain("signatureNotApplied");
    }

    @Test
    @DisplayName("warns the provider but still saves when a stamp cannot be applied on update")
    void shouldWarnButStillSave_whenStampFailsOnUpdate() throws Exception {
        ConsultationRequest existing = new ConsultationRequest();
        when(consultationRequestDao.find(Integer.valueOf(9))).thenReturn(existing);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("0");
        action.setSignatureImg("");
        request.setParameter("newSignature", "false");

        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.STAMP_FILE_MISSING, null));

        action.execute();

        assertThat(existing.getSignatureImg()).isNull();
        assertThat(response.getRedirectedUrl()).contains("signatureNotApplied=1");
        verify(consultationRequestDao).merge(existing);
    }

    @Test
    @DisplayName("warns the provider but still saves when a collected manual signature fails to persist on update")
    void shouldWarnButStillSave_whenCapturedManualFailsOnUpdate() throws Exception {
        ConsultationRequest existing = new ConsultationRequest();
        when(consultationRequestDao.find(Integer.valueOf(9))).thenReturn(existing);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("0");
        action.setSignatureImg("");
        request.setParameter("newSignature", "true");

        when(consultationSignatureService.resolveManualSignatureRequestId("", "sig-request"))
                .thenReturn("sig-request");
        when(consultationSignatureService.wasManualSignatureCaptured("sig-request")).thenReturn(true);
        when(digitalSignatureManager.processAndSaveDigitalSignature(
                loggedInInfo, "sig-request", 1, ModuleType.CONSULTATION))
                .thenReturn(null);

        action.execute();

        assertThat(existing.getSignatureImg()).isNull();
        assertThat(response.getRedirectedUrl()).contains("signatureNotApplied=1");
        verify(consultationRequestDao).merge(existing);
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
    }
}
