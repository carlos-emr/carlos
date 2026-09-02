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
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
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
import java.io.File;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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

        // Grant both READ and WRITE for the patient; the fax path requires WRITE, a preview READ.
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(true);
    }

    /** A 13-digit-suffix pad file name for the given provider, matching generateSignatureRequestId. */
    private static Path padFileFor(String providerNo) {
        // providerNo + a 13-digit timestamp, the exact shape the servlet accepts.
        return Path.of(System.getProperty("java.io.tmpdir"), "signature_" + providerNo + System.currentTimeMillis() + ".jpg");
    }

    /** A decodable PNG that is distinct from {@link #tinyPng()} so byte assertions can tell them apart. */
    private static byte[] otherPng() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
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
    @DisplayName("should prefer a valid pad capture image over the stored signature when it exists")
    void shouldPreferPadFile_whenPresentInTempDirectory() throws Exception {
        Path padFile = padFileFor("999998"); // this provider's capture (13-digit millis suffix)
        try {
            byte[] padBytes = otherPng(); // distinct from the stored signature's tinyPng()
            Files.write(padFile, padBytes);
            MockHttpServletRequest request = createFaxRequest();
            request.addParameter("imgFile", padFile.toString());
            stubStoredSignature();
            LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

            byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

            assertThat(resolved).isEqualTo(padBytes); // the pad was used, not the stored signature
            verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
        } finally {
            Files.deleteIfExists(padFile);
        }
    }

    @Test
    @DisplayName("should reject another provider's pad capture and use the stored signature")
    void shouldRejectAnotherProvidersPadFile_andUseStoredSignature() throws Exception {
        Path foreignPad = padFileFor("888888"); // a different provider's capture
        try {
            byte[] foreignBytes = otherPng(); // a real, decodable image — distinct from the stored one
            Files.write(foreignPad, foreignBytes);
            MockHttpServletRequest request = createFaxRequest();
            request.addParameter("imgFile", foreignPad.toString());
            stubStoredSignature();
            LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

            byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

            // The stored signature (tinyPng) is rendered, NOT the foreign pad (otherPng) — the byte
            // assertion itself proves the foreign capture was rejected.
            assertThat(resolved).isEqualTo(tinyPng());
            assertThat(resolved).isNotEqualTo(foreignBytes);
            verify(digitalSignatureManager).getDigitalSignature(SIGNATURE_ID);
        } finally {
            Files.deleteIfExists(foreignPad);
        }
    }

    @Test
    @DisplayName("should fall back to the stored signature when the pad file is not a decodable image")
    void shouldFallBackToStoredSignature_whenPadFileNotAnImage() throws Exception {
        Path padFile = padFileFor("999998");
        try {
            Files.write(padFile, "not-an-image".getBytes(StandardCharsets.UTF_8));
            MockHttpServletRequest request = createFaxRequest();
            request.addParameter("imgFile", padFile.toString());
            stubStoredSignature();
            LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

            byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

            assertThat(resolved).isEqualTo(tinyPng());
        } finally {
            Files.deleteIfExists(padFile);
        }
    }

    @Test
    @DisplayName("should ignore an imgFile that is not a signature-pad capture and use the stored signature")
    void shouldIgnoreNonPadImgFile_andUseStoredSignature() throws Exception {
        Path stray = Files.createTempFile("stray-", ".jpg");
        try {
            Files.write(stray, tinyPng());
            MockHttpServletRequest request = createFaxRequest();
            request.addParameter("imgFile", stray.toString());
            stubStoredSignature();
            LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

            byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

            assertThat(resolved).isEqualTo(tinyPng());
            verify(digitalSignatureManager).getDigitalSignature(SIGNATURE_ID);
        } finally {
            Files.deleteIfExists(stray);
        }
    }

    @Test
    @DisplayName("should fall back to the stored signature when the named pad file does not exist")
    void shouldFallBackToStoredSignature_whenPadFileMissing() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        // A well-formed pad name for THIS provider that was never written.
        request.addParameter("imgFile", padFileFor("999998").getFileName().toString());
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isEqualTo(tinyPng());
        verify(digitalSignatureManager).getDigitalSignature(SIGNATURE_ID);
    }

    @Test
    @DisplayName("should fall back to the stored signature when the named pad file escapes the temp directory")
    void shouldFallBackToStoredSignature_whenPadFileOutsideTempDirectory() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.addParameter("imgFile", "../../etc/passwd");
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        // The pad branch is only entered for a logged-in provider; without this stub the
        // traversal value would never be examined and this test would prove nothing.
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        // Real PathValidationUtils behaviour, but PROVE the traversal value reached it.
        try (MockedStatic<PathValidationUtils> pathValidation = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

            assertThat(resolved).isEqualTo(tinyPng());
            pathValidation.verify(() -> PathValidationUtils.validatePath(eq("../../etc/passwd"), any(File.class)));
        }
        verify(digitalSignatureManager).getDigitalSignature(SIGNATURE_ID);
    }

    @Test
    @DisplayName("should fall back to the stored signature when path validation rejects the pad file name")
    void shouldFallBackToStoredSignature_whenPadFilePathRejected() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.addParameter("imgFile", "../../etc/passwd");
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        // The SecurityException contract: a rejected path must be swallowed into the stored-signature
        // fallback, never propagated into the fax/print response.
        try (MockedStatic<PathValidationUtils> pathValidation = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            pathValidation.when(() -> PathValidationUtils.validatePath(eq("../../etc/passwd"), any(File.class)))
                    .thenThrow(new SecurityException("path escapes the allowed directory"));

            byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

            assertThat(resolved).isEqualTo(tinyPng());
        }
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
    @DisplayName("should withhold a stored signature from a print/preview when the caller lacks _rx read for the patient")
    void shouldWithholdStoredSignature_whenPreviewCallerLacksRxRead() throws Exception {
        MockHttpServletRequest request = createPreviewRequest(); // no __method → READ gate
        stubStoredSignature();
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.READ), anyString())).thenReturn(false);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isNull();
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.READ, String.valueOf(DEMOGRAPHIC_NO));
        verify(securityInfoManager, never()).hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.WRITE), anyString());
        verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
    }

    @Test
    @DisplayName("should render a stored signature on a print/preview for a caller with only _rx read")
    void shouldRenderStoredSignature_whenPreviewCallerHasOnlyRxRead() throws Exception {
        MockHttpServletRequest request = createPreviewRequest();
        stubStoredSignature(); // grants READ+WRITE by default
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.WRITE), anyString())).thenReturn(false);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isEqualTo(tinyPng());
        verify(securityInfoManager, never()).hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.WRITE), anyString());
    }

    @Test
    @DisplayName("should withhold a signature for a fax when the caller has only _rx read, not write")
    void shouldWithholdSignature_whenFaxCallerLacksRxWrite() throws Exception {
        MockHttpServletRequest request = createFaxRequest(); // __method=oscarRxFax → WRITE required
        stubStoredSignature(); // grants READ+WRITE by default
        // Now grant only READ; WRITE is denied.
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.WRITE), anyString())).thenReturn(false);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isNull();
        verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
    }

    @Test
    @DisplayName("should withhold a signature when demographic_no does not match the prescription's patient")
    void shouldWithholdSignature_whenDemographicNoMismatch() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.removeParameter("demographic_no");
        request.addParameter("demographic_no", "9999"); // a different patient than the prescription's
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isNull();
        verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
    }

    @Test
    @DisplayName("should withhold a signature for a fax when demographic_no is missing")
    void shouldWithholdSignature_whenFaxDemographicMissing() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.removeParameter("demographic_no"); // a fax MUST carry a positive, matching demographic
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isNull();
        verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
    }

    /** The same request as {@link #createFaxRequest()} but a print/preview: no {@code __method}. */
    private MockHttpServletRequest createPreviewRequest() {
        MockHttpServletRequest request = createFaxRequest();
        request.removeParameter("__method");
        return request;
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
