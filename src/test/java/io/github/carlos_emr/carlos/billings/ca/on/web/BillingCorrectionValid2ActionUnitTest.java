/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionReviewPreparationService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingCorrectionValid2Action")
@Tag("unit")
@Tag("billing")
class BillingCorrectionValid2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private BillingCorrectionReviewPreparationService preparationService;
    @Mock private LoggedInInfo loggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldForwardReviewModelWithoutLegacySessionCarrierBeans() throws Exception {
        request.setParameter("xml_billing_no", "42");
        request.setParameter("servicecode0", "A001A");
        request.setParameter("billingunit0", "1");

        BillingCorrectionReviewViewModel model = BillingCorrectionReviewViewModel.builder()
                .dataLoaded(true)
                .billingNo("42")
                .build();
        when(preparationService.prepareReview(any())).thenReturn(model);

        String result = new BillingCorrectionValid2Action(securityInfoManager, preparationService).execute();

        assertThat(result).isEqualTo("review");
        assertThat(request.getAttribute("reviewModel")).isSameAs(model);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(request.getSession().getAttribute("billing")).isNull();
        assertThat(request.getSession().getAttribute("billingDataBean")).isNull();
        assertThat(request.getSession().getAttribute("billingPatientDataBean")).isNull();
        verify(preparationService).prepareReview(any());
    }
}
