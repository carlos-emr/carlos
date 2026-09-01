/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@DisplayName("FrmCustomedPDFServlet path validation")
@Tag("unit")
@Tag("web")
@Tag("security")
class FrmCustomedPDFServletUnitTest extends CarlosUnitTestBase {

    private static final int SCRIPT_ID = 1;
    private static final int DEMOGRAPHIC_NO = 1;
    private static final int SIGNATURE_ID = 77;

    private FaxConfigDao faxConfigDao;
    private FaxJobDao faxJobDao;
    private PrescriptionDao prescriptionDao;
    private DigitalSignatureManager digitalSignatureManager;
    private SecurityInfoManager securityInfoManager;

    @BeforeEach
    void setUp() {
        faxConfigDao = mock(FaxConfigDao.class);
        faxJobDao = mock(FaxJobDao.class);
        prescriptionDao = mock(PrescriptionDao.class);
        digitalSignatureManager = mock(DigitalSignatureManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(FaxConfigDao.class, faxConfigDao);
        registerMock(FaxJobDao.class, faxJobDao);
        registerMock(DigitalSignatureManager.class, digitalSignatureManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(FaxManager.class, mock(FaxManager.class));
        registerMock(ClinicDAO.class, mock(ClinicDAO.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        registerMock(PrescriptionDao.class, prescriptionDao);
        registerMock(DrugDao.class, mock(DrugDao.class));
    }

    /**
     * Makes script {@value #SCRIPT_ID} carry stored signature {@value #SIGNATURE_ID} for patient
     * {@value #DEMOGRAPHIC_NO}, readable by a caller with {@code _rx} read privilege. Fax requests
     * need this: the servlet refuses to fax an unsigned prescription.
     */
    private void stubStoredSignature() throws Exception {
        Prescription prescription = new Prescription();
        prescription.setDemographicId(DEMOGRAPHIC_NO);
        prescription.setDigitalSignatureId(SIGNATURE_ID);
        when(prescriptionDao.find(SCRIPT_ID)).thenReturn(prescription);

        DigitalSignature metadata = new DigitalSignature();
        metadata.setDemographicId(DEMOGRAPHIC_NO);
        metadata.setModuleType(ModuleType.PRESCRIPTION);
        when(digitalSignatureManager.getDigitalSignatureMetadata(SIGNATURE_ID)).thenReturn(metadata);

        DigitalSignature signature = new DigitalSignature();
        signature.setDemographicId(DEMOGRAPHIC_NO);
        signature.setModuleType(ModuleType.PRESCRIPTION);
        signature.setSignatureImage(tinyPng());
        when(digitalSignatureManager.getDigitalSignature(SIGNATURE_ID)).thenReturn(signature);

        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.READ), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(true);
    }

    private static byte[] tinyPng() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("should return server error when document directory is invalid")
    void shouldReturnServerError_whenDocumentDirectoryIsInvalid() throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedStatic<PrescriptionQrCodeUIBean> qrCodeMock = mockStatic(PrescriptionQrCodeUIBean.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCodeMock.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", " ");

            FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
            servlet.init(new MockServletConfig(new MockServletContext()));

            servlet.service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(response.getContentAsString()).contains("Unable to generate fax");
            verify(faxConfigDao, never()).findAll(any(), any());
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
        }
    }

    @Test
    @DisplayName("should write fax files when configured directories are valid")
    void shouldWriteValidatedFaxFiles_whenConfiguredDirectoriesAreValid(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(faxConfigDao.findAll(any(), any())).thenReturn(Collections.emptyList());

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedStatic<PrescriptionQrCodeUIBean> qrCodeMock = mockStatic(PrescriptionQrCodeUIBean.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCodeMock.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());

            FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
            servlet.init(new MockServletConfig(new MockServletContext()));

            servlet.service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).exists();
            assertThat(faxDir.resolve("prescription_rx-123.txt")).hasContent("4165551212");
            verify(faxConfigDao).findAll(any(), any());
            verify(faxJobDao, never()).persist(any());
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should preserve existing prescription PDF when document file already exists")
    void shouldPreserveExistingPrescriptionPdf_whenDocumentFileAlreadyExists(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        Path existingPdf = documentDir.resolve("prescription_rx-123.pdf");
        Files.writeString(existingPdf, "existing pdf", StandardCharsets.UTF_8);
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(faxConfigDao.findAll(any(), any())).thenReturn(Collections.emptyList());

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedStatic<PrescriptionQrCodeUIBean> qrCodeMock = mockStatic(PrescriptionQrCodeUIBean.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCodeMock.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());

            FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
            servlet.init(new MockServletConfig(new MockServletContext()));

            servlet.service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(existingPdf).hasContent("existing pdf");
            assertThat(faxDir.resolve("prescription_rx-123.pdf")).hasContent("existing pdf");
            assertThat(faxDir.resolve("prescription_rx-123.txt")).hasContent("4165551212");
            verify(faxConfigDao).findAll(any(), any());
            verify(faxJobDao, never()).persist(any());
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should return server error when fax tracking write fails")
    void shouldReturnServerError_whenFaxTrackingWriteFails(@TempDir Path tempDir) throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = Files.createDirectory(tempDir.resolve("fax"));
        Files.createDirectory(faxDir.resolve("prescription_rx-123.txt"));
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubStoredSignature();
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

            FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
            servlet.init(new MockServletConfig(new MockServletContext()));

            servlet.service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(response.getContentAsString()).contains("Unable to generate fax");
            assertThat(faxDir.resolve("prescription_rx-123.txt")).isDirectory();
            verify(faxConfigDao, never()).findAll(any(), any());
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
            restoreProperty("fax_file_location", previousFaxFileLocation);
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
        // No stubStoredSignature(): PrescriptionDao.find returns null, and no pad file is named.

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

            FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
            servlet.init(new MockServletConfig(new MockServletContext()));

            servlet.service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("not signed");
            assertThat(documentDir.resolve("prescription_rx-123.pdf")).doesNotExist();
            verify(faxConfigDao, never()).findAll(any(), any());
            verify(faxJobDao, never()).persist(any());
        } finally {
            restoreProperty("DOCUMENT_DIR", previousDocumentDir);
        }
    }

    @Test
    @DisplayName("should sign the fax from the stored prescription signature when no pad file is named")
    void shouldUseStoredSignature_whenPadFileAbsent() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
        byte[] resolved = servlet.resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isEqualTo(tinyPng());
        verify(digitalSignatureManager).getDigitalSignature(SIGNATURE_ID);
    }

    @Test
    @DisplayName("should prefer the pad capture file over the stored signature when it exists")
    void shouldPreferPadFile_whenPresentInTempDirectory() throws Exception {
        Path padFile = Files.createTempFile("signature_test-", ".jpg");
        try {
            byte[] padBytes = "pad-signature".getBytes(StandardCharsets.UTF_8);
            Files.write(padFile, padBytes);
            MockHttpServletRequest request = createFaxRequest();
            request.addParameter("imgFile", padFile.toString());
            stubStoredSignature();
            LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

            byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

            assertThat(resolved).isEqualTo(padBytes);
            verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
        } finally {
            Files.deleteIfExists(padFile);
        }
    }

    @Test
    @DisplayName("should fall back to the stored signature when the named pad file is missing or escapes the temp directory")
    void shouldFallBackToStoredSignature_whenPadFileMissingOrOutsideTempDirectory() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.addParameter("imgFile", "../../etc/passwd");
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isEqualTo(tinyPng());
    }

    @Test
    @DisplayName("should withhold a stored signature that belongs to another patient's record")
    void shouldWithholdStoredSignature_whenPatientMismatch() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        DigitalSignature foreign = new DigitalSignature();
        foreign.setDemographicId(DEMOGRAPHIC_NO + 1);
        foreign.setModuleType(ModuleType.PRESCRIPTION);
        when(digitalSignatureManager.getDigitalSignatureMetadata(SIGNATURE_ID)).thenReturn(foreign);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isNull();
        verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
    }

    @Test
    @DisplayName("should withhold a stored signature when the caller lacks _rx read privilege for the patient")
    void shouldWithholdStoredSignature_whenCallerLacksRxRead() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyString())).thenReturn(false);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isNull();
        verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
    }

    private MockHttpServletRequest createFaxRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/form/frmcustomedpdf");
        request.addParameter("__method", "oscarRxFax");
        request.addParameter("pdfId", "rx-123");
        request.addParameter("pharmaFax", "4165551212");
        request.addParameter("clinicFax", "4165553434");
        request.addParameter("pharmaName", "Test Pharmacy");
        request.addParameter("demographic_no", "1");
        request.addParameter("clinicName", "Test Clinic");
        request.addParameter("clinicPhone", "4165550000");
        request.addParameter("patientName", "Test Patient");
        request.addParameter("patientAddress", "123 Test Street");
        request.addParameter("patientCityPostal", "Toronto ON");
        request.addParameter("patientPhone", "4165559999");
        request.addParameter("sigDoctorName", "Dr Test");
        request.addParameter("rxDate", "2026-06-19");
        request.addParameter("rx", "Test prescription");
        request.addParameter("scriptId", "1");
        return request;
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, previousValue);
        }
    }
}
