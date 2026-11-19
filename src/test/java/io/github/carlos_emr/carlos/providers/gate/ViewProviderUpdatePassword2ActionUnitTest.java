/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */
package io.github.carlos_emr.carlos.providers.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The Preferences "change password" route must apply the configured password policy itself.
 *
 * <p>The form checks the policy in the browser, but that is feedback only: a POST sent without
 * the form reached the page that stores the password, and a password the policy forbids was
 * saved. The gate now refuses such a request before that page runs, so nothing is stored, and
 * sends the user back to the form with the message the form shows for the broken rule.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("security")
@DisplayName("Preferences password change policy gate")
class ViewProviderUpdatePassword2ActionUnitTest extends CarlosUnitTestBase {

    // Synthetic candidates, never real credentials.
    private static final String COMPLIANT = new String(new char[] {'F', 'a', 'k', 'e', '-', 'P', 'w', '1', 'x'});
    private static final String TOO_SHORT = new String(new char[] {'A', 'b', '1', '!'});
    private static final String TWO_GROUPS = new String(new char[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', '1'});

    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private final CarlosProperties properties = mock(CarlosProperties.class);
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/carlos/provider/ViewProviderUpdatePassword");
        request.setContextPath("/carlos");
        request.addPreferredLocale(Locale.ENGLISH);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "999998");
        request.setSession(session);
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenAnswer(invocation -> request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenAnswer(invocation -> response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)).thenReturn(true);
        // The shipped defaults: eight characters from three of the four groups, four-digit PIN.
        when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(properties.getProperty("password_min_length")).thenReturn("8");
        when(properties.getProperty("password_min_groups")).thenReturn("3");
        when(properties.getProperty("password_pin_min_length")).thenReturn("4");
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should send a compliant password on to the page that stores it")
    void shouldForwardToUpdatePage_whenPasswordMeetsPolicy() throws Exception {
        submitPassword(COMPLIANT);

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("should refuse a password shorter than the configured minimum")
    void shouldRefuseBeforeUpdatePage_whenPasswordIsTooShort() throws Exception {
        submitPassword(TOO_SHORT);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(refusalMessage()).isEqualTo("Password is too short, minimum length is 8 symbols.");
    }

    @Test
    @DisplayName("should refuse a password that uses too few character groups")
    void shouldRefuseBeforeUpdatePage_whenPasswordUsesTooFewGroups() throws Exception {
        submitPassword(TWO_GROUPS);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(refusalMessage()).isEqualTo("Password must contain at least 3"
                + " of the following: capital chars, lower chars, digits, special chars.");
    }

    @Test
    @DisplayName("should apply the configured length, not the default")
    void shouldApplyConfiguredLength_whenClinicRaisesMinimum() throws Exception {
        when(properties.getProperty("password_min_length")).thenReturn("12");
        submitPassword(COMPLIANT);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(refusalMessage()).isEqualTo("Password is too short, minimum length is 12 symbols.");
    }

    @Test
    @DisplayName("should refuse a weak password even when the old password and confirmation are missing")
    void shouldRefuse_whenWeakPasswordIsSubmittedAlone() throws Exception {
        request.setParameter("mypassword", TOO_SHORT);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).startsWith("/carlos/provider/ViewProviderChangePassword?errormsg=");
    }

    @Test
    @DisplayName("should accept a weak password when the clinic disabled the password requirements")
    void shouldForwardToUpdatePage_whenRequirementsAreIgnored() throws Exception {
        when(properties.getProperty("IGNORE_PASSWORD_REQUIREMENTS")).thenReturn("true");
        submitPassword(TOO_SHORT);

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should not judge an empty new password, which the page treats as no password change")
    void shouldForwardToUpdatePage_whenNoNewPasswordIsSubmitted() throws Exception {
        request.setParameter("oldpassword", "");
        request.setParameter("mypassword", "");
        request.setParameter("confirmpassword", "");
        submitPin("2026", "4821");

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should refuse a new PIN shorter than the configured minimum")
    void shouldRefuseBeforeUpdatePage_whenPinIsTooShort() throws Exception {
        submitPin("2026", "12");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(refusalMessage()).isEqualTo("PIN is too short, minimum length is 4 digits.");
    }

    @Test
    @DisplayName("should refuse the whole request when a compliant PIN accompanies a weak password")
    void shouldRefuseWholeRequest_whenPinIsValidButPasswordIsWeak() throws Exception {
        submitPassword(TOO_SHORT);
        submitPin("2026", "4821");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(refusalMessage()).startsWith("Password is too short");
    }

    @Test
    @DisplayName("should say nothing about the submitted password in the refusal")
    void shouldKeepPasswordOutOfRefusal_whenPolicyIsViolated() throws Exception {
        submitPassword(TOO_SHORT);

        execute();

        assertThat(URLDecoder.decode(response.getRedirectedUrl(), StandardCharsets.UTF_8)).doesNotContain(TOO_SHORT);
        assertThat(response.getContentAsString()).doesNotContain(TOO_SHORT).doesNotContain("self.close()");
    }

    @Test
    @DisplayName("should answer GET with 405 before the policy is consulted")
    void shouldRejectGet_beforePolicyIsConsulted() throws Exception {
        request.setMethod("GET");
        submitPassword(TOO_SHORT);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should keep refusing a caller without the gate privilege")
    void shouldThrowSecurityException_whenPrivilegeIsMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)).thenReturn(false);
        submitPassword(COMPLIANT);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    private String execute() throws Exception {
        return new ViewProviderUpdatePassword2Action(securityInfoManager, () -> properties).execute();
    }

    private void submitPassword(String candidate) {
        request.setParameter("oldpassword", "FAKE-current-1");
        request.setParameter("mypassword", candidate);
        request.setParameter("confirmpassword", candidate);
    }

    private void submitPin(String current, String candidate) {
        request.setParameter("pin", current);
        request.setParameter("newpin", candidate);
        request.setParameter("confirmpin", candidate);
    }

    /** The message the form will show, read back from the redirect to the change-password form. */
    private String refusalMessage() {
        String location = response.getRedirectedUrl();
        assertThat(location).startsWith("/carlos/provider/ViewProviderChangePassword?errormsg=");
        return URLDecoder.decode(location.substring(location.indexOf("errormsg=") + "errormsg=".length()),
                StandardCharsets.UTF_8);
    }
}
