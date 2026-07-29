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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Error-path unit test for {@link EctConsultationFormRequestPrintAction22Action}: when an attached
 * eForm cannot be rendered, the failing attachment is named in the wrapped
 * {@link PDFGenerationException} (replacing the pre-fix context-free NPE from
 * {@code Files.newInputStream(null)}), and the action returns the {@code "error"} result.
 *
 * <p>The pre-attachment consultation-PDF writer and lab-data loader are stubbed to no-ops (mocked
 * construction) and the document listing is stubbed (mocked static) so the test reaches the
 * eForm-attachment loop without a database.</p>
 */
@DisplayName("EctConsultationFormRequestPrintAction22Action")
@Tag("unit")
@Tag("fast")
class EctConsultationFormRequestPrintAction22ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<EDocUtil> eDocUtilMock;
    private MockedConstruction<CommonLabResultData> commonLabResultDataConstruction;
    private MockedConstruction<ConsultationPDFCreator> consultationPdfCreatorConstruction;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo loggedInInfo;
    private SecurityInfoManager securityInfoManager;
    private ConsultationManager consultationManager;
    private FaxManager faxManager;

    private EctConsultationFormRequestPrintAction22Action action;
    private Object originalStaticFaxManager;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/encounter/PrintConsultation");
        request.setParameter("reqId", "42");
        request.setParameter("demographicNo", "1");
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        consultationManager = mock(ConsultationManager.class);
        faxManager = mock(FaxManager.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ConsultationManager.class, consultationManager);
        registerMock(FaxManager.class, faxManager);
        ConsultationRequestDao consultationRequestDao = mock(ConsultationRequestDao.class);
        ConsultationRequest consultationRequest = new ConsultationRequest();
        consultationRequest.setDemographicId(1);
        when(consultationRequestDao.find(42)).thenReturn(consultationRequest);
        registerMock(ConsultationRequestDao.class, consultationRequestDao);
        // CommonLabResultData resolves these DAOs in its static initializer; register them so the
        // class can initialize when Mockito instruments it for mocked construction below.
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        eDocUtilMock = mockStatic(EDocUtil.class);
        eDocUtilMock.when(() -> EDocUtil.listDocs(any(), any(), any(), anyBoolean()))
                .thenReturn(new ArrayList<EDoc>());

        // Keep the pre-loop consultation-PDF writer and lab-data loader inert so the test reaches the
        // eForm attachment loop without a database.
        commonLabResultDataConstruction = mockConstruction(CommonLabResultData.class);
        consultationPdfCreatorConstruction = mockConstruction(ConsultationPDFCreator.class);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("r"), isNull())).thenReturn(true);

        action = new EctConsultationFormRequestPrintAction22Action();
        // faxManager is a STATIC field resolved once at class load; capture the original and
        // override it so this test's mock is used regardless of when the class was first loaded.
        // tearDown restores it — a dead mock left on the static field would silently poison any
        // later test (or production-code path) constructing this action in the same fork.
        originalStaticFaxManager = ReflectionTestUtils.getField(
                EctConsultationFormRequestPrintAction22Action.class, "faxManager");
        ReflectionTestUtils.setField(EctConsultationFormRequestPrintAction22Action.class, "faxManager", faxManager);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(
                EctConsultationFormRequestPrintAction22Action.class, "faxManager", originalStaticFaxManager);
        if (consultationPdfCreatorConstruction != null) {
            consultationPdfCreatorConstruction.close();
        }
        if (commonLabResultDataConstruction != null) {
            commonLabResultDataConstruction.close();
        }
        if (eDocUtilMock != null) {
            eDocUtilMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should return NONE after a successful print whose bytes were already streamed")
    void shouldReturnNone_afterSuccessfulPrintStreaming() throws Exception {
        // No attachments; ConcatPDF is stubbed inert so the (mock-inert) consultation PDF
        // concatenates trivially. The direct-response contract requires NONE after streaming —
        // a named result (or bare null) lets Struts write page content into the committed
        // binary response.
        try (MockedStatic<ConcatPDF> concatPdfMock = mockStatic(ConcatPDF.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(request.getAttribute("printError")).isNull();
        }
    }

    @Test
    @DisplayName("should return NONE when a stream close fails after a fully successful print")
    void shouldReturnNone_whenStreamCloseFailsAfterSuccessfulPrint() throws Exception {
        // A cleanup-only close failure after the PDF bytes were streamed must not flip the
        // result to "error": Struts would forward an error JSP into the committed binary
        // response — exactly the direct-response corruption CLAUDE.md forbids.
        Path attachedForm = Files.createTempFile("consult-print-close-fail-", ".pdf");
        try {
            EFormData eForm = mock(EFormData.class);
            when(eForm.getId()).thenReturn(7);
            when(eForm.getDemographicId()).thenReturn(1);
            when(eForm.getFormName()).thenReturn("Diabetes Flow Sheet");
            when(consultationManager.getAttachedEForms("42")).thenReturn(List.of(eForm));
            when(faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.EFORM, 7, 1))
                    .thenReturn(attachedForm);

            InputStream closeThrowingStream = new ByteArrayInputStream(new byte[0]) {
                @Override
                public void close() throws IOException {
                    throw new IOException("close failed");
                }
            };

            try (MockedStatic<ConcatPDF> concatPdfMock = mockStatic(ConcatPDF.class);
                 MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                filesMock.when(() -> Files.newInputStream(attachedForm)).thenReturn(closeThrowingStream);

                String result = action.execute();

                assertThat(result).isEqualTo(ActionSupport.NONE);
                assertThat(request.getAttribute("printError")).isNull();
            }
        } finally {
            Files.deleteIfExists(attachedForm);
        }
    }

    @Test
    @DisplayName("should return error and name the failing attachment when an attached form cannot be rendered")
    void shouldReturnErrorNamingFailedAttachment_whenAttachedFormRenderFails() throws Exception {
        servletActionContextMock.when(ServletActionContext::getServletContext)
                .thenReturn(new org.springframework.mock.web.MockServletContext());
        EctFormData.PatientForm form = mock(EctFormData.PatientForm.class);
        when(form.getFormName()).thenReturn("Rourke Growth Chart");
        when(form.getDemoNo()).thenReturn("1");
        when(form.getFormId()).thenReturn("55");
        when(consultationManager.getAttachedForms(loggedInInfo, 42, 1)).thenReturn(List.of(form));
        when(faxManager.renderFaxDocument(eq(loggedInInfo), eq(FaxManager.TransactionType.FORM),
                any(FormTransportContainer.class)))
                .thenThrow(new PDFGenerationException("renderer unavailable"));

        try (LogCapture logCapture = LogCapture.forLogger(EctConsultationFormRequestPrintAction22Action.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo("error");
            assertThat(request.getAttribute("printError")).isEqualTo(Boolean.TRUE);
            // The FORM leg wraps identically to the EFORM leg: attachment named, reason preserved.
            assertThat(logCapture.events())
                    .anySatisfy(event -> {
                        assertThat(event.getThrown()).isInstanceOf(PDFGenerationException.class);
                        assertThat(event.getThrown().getMessage())
                                .contains("Rourke Growth Chart")
                                .contains("renderer unavailable");
                    });
        }
    }

    @Test
    @DisplayName("should return error and name the failing attachment in the wrapped PDFGenerationException when an attached eForm cannot be rendered")
    void shouldReturnErrorNamingFailedAttachment_whenAttachedEformRenderFails() throws Exception {
        EFormData eForm = mock(EFormData.class);
        when(eForm.getId()).thenReturn(7);
        when(eForm.getDemographicId()).thenReturn(1);
        when(eForm.getFormName()).thenReturn("Diabetes Flow Sheet");
        when(consultationManager.getAttachedEForms("42")).thenReturn(List.of(eForm));
        when(faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.EFORM, 7, 1))
                .thenThrow(new PDFGenerationException("renderer unavailable"));

        try (LogCapture logCapture = LogCapture.forLogger(EctConsultationFormRequestPrintAction22Action.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo("error");
            assertThat(request.getAttribute("printError")).isEqualTo(Boolean.TRUE);
            // The wrapped exception names the failing attachment and preserves the renderer's reason,
            // replacing the pre-fix context-free NPE from Files.newInputStream(null).
            assertThat(logCapture.events())
                    .anySatisfy(event -> {
                        assertThat(event.getThrown()).isInstanceOf(PDFGenerationException.class);
                        assertThat(event.getThrown().getMessage())
                                .contains("Diabetes Flow Sheet")
                                .contains("renderer unavailable");
                    });
        }
    }
}
