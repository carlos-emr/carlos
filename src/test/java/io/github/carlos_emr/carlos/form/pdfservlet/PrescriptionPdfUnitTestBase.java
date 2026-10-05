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
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.GregorianCalendar;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

/**
 * Shared fixture for the prescription PDF and fax tests: the mocked DAOs and managers, a signed
 * prescription record, fax and preview requests, and the helpers that stub them. The composer and the
 * fax service are registered over these mocks, so {@link FrmCustomedPDFServlet} and the fax action
 * pick them up through SpringUtils.
 */
public abstract class PrescriptionPdfUnitTestBase extends CarlosUnitTestBase {

    protected static final int SCRIPT_ID = 1;
    protected static final int DEMOGRAPHIC_NO = 1;
    protected static final int SIGNATURE_ID = 77;

    protected FaxConfigDao faxConfigDao;
    protected FaxJobDao faxJobDao;
    protected FaxManager faxManager;
    protected PrescriptionDao prescriptionDao;
    protected DigitalSignatureManager digitalSignatureManager;
    protected SecurityInfoManager securityInfoManager;
    protected DrugDao drugDao;
    protected ProviderExtDao providerExtDao;
    protected DemographicManager demographicManager;
    protected ProviderDao providerDao;
    protected ClinicDAO clinicDao;
    protected UserPropertyDAO userPropertyDao;
    protected SiteDao siteDao;

    @BeforeEach
    protected void setUpPrescriptionPdfMocks() {
        faxConfigDao = mock(FaxConfigDao.class);
        faxJobDao = mock(FaxJobDao.class);
        registerMock(FaxJobDao.class, faxJobDao);
        prescriptionDao = mock(PrescriptionDao.class);
        digitalSignatureManager = mock(DigitalSignatureManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(FaxConfigDao.class, faxConfigDao);
        registerMock(DigitalSignatureManager.class, digitalSignatureManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        faxManager = mock(FaxManager.class);
        registerMock(FaxManager.class, faxManager);
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
        registerMock(PrescriptionPdfComposer.class, newComposer());
        registerMock(PrescriptionFaxService.class, newFaxService());
    }

    /** A composer over this fixture's mocks. */
    protected PrescriptionPdfComposer newComposer() {
        return new PrescriptionPdfComposer(prescriptionDao, digitalSignatureManager, securityInfoManager, providerDao,
                demographicManager);
    }

    /** A fax service over this fixture's mocks. */
    protected PrescriptionFaxService newFaxService() {
        return new PrescriptionFaxService(faxConfigDao, faxJobDao, faxManager, securityInfoManager);
    }

    protected static final String RECORD_DRUG_LINE = "Amoxicillin 500 mg capsule\n1 cap PO TID x 7 days";
    protected static final String SECOND_DRUG_LINE = "Ibuprofen 400 mg tablet\n1 tab PO q6h PRN pain";

    /** A persisted drugs row of script {@value #SCRIPT_ID} whose full out line is {@code special}. */
    protected static Drug drugRow(int id, String special) {
        Drug drug = new Drug();
        drug.setId(id);
        drug.setScriptNo(SCRIPT_ID);
        drug.setDemographicId(DEMOGRAPHIC_NO);
        drug.setProviderNo("999998");
        drug.setSpecial(special);
        return drug;
    }

    /** Makes the record of script {@value #SCRIPT_ID} carry the given drug rows. */
    protected void stubRecordDrugs(Prescription prescription, Drug... drugs) {
        List<Object[]> pairs = new java.util.ArrayList<>();
        for (Drug drug : drugs) {
            pairs.add(new Object[] {drug, prescription});
        }
        when(drugDao.findDrugsAndPrescriptionsByScriptNumber(SCRIPT_ID)).thenReturn(pairs);
    }

    /**
     * Makes script {@value #SCRIPT_ID} carry stored signature {@value #SIGNATURE_ID} for patient
     * {@value #DEMOGRAPHIC_NO}, readable by a caller with {@code _rx} read privilege. Fax requests
     * need this: the fax action refuses to fax an unsigned prescription.
     */
    protected void stubStoredSignature() throws Exception {
        stubStoredSignature("999998");
    }

    /**
     * As {@link #stubStoredSignature()}, but the persisted row records {@code prescriberNo} as its
     * prescriber — used to separate the signing caller from the provider who wrote the script.
     */
    protected void stubStoredSignature(String prescriberNo) throws Exception {
        Prescription prescription = new Prescription();
        org.springframework.test.util.ReflectionTestUtils.setField(prescription, "id", SCRIPT_ID);
        prescription.setDemographicId(DEMOGRAPHIC_NO);
        prescription.setProviderNo(prescriberNo);
        prescription.setDigitalSignatureId(SIGNATURE_ID);
        when(prescriptionDao.find(SCRIPT_ID)).thenReturn(prescription);
        // The record has one drug, so a fax has something legitimate to render.
        stubRecordDrugs(prescription, drugRow(5, RECORD_DRUG_LINE));

        DigitalSignature metadata = new DigitalSignature();
        metadata.setProviderNo(prescriberNo);
        metadata.setDemographicId(DEMOGRAPHIC_NO);
        metadata.setModuleType(ModuleType.PRESCRIPTION);
        when(digitalSignatureManager.getDigitalSignatureMetadata(SIGNATURE_ID)).thenReturn(metadata);

        DigitalSignature signature = new DigitalSignature();
        signature.setProviderNo(prescriberNo);
        signature.setDemographicId(DEMOGRAPHIC_NO);
        signature.setModuleType(ModuleType.PRESCRIPTION);
        signature.setSignatureImage(tinyPng());
        when(digitalSignatureManager.getDigitalSignature(SIGNATURE_ID)).thenReturn(signature);

        // Grant both READ and WRITE for the patient; the fax path requires WRITE, a preview READ.
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.WRITE), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(true);
        // The fax also heads the page with the demographic record, so faxing needs _demographic READ.
        when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.READ), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.WRITE), isNull()))
                .thenReturn(true);
    }

    /** Lets the caller fax prescriptions for patient {@value #DEMOGRAPHIC_NO}, without stubbing any record. */
    protected void grantFaxRights() {
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.WRITE), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq(SecurityInfoManager.WRITE), isNull()))
                .thenReturn(true);
    }

    protected void stubActiveFaxConfig() {
        FaxConfig config = new FaxConfig();
        config.setFaxNumber("4165553434");
        config.setFaxUser("fax-user");
        config.setSenderEmail("fax@example.invalid");
        config.setActive(true);
        when(faxConfigDao.getActiveConfigByNumber("4165553434")).thenReturn(config);
    }

    protected void verifyFaxWasNotQueued() {
        verify(faxManager, never()).persistAndLogFaxJob(
                any(), any(), any(), anyInt());
    }

    protected void serviceAs(FrmCustomedPDFServlet servlet, MockHttpServletRequest request,
            MockHttpServletResponse response, LoggedInInfo loggedInInfo) throws Exception {
        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
                MockedStatic<PrescriptionQrCodeUIBean> qrCodeMock = mockStatic(PrescriptionQrCodeUIBean.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCodeMock.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);
            servlet.service(request, response);
        }
    }

    /** A 13-digit-suffix pad file name for the given provider, matching generateSignatureRequestId. */
    protected static Path padFileFor(String providerNo) {
        // providerNo + a 13-digit timestamp, the exact shape the servlet accepts.
        return Path.of(System.getProperty("java.io.tmpdir"), "signature_" + providerNo + System.currentTimeMillis() + ".jpg");
    }

    /** A decodable PNG that is distinct from {@link #tinyPng()} so byte assertions can tell them apart. */
    protected static byte[] otherPng() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    protected static byte[] tinyPng() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * The demographic row of patient {@value #DEMOGRAPHIC_NO}, as the record holds it. Every value
     * here is deliberately different from what {@link #createFaxRequest()} posts, so an assertion
     * that finds the record's value proves the binding rather than a coincidence.
     */
    protected void stubRecordDemographic() {
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

    /** The prescriber 999998 and the clinic row RxProviderData composes the clinic header from. */
    protected void stubPrescriberClinic() {
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

    protected static Site northSite() {
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

    protected static String telLabel(HttpServletRequest request) {
        return SafeEncode.forHtml(LocaleUtils.getMessage(request.getLocale(), "RxPreview.msgTel"));
    }

    protected static String faxLabel(HttpServletRequest request) {
        return SafeEncode.forHtml(LocaleUtils.getMessage(request.getLocale(), "RxPreview.msgFax"));
    }

    protected MockHttpServletRequest createPreviewRequest() {
        MockHttpServletRequest request = createFaxRequest();
        request.setRequestURI("/form/createcustomedpdf");
        return request;
    }

    protected MockHttpServletRequest createFaxRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rx/faxPrescription");
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

    protected static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, previousValue);
        }
    }
}
