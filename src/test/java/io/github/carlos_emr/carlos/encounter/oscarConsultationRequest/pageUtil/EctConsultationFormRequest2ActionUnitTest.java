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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.ConsultationPreviewSignatureOutcome;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("EctConsultationFormRequest2Action")
@Tag("unit")
class EctConsultationFormRequest2ActionUnitTest extends CarlosUnitTestBase {

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
    private ConsultationManager consultationManager;
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
        consultationManager = mock(ConsultationManager.class);
        consultationRequestDao = mock(ConsultationRequestDao.class);

        var transactions = createAndRegisterMock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ConsultationManager.class, consultationManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(FaxManager.class, mock(FaxManager.class));
        registerMock(DigitalSignatureManager.class, digitalSignatureManager);
        registerMock(ConsultationSignatureService.class, consultationSignatureService);
        registerMock(ConsultationRequestDao.class, consultationRequestDao);
        registerMock(ConsultationRequestExtDao.class, mock(ConsultationRequestExtDao.class));
        ProfessionalSpecialistDao specialists = mock(ProfessionalSpecialistDao.class);
        ProfessionalSpecialist activeConsultant = new ProfessionalSpecialist();
        ReflectionTestUtils.setField(activeConsultant, "id", 7);
        when(specialists.find((Object) 7)).thenReturn(activeConsultant);
        registerMock(ProfessionalSpecialistDao.class, specialists);
        registerMock(DemographicManager.class, demographicManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), eq("1")))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(1))).thenReturn(true);
        when(consultationSignatureService.resolveManualSignatureRequestId("", "sig-request"))
                .thenReturn("sig-request");
        when(consultationSignatureService.resolveSignatureProviderNo("999998", "999998", "999998"))
                .thenReturn("999998");
        when(demographicManager.getDemographicFormattedName(loggedInInfo, 1)).thenReturn("Patient, Test");

        pdfPath = Files.createTempFile("consult-preview", ".pdf");
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response)).thenReturn(pdfPath);
        when(documentAttachmentManager.convertPDFToBase64(pdfPath)).thenReturn(PDF_BASE64);
        when(consultationRequestDao.find(9)).thenReturn(consultationRequest(1));

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
    @DisplayName("persists a captured manual signature before direct print preview")
    void shouldPersistManualSignature_beforeDirectPrintPreview() throws Exception {
        action.setSignatureImg("9999981000");
        request.setParameter("newSignature", "true");
        request.setParameter("newSignatureImg", "9999981000");
        when(consultationSignatureService.resolveManualSignatureRequestId("9999981000", "9999981000"))
                .thenReturn("9999981000");
        when(consultationSignatureService.saveManualSignatureForPreview(
                loggedInInfo, 9, 1, "9999981000", "9999981000", "999998"))
                .thenReturn(new ConsultationPreviewSignatureOutcome(ConsultationPreviewSignatureOutcome.Status.SAVED, "77"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"consultPDF\":\"" + PDF_BASE64 + "\"");
        assertThat(response.getContentAsString()).contains("\"errorMessage\":null");
        assertThat(response.getContentAsString()).contains("\"warningMessage\":null");
        assertThat(response.getContentAsString()).contains("\"signatureImg\":\"77\"");
        verify(consultationSignatureService).saveManualSignatureForPreview(
                loggedInInfo, 9, 1, "9999981000", "9999981000", "999998");
        verify(documentAttachmentManager).renderConsultationFormWithAttachments(request, response);
    }

    @Test
    @DisplayName("returns the base64 PDF JSON without creating a duplicate signature for direct print preview")
    void shouldReturnJsonPdfWithoutDuplicateSignature_forDirectPrintPreview() throws Exception {
        action.setSignatureImg("123");
        request.setParameter("newSignature", "false");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"consultPDF\":\"" + PDF_BASE64 + "\"");
        assertThat(response.getContentAsString()).contains("\"errorMessage\":null");
        assertThat(response.getContentAsString()).contains("\"warningMessage\":null");
        assertThat(response.getContentAsString()).contains("\"signatureImg\":null");
        verify(digitalSignatureManager, never()).processAndSaveDigitalSignature(any(), any(), any(), any());
        verify(consultationRequestDao, never()).merge(any());
    }

    @Test
    @DisplayName("warns but still returns unsigned PDF JSON when a submitted manual signature cannot be persisted")
    void shouldWarnButReturnPdf_whenManualSignatureCannotBePersistedForDirectPrintPreview() throws Exception {
        action.setSignatureImg("9999981000");
        request.setParameter("newSignature", "true");
        request.setParameter("newSignatureImg", "9999981000");
        when(consultationSignatureService.resolveManualSignatureRequestId("9999981000", "9999981000"))
                .thenReturn("9999981000");
        when(consultationSignatureService.saveManualSignatureForPreview(
                loggedInInfo, 9, 1, "9999981000", "9999981000", "999998"))
                .thenReturn(new ConsultationPreviewSignatureOutcome(ConsultationPreviewSignatureOutcome.Status.PERSIST_FAILED, ""));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).contains("\"consultPDF\":\"" + PDF_BASE64 + "\"");
        assertThat(response.getContentAsString()).contains("The captured signature could not be saved and will not appear on the PDF.");
        assertThat(response.getContentAsString()).contains("\"signatureImg\":null");
        assertThat(request.getAttribute(ConsultationSignatureService.SUPPRESS_SIGNATURE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
        verify(consultationRequestDao, never()).merge(any());
        verify(documentAttachmentManager).renderConsultationFormWithAttachments(request, response);
    }

    @Test
    @DisplayName("warns but returns unsigned PDF JSON when preview signature persistence throws")
    void shouldReturnUnsignedPdfWithWarning_whenManualSignaturePersistenceThrowsForDirectPrintPreview() throws Exception {
        action.setSignatureImg("9999981000");
        request.setParameter("newSignature", "true");
        request.setParameter("newSignatureImg", "9999981000");
        when(consultationSignatureService.resolveManualSignatureRequestId("9999981000", "9999981000"))
                .thenReturn("9999981000");
        when(consultationSignatureService.saveManualSignatureForPreview(
                loggedInInfo, 9, 1, "9999981000", "9999981000", "999998"))
                .thenThrow(new DataIntegrityViolationException("database commit failed"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).contains("\"consultPDF\":\"" + PDF_BASE64 + "\"");
        assertThat(response.getContentAsString()).contains("The captured signature could not be saved and will not appear on the PDF.");
        assertThat(request.getAttribute(ConsultationSignatureService.SUPPRESS_SIGNATURE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
        verify(documentAttachmentManager).renderConsultationFormWithAttachments(request, response);
    }

    /**
     * Issue #4693: the packet names the attached labs it cannot include on a page of its own; the
     * clinician who pressed Print is told in the same reply that brings the PDF.
     */
    @Test
    @DisplayName("should warn in the preview reply about attached labs the PDF could not include")
    void shouldWarnAboutLabsNotIncluded_forDirectPrintPreview() throws Exception {
        action.setSignatureImg("123");
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response)).thenAnswer(invocation -> {
            request.setAttribute(ConsultationLabsNotIncluded.REQUEST_ATTRIBUTE,
                    List.of("MDS lab result: Hem/Chem/Other, 2024-03-05 10:22:00"));
            return pdfPath;
        });

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        JsonNode json = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(json.get("consultPDF").asText()).isEqualTo(PDF_BASE64);
        assertThat(json.get("warningMessage").asText())
                .startsWith("The PDF was created, but these attached lab results could not be included in it:")
                .contains("\n- MDS lab result: Hem/Chem/Other, 2024-03-05 10:22:00\n");
    }

    @Test
    @DisplayName("should keep the signature warning when the preview also leaves out labs")
    void shouldKeepSignatureWarning_whenPreviewAlsoLeavesOutLabs() throws Exception {
        action.setSignatureImg("9999981000");
        request.setParameter("newSignature", "true");
        request.setParameter("newSignatureImg", "9999981000");
        when(consultationSignatureService.resolveManualSignatureRequestId("9999981000", "9999981000"))
                .thenReturn("9999981000");
        when(consultationSignatureService.saveManualSignatureForPreview(
                loggedInInfo, 9, 1, "9999981000", "9999981000", "999998"))
                .thenReturn(new ConsultationPreviewSignatureOutcome(ConsultationPreviewSignatureOutcome.Status.PERSIST_FAILED, ""));
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response)).thenAnswer(invocation -> {
            request.setAttribute(ConsultationLabsNotIncluded.REQUEST_ATTRIBUTE, List.of("Lab result (source not confirmed)"));
            return pdfPath;
        });

        action.execute();

        String warning = new ObjectMapper().readTree(response.getContentAsString()).get("warningMessage").asText();
        assertThat(warning).contains("The captured signature could not be saved and will not appear on the PDF.");
        assertThat(warning).contains("- Lab result (source not confirmed)");
    }

    @Test
    @DisplayName("returns an error without rendering when direct print preview targets a missing consultation")
    void shouldReturnErrorWithoutRendering_whenDirectPrintPreviewTargetMissing() throws Exception {
        when(consultationRequestDao.find(9)).thenReturn(null);
        action.setSignatureImg("9999981000");
        request.setParameter("newSignature", "true");
        request.setParameter("newSignatureImg", "9999981000");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).contains("Consultation request unavailable.");
        verify(consultationSignatureService, never()).saveManualSignatureForPreview(any(), anyInt(), anyInt(), any(), any(), any());
        verify(documentAttachmentManager, never()).renderConsultationFormWithAttachments(any(), any());
    }

    @Test
    @DisplayName("returns an error without rendering when direct print preview demographic does not match the consultation")
    void shouldReturnErrorWithoutRendering_whenDirectPrintPreviewDemographicMismatches() throws Exception {
        when(consultationRequestDao.find(9)).thenReturn(consultationRequest(2));
        action.setSignatureImg("9999981000");
        request.setParameter("newSignature", "true");
        request.setParameter("newSignatureImg", "9999981000");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).contains("Consultation request unavailable.");
        verify(consultationSignatureService, never()).saveManualSignatureForPreview(any(), anyInt(), anyInt(), any(), any(), any());
        verify(documentAttachmentManager, never()).renderConsultationFormWithAttachments(any(), any());
    }

    @Test
    @DisplayName("returns an indistinguishable preview error before lookup when consult write is missing")
    void shouldReturnUnavailablePreviewError_withoutLookupWhenConsultWriteMissing() throws Exception {
        action.setDemographicNo("2");
        action.setSignatureImg("9999981000");
        request.setParameter("newSignature", "true");
        request.setParameter("newSignatureImg", "9999981000");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).contains("Consultation request unavailable.");
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", "w", "2");
        verify(consultationRequestDao, never()).find(9);
        verify(consultationSignatureService, never()).saveManualSignatureForPreview(any(), anyInt(), anyInt(), any(), any(), any());
        verify(documentAttachmentManager, never()).renderConsultationFormWithAttachments(any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("keeps print preview errors generic when rendering fails")
    void shouldReturnGenericErrorMessage_whenDirectPrintPreviewFails(boolean checkedFailure) throws Exception {
        Exception failure = checkedFailure
                ? new io.github.carlos_emr.carlos.utility.PDFGenerationException("PRIVATE_RENDER_MESSAGE", new IllegalStateException("PRIVATE_RENDER_CAUSE"))
                : new RuntimeException("PRIVATE_RENDER_MESSAGE", new IllegalStateException("PRIVATE_RENDER_CAUSE"));
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response))
                .thenThrow(failure);

        String result;
        try (var capture = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(EctConsultationFormRequest2Action.class)) {
            result = action.execute();
            assertThat(capture.messages()).anyMatch(message -> message.contains(failure.getClass().getSimpleName()));
            assertThat(capture.messages().toString()).doesNotContain("PRIVATE_RENDER_MESSAGE", "PRIVATE_RENDER_CAUSE");
            assertThat(capture.events()).allMatch(event -> event.getThrown() == null);
        }

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString())
                .contains("A print preview of this consultation could not be generated. Please try again or contact support.")
                .doesNotContain("PRIVATE_RENDER_MESSAGE", "PRIVATE_RENDER_CAUSE");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("should bound consultation JSON and fallback response diagnostics")
    void shouldKeepResponseDiagnosticsPrivate_whenWritingFails(boolean fallbackFails) throws Exception {
        var failedResponse = mock(HttpServletResponse.class);
        when(failedResponse.getWriter()).thenThrow(new java.io.IOException("PRIVATE_RESPONSE_MESSAGE",
                new IllegalStateException("PRIVATE_RESPONSE_CAUSE")));
        if (fallbackFails) org.mockito.Mockito.doThrow(new java.io.IOException("PRIVATE_FALLBACK_MESSAGE",
                new IllegalStateException("PRIVATE_FALLBACK_CAUSE"))).when(failedResponse).sendError(500);
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(EctConsultationFormRequest2Action.class)) {
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(action, "generatePDFResponse", request, failedResponse);
            verify(failedResponse).sendError(500);
            assertThat(logs.messages()).hasSize(fallbackFails ? 2 : 1);
            assertThat(logs.messages().toString()).contains("IOException").doesNotContain("PRIVATE_");
            assertThat(logs.events()).allMatch(event -> event.getThrown() == null);
        }
    }

    @Test
    @DisplayName("reuses the stored signature id when a manual re-sign produces no new signature on update")
    void shouldReuseStoredSignatureId_whenManualReSignReturnsNullOnUpdate() throws Exception {
        ConsultationRequest existing = consultationRequest(1, "123"); // pre-existing DB-stored signature id
        when(consultationRequestDao.find(9)).thenReturn(existing);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("7");
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
    @DisplayName("returns input without archiving when the consultation update request id is malformed")
    void shouldReturnInput_withoutArchivingWhenUpdateRequestIdIsMalformed() throws Exception {
        action.setSubmission("Update");
        action.setRequestId("../9");
        action.setDemographicNo("1");
        action.setService("1");
        action.setSpecialist("7");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        verify(consultationManager, never()).archiveConsultationRequest(any(Integer.class));
        verifyNoInteractions(consultationRequestDao);
    }

    @Test
    @DisplayName("returns input without archiving when the consultation update target no longer exists")
    void shouldReturnInput_whenConsultationUpdateTargetIsMissing() throws Exception {
        when(consultationRequestDao.find(9)).thenReturn(null);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setDemographicNo("1");
        action.setService("1");
        action.setSpecialist("7");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(action.getActionErrors()).containsExactly("Consultation request unavailable");
        // The input result forwards to ViewRequest, so the form's alert reads this attribute; the
        // action errors do not survive the forward.
        assertThat(request.getAttribute("errorMessage")).isEqualTo("Consultation request unavailable");
        verify(consultationManager, never()).archiveConsultationRequest(9);
        verify(consultationRequestDao).find(9);
        verify(consultationRequestDao, never()).merge(any());
    }

    @Test
    @DisplayName("returns input without archiving when the stored demographic does not match the submitted patient")
    void shouldReturnInput_beforeArchivingWhenStoredDemographicMismatches() throws Exception {
        when(consultationRequestDao.find(9)).thenReturn(consultationRequest(2));

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setDemographicNo("1");
        action.setService("1");
        action.setSpecialist("7");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(action.getActionErrors()).containsExactly("Consultation request unavailable");
        verify(consultationManager, never()).archiveConsultationRequest(any(Integer.class));
        verify(consultationRequestDao, never()).merge(any());
    }

    @Test
    @DisplayName("denies update before lookup when scoped consult write is missing for the submitted patient")
    void shouldDenyUpdate_beforeLookupWhenSubmittedPatientWritePrivilegeMissing() {
        action.setSubmission("Update");
        action.setRequestId("9");
        action.setDemographicNo("2");
        action.setService("1");
        action.setSpecialist("7");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_con)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", "w", "2");
        verify(consultationRequestDao, never()).find(9);
        verify(consultationManager, never()).archiveConsultationRequest(any(Integer.class));
        verify(consultationRequestDao, never()).merge(any());
    }

    @Test
    @DisplayName("denies standalone fax before lookup when consult write is missing")
    void shouldDenyStandaloneFax_beforeLookupWhenSubmittedPatientWritePrivilegeMissing() {
        action.setSubmission("And Fax");
        action.setRequestId("9");
        action.setDemographicNo("2");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_con)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", "w", "2");
        verify(consultationRequestDao, never()).find(9);
        verifyNoInteractions(documentAttachmentManager);
    }

    @Test
    void shouldDenySubmitAndFax_beforeAnySaveWhenFeatureIsDisabled() {
        action.setSubmission("Submit And Fax");
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.isConsultationFaxEnabled()).thenReturn(false);
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                    .hasMessage("consultation fax is disabled");
            verify(properties).isConsultationFaxEnabled();
            verifyNoInteractions(consultationRequestDao, documentAttachmentManager, consultationManager);
        }
    }

    @Test
    void shouldDenySubmitAndFax_beforeAnySaveWhenFaxWriteMissing() {
        action.setSubmission("Submit And Fax");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)).thenReturn(false);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_fax)");
        verifyNoInteractions(consultationManager, consultationRequestDao, documentAttachmentManager,
                consultationSignatureService, demographicManager, digitalSignatureManager);
    }

    @Test
    void shouldDenyUpdateAndFax_beforeArchiveWhenFaxAccountReadMissing() {
        action.setSubmission("Update And Fax");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)).thenReturn(false);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_fax)");
        verifyNoInteractions(consultationManager, consultationRequestDao, documentAttachmentManager,
                consultationSignatureService, demographicManager, digitalSignatureManager);
    }

    @Test
    @DisplayName("saves a stamp for the selected signature provider when an update has no stored signature")
    void shouldSaveStampForSelectedProvider_whenUpdateHasNoStoredSignature() throws Exception {
        ConsultationRequest existing = consultationRequest(1);
        when(consultationRequestDao.find(9)).thenReturn(existing);

        DigitalSignature savedStamp = mock(DigitalSignature.class);
        when(savedStamp.getId()).thenReturn(55);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("7");
        action.setSignatureImg("");
        request.setParameter("newSignature", "false");

        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.SAVED, savedStamp));

        action.execute();

        assertThat(existing.getSignatureImg()).isEqualTo("55");
        verify(consultationSignatureService).saveConsultationStamp(loggedInInfo, "999998", 1);
        verify(consultationRequestDao).merge(existing);
    }

    @Test
    void shouldRollBackConsultationSave_whenAttachmentSelectionFails() throws Exception {
        capturePersistedConsultationRequest();
        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("7");
        request.setParameter("newSignature", "true");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("ambiguous lab source"))
                .when(documentAttachmentManager).attachToConsult(eq(loggedInInfo),
                        eq(io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType.LAB), any(), any(), any(), any());

        assertThat(action.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        var transactions = io.github.carlos_emr.carlos.utility.SpringUtils.getBean(org.springframework.transaction.PlatformTransactionManager.class);
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
        assertThat(response.getRedirectedUrl()).isNull();
    }

    /**
     * Pins the intended create/update asymmetry: unlike the update branch, a new consultation has no
     * stored-id fallback, so a manual re-sign that yields no DigitalSignature persists a null signatureImg
     * (and never falls back to a stamp).
     */
    @Test
    @DisplayName("persists a null signature without a stored-id fallback when a manual sign yields nothing on create")
    void shouldPersistNullSignature_whenManualSignYieldsNothingOnCreate() throws Exception {
        ConsultationRequest[] persisted = capturePersistedConsultationRequest();

        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("7");
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

    /**
     * Regression for #2241. Both {@code serviceId} and {@code specId} are nullable columns, so a
     * blank field is a legitimate "not chosen" value. Parsing them eagerly threw out of the action
     * into the Struts global {@code Exception -> error} mapping, which logs nothing, so the
     * clinician's referral was discarded with only a blank page to show for it.
     *
     * <p>Every other create test in this class supplies both fields, which is exactly why the
     * defect survived: the unfilled form was never exercised.</p>
     */
    @Test
    @DisplayName("persists a null serviceId instead of throwing when the service is blank on create")
    void shouldPersistNullServiceId_whenServiceIsBlankOnCreate() throws Exception {
        ConsultationRequest[] persisted = capturePersistedConsultationRequest();

        action.setSubmission("Submit");
        action.setService("");
        action.setSpecialist("7");

        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.SIGNATURES_DISABLED, null));

        action.execute();

        assertThat(persisted[0]).isNotNull();
        assertThat(persisted[0].getServiceId()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "0", "-1", "abc", "2147483648", "999999"})
    void shouldRejectConsultant_beforeAnyCreateSideEffect(String specialist) throws Exception {
        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist(specialist);
        action.setReasonForConsultation("Entered referral reason");
        action.setClinicalInformation("Entered clinical information");
        action.setAppointmentNotes("Entered appointment notes");

        assertThat(action.execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(request.getAttribute("demographicId")).isEqualTo("1");
        assertThat(request.getAttribute("errorMessage")).asString().contains("active consultant");
        assertThat(((java.util.Map<?, ?>) request.getAttribute("consultationSubmittedValues")).get("reasonForConsultation"))
                .isEqualTo("Entered referral reason");
        verify(consultationRequestDao, never()).persist(any());
        verifyNoInteractions(consultationManager, digitalSignatureManager);
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), anyInt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "abc", "2147483648", "999999"})
    void shouldRejectConsultant_beforeArchivingOrMutatingExistingRequest(String specialist) throws Exception {
        ConsultationRequest stored = consultationRequest(1);
        ProfessionalSpecialist original = new ProfessionalSpecialist();
        stored.setProfessionalSpecialist(original);
        stored.setReasonForReferral("Original reason");
        when(consultationRequestDao.find(9)).thenReturn(stored);
        action.setSubmission("Update");
        action.setSpecialist(specialist);
        action.setReasonForConsultation("Unsaved reason");

        assertThat(action.execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(stored.getProfessionalSpecialist()).isSameAs(original);
        assertThat(stored.getReasonForReferral()).isEqualTo("Original reason");
        assertThat(request.getAttribute("reqId")).isEqualTo("9");
        verify(consultationRequestDao, never()).merge(any());
        verifyNoInteractions(consultationManager, digitalSignatureManager);
    }

    @Test
    void shouldRejectInactiveConsultant_beforeSaving() throws Exception {
        ProfessionalSpecialist inactive = new ProfessionalSpecialist();
        ReflectionTestUtils.setField(inactive, "id", 8);
        inactive.setDeleted(true);
        ProfessionalSpecialistDao specialists = mock(ProfessionalSpecialistDao.class);
        when(specialists.find((Object) 8)).thenReturn(inactive);
        registerMock(ProfessionalSpecialistDao.class, specialists);
        action.setSubmission("Submit");
        action.setSpecialist("8");

        assertThat(action.execute()).isEqualTo(ActionSupport.INPUT);
        verify(consultationRequestDao, never()).persist(any());
        verifyNoInteractions(consultationManager, digitalSignatureManager);
    }

    /**
     * Regression for #2241. The blank-service parse existed on the update branch too, so clearing
     * the service on an existing consultation discarded the edit the same way.
     */
    @Test
    @DisplayName("merges a null serviceId instead of throwing when the service is blank on update")
    void shouldMergeNullServiceId_whenServiceIsBlankOnUpdate() throws Exception {
        action.setSubmission("Update");
        action.setService("");
        action.setSpecialist("7");

        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.SIGNATURES_DISABLED, null));

        action.execute();

        ArgumentCaptor<ConsultationRequest> mergedConsultation =
                ArgumentCaptor.forClass(ConsultationRequest.class);
        verify(consultationRequestDao).merge(mergedConsultation.capture());
        assertThat(mergedConsultation.getValue().getServiceId()).isNull();
    }

    /** Prepares an Update of request 9 whose stored Last Follow-up is 2026-11-04. */
    private ConsultationRequest updateWithStoredFollowUp() throws Exception {
        ConsultationRequest existing = consultationRequest(1);
        existing.setFollowUpDate(org.apache.commons.lang3.time.DateUtils.parseDateStrictly("2026-11-04", "yyyy-MM-dd"));
        when(consultationRequestDao.find(9)).thenReturn(existing);
        action.setSubmission("Update Consultation Request");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("7");
        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.SIGNATURES_DISABLED, null));
        return existing;
    }

    /**
     * Issue #5272: the form posts Last Follow-up empty when the clinician clears it. The update
     * branch only ever set a non-blank date, so a cleared field kept the stored date.
     */
    @Test
    @DisplayName("clears the stored follow-up date when the form posts the field empty on update")
    void shouldClearFollowUpDate_whenFieldIsPostedEmptyOnUpdate() throws Exception {
        ConsultationRequest existing = updateWithStoredFollowUp();
        action.setFollowUpDate("");
        request.addParameter("followUpDate", "");

        action.execute();

        verify(consultationRequestDao).merge(existing);
        assertThat(existing.getFollowUpDate()).isNull();
    }

    /** A client that does not post the field at all is not asking to clear it. */
    @Test
    @DisplayName("keeps the stored follow-up date when the update does not post the field")
    void shouldKeepFollowUpDate_whenFieldIsNotPostedOnUpdate() throws Exception {
        ConsultationRequest existing = updateWithStoredFollowUp();

        action.execute();

        verify(consultationRequestDao).merge(existing);
        assertThat(existing.getFollowUpDate())
                .isEqualTo(org.apache.commons.lang3.time.DateUtils.parseDateStrictly("2026-11-04", "yyyy-MM-dd"));
    }

    @Test
    @DisplayName("replaces the stored follow-up date with a posted valid one on update")
    void shouldReplaceFollowUpDate_whenValidDateIsPostedOnUpdate() throws Exception {
        ConsultationRequest existing = updateWithStoredFollowUp();
        action.setFollowUpDate("2026-12-01");
        request.addParameter("followUpDate", "2026-12-01");

        action.execute();

        verify(consultationRequestDao).merge(existing);
        assertThat(existing.getFollowUpDate())
                .isEqualTo(org.apache.commons.lang3.time.DateUtils.parseDateStrictly("2026-12-01", "yyyy-MM-dd"));
    }

    /**
     * Issue #5272: commons-lang's lenient parseDate stored 2026-02-30 as 2026-03-02. An impossible
     * date is now refused before the archive copy, the stamp and the merge, so nothing changes.
     */
    @ParameterizedTest
    @ValueSource(strings = {"followUp:2026-02-30", "followUp:2026-13-01", "appointment:2026-04-31",
            "referral:2026-00-10", "followUp:20260-01-01", "followUp:0999-12-31"})
    @DisplayName("refuses an impossible posted date on update before archiving or saving anything")
    void shouldRefuseImpossibleDate_beforeAnyUpdateSideEffect(String fieldAndValue) throws Exception {
        ConsultationRequest existing = updateWithStoredFollowUp();
        String value = fieldAndValue.substring(fieldAndValue.indexOf(':') + 1);
        switch (fieldAndValue.substring(0, fieldAndValue.indexOf(':'))) {
            case "followUp" -> action.setFollowUpDate(value);
            case "appointment" -> action.setAppointmentDate(value);
            default -> action.setReferalDate(value);
        }

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(consultationManager, never()).archiveConsultationRequest(anyInt());
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
        verify(consultationRequestDao, never()).merge(any());
        assertThat(existing.getFollowUpDate())
                .isEqualTo(org.apache.commons.lang3.time.DateUtils.parseDateStrictly("2026-11-04", "yyyy-MM-dd"));
    }

    @Test
    @DisplayName("refuses an impossible follow-up date on create before the stamp or the consultation is saved")
    void shouldRefuseImpossibleDate_beforeAnyCreateSideEffect() throws Exception {
        action.setSubmission("Submit Consultation Request");
        action.setService("1");
        action.setSpecialist("7");
        action.setFollowUpDate("2026-02-30");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
        verify(consultationRequestDao, never()).persist(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-28", "2028-02-29", "2026/02/28", "1000-01-01", "9999-12-31", "", " "})
    @DisplayName("accepts real calendar dates and blank values")
    void shouldAcceptPostedDates_whenEachIsBlankOrARealDate(String value) {
        action.setFollowUpDate(value);
        action.setAppointmentDate(value);
        action.setReferalDate(value);

        assertThat(action.postedDatesAreValid(new String[]{"yyyy-MM-dd", "yyyy/MM/dd"})).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"GET", "HEAD", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "post", "PoSt", "POſT"})
    @DisplayName("rejects every non-POST verb with 405 and performs no persistence")
    void shouldRejectNonPost_withMethodNotAllowed(String verb) throws Exception {
        request.setMethod(verb);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(consultationRequestDao, never()).persist(any());
        verify(consultationRequestDao, never()).merge(any());
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
        verify(documentAttachmentManager, never()).renderConsultationFormWithAttachments(any(), any());
    }

    @Test
    @DisplayName("warns the provider but still saves when a stamp cannot be applied on create")
    void shouldWarnButStillSave_whenStampFailsOnCreate() throws Exception {
        ConsultationRequest[] persisted = capturePersistedConsultationRequest();

        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("7");
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
        ConsultationRequest[] persisted = capturePersistedConsultationRequest();

        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("7");
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
        ConsultationRequest[] persisted = capturePersistedConsultationRequest();

        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("7");
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
    @DisplayName("preserves an existing stored signature when stamp mode is skipped for a benign update outcome")
    void shouldPreserveStoredSignature_whenStampUpdateReturnsBenignNonSavedOutcome() throws Exception {
        ConsultationRequest existing = consultationRequest(1, "123");
        when(consultationRequestDao.find(9)).thenReturn(existing);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("7");
        action.setSignatureImg("");
        request.setParameter("newSignature", "false");

        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.SIGNATURES_DISABLED, null));

        action.execute();

        assertThat(existing.getSignatureImg()).isEqualTo("123");
        assertThat(response.getRedirectedUrl()).doesNotContain("signatureNotApplied");
        verify(consultationSignatureService).saveConsultationStamp(loggedInInfo, "999998", 1);
        verify(consultationRequestDao).merge(existing);
    }

    @Test
    @DisplayName("warns the provider but preserves an existing stored signature when a stamp cannot be applied on update")
    void shouldWarnButPreserveStoredSignature_whenStampFailsOnUpdate() throws Exception {
        ConsultationRequest existing = consultationRequest(1, "123");
        when(consultationRequestDao.find(9)).thenReturn(existing);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("7");
        action.setSignatureImg("");
        request.setParameter("newSignature", "false");

        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.STAMP_FILE_MISSING, null));

        action.execute();

        assertThat(existing.getSignatureImg()).isEqualTo("123");
        assertThat(response.getRedirectedUrl()).contains("signatureNotApplied=1");
        verify(consultationSignatureService).saveConsultationStamp(loggedInInfo, "999998", 1);
        verify(consultationRequestDao).merge(existing);
    }

    @Test
    @DisplayName("warns the provider but still saves when a collected manual signature fails to persist on update")
    void shouldWarnButStillSave_whenCapturedManualFailsOnUpdate() throws Exception {
        ConsultationRequest existing = consultationRequest(1);
        when(consultationRequestDao.find(9)).thenReturn(existing);

        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("7");
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

    private ConsultationRequest consultationRequest(Integer demographicId) {
        ConsultationRequest consult = new ConsultationRequest();
        ReflectionTestUtils.setField(consult, "id", 9);
        consult.setProviderNo("999998");
        consult.setDemographicId(demographicId);
        return consult;
    }

    /**
     * Issue #3867: the create path persists the consultation and then each attachment type in its
     * own transaction, so a foreign attachment must be refused before the first write rather than
     * half-way through, and the user gets the form's alert instead of the raw error page.
     */
    @Test
    @DisplayName("rejects a create with a foreign attachment before the consultation or signature is saved")
    void shouldReturnInput_beforeAnyWriteWhenCreateAttachmentNotOwned() throws Exception {
        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("7");
        action.setDocNo(new String[] {"10"});
        action.setLabNo(new String[] {"999"});
        org.mockito.Mockito.doThrow(new SecurityException("attachment does not belong to the consultation patient"))
                .when(documentAttachmentManager).verifyConsultAttachments(eq(loggedInInfo), org.mockito.ArgumentMatchers.isNull(),
                        eq(1), org.mockito.ArgumentMatchers.anyMap());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("errorMessage")).asString()
                .startsWith("One or more attachments could not be verified for this patient.")
                .doesNotContain("999");
        // The retry form must render for the consultation patient, not as a blank "null, null".
        assertThat(request.getAttribute("demographicId")).isEqualTo("1");
        assertThat(request.getAttribute("reqId")).isNull();
        verify(consultationRequestDao, never()).persist(any());
        verify(documentAttachmentManager, never()).attachToConsult(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(digitalSignatureManager);
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
    }

    @Test
    @DisplayName("rejects an update with a foreign attachment before archiving or merging the consultation")
    void shouldReturnInput_beforeArchivingWhenUpdateAttachmentNotOwned() throws Exception {
        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("7");
        action.setDocNo(new String[] {"999"});
        org.mockito.Mockito.doThrow(new SecurityException("attachment does not belong to the consultation patient"))
                .when(documentAttachmentManager).verifyConsultAttachments(eq(loggedInInfo), eq(9), eq(1),
                        org.mockito.ArgumentMatchers.anyMap());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("demographicId")).isEqualTo("1");
        assertThat(request.getAttribute("reqId")).isEqualTo("9");
        verify(consultationManager, never()).archiveConsultationRequest(any(Integer.class));
        verify(consultationRequestDao, never()).merge(any());
        verify(documentAttachmentManager, never()).attachToConsult(any(), any(), any(), any(), any(), any());
    }

    /**
     * A patient-scoped {@code _con} write grant is not the circle-of-care check: attaching records to,
     * previewing or faxing a consultation discloses the patient's chart, so it also requires access
     * to the patient's record, before anything is written.
     */
    @Test
    @DisplayName("returns the indistinguishable preview error without rendering when patient record access is denied")
    void shouldReturnUnavailablePreviewError_whenPatientRecordAccessDenied() throws Exception {
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(1))).thenReturn(false);
        action.setSignatureImg("9999981000");
        request.setParameter("newSignature", "true");
        request.setParameter("newSignatureImg", "9999981000");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).contains("Consultation request unavailable.");
        verify(consultationSignatureService, never()).saveManualSignatureForPreview(any(), anyInt(), anyInt(), any(), any(), any());
        verify(documentAttachmentManager, never()).renderConsultationFormWithAttachments(any(), any());
    }

    @Test
    @DisplayName("denies a create that attaches records before any write when patient record access is denied")
    void shouldDenyCreateWithAttachments_whenPatientRecordAccessDenied() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(1))).thenReturn(false);
        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("7");
        action.setDocNo(new String[] {"10"});

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");

        verify(consultationRequestDao, never()).persist(any());
        verifyNoInteractions(documentAttachmentManager, digitalSignatureManager);
        verify(consultationSignatureService, never()).saveConsultationStamp(any(), any(), any());
    }

    @Test
    @DisplayName("denies an update that attaches records before archiving when patient record access is denied")
    void shouldDenyUpdateWithAttachments_whenPatientRecordAccessDenied() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(1))).thenReturn(false);
        action.setSubmission("Update");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("7");
        action.setLabNo(new String[] {"20"});

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");

        verify(consultationManager, never()).archiveConsultationRequest(any(Integer.class));
        verify(consultationRequestDao, never()).merge(any());
        verifyNoInteractions(documentAttachmentManager);
    }

    @Test
    @DisplayName("denies an update-and-fax before archiving when patient record access is denied")
    void shouldDenyUpdateAndFax_whenPatientRecordAccessDenied() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(1))).thenReturn(false);
        action.setSubmission("Update And Fax");
        action.setRequestId("9");
        action.setService("1");
        action.setSpecialist("7");
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.isConsultationFaxEnabled()).thenReturn(true);
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_con)");
        }

        verify(consultationManager, never()).archiveConsultationRequest(any(Integer.class));
        verify(consultationRequestDao, never()).merge(any());
        verifyNoInteractions(documentAttachmentManager);
    }

    @Test
    @DisplayName("still saves an update with no attachments when patient record access is denied")
    void shouldSaveUpdateWithoutAttachments_whenPatientRecordAccessDenied() throws Exception {
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(1))).thenReturn(false);
        action.setSubmission("Update");
        action.setService("1");
        action.setSpecialist("7");
        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.SIGNATURES_DISABLED, null));

        action.execute();

        verify(consultationRequestDao).merge(any(ConsultationRequest.class));
    }

    @Test
    @DisplayName("verifies every attachment type once before the create path writes anything")
    void shouldVerifyAllAttachmentTypes_beforeCreateWrites() throws Exception {
        ConsultationRequest[] persisted = capturePersistedConsultationRequest();
        action.setSubmission("Submit");
        action.setService("1");
        action.setSpecialist("7");
        action.setDocNo(new String[] {"10"});
        when(consultationSignatureService.saveConsultationStamp(loggedInInfo, "999998", 1))
                .thenReturn(new ConsultationStampOutcome(ConsultationStampOutcome.Status.SIGNATURES_DISABLED, null));

        action.execute();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType, String[]>> verified =
                ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(documentAttachmentManager, consultationRequestDao);
        order.verify(documentAttachmentManager).verifyConsultAttachments(eq(loggedInInfo),
                org.mockito.ArgumentMatchers.isNull(), eq(1), verified.capture());
        order.verify(consultationRequestDao).persist(any(ConsultationRequest.class));
        assertThat(verified.getValue()).containsKeys(
                io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType.DOC,
                io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType.LAB,
                io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType.EFORM,
                io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType.HRM);
        assertThat(persisted[0]).isNotNull();
    }

    private ConsultationRequest[] capturePersistedConsultationRequest() {
        ConsultationRequest[] persisted = new ConsultationRequest[1];
        doAnswer(invocation -> {
            ConsultationRequest consult = invocation.getArgument(0);
            // ConsultationRequest#id is a generated @Id with no setter; assign it so the action's
            // post-persist attachment path has the generated request id.
            ReflectionTestUtils.setField(consult, "id", 7);
            persisted[0] = consult;
            return null;
        }).when(consultationRequestDao).persist(org.mockito.ArgumentMatchers.any(ConsultationRequest.class));
        return persisted;
    }

    private ConsultationRequest consultationRequest(Integer demographicId, String signatureImg) {
        ConsultationRequest consult = consultationRequest(demographicId);
        consult.setSignatureImg(signatureImg);
        return consult;
    }

    /**
     * Issue #3867: the fax cover page listed attachment names looked up by consultation id alone, so
     * a legacy consult_docs row could put another patient's document description or eForm name on
     * the cover. It now lists only what the faxed packet contains.
     */
    @Test
    @DisplayName("should list only the patient's own documents, eForms and HL7 labs on the fax cover")
    void shouldOmitForeignAttachmentNames_fromFaxCoverList() {
        CtlDocumentDao ctlDocumentDao = mock(CtlDocumentDao.class);
        PatientLabRoutingDao patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        EFormDataDao eFormDataDao = mock(EFormDataDao.class);
        registerMock(PatientLabRoutingDao.class, patientLabRoutingDao);
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(AttachmentOwnershipService.class, new AttachmentOwnershipService(ctlDocumentDao,
                patientLabRoutingDao, eFormDataDao, mock(HRMDocumentToDemographicDao.class), consultationRequestDao));
        when(ctlDocumentDao.findDocumentNosForDemographic(eq(1), anyCollection())).thenReturn(List.of(10));
        when(eFormDataDao.findFdidsForDemographic(eq(1), anyCollection())).thenReturn(List.of(7));
        when(patientLabRoutingDao.findLabNosForDemographic(eq(1), eq(PatientLabRoutingDao.HL7), anyCollection()))
                .thenReturn(List.of(30));

        EDoc ownDoc = mock(EDoc.class);
        when(ownDoc.getDocId()).thenReturn("10");
        when(ownDoc.getDescription()).thenReturn("Own referral letter");
        EDoc foreignDoc = mock(EDoc.class);
        when(foreignDoc.getDocId()).thenReturn("11");
        when(foreignDoc.getDescription()).thenReturn("Foreign discharge summary");
        EFormData ownEForm = mock(EFormData.class);
        when(ownEForm.getId()).thenReturn(7);
        when(ownEForm.getFormName()).thenReturn("Own eForm");
        EFormData foreignEForm = mock(EFormData.class);
        when(foreignEForm.getId()).thenReturn(8);
        when(foreignEForm.getFormName()).thenReturn("Foreign eForm");
        when(consultationManager.getAttachedEForms("9")).thenReturn(List.of(ownEForm, foreignEForm));
        LabResultData ownLab = mock(LabResultData.class);
        when(ownLab.getSegmentID()).thenReturn("30");
        when(ownLab.getDisciplineDisplayString()).thenReturn("Own lab");
        when(ownLab.isHL7TEXT()).thenReturn(true);
        LabResultData foreignLab = mock(LabResultData.class);
        when(foreignLab.isHL7TEXT()).thenReturn(true);
        when(foreignLab.getSegmentID()).thenReturn("31");
        when(foreignLab.getDisciplineDisplayString()).thenReturn("Foreign lab");
        // A CML lab whose number collides with the patient's own HL7 lab 30: the packet would print
        // lab 30 in its place, so the cover must not list it either.
        LabResultData collidingCmlLab = mock(LabResultData.class);
        when(collidingCmlLab.isHL7TEXT()).thenReturn(false);
        org.mockito.Mockito.lenient().when(collidingCmlLab.getSegmentID()).thenReturn("30");
        org.mockito.Mockito.lenient().when(collidingCmlLab.getDisciplineDisplayString()).thenReturn("Legacy CML lab");

        try (MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
             MockedConstruction<CommonLabResultData> labConstruction = mockConstruction(CommonLabResultData.class,
                     (labData, context) -> when(labData.populateLabResultsData(any(), eq("1"), eq("9"), anyBoolean()))
                             .thenReturn(new ArrayList<>(List.of(ownLab, foreignLab, collidingCmlLab))))) {
            eDocUtilMock.when(() -> EDocUtil.listDocs(any(), eq("1"), eq("9"), anyBoolean()))
                    .thenReturn(new ArrayList<>(List.of(ownDoc, foreignDoc)));

            List<String> names = action.faxCoverAttachments(loggedInInfo, "9", "1", Locale.ENGLISH).documents();

            assertThat(names).containsExactly("Own referral letter", "Own lab", "Own eForm");
        }
    }

    /**
     * Issue #4693: the faxed packet cannot hold a CML, MDS or PathNet lab. The cover page used to
     * list only what the packet contains, so the sender never learned that such a lab was left out.
     * It is now named apart from the attachments the fax does contain.
     */
    @Test
    @DisplayName("should name each attached lab the fax cannot include, apart from what it does include")
    void shouldNameLabsNotIncluded_onFaxCover() {
        PatientLabRoutingDao patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        registerMock(PatientLabRoutingDao.class, patientLabRoutingDao);
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(AttachmentOwnershipService.class, new AttachmentOwnershipService(mock(CtlDocumentDao.class),
                patientLabRoutingDao, mock(EFormDataDao.class), mock(HRMDocumentToDemographicDao.class), consultationRequestDao));
        // Number 20 is also one of the patient's HL7 labs; only the listed type keeps the MDS lab out
        // of the "included" names.
        when(patientLabRoutingDao.findLabNosForDemographic(eq(1), eq(PatientLabRoutingDao.HL7), anyCollection()))
                .thenReturn(List.of(30, 20));
        LabResultData hl7Lab = new LabResultData(LabResultData.HL7TEXT);
        hl7Lab.setSegmentID("30");
        hl7Lab.discipline = "CHEM";
        LabResultData mdsLab = new LabResultData(LabResultData.MDS);
        mdsLab.setSegmentID("20");
        mdsLab.discipline = "Hem/Chem/Other";
        mdsLab.dateTime = "2024-03-05 10:22:00";
        when(consultationManager.getAttachedEForms("9")).thenReturn(List.of());

        try (MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class);
             MockedConstruction<CommonLabResultData> labConstruction = mockConstruction(CommonLabResultData.class,
                     (labData, context) -> when(labData.populateLabResultsData(any(), eq("1"), eq("9"), anyBoolean()))
                             .thenReturn(new ArrayList<>(List.of(hl7Lab, mdsLab))))) {
            eDocUtilMock.when(() -> EDocUtil.listDocs(any(), eq("1"), eq("9"), anyBoolean())).thenReturn(new ArrayList<>());

            EctConsultationFormRequest2Action.FaxCoverAttachments cover =
                    action.faxCoverAttachments(loggedInInfo, "9", "1", Locale.ENGLISH);

            assertThat(cover.documents()).containsExactly("CHEM");
            assertThat(cover.labsNotIncluded()).containsExactly("MDS lab result: Hem/Chem/Other, 2024-03-05 10:22:00");
        }
    }
}
