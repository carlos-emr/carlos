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
import io.github.carlos_emr.carlos.managers.FaxManager;
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

@DisplayName("FrmCustomedPDFServlet path validation")
@Tag("unit")
@Tag("web")
@Tag("security")
class FrmCustomedPDFServletUnitTest extends CarlosUnitTestBase {

    private FaxConfigDao faxConfigDao;
    private FaxJobDao faxJobDao;

    @BeforeEach
    void setUp() {
        faxConfigDao = mock(FaxConfigDao.class);
        faxJobDao = mock(FaxJobDao.class);
        registerMock(FaxConfigDao.class, faxConfigDao);
        registerMock(FaxJobDao.class, faxJobDao);
        registerMock(FaxManager.class, mock(FaxManager.class));
        registerMock(ClinicDAO.class, mock(ClinicDAO.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        registerMock(PrescriptionDao.class, mock(PrescriptionDao.class));
        registerMock(DrugDao.class, mock(DrugDao.class));
    }

    @Test
    @DisplayName("should return server error when document directory is invalid")
    void shouldReturnServerError_whenDocumentDirectoryIsInvalid() throws Exception {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        MockHttpServletRequest request = createFaxRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
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
