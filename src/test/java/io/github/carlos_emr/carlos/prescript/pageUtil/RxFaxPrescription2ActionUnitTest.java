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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionPdfUnitTestBase;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@Tag("prescription")
class RxFaxPrescription2ActionUnitTest extends PrescriptionPdfUnitTestBase {

    private RxFaxPrescription2Action newFaxAction() {
        return new RxFaxPrescription2Action(newComposer(), newFaxService());
    }

    private void faxAs(RxFaxPrescription2Action action, MockHttpServletRequest request,
            MockHttpServletResponse response, LoggedInInfo loggedInInfo) throws Exception {
        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<PrescriptionQrCodeUIBean> qrCodeMock = mockStatic(PrescriptionQrCodeUIBean.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCodeMock.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);
            action.faxPrescription(request, response);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should refuse GET and HEAD with 405 and Allow: POST before anything runs")
    void shouldRejectUnsafeMethod_withAllowHeader(String method) throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.setMethod(method);
        MockHttpServletResponse response = new MockHttpServletResponse();

        faxAs(newFaxAction(), request, response, mock(LoggedInInfo.class));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(prescriptionDao, securityInfoManager, faxConfigDao, faxJobDao, faxManager);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "pharmaFax, ''", "pharmaFax, 555-12", "pharmaFax, no digits",
            "pdfId, ''", "pdfId, ../etc/passwd", "pdfId, has space"})
    @DisplayName("should refuse a bad fax number or document id with 400 before writing anything")
    void shouldRejectFaxBeforeWriting_whenRequestFieldIsInvalid(String field, String value, @TempDir Path tempDir)
            throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        MockHttpServletRequest request = createFaxRequest();
        request.setParameter(field, value);
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", tempDir.toString());

            faxAs(newFaxAction(), request, response, loggedInInfo);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(response.getContentAsString()).contains("fax-failure").doesNotContain("fax-success");
            try (var written = Files.list(tempDir)) {
                assertThat(written).isEmpty();
            }
            verifyFaxWasNotQueued();
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should refuse a fax with 401 and the failure marker when there is no session")
    void shouldRefuseFax_whenSessionIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        faxAs(newFaxAction(), createFaxRequest(), response, null);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("fax-failure");
        verifyNoInteractions(prescriptionDao, securityInfoManager, faxConfigDao, faxJobDao, faxManager);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("should report fixed fax errors without logging clinical exception messages or causes")
    void shouldKeepClinicalExceptionsPrivate_whenReportingFaxFailure(boolean uncertain) throws Exception {
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var failure = new IllegalStateException("PRIVATE_CLINICAL_MESSAGE",
                new IllegalArgumentException("PRIVATE_CLINICAL_CAUSE"));
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(RxFaxPrescription2Action.class)) {
            if (uncertain) {
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(RxFaxPrescription2Action.class,
                        "reportFaxUncertain", response, response.getWriter(), failure);
            } else {
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(RxFaxPrescription2Action.class,
                        "reportFaxFailure", response, response.getWriter(), "Preparing prescription fax", failure);
            }
            assertThat(response.getStatus()).isEqualTo(uncertain ? 503 : 500);
            assertThat(response.getContentAsString()).contains(uncertain ? "fax-uncertain" : "fax-failure")
                    .doesNotContain("PRIVATE_CLINICAL");
            assertThat(logs.events()).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getMessage().getFormattedMessage()).contains("IllegalStateException")
                        .doesNotContain("PRIVATE_CLINICAL");
                assertThat(event.getThrown()).isNull();
            });
        }
    }

    @Test
    @DisplayName("should return server error when document directory is invalid")
    void shouldReturnServerError_whenDocumentDirectoryIsInvalid() throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedStatic<PrescriptionQrCodeUIBean> qrCodeMock = mockStatic(PrescriptionQrCodeUIBean.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCodeMock.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", " ");

            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(response.getContentAsString()).contains("Unable to generate fax");
            verify(faxConfigDao).getActiveConfigByNumber("4165553434");
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("should write fax files when configured directories are valid")
    void shouldWriteValidatedFaxFiles_whenConfiguredDirectoriesAreValid(boolean omitPharmacyName, @TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        MockHttpServletRequest request = createFaxRequest();
        if (omitPharmacyName) request.removeParameter("pharmaName");
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedStatic<PrescriptionQrCodeUIBean> qrCodeMock = mockStatic(PrescriptionQrCodeUIBean.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCodeMock.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());

            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("fax-success").doesNotContain("<p>null (");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).hasContent("4165551212");
            verify(faxConfigDao).getActiveConfigByNumber("4165553434");
            ArgumentCaptor<FaxJob> faxJobCaptor = ArgumentCaptor.forClass(FaxJob.class);
            verify(faxManager).persistAndLogFaxJob(any(), faxJobCaptor.capture(), eq(TransactionType.RX), eq(SCRIPT_ID));
            assertThat(faxJobCaptor.getValue().getDemographicNo()).isEqualTo(DEMOGRAPHIC_NO);
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"document", "missing-file", "io", "runtime"})
    @DisplayName("should report a definite fax failure when PDF generation fails before persistence")
    void shouldReportDefiniteFaxFailure_whenPdfGenerationFails(String failureType) throws Exception {
        Exception failure = switch (failureType) {
            case "document" -> new DocumentException("fixture rendering failure");
            case "missing-file" -> new java.io.FileNotFoundException("fixture missing file");
            case "io" -> new java.io.IOException("fixture read failure");
            default -> new IllegalStateException("fixture rendering state");
        };
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loginMock = mockStatic(LoggedInInfo.class);
             MockedStatic<PdfWriter> pdfWriterMock = mockStatic(PdfWriter.class)) {
            loginMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            pdfWriterMock.when(() -> PdfWriter.getInstance(any(), any())).thenAnswer(invocation -> { throw failure; });
            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(response.getContentAsString()).contains("fax-failure")
                    .doesNotContain("fax-uncertain", "fax-success", "fixture");
            verifyFaxWasNotQueued();
            verify(faxConfigDao, never()).getActiveConfigByNumber(anyString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"prescription", "privilege", "binding", "config"})
    @DisplayName("should report definite failure for pre-persistence record preparation exceptions")
    void shouldReportDefiniteFaxFailure_whenRecordPreparationFails(String stage) throws Exception {
        stubStoredSignature();
        stubRecordDemographic();
        IllegalStateException failure = new IllegalStateException("fixture private diagnostic");
        if ("prescription".equals(stage)) {
            when(prescriptionDao.find(SCRIPT_ID)).thenThrow(failure);
        } else if ("privilege".equals(stage)) {
            when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.READ), eq(String.valueOf(DEMOGRAPHIC_NO))))
                    .thenThrow(failure);
        } else if ("binding".equals(stage)) {
            when(demographicManager.getDemographic(any(), eq(DEMOGRAPHIC_NO))).thenThrow(failure);
        } else {
            when(faxConfigDao.getActiveConfigByNumber(anyString())).thenThrow(failure);
        }
        RxFaxPrescription2Action action = newFaxAction();
        MockHttpServletResponse response = new MockHttpServletResponse();
        faxAs(action, createFaxRequest(), response, mock(LoggedInInfo.class));
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("fax-failure")
                .doesNotContain("fax-uncertain", "fax-success", "fixture", "private diagnostic");
        verifyFaxWasNotQueued();
        if (!"config".equals(stage)) {
            verify(faxConfigDao, never()).getActiveConfigByNumber(anyString());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("should not republish a filename when existing fax ownership cannot be ruled out")
    void shouldRejectPublication_whenFaxOwnershipIsExistingOrUnknown(boolean lookupFails, @TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        if (lookupFails) {
            when(faxJobDao.findByFileName("prescription_rx-123.pdf"))
                    .thenThrow(new IllegalStateException("fixture lookup failure"));
        } else {
            FaxJob existing = new FaxJob();
            existing.setFile_name("prescription_rx-123.pdf");
            existing.setStatus(FaxJob.STATUS.WAITING);
            when(faxJobDao.findByFileName("prescription_rx-123.pdf")).thenReturn(List.of(existing));
        }
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());
            RxFaxPrescription2Action action = newFaxAction();
            MockHttpServletResponse response = new MockHttpServletResponse();
            faxAs(action, createFaxRequest(), response, loggedInInfo);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertThat(response.getContentAsString()).contains("fax-uncertain")
                    .doesNotContain("fax-failure", "fax-success", "fixture");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).doesNotExist();
            verifyFaxWasNotQueued();
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should reject a fax identifier collision without reusing the existing prescription PDF")
    void shouldRejectFax_whenDocumentFileAlreadyExists(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        Path existingPdf = documentDir.resolve("prescription_rx-123.pdf");
        Files.writeString(existingPdf, "existing pdf", StandardCharsets.UTF_8);
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedStatic<PrescriptionQrCodeUIBean> qrCodeMock = mockStatic(PrescriptionQrCodeUIBean.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCodeMock.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());

            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertThat(response.getContentAsString()).contains("fax-uncertain")
                    .doesNotContain("fax-failure", "fax-success");
            assertThat(existingPdf).hasContent("existing pdf");
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).doesNotExist();
            verify(faxConfigDao).getActiveConfigByNumber("4165553434");
            verifyFaxWasNotQueued();
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should reject an existing spool PDF without reusing or changing it")
    void shouldRejectFax_whenSpoolPdfAlreadyExists(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        Path existingSpoolPdf = faxDir.resolve("prescription_rx-123.pdf");
        Files.writeString(existingSpoolPdf, "existing spool pdf", StandardCharsets.UTF_8);
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());
            RxFaxPrescription2Action action = newFaxAction();

            faxAs(action, request, response, loggedInInfo);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertThat(response.getContentAsString()).contains("fax-uncertain")
                    .doesNotContain("fax-failure", "fax-success");
            assertThat(existingSpoolPdf).hasContent("existing spool pdf");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).doesNotExist();
            verifyFaxWasNotQueued();
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should reject an existing tracking file without truncating it")
    void shouldRejectFax_whenTrackingFileAlreadyExists(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        Path existingTrackingFile = faxDir.resolve("prescription_rx-123.txt");
        Files.writeString(existingTrackingFile, "9055550100", StandardCharsets.UTF_8);
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());
            RxFaxPrescription2Action action = newFaxAction();

            faxAs(action, request, response, loggedInInfo);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertThat(response.getContentAsString()).contains("fax-uncertain")
                    .doesNotContain("fax-failure", "fax-success");
            assertThat(existingTrackingFile).hasContent("9055550100");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            verifyFaxWasNotQueued();
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should validate the spool directory before creating the document PDF")
    void shouldLeaveNoDocument_whenFaxDirectoryIsInvalid(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", " ");
            RxFaxPrescription2Action action = newFaxAction();

            faxAs(action, request, response, loggedInInfo);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("Unable to generate fax");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            verifyFaxWasNotQueued();
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should preserve uncertainty until an operator clears a tracking path collision")
    void shouldReportUncertainty_whenFaxTrackingPathAlreadyExists(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        Files.createDirectory(faxDir.resolve("prescription_rx-123.txt"));
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedStatic<PrescriptionQrCodeUIBean> qrCodeMock = mockStatic(PrescriptionQrCodeUIBean.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCodeMock.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());

            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertThat(response.getContentAsString()).contains("fax-uncertain")
                    .doesNotContain("fax-failure", "fax-success");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).isDirectory();
            verify(faxConfigDao).getActiveConfigByNumber("4165553434");
            verifyFaxWasNotQueued();

            // Removing the operator-side blocker and submitting the SAME attempt id must work.
            // A leaked DOCUMENT_DIR PDF used to make this retry collide forever.
            Files.delete(faxDir.resolve("prescription_rx-123.txt"));
            MockHttpServletResponse retryResponse = new MockHttpServletResponse();
            action.faxPrescription(request, retryResponse);

            assertThat(retryResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(retryResponse.getContentAsString()).contains("fax-success");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).hasContent("4165551212");
            verify(faxManager).persistAndLogFaxJob(any(), any(FaxJob.class), eq(TransactionType.RX), eq(SCRIPT_ID));
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should preserve fax files and reject replay when persistence outcome is uncertain")
    void shouldKeepFilesAndRejectReplay_whenPersistenceOutcomeIsUncertain(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse failedResponse = new MockHttpServletResponse();
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        doThrow(new IllegalStateException("commit acknowledgement lost")).when(faxManager)
                .persistAndLogFaxJob(any(), any(FaxJob.class), eq(TransactionType.RX), eq(SCRIPT_ID));

        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());
            RxFaxPrescription2Action action = newFaxAction();

            faxAs(action, request, failedResponse, loggedInInfo);

            assertThat(failedResponse.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertThat(failedResponse.getContentAsString()).contains("fax-uncertain")
                    .doesNotContain("fax-failure", "fax-success");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).hasContent("4165551212");

            MockHttpServletResponse retryResponse = new MockHttpServletResponse();
            faxAs(action, request, retryResponse, loggedInInfo);

            assertThat(retryResponse.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertThat(retryResponse.getContentAsString()).contains("fax-uncertain")
                    .doesNotContain("fax-failure", "fax-success");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).hasContent("4165551212");
            verify(faxManager).persistAndLogFaxJob(
                    any(), any(FaxJob.class), eq(TransactionType.RX), eq(SCRIPT_ID));
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should keep committed fax files and report success when the secondary audit fails")
    void shouldKeepCommittedFiles_whenLegacyAuditFails(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubActiveFaxConfig();
        stubRecordDemographic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());
            logActionMock.when(() -> LogAction.addLog("999998", LogConst.SENT, LogConst.CON_FAX,
                            "PRESCRIPTION prescription_rx-123.pdf"))
                    .thenThrow(new IllegalStateException("PRIVATE_AUDIT_MESSAGE", new IllegalArgumentException("PRIVATE_AUDIT_CAUSE")));
            RxFaxPrescription2Action action = newFaxAction();

            try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(RxFaxPrescription2Action.class)) {
                faxAs(action, request, response, loggedInInfo);
                assertThat(logs.messages()).anyMatch(message -> message.contains("legacy SENT audit entry failed"));
                assertThat(logs.messages().toString()).doesNotContain("PRIVATE_AUDIT", "prescription_rx-123.pdf", documentDir.toString());
                assertThat(logs.events()).allMatch(event -> event.getThrown() == null);
            }

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("fax-success").doesNotContain("fax-failure");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).hasContent("4165551212");
            verify(faxManager).persistAndLogFaxJob(
                    any(), any(FaxJob.class), eq(TransactionType.RX), eq(SCRIPT_ID));
            logActionMock.verify(() -> LogAction.addLog("999998", LogConst.SENT, LogConst.CON_FAX,
                    "PRESCRIPTION prescription_rx-123.pdf"));
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MIDDLEWARE", "SRFAX"})
    @DisplayName("should reject an undialable destination before writing files")
    void shouldRejectFaxBeforeWriting_whenDestinationIsInvalidForProvider(String providerType, @TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        MockHttpServletRequest request = createFaxRequest();
        request.setParameter("pharmaFax", "SRFAX".equals(providerType) ? "12345678" : "123456789012");
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        stubRecordDemographic();
        stubActiveFaxConfig();
        faxConfigDao.getActiveConfigByNumber("4165553434").setProviderType(FaxConfig.ProviderType.valueOf(providerType));
        org.mockito.Mockito.clearInvocations(faxConfigDao);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());
            RxFaxPrescription2Action action = newFaxAction();

            faxAs(action, request, response, loggedInInfo);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("Valid fax number not found");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).doesNotExist();
            verify(faxConfigDao, org.mockito.Mockito.atLeastOnce()).getActiveConfigByNumber("4165553434");
            verifyFaxWasNotQueued();
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should refuse to fax when the prescription does not exist")
    void shouldRefuseFax_whenPrescriptionNotFound(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        // No stubStoredSignature(): PrescriptionDao.find returns null, and no pad file is named.

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("not signed");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            verify(faxConfigDao, never()).findAll(any(), any());
            verifyFaxWasNotQueued();
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
        }
    }

    @Test
    @DisplayName("should refuse a fax from a caller without _rx write as a permission error, not as unsigned")
    void shouldRefuseFaxAsPermissionError_whenCallerLacksRxWrite() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature(); // the script IS signed
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.WRITE), anyString())).thenReturn(false);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("permission").doesNotContain("not signed");
            verifyFaxWasNotQueued();
            verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
        }
    }

    @Test
    @DisplayName("should refuse a fax from a caller without global _fax write")
    void shouldRefuseFaxAsPermissionError_whenCallerLacksFaxWrite() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.WRITE), isNull()))
                .thenReturn(false);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("permission");
            verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
            verify(faxConfigDao, never()).findAll(any(), any());
            verifyFaxWasNotQueued();
        }
    }

    @Test
    @DisplayName("should refuse to fax when the prescription record has no drug lines")
    void shouldRefuseFax_whenPrescriptionRecordHasNoDrugs() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        when(drugDao.findDrugsAndPrescriptionsByScriptNumber(SCRIPT_ID)).thenReturn(Collections.emptyList());
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("incomplete");
            verifyFaxWasNotQueued();
        }
    }

    @Test
    @DisplayName("should refuse to fax a prescription that carries no signature")
    void shouldRefuseFax_whenPrescriptionUnsigned(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        // The row exists, the caller is authorized for its patient, but digital_signature_id is
        // null and no pad file is named: the "no signature" guard itself must refuse the fax.
        Prescription unsigned = new Prescription();
        unsigned.setDemographicId(DEMOGRAPHIC_NO);
        when(prescriptionDao.find(SCRIPT_ID)).thenReturn(unsigned);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.READ), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.WRITE), isNull()))
                .thenReturn(true);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("not signed");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            verify(faxConfigDao, never()).findAll(any(), any());
            verifyFaxWasNotQueued();
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
        }
    }

    /** The same request as {@link #createFaxRequest()} but a print/preview: no {@code __method}. */
    @Test
    @DisplayName("should refuse to fax on anything but POST before touching the prescription")
    void shouldRejectFax_whenRequestMethodIsNotPost() throws Exception {
        // CSRFGuard protects POST only, and this servlet answers every method through service():
        // a GET that faxed would be a cross-site-triggerable fax of a real prescription to a
        // caller-chosen number.
        MockHttpServletRequest request = createFaxRequest();
        request.setMethod("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verify(prescriptionDao, never()).find(anyInt());
            verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
            verifyFaxWasNotQueued();
        }
    }

    @Test
    @DisplayName("should refuse to fax when the caller may not read the patient's demographic")
    void shouldRefuseFax_whenCallerLacksDemographicRead() throws Exception {
        // The fax heads the page with the demographic record, so _demographic READ is part of the
        // permission to fax; refused here deliberately instead of surfacing as DemographicManager's
        // RuntimeException half-way through.
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.READ), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(false);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            RxFaxPrescription2Action action = newFaxAction();

            action.faxPrescription(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("permission").doesNotContain("not signed");
            verify(demographicManager, never()).getDemographic(any(), anyInt());
            verifyFaxWasNotQueued();
        }
    }
}
