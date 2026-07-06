/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionFaxService;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionFaxViewModel;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionPdfComposer;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for {@link RxFaxPrescription2Action}.
 */
@DisplayName("RxFaxPrescription2Action Unit Tests")
@Tag("unit")
@Tag("rx")
class RxFaxPrescription2ActionTest {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private PrescriptionPdfComposer mockPrescriptionPdfComposer;

    @Mock
    private PrescriptionFaxService mockPrescriptionFaxService;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockServletContext mockServletContext;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private RxFaxPrescription2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockServletContext = new MockServletContext();
        mockRequest = new MockHttpServletRequest(mockServletContext);
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");
        mockRequest.setParameter("pharmaFax", "416-555-0199");
        mockRequest.setParameter("demographic_no", "123");

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull()))
                .thenReturn(true);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
        servletActionContextMock.when(ServletActionContext::getServletContext).thenReturn(mockServletContext);

        action = new RxFaxPrescription2Action(
                mockSecurityInfoManager,
                mockPrescriptionPdfComposer,
                mockPrescriptionFaxService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should reject GET before generating PDF or creating fax job")
    void shouldRejectGet_beforeGeneratingPdfOrCreatingFaxJob() throws Exception {
        mockRequest.setMethod("GET");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(MockHttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(mockSecurityInfoManager, mockPrescriptionPdfComposer, mockPrescriptionFaxService);
    }

    @Test
    @DisplayName("should reject HEAD before generating PDF or creating fax job")
    void shouldRejectHead_beforeGeneratingPdfOrCreatingFaxJob() throws Exception {
        mockRequest.setMethod("HEAD");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(MockHttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(mockSecurityInfoManager, mockPrescriptionPdfComposer, mockPrescriptionFaxService);
    }

    @Test
    @DisplayName("should reject POST when session is unauthenticated")
    void shouldRejectPost_whenSessionUnauthenticated() throws Exception {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(mockSecurityInfoManager, mockPrescriptionPdfComposer, mockPrescriptionFaxService);
    }

    @Test
    @DisplayName("should reject POST when Rx write privilege is missing")
    void shouldRejectPost_whenRxWritePrivilegeMissing() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull()))
                .thenReturn(false);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(MockHttpServletResponse.SC_FORBIDDEN);
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", SecurityInfoManager.WRITE, null);
        verifyNoInteractions(mockPrescriptionPdfComposer, mockPrescriptionFaxService);
    }

    @Test
    @DisplayName("should reject POST when fax number is missing")
    void shouldRejectPost_whenFaxNumberIsMissing() throws Exception {
        mockRequest.removeParameter("pharmaFax");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getContentAsString()).contains("fax-failure", "Valid fax number");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", SecurityInfoManager.WRITE, null);
        verifyNoInteractions(mockPrescriptionPdfComposer, mockPrescriptionFaxService);
    }

    @Test
    @DisplayName("should reject POST when demographic number is invalid")
    void shouldRejectPost_whenDemographicNumberIsInvalid() throws Exception {
        mockRequest.setParameter("demographic_no", "not-a-number");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getContentAsString()).contains("fax-failure", "Valid demographic number");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", SecurityInfoManager.WRITE, null);
        verifyNoInteractions(mockPrescriptionPdfComposer, mockPrescriptionFaxService);
    }

    @Test
    @DisplayName("should create fax job when POST is authorized")
    void shouldCreateFaxJob_whenPostAuthorized() throws Exception {
        ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
        pdfBytes.write("%PDF".getBytes());
        when(mockPrescriptionPdfComposer.compose(mockRequest, mockServletContext)).thenReturn(pdfBytes);
        when(mockPrescriptionFaxService.createFaxJob(mockLoggedInInfo, mockRequest, pdfBytes))
                .thenReturn(new PrescriptionFaxViewModel(true, "Main Pharmacy", "4165550199"));

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(mockResponse.getContentAsString()).contains("fax-success", "Main Pharmacy", "4165550199");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", SecurityInfoManager.WRITE, null);
        verify(mockPrescriptionPdfComposer).compose(mockRequest, mockServletContext);
        verify(mockPrescriptionFaxService).createFaxJob(mockLoggedInInfo, mockRequest, pdfBytes);
    }

    @Test
    @DisplayName("should show failure when clinic fax configuration is missing")
    void shouldShowFailure_whenClinicFaxConfigurationIsMissing() throws Exception {
        ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
        pdfBytes.write("%PDF".getBytes());
        when(mockPrescriptionPdfComposer.compose(mockRequest, mockServletContext)).thenReturn(pdfBytes);
        when(mockPrescriptionFaxService.createFaxJob(mockLoggedInInfo, mockRequest, pdfBytes))
                .thenReturn(new PrescriptionFaxViewModel(false, "Main Pharmacy", "4165550199"));

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(mockResponse.getContentAsString()).contains("fax-failure", "No matching clinic fax configuration");
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", SecurityInfoManager.WRITE, null);
        verify(mockPrescriptionPdfComposer).compose(mockRequest, mockServletContext);
        verify(mockPrescriptionFaxService).createFaxJob(mockLoggedInInfo, mockRequest, pdfBytes);
    }
}
