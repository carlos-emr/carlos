/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxSatelliteClinicAddress;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("web")
class PrescriptionPdfServletUnitTest extends CarlosUnitTestBase {
    @BeforeEach
    void registerRendererDependencies() {
        registerMock(ClinicDAO.class, mock(ClinicDAO.class));
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        registerMock(DrugDao.class, mock(DrugDao.class));
        registerMock(FaxConfigDao.class, mock(FaxConfigDao.class));
        registerMock(FaxJobDao.class, mock(FaxJobDao.class));
        registerMock(FaxManager.class, mock(FaxManager.class));
        registerMock(PrescriptionDao.class, mock(PrescriptionDao.class));
        registerMock(DigitalSignatureManager.class, mock(DigitalSignatureManager.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(DemographicManager.class, mock(DemographicManager.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "PRIVATE_INVALID_ADDRESS", "<b>Dr A</b>",
            "<b>Dr A</b>Clinic<br>Street<br>City<br>Tel: 123<br>Fax: 456",
            "<b>Dr A</b><br>Clinic", "<b>Dr A</b><br>Clinic<br>Street<br>City<br>Tel: 123"})
    void shouldReturnBadRequest_whenSatelliteAddressIsMalformed(String address) throws Exception {
        MockHttpServletRequest request = printRequest();
        request.setParameter("useSC", "TrUe");
        if (address != null) request.setParameter("scAddress", address);
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet().service(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getErrorMessage()).isEqualTo("Invalid satellite clinic address");
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TrUe", "false"})
    void shouldGeneratePdf_withValidClinicSelection(String useSatellite) throws Exception {
        MockHttpServletRequest request = printRequest();
        request.setParameter("useSC", useSatellite);
        request.setParameter("scAddress", "false".equals(useSatellite) ? "INVALID_UNUSED_ADDRESS"
                : RxSatelliteClinicAddress.html("Dr A", "Satellite Clinic", "2 North Ave", "Barrie", "ON",
                        "L4M 1A1", "", "", "Tel", "Fax"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        try (MockedStatic<LoggedInInfo> login = mockStatic(LoggedInInfo.class);
             MockedStatic<PrescriptionQrCodeUIBean> qrCode = mockStatic(PrescriptionQrCodeUIBean.class)) {
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            qrCode.when(() -> PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider("999998"))
                    .thenReturn(false);

            servlet().service(request, response);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getContentAsByteArray()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
            try (PdfReader reader = new PdfReader(response.getContentAsByteArray())) {
                String text = new PdfTextExtractor(reader).getTextFromPage(1);
                assertThat(text).contains("false".equals(useSatellite) ? "Test Clinic" : "Satellite Clinic");
            }
        }
    }

    @Test
    void shouldPreserveMethodRefusal_whenFaxUsesGetWithInvalidSatelliteInput() throws Exception {
        MockHttpServletRequest request = printRequest();
        request.setParameter("__method", "oscarRxFax");
        request.setParameter("useSC", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet().service(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldPreserveSignatureRefusal_whenFaxHasInvalidSatelliteInput() throws Exception {
        MockHttpServletRequest request = printRequest();
        request.setMethod("POST");
        request.setParameter("__method", "oscarRxFax");
        request.setParameter("useSC", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet().service(request, response);

        assertThat(response.getContentAsString()).contains("fax-failure", "not signed");
        assertThat(response.getErrorMessage()).isNull();
    }

    private static PrescriptionPdfServlet servlet() throws ServletException {
        PrescriptionPdfServlet servlet = new PrescriptionPdfServlet();
        servlet.init(new MockServletConfig(new MockServletContext()));
        return servlet;
    }

    private static MockHttpServletRequest printRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/form/createcustomedpdf");
        request.setParameter("clinicName", "Test Clinic");
        request.setParameter("clinicPhone", "4165550000");
        request.setParameter("clinicFax", "4165553434");
        request.setParameter("patientName", "Test Patient");
        request.setParameter("patientAddress", "123 Test Street");
        request.setParameter("patientCityPostal", "Toronto ON");
        request.setParameter("patientPhone", "4165559999");
        request.setParameter("sigDoctorName", "Dr Test");
        request.setParameter("rxDate", "2026-06-19");
        request.setParameter("rx", "Test prescription");
        return request;
    }
}
