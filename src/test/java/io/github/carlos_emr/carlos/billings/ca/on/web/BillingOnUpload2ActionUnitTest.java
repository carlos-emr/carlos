/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the view-only contract of {@link BillingOnUpload2Action}. The action
 * only forwards to {@code billingONUpload.jsp}, which renders a file-picker
 * form whose {@code onSubmit} JS reroutes the multipart POST to the actual
 * upload endpoints. A POST gate here would 405 the GET that renders the form
 * (regression caught in PR #1967 review).
 */
@DisplayName("BillingOnUpload2Action")
@Tag("unit")
@Tag("billing")
class BillingOnUpload2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldRenderForm_onGetRequest() {
        request.setMethod("GET");

        String result = new BillingOnUpload2Action(securityInfoManager).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldRenderForm_onPostRequest() {
        request.setMethod("POST");

        String result = new BillingOnUpload2Action(securityInfoManager).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new BillingOnUpload2Action(securityInfoManager).execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.billing");
    }
}
