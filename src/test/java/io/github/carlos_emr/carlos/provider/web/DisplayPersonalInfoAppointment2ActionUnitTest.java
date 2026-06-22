/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the session-toggle action behind {@code Provider/showPersonal}.
 *
 * <p>The action has no Struts result mappings: it flips the {@code showPersonal} session attribute
 * and must terminate processing with {@link ActionSupport#NONE}. Returning a bare {@code null}
 * (the regression fixed on this branch) lets Struts try to resolve a named result and render HTML
 * into what callers treat as a no-body toggle response. These tests pin the {@code NONE} contract
 * and the toggle semantics.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("DisplayPersonalInfoAppointment2Action")
class DisplayPersonalInfoAppointment2ActionUnitTest {

    /**
     * Pins the regression directly on {@code toggle()}: a first-time toggle (no prior session
     * attribute) must default {@code showPersonal} to {@code true} and return {@link
     * ActionSupport#NONE} so Struts renders no result body.
     */
    @Test
    @DisplayName("should return NONE and set showPersonal true when attribute is absent")
    void shouldReturnNoneAndSetTrue_whenAttributeAbsent() {
        withMockedContext(true, request -> {
            DisplayPersonalInfoAppointment2Action action = new DisplayPersonalInfoAppointment2Action();

            String result = action.toggle();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(request.getSession().getAttribute("showPersonal")).isEqualTo(Boolean.TRUE);
        });
    }

    /**
     * Verifies {@code toggle()} flips an existing {@code true} flag to {@code false} and still
     * returns {@link ActionSupport#NONE}.
     */
    @Test
    @DisplayName("should return NONE and flip showPersonal to false when previously true")
    void shouldReturnNoneAndSetFalse_whenPreviouslyTrue() {
        withMockedContext(true, request -> {
            request.getSession().setAttribute("showPersonal", Boolean.TRUE);
            DisplayPersonalInfoAppointment2Action action = new DisplayPersonalInfoAppointment2Action();

            String result = action.toggle();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(request.getSession().getAttribute("showPersonal")).isEqualTo(Boolean.FALSE);
        });
    }

    /**
     * Verifies {@code toggle()} flips an existing {@code false} flag back to {@code true} and still
     * returns {@link ActionSupport#NONE}.
     */
    @Test
    @DisplayName("should return NONE and flip showPersonal to true when previously false")
    void shouldReturnNoneAndSetTrue_whenPreviouslyFalse() {
        withMockedContext(true, request -> {
            request.getSession().setAttribute("showPersonal", Boolean.FALSE);
            DisplayPersonalInfoAppointment2Action action = new DisplayPersonalInfoAppointment2Action();

            String result = action.toggle();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(request.getSession().getAttribute("showPersonal")).isEqualTo(Boolean.TRUE);
        });
    }

    /**
     * Confirms the privilege gate in {@code execute()} rejects callers lacking {@code _demographic}
     * read rights before any toggle side-effect runs. This case must drive {@code execute()} (not
     * {@code toggle()}) because the security check lives in {@code execute()}.
     */
    @Test
    @DisplayName("should throw SecurityException when _demographic read privilege is missing")
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        withMockedContext(false, request -> {
            DisplayPersonalInfoAppointment2Action action = new DisplayPersonalInfoAppointment2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_demographic)");
            assertThat(request.getSession().getAttribute("showPersonal")).isNull();
        });
    }

    /**
     * Drives {@code body} with {@link ServletActionContext}, {@link SpringUtils}, and
     * {@link LoggedInInfo} statically mocked so the action's field initializers resolve against the
     * test doubles. The action is constructed inside {@code body} so it picks up these statics.
     *
     * @param hasPrivilege whether {@code _demographic}/{@code r} privilege is granted
     * @param body         test logic receiving the backing request
     */
    private void withMockedContext(boolean hasPrivilege, RequestConsumer body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/Provider/showPersonal");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<ServletActionContext> servletContext = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfoStatic = mockStatic(LoggedInInfo.class)) {

            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);
            springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class))
                    .thenReturn(securityInfoManager);
            loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_demographic"), eq("r"), isNull()))
                    .thenReturn(hasPrivilege);

            body.accept(request);
        }
    }

    @FunctionalInterface
    private interface RequestConsumer {
        void accept(MockHttpServletRequest request);
    }
}
