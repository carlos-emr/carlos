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
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceRequestTokenDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.decisionSupport.service.DSService;
import io.github.carlos_emr.carlos.managers.AppManager;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the forced-password-reset security contract.
 *
 * <p>These tests intentionally exercise direct action POST behavior rather than relying on the
 * browser-side password policy JavaScript. The server must reject weak direct POSTs, preserve the
 * credential token for retryable validation errors, consume the token before terminal persistence,
 * and leave the account in a usable state after a valid reset.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("Login2Action forced password reset")
class Login2ActionForcedPasswordResetUnitTest extends CarlosUnitTestBase {

    private static final String USERNAME = "carlosdoc";
    private static final String ENCODED_OLD_PASSWORD = "encoded-old-password";
    private static final String OLD_PASSWORD = "OldPass1!";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private AutoCloseable mockitoCloseable;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Mock private ProviderManager providerManager;
    @Mock private AppManager appManager;
    @Mock private FacilityDao facilityDao;
    @Mock private ProviderPreferenceDao providerPreferenceDao;
    @Mock private ProviderDao providerDao;
    @Mock private UserPropertyDAO userPropertyDao;
    @Mock private DSService dsService;
    @Mock private ServiceRequestTokenDao serviceRequestTokenDao;
    @Mock private SecurityManager securityManager;
    @Mock private SecurityDao securityDao;
    @Mock private UserSessionManager userSessionManager;
    @Mock private MfaManager mfaManager;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(ProviderManager.class, providerManager);
        registerMock(AppManager.class, appManager);
        registerMock(FacilityDao.class, facilityDao);
        registerMock(ProviderPreferenceDao.class, providerPreferenceDao);
        registerMock(ProviderDao.class, providerDao);
        registerMock(UserPropertyDAO.class, userPropertyDao);
        registerMock(DSService.class, dsService);
        registerMock(ServiceRequestTokenDao.class, serviceRequestTokenDao);
        registerMock(SecurityManager.class, securityManager);
        registerMock(SecurityDao.class, securityDao);
        registerMock(UserSessionManager.class, userSessionManager);
        registerMock(MfaManager.class, mfaManager);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setContextPath("/carlos");
        request.addHeader("user-agent", "Mozilla/5.0");
        request.addHeader("Accept", "text/html");
        request.addParameter("forcedpasswordchange", "true");
        request.getSession(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    @Test
    @DisplayName("should redirect to login failure when credential token is missing")
    void shouldRedirectToLoginFailure_whenCredentialTokenMissing() throws Exception {
        Login2Action action = newAction(OLD_PASSWORD, "ValidPass1!", "ValidPass1!");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should keep credential token when old password validation fails")
    void shouldKeepCredentialToken_whenOldPasswordValidationFails() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword("wrong-old-password", ENCODED_OLD_PASSWORD)).thenReturn(false);
        Login2Action action = newAction("wrong-old-password", "ValidPass1!", "ValidPass1!");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
        assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR)).isNotNull();
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNotNull();
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
        LoginCredentialCache.getInstance().invalidate(token);
    }

    @Test
    @DisplayName("should keep credential token when password confirmation mismatches")
    void shouldKeepCredentialToken_whenPasswordConfirmationMismatches() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        Login2Action action = newAction(OLD_PASSWORD, "ValidPass1!", "OtherPass1!");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
        assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR))
                .isEqualTo(Login2Action.message(request,
                        "provider.providerchangepassword.errorConfirmPasswordMismatch"));
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNotNull();
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
        LoginCredentialCache.getInstance().invalidate(token);
    }

    @Test
    @DisplayName("should keep credential token when new password matches old password")
    void shouldKeepCredentialToken_whenNewPasswordMatchesOldPassword() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        Login2Action action = newAction(OLD_PASSWORD, OLD_PASSWORD, OLD_PASSWORD);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
        assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR))
                .isEqualTo(Login2Action.message(request,
                        "provider.providerchangepassword.errorNewPasswordSameAsOld"));
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNotNull();
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
        LoginCredentialCache.getInstance().invalidate(token);
    }

    @Test
    @DisplayName("should reject weak direct POST password server side and keep retry token")
    void shouldRejectWeakDirectPostPasswordServerSide_andKeepRetryToken() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        Login2Action action = newAction(OLD_PASSWORD, "weakpass", "weakpass");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
        assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR))
                .asString()
                .contains(Login2Action.message(request, "password.policy.violation.msgPasswordStrengthError"));
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNotNull();
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
        LoginCredentialCache.getInstance().invalidate(token);
    }

    @Test
    @DisplayName("should allow weak password when password requirements are ignored")
    void shouldAllowWeakPassword_whenPasswordRequirementsIgnored() throws Exception {
        String originalIgnoreSetting = CarlosProperties.getInstance().getProperty("IGNORE_PASSWORD_REQUIREMENTS");
        CarlosProperties.getInstance().setProperty("IGNORE_PASSWORD_REQUIREMENTS", "true");
        String token = cacheCredentials();
        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.emptyList());
        Login2Action action = newAction(OLD_PASSWORD, "weakpass", "weakpass");

        try {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/loginfailed");
            assertThat(LoginCredentialCache.getInstance().peek(token)).isNull();
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
            verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
        } finally {
            if (originalIgnoreSetting == null) {
                CarlosProperties.getInstance().remove("IGNORE_PASSWORD_REQUIREMENTS");
            } else {
                CarlosProperties.getInstance().setProperty("IGNORE_PASSWORD_REQUIREMENTS", originalIgnoreSetting);
            }
        }
    }

    @Test
    @DisplayName("should fall back to English login message when locale bundle is missing")
    void shouldFallBackToEnglishLoginMessage_whenLocaleBundleMissing() {
        request.addPreferredLocale(java.util.Locale.GERMAN);

        assertThat(Login2Action.message(request, "login.errorUnableToProcess"))
                .isEqualTo("Unable to process login at this time. Please try again.");
    }

    @Test
    @DisplayName("should consume credential token once validation passes before persistence")
    void shouldConsumeCredentialTokenOnceValidationPasses_beforePersistence() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.emptyList());
        Login2Action action = newAction(OLD_PASSWORD, "ValidPass1!", "ValidPass1!");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNull();
        assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should persist valid forced reset password and clear reset token")
    void shouldPersistValidForcedResetPassword_andClearResetToken() throws Exception {
        String token = cacheCredentials();
        String newPassword = "ValidPass1!";
        String encodedNewPassword = "encoded-new-password";
        Security security = forcedResetSecurity();
        Provider provider = activeProvider();
        ArgumentCaptor<Security> securityCaptor = ArgumentCaptor.forClass(Security.class);

        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        when(securityManager.encodePassword(newPassword)).thenReturn(encodedNewPassword);
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(provider);
        when(providerManager.getProvider("999998")).thenReturn(provider);
        when(providerPreferenceDao.find("999998")).thenReturn(new ProviderPreference());
        when(providerDao.getFacilityIds("999998")).thenReturn(Collections.emptyList());
        when(facilityDao.findAll(true)).thenReturn(Collections.emptyList());

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, newPassword, "2026", request.getRemoteAddr()))
                            .thenReturn(new String[]{"999998", "Test", "Provider", "", "doctor", "0"});
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            Login2Action action = newAction(OLD_PASSWORD, newPassword, newPassword);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            assertThat(LoginCredentialCache.getInstance().peek(token)).isNull();
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
        }

        verify(securityDao).saveEntity(securityCaptor.capture());
        Security persistedSecurity = securityCaptor.getValue();
        assertThat(persistedSecurity).isSameAs(security);
        assertThat(persistedSecurity.getPassword()).isEqualTo(encodedNewPassword);
        assertThat(persistedSecurity.isForcePasswordReset()).isFalse();
        assertThat(persistedSecurity.getPasswordUpdateDate()).isNotNull();
    }

    @Test
    @DisplayName("should count password character groups using configured character sets")
    void shouldCountPasswordCharacterGroups_withConfiguredCharacterSets() {
        assertThat(Login2Action.countPasswordGroups("lowercase", "abcdefghijklmnopqrstuvwxyz",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "0123456789", "!@#")).isOne();
        assertThat(Login2Action.countPasswordGroups("Lower123", "abcdefghijklmnopqrstuvwxyz",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "0123456789", "!@#")).isEqualTo(3);
        assertThat(Login2Action.countPasswordGroups("Lower123!", "abcdefghijklmnopqrstuvwxyz",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "0123456789", "!@#")).isEqualTo(4);
    }

    private Login2Action newAction(String oldPassword, String newPassword, String confirmPassword) {
        Login2Action action = new Login2Action();
        action.setOldPassword(oldPassword);
        action.setNewPassword(newPassword);
        action.setConfirmPassword(confirmPassword);
        return action;
    }

    private String cacheCredentials() {
        String token = LoginCredentialCache.getInstance().store(
                new LoginCredentialCache.LoginCredentials(USERNAME, ENCODED_OLD_PASSWORD, "2026", null));
        request.getSession().setAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR, token);
        return token;
    }

    private Security forcedResetSecurity() {
        Security security = new Security();
        security.setSecurityNo(12345);
        security.setUserName(USERNAME);
        security.setProviderNo("999998");
        security.setPassword(ENCODED_OLD_PASSWORD);
        security.setForcePasswordReset(Boolean.TRUE);
        return security;
    }

    private Provider activeProvider() {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setFirstName("Test");
        provider.setLastName("Provider");
        provider.setStatus("1");
        return provider;
    }
}
