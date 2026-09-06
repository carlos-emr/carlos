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
import io.github.carlos_emr.carlos.commn.dao.ProviderExtDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.casemgmt.model.ProviderExt;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.prescript.data.RxSatelliteClinicAddress;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
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
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private DrugDao drugDao;
    private ProviderExtDao providerExtDao;
    private DemographicManager demographicManager;
    private ProviderDao providerDao;
    private ClinicDAO clinicDao;
    private UserPropertyDAO userPropertyDao;
    private SiteDao siteDao;

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
        clinicDao = mock(ClinicDAO.class);
        registerMock(ClinicDAO.class, clinicDao);
        providerDao = mock(ProviderDao.class);
        registerMock(ProviderDao.class, providerDao);
        userPropertyDao = mock(UserPropertyDAO.class);
        registerMock(UserPropertyDAO.class, userPropertyDao);
        siteDao = mock(SiteDao.class);
        registerMock(SiteDao.class, siteDao);
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        demographicManager = mock(DemographicManager.class);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(PrescriptionDao.class, prescriptionDao);
        drugDao = mock(DrugDao.class);
        registerMock(DrugDao.class, drugDao);
        providerExtDao = mock(ProviderExtDao.class);
        registerMock(ProviderExtDao.class, providerExtDao);
    }

    private static final String RECORD_DRUG_LINE = "Amoxicillin 500 mg capsule\n1 cap PO TID x 7 days";
    private static final String SECOND_DRUG_LINE = "Ibuprofen 400 mg tablet\n1 tab PO q6h PRN pain";

    /** A persisted drugs row of script {@value #SCRIPT_ID} whose full out line is {@code special}. */
    private static Drug drugRow(int id, String special) {
        Drug drug = new Drug();
        drug.setId(id);
        drug.setScriptNo(SCRIPT_ID);
        drug.setDemographicId(DEMOGRAPHIC_NO);
        drug.setProviderNo("999998");
        drug.setSpecial(special);
        return drug;
    }

    /** Makes the record of script {@value #SCRIPT_ID} carry the given drug rows. */
    private void stubRecordDrugs(Prescription prescription, Drug... drugs) {
        List<Object[]> pairs = new java.util.ArrayList<>();
        for (Drug drug : drugs) {
            pairs.add(new Object[] {drug, prescription});
        }
        when(drugDao.findDrugsAndPrescriptionsByScriptNumber(SCRIPT_ID)).thenReturn(pairs);
    }

    /**
     * Makes script {@value #SCRIPT_ID} carry stored signature {@value #SIGNATURE_ID} for patient
     * {@value #DEMOGRAPHIC_NO}, readable by a caller with {@code _rx} read privilege. Fax requests
     * need this: the servlet refuses to fax an unsigned prescription.
     */
    private void stubStoredSignature() throws Exception {
        stubStoredSignature("999998");
    }

    /**
     * As {@link #stubStoredSignature()}, but the persisted row records {@code prescriberNo} as its
     * prescriber — used to separate the signing caller from the provider who wrote the script.
     */
    private void stubStoredSignature(String prescriberNo) throws Exception {
        Prescription prescription = new Prescription();
        prescription.setDemographicId(DEMOGRAPHIC_NO);
        prescription.setProviderNo(prescriberNo);
        prescription.setDigitalSignatureId(SIGNATURE_ID);
        when(prescriptionDao.find(SCRIPT_ID)).thenReturn(prescription);
        // The record has one drug, so a fax has something legitimate to render.
        stubRecordDrugs(prescription, drugRow(5, RECORD_DRUG_LINE));

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
        // The fax also heads the page with the demographic record, so faxing needs _demographic READ.
        when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.READ), eq(String.valueOf(DEMOGRAPHIC_NO))))
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
            FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
            servlet.init(new MockServletConfig(new MockServletContext()));

            servlet.service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("permission").doesNotContain("not signed");
            verify(faxJobDao, never()).persist(any());
            verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
        }
    }

    @Test
    @DisplayName("should fax the drug lines and prescriber of the prescription record, not the request body")
    void shouldBindFaxBody_toPrescriptionRecord() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        // A caller with _rx write on the patient posts their OWN text and name with the scriptId
        // of a signed prescription: the fax must carry the record, not the request.
        request.setParameter("rx", "Oxycodone 80 mg tablet" + System.lineSeparator() + "#100, refills x 5" + System.lineSeparator() + System.lineSeparator());
        request.setParameter("sigDoctorName", "Dr Somebody Else");
        stubStoredSignature();
        ProviderExt ext = new ProviderExt();
        ext.setSignature("Dr A. Prescriber");
        when(providerExtDao.find("999998")).thenReturn(ext);

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound).isNotNull();
        assertThat(bound.getParameter("rx")).contains("Amoxicillin 500 mg capsule").contains("1 cap PO TID x 7 days").doesNotContain("Oxycodone");
        assertThat(bound.getParameter("sigDoctorName")).isEqualTo("Dr A. Prescriber");
        assertThat(bound.getParameter("pharmaFax")).isEqualTo("4165551212"); // untouched
        assertThat(bound.getParameterMap().get("rx")).containsExactly(bound.getParameter("rx"));
    }

    @Test
    @DisplayName("should keep the previewed drug order when the request body is a reordering of the record")
    void shouldKeepPreviewOrder_whenRequestBodyMatchesRecord() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        Prescription prescription = prescriptionDao.find(SCRIPT_ID);
        stubRecordDrugs(prescription, drugRow(5, RECORD_DRUG_LINE), drugRow(6, SECOND_DRUG_LINE));
        String nl = System.lineSeparator();
        // Preview2.jsp posts getFullOutLine() per drug joined by ";;" with ";" -> newline; the second
        // drug first here, i.e. the reverse of the record's order.
        String posted = (SECOND_DRUG_LINE.replace("\n", "; ") + ";;" + RECORD_DRUG_LINE.replace("\n", "; ") + ";;").replace(";", nl);
        request.setParameter("rx", posted);

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        String rx = bound.getParameter("rx");
        assertThat(rx.indexOf("Ibuprofen")).isLessThan(rx.indexOf("Amoxicillin"));
        assertThat(rx).contains("Amoxicillin 500 mg capsule").contains("Ibuprofen 400 mg tablet");
    }

    @Test
    @DisplayName("should fax the record in record order when the request body only partly matches it")
    void shouldUseRecordOrder_whenRequestBodyPartlyMatchesRecord() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        Prescription prescription = prescriptionDao.find(SCRIPT_ID);
        stubRecordDrugs(prescription, drugRow(5, RECORD_DRUG_LINE), drugRow(6, SECOND_DRUG_LINE));
        String nl = System.lineSeparator();
        // The second record drug first, then a block that is NOT in the record: a tampered body
        // that happens to contain one real line must not keep even that line's request position.
        String posted = (SECOND_DRUG_LINE.replace("\n", "; ") + ";;Oxycodone 80 mg; #100;;").replace(";", nl);
        request.setParameter("rx", posted);

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        String rx = bound.getParameter("rx");
        assertThat(rx).doesNotContain("Oxycodone");
        assertThat(rx.indexOf("Amoxicillin")).isLessThan(rx.indexOf("Ibuprofen"));
    }

    @Test
    @DisplayName("should render a multi-line direction on separate lines while keeping a typed semicolon whole")
    void shouldRestoreRecordLineBreaks_whenBindingFaxContentToRecord() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        Prescription prescription = prescriptionDao.find(SCRIPT_ID);
        // Both properties at once: the drug's own line breaks must survive to the fax, AND the
        // semicolon inside the second line must not be mistaken for one of them. Decoding the
        // flattened "; " form cannot do both, so the block is rebuilt from the record's own text.
        String multiLineSig = "Metoprolol 25 mg tablet\n1 tab PO BID; hold if SBP<100\nQty 60";
        stubRecordDrugs(prescription, drugRow(8, multiLineSig));
        String nl = System.lineSeparator();

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        String rx = bound.getParameter("rx");
        assertThat(rx).contains("Metoprolol 25 mg tablet" + nl + "1 tab PO BID; hold if SBP<100");
        assertThat(rx).contains("1 tab PO BID; hold if SBP<100" + nl + "Qty 60");
    }

    @Test
    @DisplayName("should keep a semicolon a prescriber typed inside an instruction intact in the faxed body")
    void shouldPreserveTypedSemicolon_whenBindingFaxContentToRecord() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        Prescription prescription = prescriptionDao.find(SCRIPT_ID);
        // A conditional Sig, which is exactly the phrasing that carries a semicolon. The old body
        // encoding joined blocks with ";;" and then replaced EVERY ";" with a line separator, so
        // this instruction was split mid-sentence and reached the PDF as two rendered lines. (A
        // remainder that happened to be a single character would additionally have started a new
        // drug block, per generatePDFDocumentBytes; this fixture pins the split itself.)
        String conditionalSig = "Metoprolol 25 mg tablet\n1 tab PO BID; hold if SBP<100";
        stubRecordDrugs(prescription, drugRow(7, conditionalSig));

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        String rx = bound.getParameter("rx");
        assertThat(rx).contains("1 tab PO BID; hold if SBP<100");
        assertThat(rx).doesNotContain("BID" + System.lineSeparator() + " hold");
    }

    @Test
    @DisplayName("should keep a one-character line inside its drug block and split only on blank lines")
    void shouldSplitRxBlocks_onBlankLinesOnly() {
        String nl = System.lineSeparator();
        String body = "Amoxicillin 500 mg" + nl + "1" + nl + "cap PO TID" + nl + nl + "Ibuprofen 400 mg" + nl + "\r" + nl + "1 tab PRN" + nl + "   " + nl;

        List<String> blocks = FrmCustomedPDFServlet.splitRxBlocks(body, nl);

        assertThat(blocks).hasSize(3);
        assertThat(blocks.get(0)).contains("Amoxicillin 500 mg").contains(nl + "1" + nl).contains("cap PO TID");
        assertThat(blocks.get(1)).contains("Ibuprofen 400 mg").doesNotContain("1 tab PRN");
        assertThat(blocks.get(2)).contains("1 tab PRN");
    }

    /**
     * The demographic row of patient {@value #DEMOGRAPHIC_NO}, as the record holds it. Every value
     * here is deliberately different from what {@link #createFaxRequest()} posts, so an assertion
     * that finds the record's value proves the binding rather than a coincidence.
     */
    private void stubRecordDemographic() {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(DEMOGRAPHIC_NO);
        demographic.setFirstName("Real");
        demographic.setLastName("Patient");
        demographic.setAddress("1 Record Lane");
        demographic.setCity("Hamilton");
        demographic.setProvince("ON");
        demographic.setPostal("L8S 4L8");
        demographic.setPhone("9055550101");
        demographic.setHin("1234567890");
        demographic.setBirthDay(new GregorianCalendar(1980, 2, 4));
        when(demographicManager.getDemographic(any(), eq(DEMOGRAPHIC_NO))).thenReturn(demographic);
    }

    @Test
    @DisplayName("should fax the patient identity of the prescription record, not the request")
    void shouldBindPatientIdentity_toPrescriptionDemographic() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        // A caller who legitimately holds _rx write on this patient posts someone else's identity
        // alongside the signed script's id. The verified drugs and stored signature must never go
        // out under it.
        request.setParameter("patientName", "Someone Else");
        request.setParameter("patientDOB", "Jan 1, 1900");
        request.setParameter("patientHIN", "9999999999");
        request.setParameter("patientAddress", "999 Attacker Ave");
        request.setParameter("patientCityPostal", "Nowhere ZZ");
        request.setParameter("patientPhone", "Tel: 4165559999");
        request.setParameter("patientChartNo", "INJECTED");
        stubStoredSignature();
        stubRecordDemographic();

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound).isNotNull();
        assertThat(bound.getParameter("patientName")).isEqualTo("Real Patient");
        assertThat(bound.getParameter("patientDOB")).isEqualTo("Mar 4, 1980");
        assertThat(bound.getParameter("patientHIN")).isEqualTo("1234567890");
        assertThat(bound.getParameter("patientAddress")).isEqualTo("1 Record Lane");
        assertThat(bound.getParameter("patientCityPostal")).isEqualTo("Hamilton, ON L8S 4L8");
        // The label is whatever RxPreview.msgTel resolves to on this classpath (the key itself when the
        // bundle is absent); resolving it the way production does keeps the assertion exact either way.
        assertThat(bound.getParameter("patientPhone"))
                .isEqualTo(LocaleUtils.getMessage(request.getLocale(), "RxPreview.msgTel") + ": 9055550101");
        // Never populated by the Rx preview, so the only thing it could carry is chosen text.
        assertThat(bound.getParameter("patientChartNo")).isEmpty();
    }

    @Test
    @DisplayName("should blank the patient heading when the prescription's demographic row is missing")
    void shouldBlankPatientIdentity_whenDemographicRowMissing() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.setParameter("patientName", "Someone Else");
        request.setParameter("patientHIN", "9999999999");
        stubStoredSignature();
        when(demographicManager.getDemographic(any(), eq(DEMOGRAPHIC_NO))).thenReturn(null);

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound).isNotNull();
        // Absent data prints as absent. Falling back to the request would be the very bypass.
        assertThat(bound.getParameter("patientName")).isEmpty();
        assertThat(bound.getParameter("patientHIN")).isEmpty();
        assertThat(bound.getParameter("patientAddress")).isEmpty();
        assertThat(bound.getParameter("patientCityPostal")).isEmpty();
    }

    @Test
    @DisplayName("should blank the patient heading, not fail the fax, when a directive refuses the demographic read")
    void shouldBlankPatientIdentity_whenDirectiveRefusesDemographicRead() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.setParameter("patientName", "Someone Else");
        stubStoredSignature();
        // getDemographic declares PatientDirectiveException from its own privilege check. After the
        // signature gate has passed that must not surface as a 500 -- and must not fall back to the request.
        when(demographicManager.getDemographic(any(), eq(DEMOGRAPHIC_NO))).thenThrow(new PatientDirectiveException("directive"));

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound).isNotNull();
        assertThat(bound.getParameter("patientName")).isEmpty();
        assertThat(bound.getParameter("rx")).contains("Amoxicillin 500 mg capsule");
    }

    @Test
    @DisplayName("should abort the fax, not send a blank heading, when the demographic read fails for any other reason")
    void shouldPropagateFailure_whenDemographicReadFailsUnexpectedly() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        // A database or wiring failure is not a directive. Absorbing it would fax a prescription with no
        // patient on it while masking an outage; it must propagate and stop the fax.
        when(demographicManager.getDemographic(any(), eq(DEMOGRAPHIC_NO))).thenThrow(new IllegalStateException("datasource down"));

        assertThatThrownBy(() -> new FrmCustomedPDFServlet().bindFaxContentToRecord(request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should compose the city line the way the Rx preview does, by which parts are present")
    void shouldComposeCityPostal_byWhichPartsArePresent() {
        assertThat(FrmCustomedPDFServlet.formatCityPostal("Hamilton", "ON", "L8S 4L8")).isEqualTo("Hamilton, ON L8S 4L8");
        assertThat(FrmCustomedPDFServlet.formatCityPostal("", "ON", "L8S 4L8")).isEqualTo("ON L8S 4L8");
        assertThat(FrmCustomedPDFServlet.formatCityPostal("Hamilton", "", "L8S 4L8")).isEqualTo("Hamilton  L8S 4L8");
    }

    @Test
    @DisplayName("should keep a one-character prescription line in the rendered fax body")
    void shouldKeepOneCharacterLine_whenSplittingRenderedBlocks() {
        String nl = System.lineSeparator();
        // The record-bound fax body is written server-side with plain platform newlines, so a
        // standalone dose line is a real line, not the CRLF remnant the separator test looks for.
        String body = "Amoxicillin 500 mg" + nl + "1" + nl + "cap PO TID" + nl + nl + "Ibuprofen 400 mg" + nl + nl;

        List<String> blocks = FrmCustomedPDFServlet.splitRenderedRxBlocks(body, nl);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).contains("Amoxicillin 500 mg").contains("1").contains("cap PO TID");
        assertThat(blocks.get(1)).contains("Ibuprofen 400 mg");
    }

    @Test
    @DisplayName("should still break blocks on the lone carriage return left by a browser-submitted body")
    void shouldSplitRenderedBlocks_onLoneCarriageReturn() {
        String nl = "\n";
        // A CRLF body split on "\n": every line keeps a trailing "\r", and a blank line is "\r"
        // alone. That remnant is the only one-character separator, and it must keep working.
        String body = "Amoxicillin 500 mg\r" + nl + "\r" + nl + "Ibuprofen 400 mg\r" + nl;

        List<String> blocks = FrmCustomedPDFServlet.splitRenderedRxBlocks(body, nl);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).contains("Amoxicillin 500 mg").doesNotContain("Ibuprofen");
        assertThat(blocks.get(1)).contains("Ibuprofen 400 mg");
    }

    @Test
    @DisplayName("should fall back to the prescriber's provider name when no signature text is on file")
    void shouldUsePrescriberName_whenNoSignatureTextOnFile() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        io.github.carlos_emr.carlos.commn.model.Provider provider = new io.github.carlos_emr.carlos.commn.model.Provider();
        provider.setFirstName("Ada");
        provider.setLastName("Prescriber");
        ProviderDao providerDao = mock(ProviderDao.class);
        when(providerDao.getProvider("999998")).thenReturn(provider);
        registerMock(ProviderDao.class, providerDao);

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound.getParameter("sigDoctorName")).isEqualTo("Ada Prescriber");
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
            FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
            servlet.init(new MockServletConfig(new MockServletContext()));

            servlet.service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("no drugs");
            verify(faxJobDao, never()).persist(any());
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
    @DisplayName("should reject the caller's own pad capture when they did not write the prescription")
    void shouldRejectOwnPadFile_whenCallerIsNotThePersistedPrescriber() throws Exception {
        // A covering provider with _rx write on the same patient re-faxes another prescriber's
        // script and draws on the pad. The document names the PERSISTED prescriber (the fax body is
        // bound to the record), so honouring this capture would put provider B's ink under provider
        // A's name. The stored signature — the one A actually left on the script — is used instead.
        Path ownPad = padFileFor("999998");
        try {
            byte[] ownBytes = otherPng(); // a real, decodable image, and this caller's own capture
            Files.write(ownPad, ownBytes);
            MockHttpServletRequest request = createFaxRequest();
            request.addParameter("imgFile", ownPad.toString());
            stubStoredSignature("111111"); // the script was written by a DIFFERENT provider
            LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

            byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

            assertThat(resolved).isEqualTo(tinyPng());
            assertThat(resolved).isNotEqualTo(ownBytes);
            verify(digitalSignatureManager).getDigitalSignature(SIGNATURE_ID);
        } finally {
            Files.deleteIfExists(ownPad);
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
    @DisplayName("should fall back to the stored signature when the named pad file is not a valid pad capture (traversal sanitized)")
    void shouldFallBackToStoredSignature_whenTraversalPadNameSanitized() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.addParameter("imgFile", "../../etc/passwd");
        stubStoredSignature();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        // The pad branch is only entered for a logged-in provider; without this stub the
        // traversal value would never be examined and this test would prove nothing.
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        // Real PathValidationUtils behaviour: validatePath reduces "../../etc/passwd" to its base
        // name "passwd" (it does not throw), which then fails the signature_<provider><millis>.jpg
        // pad pattern, so the servlet falls back to the stored signature. The SecurityException
        // path is exercised separately below with a forced stub. Here we PROVE the raw traversal
        // value reached the validator rather than being used as a path directly.
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
            // Prove the rejection branch ran: the stub was hit, so the fallback came from the
            // swallowed SecurityException, not from the validator being bypassed.
            pathValidation.verify(() -> PathValidationUtils.validatePath(eq("../../etc/passwd"), any(File.class)));
        }
        verify(digitalSignatureManager).getDigitalSignature(SIGNATURE_ID);
    }

    @Test
    @DisplayName("should treat a stored signature that is not a decodable image as absent")
    void shouldWithholdStoredSignature_whenImageNotRenderable() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        DigitalSignature corrupt = new DigitalSignature();
        corrupt.setDemographicId(DEMOGRAPHIC_NO);
        corrupt.setModuleType(ModuleType.PRESCRIPTION);
        corrupt.setSignatureImage("not an image".getBytes(StandardCharsets.UTF_8));
        when(digitalSignatureManager.getDigitalSignature(SIGNATURE_ID)).thenReturn(corrupt);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        // The fax gate keys off this result, so an undecodable blob must refuse the fax rather
        // than let EndPage drop it silently and send a "signed" fax with a blank signature line.
        assertThat(resolved).isNull();
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
    @DisplayName("should bind the notes and prescriber ids to the record, not to the request")
    void shouldBindNotesAndPrescriberIds_toPrescriptionRecord() throws Exception {
        // The PDF prints additNotes immediately ABOVE the signature line, and the College ID and
        // billing number beside the prescriber's name. Binding only rx and sigDoctorName left the
        // record-binding control bypassable: a caller with _rx write on the patient could post
        // arbitrary additNotes and have it render over another prescriber's stored signature.
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        // Give the RECORD known values so the assertions prove where the replacements came FROM.
        // Asserting only "not the posted value" would pass even if the binding wrote empty strings,
        // i.e. it would go green over a fix that silently dropped the prescriber's real notes.
        Prescription record = prescriptionDao.find(SCRIPT_ID);
        record.setComments("Take with food. Recheck INR in 5 days.");
        io.github.carlos_emr.carlos.commn.model.Provider provider =
                new io.github.carlos_emr.carlos.commn.model.Provider();
        provider.setFirstName("Ada");
        provider.setLastName("Prescriber");
        provider.setPractitionerNo("CPSO-12345");
        provider.setBillingNo("BILL-67890");
        ProviderDao providerDao = mock(ProviderDao.class);
        when(providerDao.getProvider("999998")).thenReturn(provider);
        registerMock(ProviderDao.class, providerDao);

        request.addParameter("additNotes", "Oxycodone 80 mg, #100, refills x5");
        request.addParameter("pracNo", "999999");
        request.addParameter("billingNumber", "888888");

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound.getParameter("additNotes"))
                .as("the notes rendered above the signature must be the RECORD's, not the caller's")
                .isEqualTo("Take with food. Recheck INR in 5 days.");
        assertThat(bound.getParameter("pracNo"))
                .as("the College ID beside the prescriber's name must be that prescriber's")
                .isEqualTo("CPSO-12345");
        assertThat(bound.getParameter("billingNumber")).isEqualTo("BILL-67890");
    }

    @Test
    @DisplayName("should render every drug block when the body ends with its own separator")
    void shouldRenderEveryBlock_whenBodyEndsWithSeparator() {
        // The record-bound fax body appends newline+newline after EVERY block, so it ends with its
        // own separator. String.split drops trailing empty strings, so without an explicit tail
        // flush the final block is lost: a one-drug script faxes with the clinic header, the
        // prescriber's name and their signature, and no medication at all.
        String nl = System.getProperty("line.separator");
        String oneDrug = "Amoxicillin 500 mg capsule" + nl + "1 cap PO TID x 7 days" + nl + nl;
        String twoDrugs = oneDrug + "Ibuprofen 400 mg tablet" + nl + "1 tab PO QID PRN" + nl + nl;

        assertThat(FrmCustomedPDFServlet.splitRenderedRxBlocks(oneDrug, nl))
                .as("a single-drug fax must still render its one drug block")
                .hasSize(1);
        assertThat(FrmCustomedPDFServlet.splitRenderedRxBlocks(oneDrug, nl).get(0))
                .contains("Amoxicillin 500 mg capsule")
                .contains("1 cap PO TID x 7 days");
        assertThat(FrmCustomedPDFServlet.splitRenderedRxBlocks(twoDrugs, nl))
                .as("the LAST drug must not be dropped from a multi-drug fax")
                .hasSize(2);
        assertThat(FrmCustomedPDFServlet.splitRenderedRxBlocks(twoDrugs, nl).get(1))
                .contains("Ibuprofen 400 mg tablet");
    }

    @Test
    @DisplayName("should keep browser-posted CRLF bodies splitting exactly as before")
    void shouldPreserveLegacySplit_forBrowserPostedBody() {
        // Preview2.jsp TERMINATES every block with ";;" which becomes a blank line, and the form post
        // CRLF-normalises it, so each separator reaches the servlet as a lone "\r". This is the
        // print path and the pre-PR fax path; the tail flush must not change its block count.
        String nl = System.getProperty("line.separator");
        String legacyTwo = ("Amox 500mg; 1 cap TID" + ";;" + "Ibu 400mg; 1 tab QID" + ";;")
                .replace(";", nl).replace("\n", "\r\n");

        assertThat(FrmCustomedPDFServlet.splitRenderedRxBlocks(legacyTwo, nl)
                .stream().filter(b -> !b.isBlank()).count())
                .as("the browser-posted body already flushed its tail via the lone CR")
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("should withhold the signature when the privilege check itself throws")
    void shouldWithholdSignature_whenPrivilegeCheckThrows() throws Exception {
        // SecurityInfoManagerImpl rethrows PatientDirectiveException. Unguarded it would abort PDF
        // generation with a 500 rather than the deliberate refusal this method exists to produce.
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.WRITE), anyString()))
                .thenThrow(new PatientDirectiveException("directive blocks this chart"));
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        byte[] resolved = new FrmCustomedPDFServlet().resolveSignatureImage(request, loggedInInfo);

        assertThat(resolved).isNull();
        verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
    }

    @Test
    @DisplayName("should defer to the signature gate when the fax permission check throws")
    void shouldNotReportPermissionDenial_whenPrivilegeCheckThrows() {
        // Answering "denied" here would emit the specific permission wording, which under a directive
        // confirms the script exists. Answering "not denied" is not an authorization: resolveSignature
        // Image withholds the signature on the same failure, so the fax still refuses — as "not signed",
        // the same answer a non-existent script id gives.
        Prescription prescription = new Prescription();
        prescription.setDemographicId(DEMOGRAPHIC_NO);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), anyString()))
                .thenThrow(new PatientDirectiveException("directive blocks this chart"));
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        assertThat(new FrmCustomedPDFServlet().isFaxDeniedByPrivilege(prescription, loggedInInfo)).isFalse();
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
            FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
            servlet.init(new MockServletConfig(new MockServletContext()));

            servlet.service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verify(prescriptionDao, never()).find(anyInt());
            verify(digitalSignatureManager, never()).getDigitalSignature(anyInt());
            verify(faxJobDao, never()).persist(any());
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
            FrmCustomedPDFServlet servlet = new FrmCustomedPDFServlet();
            servlet.init(new MockServletConfig(new MockServletContext()));

            servlet.service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("fax-failure").contains("permission").doesNotContain("not signed");
            verify(demographicManager, never()).getDemographic(any(), anyInt());
            verify(faxJobDao, never()).persist(any());
        }
    }

    @Test
    @DisplayName("should fax the latest drug date of the record, not the request's rxDate")
    void shouldBindRxDate_toLatestRecordDrugDate() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.setParameter("rxDate", "January 1, 1900");
        stubStoredSignature();
        Prescription prescription = prescriptionDao.find(SCRIPT_ID);
        Drug older = drugRow(5, RECORD_DRUG_LINE);
        older.setRxDate(new GregorianCalendar(2026, 2, 4).getTime());
        Drug newer = drugRow(6, SECOND_DRUG_LINE);
        Date latest = new GregorianCalendar(2026, 4, 6).getTime();
        newer.setRxDate(latest);
        stubRecordDrugs(prescription, older, newer);

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound.getParameter("rxDate")).isEqualTo(RxUtil.DateToString(latest, "MMMM d, yyyy")).doesNotContain("1900");
    }

    @Test
    @DisplayName("should fax the prescriber's clinic header, not the request's")
    void shouldBindClinicHeader_toPrescriberClinic() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.setParameter("clinicName", "Forged Clinic\n1 Forged Way");
        request.setParameter("clinicPhone", "4165550001");
        stubStoredSignature();
        stubPrescriberClinic();

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        // The page strips the "(nnnnnn)" clinic number and joins name, address, "city   postal".
        assertThat(bound.getParameter("clinicName")).isEqualTo("Record Clinic \n10 Record Rd\nHamilton   L8S 4L8");
        assertThat(bound.getParameter("clinicPhone")).isEqualTo("9055550000");
        assertThat(bound.getParameter("useSC")).isEqualTo("false"); // always rebound from the block, never read
    }

    @Test
    @DisplayName("should let the prescriber's rxPhone preference win over the clinic telephone")
    void shouldBindClinicPhone_toPrescriberPreference() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        stubPrescriberClinic();
        UserProperty rxPhone = new UserProperty();
        rxPhone.setProviderNo("999998");
        rxPhone.setName("rxPhone");
        rxPhone.setValue("4161112222");
        when(userPropertyDao.getProp("999998", "rxPhone")).thenReturn(rxPhone);

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound.getParameter("clinicPhone")).isEqualTo("4161112222");
    }

    @Test
    @DisplayName("should keep a satellite clinic block the provider was offered")
    void shouldKeepSatelliteClinic_whenBlockIsOffered() throws Exception {
        String previousMultisites = (String) CarlosProperties.getInstance().get("multisites");
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        stubPrescriberClinic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        String offered = RxSatelliteClinicAddress.html("Dr A", "North Site", "2 North Ave", "Barrie", "ON", "L4M 1A1",
                "7055551111", "7055552222", telLabel(request), faxLabel(request));
        request.setParameter("useSC", "true");
        request.setParameter("scAddress", offered);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            CarlosProperties.getInstance().setProperty("multisites", "true");
            when(siteDao.getActiveSitesByProviderNo("999998")).thenReturn(List.of(northSite()));

            HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

            assertThat(bound.getParameter("useSC")).isEqualTo("true");
            // The bound block is the OFFERED one (the prescriber-name prefix is not part of the match
            // and not rendered); its clinic part is what parseSCAddress reads.
            assertThat(RxSatelliteClinicAddress.clinicPart(bound.getParameter("scAddress")))
                    .isEqualTo(RxSatelliteClinicAddress.clinicPart(offered));
        } finally {
            restoreProperty("multisites", previousMultisites);
        }
    }

    @Test
    @DisplayName("should print the clinic's official fax, never the outgoing line the request names")
    void shouldBindClinicFax_toClinicOfficialFax() throws Exception {
        MockHttpServletRequest request = createFaxRequest(); // clinicFax 4165553434 = the sending line
        stubStoredSignature();
        stubPrescriberClinic(); // clinic row fax 9055550009

        assertThat(new FrmCustomedPDFServlet().bindFaxContentToRecord(request).getParameter("clinicFax")).isEqualTo("9055550009");

        // The prescriber's own faxnumber preference wins over the clinic row, as on the preview.
        UserProperty faxPreference = new UserProperty();
        faxPreference.setProviderNo("999998");
        faxPreference.setName("faxnumber");
        faxPreference.setValue("9055551234");
        when(userPropertyDao.getProp("999998", "faxnumber")).thenReturn(faxPreference);
        assertThat(new FrmCustomedPDFServlet().bindFaxContentToRecord(request).getParameter("clinicFax")).isEqualTo("9055551234");
    }

    @Test
    @DisplayName("should keep a satellite block whose clinic text needed HTML encoding")
    void shouldKeepSatelliteClinic_whenBlockTextWasHtmlEncoded() throws Exception {
        String previousMultisites = (String) CarlosProperties.getInstance().get("multisites");
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        stubPrescriberClinic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        Site site = northSite();
        site.setName("Smith & Jones");
        // The page unescapes the block before it goes on the wire, so the request carries "&", not "&amp;".
        String posted = org.apache.commons.text.StringEscapeUtils.unescapeHtml4(RxSatelliteClinicAddress.html("Dr A",
                "Smith & Jones", "2 North Ave", "Barrie", "ON", "L4M 1A1", "7055551111", "7055552222", telLabel(request), faxLabel(request)));
        request.setParameter("useSC", "true");
        request.setParameter("scAddress", posted);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            CarlosProperties.getInstance().setProperty("multisites", "true");
            when(siteDao.getActiveSitesByProviderNo("999998")).thenReturn(List.of(site));

            HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

            assertThat(bound.getParameter("useSC")).isEqualTo("true");
            assertThat(RxSatelliteClinicAddress.clinicPart(bound.getParameter("scAddress")))
                    .isEqualTo(RxSatelliteClinicAddress.clinicPart(posted));
        } finally {
            restoreProperty("multisites", previousMultisites);
        }
    }

    @Test
    @DisplayName("should render the offered block, not the request's entity-spelled copy of it")
    void shouldRenderOfferedBlock_whenRequestSpellsItWithEntities() throws Exception {
        String previousMultisites = (String) CarlosProperties.getInstance().get("multisites");
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        stubPrescriberClinic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        Site site = northSite();
        site.setName("Smith & Jones");
        String wire = org.apache.commons.text.StringEscapeUtils.unescapeHtml4(RxSatelliteClinicAddress.html("Dr A",
                "Smith & Jones", "2 North Ave", "Barrie", "ON", "L4M 1A1", "7055551111", "7055552222", telLabel(request), faxLabel(request)));
        // Same clinic, but the ampersand spelled as a numeric entity: it matches after decoding, yet
        // this spelling must never reach the parser and print as "&#38;".
        request.setParameter("useSC", "true");
        request.setParameter("scAddress", wire.replace("Smith & Jones", "Smith &#38; Jones"));

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            CarlosProperties.getInstance().setProperty("multisites", "true");
            when(siteDao.getActiveSitesByProviderNo("999998")).thenReturn(List.of(site));

            HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

            assertThat(bound.getParameter("useSC")).isEqualTo("true");
            assertThat(RxSatelliteClinicAddress.clinicPart(bound.getParameter("scAddress")))
                    .isEqualTo(RxSatelliteClinicAddress.clinicPart(wire)).contains("Smith & Jones").doesNotContain("&#38;");
        } finally {
            restoreProperty("multisites", previousMultisites);
        }
    }

    @Test
    @DisplayName("should strip whichever localized label precedes the satellite telephone and fax")
    void shouldStripLocalizedLabels_whenParsingSatelliteBlock() {
        String block = RxSatelliteClinicAddress.html("Dr A", "Site Nord", "2 rue Nord", "Gatineau", "QC", "J8X 1A1",
                "8195551111", "8195552222", "T&eacute;l", "T&eacute;l&eacute;copieur");

        java.util.HashMap<String, String> parsed = FrmCustomedPDFServlet.parseSCAddress(block);

        assertThat(parsed.get("clinicTel")).isEqualTo("8195551111");
        assertThat(parsed.get("clinicFax")).isEqualTo("8195552222");
        assertThat(parsed.get("clinicName")).isEqualTo("Site Nord\n2 rue Nord\nGatineau, QC J8X 1A1");
    }

    @Test
    @DisplayName("should let an unexpected privilege-lookup failure abort the fax instead of deferring it")
    void shouldPropagateFailure_whenPrivilegeLookupFailsUnexpectedly() throws Exception {
        stubStoredSignature();
        Prescription prescription = prescriptionDao.find(SCRIPT_ID);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.READ), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenThrow(new IllegalStateException("datasource down"));

        assertThatThrownBy(() -> new FrmCustomedPDFServlet().isFaxDeniedByPrivilege(prescription, mock(LoggedInInfo.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should decide the satellite flag from the block alone, whatever case the request spelled it in")
    void shouldRebindSatelliteFlag_fromOfferedBlockNotRequestCase() throws Exception {
        String previousMultisites = (String) CarlosProperties.getInstance().get("multisites");
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        stubPrescriberClinic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        // generatePDFDocumentBytes reads useSC case-insensitively, so "TrUe" with a forged block would
        // reach parseSCAddress if the flag were only rewritten when spelled "true".
        String forged = RxSatelliteClinicAddress.html("Dr A", "Forged Site", "2 Forged Rd", "Forgedville", "ZZ", "Z0Z 0Z0",
                "4165550002", "4165550003", telLabel(request), faxLabel(request));
        request.setParameter("useSC", "TrUe");
        request.setParameter("scAddress", forged);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            CarlosProperties.getInstance().setProperty("multisites", "true");
            when(siteDao.getActiveSitesByProviderNo("999998")).thenReturn(List.of(northSite()));

            HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

            assertThat(bound.getParameter("useSC")).isEqualTo("false");
            assertThat(bound.getParameter("scAddress")).isEmpty();
        } finally {
            restoreProperty("multisites", previousMultisites);
        }
    }

    @Test
    @DisplayName("should fall back to the main clinic when the satellite block was never offered")
    void shouldDropSatelliteClinic_whenBlockIsNotOffered() throws Exception {
        String previousMultisites = (String) CarlosProperties.getInstance().get("multisites");
        MockHttpServletRequest request = createFaxRequest();
        stubStoredSignature();
        stubPrescriberClinic();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        // A well-formed block for a clinic this provider has no site for.
        String forged = RxSatelliteClinicAddress.html("Dr A", "Forged Site", "2 Forged Rd", "Forgedville", "ZZ", "Z0Z 0Z0",
                "4165550002", "4165550003", telLabel(request), faxLabel(request));
        request.setParameter("useSC", "true");
        request.setParameter("scAddress", forged);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            CarlosProperties.getInstance().setProperty("multisites", "true");
            when(siteDao.getActiveSitesByProviderNo("999998")).thenReturn(List.of(northSite()));

            HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

            assertThat(bound.getParameter("useSC")).isEqualTo("false");
            assertThat(bound.getParameter("scAddress")).isEmpty();
            assertThat(bound.getParameter("clinicName")).startsWith("Record Clinic");
        } finally {
            restoreProperty("multisites", previousMultisites);
        }
    }

    @Test
    @DisplayName("should fax the record's print history as the reprint annotation when reprinting")
    void shouldBindReprintAnnotation_fromRecordPrintHistory() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.setParameter("rxReprint", "true");
        request.setParameter("origPrintDate", "FORGED DATE");
        request.setParameter("numPrints", "77");
        request.getSession().setAttribute("rePrint", "true");
        stubStoredSignature();
        Prescription prescription = prescriptionDao.find(SCRIPT_ID);
        Date firstPrinted = new GregorianCalendar(2026, 0, 2).getTime();
        prescription.setDatePrinted(firstPrinted);
        prescription.setDatesReprinted("2026-02-03"); // reprinted once: printed twice in all
        stubRecordDrugs(prescription, drugRow(5, RECORD_DRUG_LINE));

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound.getParameter("rxReprint")).isEqualTo("true");
        assertThat(bound.getParameter("origPrintDate")).isEqualTo(String.valueOf(firstPrinted));
        assertThat(bound.getParameter("numPrints")).isEqualTo("2");
    }

    @Test
    @DisplayName("should blank the reprint annotation when this session is not reprinting")
    void shouldBlankReprintAnnotation_whenNotReprinting() throws Exception {
        MockHttpServletRequest request = createFaxRequest();
        request.setParameter("rxReprint", "true");
        request.setParameter("origPrintDate", "FORGED DATE");
        request.setParameter("numPrints", "77");
        stubStoredSignature();

        HttpServletRequest bound = new FrmCustomedPDFServlet().bindFaxContentToRecord(request);

        assertThat(bound.getParameter("rxReprint")).isEqualTo("false");
        assertThat(bound.getParameter("origPrintDate")).isEmpty();
        assertThat(bound.getParameter("numPrints")).isEmpty();
    }

    /** The prescriber 999998 and the clinic row RxProviderData composes the clinic header from. */
    private void stubPrescriberClinic() {
        Clinic clinic = new Clinic();
        clinic.setClinicName("Record Clinic (123456)");
        clinic.setClinicAddress("10 Record Rd");
        clinic.setClinicCity("Hamilton");
        clinic.setClinicProvince("ON");
        clinic.setClinicPostal("L8S 4L8");
        clinic.setClinicPhone("9055550000");
        clinic.setClinicFax("9055550009");
        when(clinicDao.getClinic()).thenReturn(clinic);
        io.github.carlos_emr.carlos.commn.model.Provider prescriber = new io.github.carlos_emr.carlos.commn.model.Provider();
        prescriber.setProviderNo("999998");
        prescriber.setFirstName("Ann");
        prescriber.setLastName("Prescriber");
        when(providerDao.getProvider("999998")).thenReturn(prescriber);
    }

    private static Site northSite() {
        Site site = new Site();
        site.setName("North Site");
        site.setAddress("2 North Ave");
        site.setCity("Barrie");
        site.setProvince("ON");
        site.setPostal("L4M 1A1");
        site.setPhone("7055551111");
        site.setFax("7055552222");
        return site;
    }

    private static String telLabel(HttpServletRequest request) {
        return SafeEncode.forHtml(LocaleUtils.getMessage(request.getLocale(), "RxPreview.msgTel"));
    }

    private static String faxLabel(HttpServletRequest request) {
        return SafeEncode.forHtml(LocaleUtils.getMessage(request.getLocale(), "RxPreview.msgFax"));
    }

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
