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
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.commn.model.ServiceRequestToken;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.decisionSupport.service.DSService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.AppManager;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.util.AlertTimer;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLDecoder;
import java.security.InvalidKeyException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the forced-password-reset security contract.
 *
 * <p>These tests intentionally exercise direct action POST behavior rather than relying on the
 * browser-side password policy JavaScript. The server must reject weak direct POSTs, preserve the
 * credential token for retryable validation errors, consume the token before terminal persistence,
 * and leave the account in a usable state after a valid reset.</p>
 *
 * <p>The same action also owns the pending-MFA handoff, so this class pins the session-state
 * invariants that keep a password/PIN-authenticated user from becoming fully authenticated until
 * OTP validation succeeds.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("Login2Action forced password reset")
@Isolated
class Login2ActionForcedPasswordResetUnitTest extends CarlosUnitTestBase {

    private static final String USERNAME = "carlosdoc";
    private static final String ENCODED_OLD_PASSWORD = "encoded-old-password";
    private static final String OLD_PASSWORD = fixturePassword("old");
    private static final String VALID_PASSWORD = fixturePassword("valid");
    private static final String OTHER_PASSWORD = fixturePassword("other");
    private static final String WRONG_OLD_PASSWORD = String.join("-", "wrong", "old", "password");
    private static final String PENDING_MFA_PROVIDER_NO_ATTR = PendingMfaChallenges.PROVIDER_NO_ATTR;
    private static final String PENDING_MFA_TOKEN_ATTR = PendingMfaChallenges.TOKEN_ATTR;
    private static final String PENDING_MFA_ATTEMPTS_ATTR = PendingMfaChallenges.ATTEMPTS_ATTR;
    private static final String[] STR_AUTH = {"999998", "Test", "Provider", "", "doctor", "0"};

    private static String fixturePassword(String label) {
        return String.join("", "Unit", label, "2026", "!");
    }

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private AutoCloseable mockitoCloseable;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final List<String> pendingMfaTokens = new ArrayList<>();

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
        request.setRequestURI("/carlos/forcepasswordresetSubmit");
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
        if (request != null) {
            try {
                if (request.getSession(false) != null) {
                    Object tokenAttr = request.getSession(false).getAttribute(PENDING_MFA_TOKEN_ATTR);
                    if (tokenAttr instanceof String) {
                        PendingMfaChallengeCache.getInstance().invalidate((String) tokenAttr);
                    }
                }
            } catch (IllegalStateException ignored) {
                // Session was invalidated by the action under test.
            }
        }
        pendingMfaTokens.forEach(token -> PendingMfaChallengeCache.getInstance().invalidate(token));
        pendingMfaTokens.clear();
        // Used-code tracking is a process-wide singleton; reset it so deterministic test codes do
        // not leak between tests and trip replay protection.
        MfaUsedCodeCache.getInstance().invalidateAll();
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    @Test
    @DisplayName("should reject GET login before authentication")
    void shouldRejectGetLogin_beforeAuthentication() throws Exception {
        request.setMethod("GET");
        Login2Action action = newAction(null, null, null);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should stage forced reset when mandatory reset property is enabled")
    void shouldStageForcedReset_whenMandatoryResetPropertyEnabled() throws Exception {
        String originalMandatoryReset = CarlosProperties.getInstance().getProperty("mandatory_password_reset");
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        Provider provider = activeProvider();

        request.setParameter("forcedpasswordchange", "false");
        CarlosProperties.getInstance().setProperty("mandatory_password_reset", "true");
        when(securityManager.encodePassword(password)).thenReturn(ENCODED_OLD_PASSWORD);
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(provider);

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(new String[]{"999998", "Test", "Provider", "", "doctor", "0"});
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR))
                    .isInstanceOf(String.class);
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
        } finally {
            if (originalMandatoryReset == null) {
                CarlosProperties.getInstance().remove("mandatory_password_reset");
            } else {
                CarlosProperties.getInstance().setProperty("mandatory_password_reset", originalMandatoryReset);
            }
        }
    }

    @Test
    @DisplayName("should redirect with staging message when forced reset setup fails")
    void shouldRedirectWithStagingMessage_whenForcedResetSetupFails() throws Exception {
        String originalMandatoryReset = CarlosProperties.getInstance().getProperty("mandatory_password_reset");
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        Provider provider = activeProvider();

        request.setParameter("forcedpasswordchange", "false");
        CarlosProperties.getInstance().setProperty("mandatory_password_reset", "true");
        when(securityManager.encodePassword(password)).thenThrow(new IllegalStateException("encoder unavailable"));
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(provider);

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(new String[]{"999998", "Test", "Provider", "", "doctor", "0"});
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/loginfailed");
            assertThat(decodedRedirect()).contains(Login2Action.message(request, "login.errorResetStaging"));
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
        } finally {
            if (originalMandatoryReset == null) {
                CarlosProperties.getInstance().remove("mandatory_password_reset");
            } else {
                CarlosProperties.getInstance().setProperty("mandatory_password_reset", originalMandatoryReset);
            }
        }
    }

    @Test
    @DisplayName("should reject forced reset payload on login route")
    void shouldRejectForcedResetPayload_whenPostedToLoginRoute() throws Exception {
        cacheCredentials();
        request.setRequestURI("/carlos/login");
        Login2Action action = newAction(OLD_PASSWORD, VALID_PASSWORD, VALID_PASSWORD);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertThat(decodedRedirect()).contains(Login2Action.message(request, "login.errorUnableToProcess"));
        verify(securityManager, never()).matchesPassword(anyString(), anyString());
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
        logActionMock.verify(() -> LogAction.addLog("", LogConst.LOGIN, LogConst.CON_LOGIN,
                "forced_password_reset_wrong_route", request.getRemoteAddr()));
    }

    @Test
    @DisplayName("should redirect to login failure when credential token is missing")
    void shouldRedirectToLoginFailure_whenCredentialTokenMissing() throws Exception {
        Login2Action action = newAction(OLD_PASSWORD, VALID_PASSWORD, VALID_PASSWORD);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertThat(decodedRedirect()).contains(Login2Action.message(request,
                "provider.providerchangepassword.errorSessionExpired"));
        assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should keep credential token when old password validation fails")
    void shouldKeepCredentialToken_whenOldPasswordValidationFails() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword(WRONG_OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(false);
        Login2Action action = newAction(WRONG_OLD_PASSWORD, VALID_PASSWORD, VALID_PASSWORD);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
        assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR)).isNotNull();
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNotNull();
        logActionMock.verify(() -> LogAction.addLog(USERNAME, "login", "forced_password_reset_failed",
                "old_password_mismatch"));
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
        LoginCredentialCache.getInstance().invalidate(token);
    }

    @Test
    @DisplayName("should expire credential token when old password retries are exhausted")
    void shouldExpireCredentialToken_whenOldPasswordRetriesAreExhausted() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword(WRONG_OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(false);

        String result = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            response = new MockHttpServletResponse();
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
            Login2Action action = newAction(WRONG_OLD_PASSWORD, VALID_PASSWORD, VALID_PASSWORD);

            result = action.execute();
        }

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNull();
        assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
        logActionMock.verify(() -> LogAction.addLog(USERNAME, "login", "forced_password_reset_failed",
                "old_password_mismatch_limit"));
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should keep credential token when password confirmation mismatches")
    void shouldKeepCredentialToken_whenPasswordConfirmationMismatches() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        Login2Action action = newAction(OLD_PASSWORD, VALID_PASSWORD, OTHER_PASSWORD);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
        assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR))
                .isEqualTo(Login2Action.message(request,
                        "provider.providerchangepassword.errorConfirmPasswordMismatch"));
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNotNull();
        logActionMock.verify(() -> LogAction.addLog(USERNAME, "login", "forced_password_reset_failed",
                "confirm_password_mismatch"));
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
        logActionMock.verify(() -> LogAction.addLog(USERNAME, "login", "forced_password_reset_failed",
                "new_password_same_as_old"));
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
        logActionMock.verify(() -> LogAction.addLog(USERNAME, "login", "forced_password_reset_failed",
                "password_policy_min_groups"));
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
        LoginCredentialCache.getInstance().invalidate(token);
    }

    @Test
    @DisplayName("should reject too-short direct POST password server side and keep retry token")
    void shouldRejectTooShortDirectPostPasswordServerSide_andKeepRetryToken() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        Login2Action action = newAction(OLD_PASSWORD, "Aa1!", "Aa1!");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
        assertThat(request.getSession(false).getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR))
                .asString()
                .contains(Login2Action.message(request, "password.policy.violation.msgPasswordLengthError"));
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNotNull();
        logActionMock.verify(() -> LogAction.addLog(USERNAME, "login", "forced_password_reset_failed",
                "password_policy_min_length"));
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
    @DisplayName("should return localized login message when locale bundle contains key")
    void shouldReturnLocalizedLoginMessage_whenLocaleBundleContainsKey() {
        request.addPreferredLocale(java.util.Locale.FRENCH);

        assertThat(Login2Action.message(request, "login.errorInvalidCredentials"))
                .isEqualTo("Identifiants invalides.");
    }

    @Test
    @DisplayName("should consume credential token once validation passes before persistence")
    void shouldConsumeCredentialTokenOnceValidationPasses_beforePersistence() throws Exception {
        String token = cacheCredentials();
        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.emptyList());
        Login2Action action = newAction(OLD_PASSWORD, VALID_PASSWORD, VALID_PASSWORD);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertThat(LoginCredentialCache.getInstance().peek(token)).isNull();
        assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
        verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should consume token and show persistence message when database save fails")
    void shouldConsumeTokenAndShowPersistenceMessage_whenDatabaseSaveFails() throws Exception {
        String token = cacheCredentials();
        String newPassword = VALID_PASSWORD;
        Security security = forcedResetSecurity();

        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        when(securityManager.encodePassword(newPassword)).thenReturn("encoded-new-password");
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        doThrow(new RuntimeException("database unavailable"))
                .when(securityDao).saveEntity(org.mockito.ArgumentMatchers.any(Security.class));
        Login2Action action = newAction(OLD_PASSWORD, newPassword, newPassword);

        try (LogCapture capture = LogCapture.forLogger(Login2Action.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/loginfailed");
            assertThat(decodedRedirect()).contains(Login2Action.message(request, "login.errorResetPersistence"));
            assertThat(LoginCredentialCache.getInstance().peek(token)).isNull();
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("Forced password reset failed before password persistence completed");
            });
            logActionMock.verify(() -> LogAction.addLog(USERNAME, "login",
                    "forced_password_reset_failed", "persistence_failure"));
        }
    }

    @Test
    @DisplayName("should not persist when terminal credential consume returns null")
    void shouldNotPersist_whenTerminalCredentialConsumeReturnsNull() throws Exception {
        String token = "terminal-replay-token";
        LoginCredentialCache.LoginCredentials credentials =
                new LoginCredentialCache.LoginCredentials(USERNAME, ENCODED_OLD_PASSWORD, "2026", null);
        LoginCredentialCache credentialCache = mock(LoginCredentialCache.class);
        request.getSession().setAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR, token);
        when(credentialCache.peek(token)).thenReturn(credentials);
        when(credentialCache.consume(token)).thenReturn(null);
        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);

        try (MockedStatic<LoginCredentialCache> credentialCacheMock = mockStatic(LoginCredentialCache.class)) {
            credentialCacheMock.when(LoginCredentialCache::getInstance).thenReturn(credentialCache);
            Login2Action action = newAction(OLD_PASSWORD, VALID_PASSWORD, VALID_PASSWORD);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/loginfailed");
            verify(credentialCache).consume(token);
            verify(credentialCache).invalidate(token);
            verify(securityDao, never()).saveEntity(org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    @DisplayName("should return error when MFA registration setup fails")
    void shouldReturnError_whenMfaRegistrationSetupFails() throws Exception {
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        security.setUsingMfa(true);
        Provider provider = activeProvider();

        request.setParameter("forcedpasswordchange", "false");
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(provider);
        when(mfaManager.isMfaRegistrationRequired(security.getId()))
                .thenThrow(new RuntimeException("qr setup failed"));

        try (MockedStatic<MfaManager> mfaManagerStatic = mockStatic(MfaManager.class);
             LogCapture capture = LogCapture.forLogger(Login2Action.class);
             MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(new String[]{"999998", "Test", "Provider", "", "doctor", "0"});
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            mfaManagerStatic.when(MfaManager::isOscarMfaEnabled).thenReturn(true);
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo("error");
            assertThat(request.getAttribute("errormsg")).isNotNull();
            assertThat(request.getAttribute("mfaRegistrationRequired")).isNull();
            assertThat(request.getAttribute("qrData")).isNull();
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("Unable to prepare MFA registration");
            });
        }
    }

    @Test
    @DisplayName("should return error when MFA registration setup is unauthorized")
    void shouldReturnError_whenMfaRegistrationSetupUnauthorized() throws Exception {
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        security.setUsingMfa(true);

        request.setParameter("forcedpasswordchange", "false");
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(activeProvider());
        when(mfaManager.isMfaRegistrationRequired(security.getId()))
                .thenThrow(new SecurityException("missing required sec object (_security)"));

        try (MockedStatic<MfaManager> mfaManagerStatic = mockStatic(MfaManager.class);
             MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(STR_AUTH);
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            mfaManagerStatic.when(MfaManager::isOscarMfaEnabled).thenReturn(true);
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo("error");
            assertThat(request.getSession(false)).isNull();
            assertThat(request.getAttribute("errormsg")).isNotNull();
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
            logActionMock.verify(() -> LogAction.addLog("999998", "login", "mfa_failed",
                    "mfa_setup_failure", request.getRemoteAddr()));
        }
    }

    @Test
    @DisplayName("should return error when MFA registration setup has invalid state")
    void shouldReturnError_whenMfaRegistrationSetupHasInvalidState() throws Exception {
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        security.setUsingMfa(true);

        request.setParameter("forcedpasswordchange", "false");
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(activeProvider());
        when(mfaManager.isMfaRegistrationRequired(security.getId()))
                .thenThrow(new NullPointerException("mfa manager null state"));

        try (MockedStatic<MfaManager> mfaManagerStatic = mockStatic(MfaManager.class);
             MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(STR_AUTH);
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            mfaManagerStatic.when(MfaManager::isOscarMfaEnabled).thenReturn(true);
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo("error");
            assertThat(request.getSession(false)).isNull();
            assertThat(request.getAttribute("errormsg")).isNotNull();
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
            logActionMock.verify(() -> LogAction.addLog("999998", "login", "mfa_failed",
                    "mfa_setup_failure", request.getRemoteAddr()));
        }
    }

    @Test
    @DisplayName("should not preserve authenticated session when MFA challenge starts")
    void shouldNotPreserveAuthenticatedSession_whenMfaChallengeStarts() throws Exception {
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        security.setUsingMfa(true);
        MockHttpSession originalSession = (MockHttpSession) request.getSession();
        originalSession.setAttribute("user", "already-authenticated");

        request.setParameter("forcedpasswordchange", "false");
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(activeProvider());
        when(mfaManager.isMfaRegistrationRequired(security.getId())).thenReturn(false);

        try (MockedStatic<MfaManager> mfaManagerStatic = mockStatic(MfaManager.class);
             MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(new String[]{"999998", "Test", "Provider", "", "doctor", "0"});
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            mfaManagerStatic.when(MfaManager::isOscarMfaEnabled).thenReturn(true);
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo("mfaHandler");
            assertThat(originalSession.isInvalid()).isTrue();
            assertThat(request.getSession().getAttribute("user")).isNull();
            assertThat(request.getSession().getAttribute(Login2Action.PENDING_MFA_AUTH_ATTR)).isEqualTo(Boolean.TRUE);
            assertThat(request.getSession().getAttribute(PENDING_MFA_PROVIDER_NO_ATTR)).isEqualTo("999998");
            Object challengeToken = request.getSession().getAttribute(PENDING_MFA_TOKEN_ATTR);
            assertThat(challengeToken).isInstanceOf(String.class);
            pendingMfaTokens.add((String) challengeToken);
            assertThat(PendingMfaChallengeCache.getInstance().peek((String) challengeToken)).isNotNull();
            assertThat(request.getSession().getAttribute("pendingMfaSecurity")).isNull();
            assertThat(request.getSession().getAttribute("pendingMfaAuthResult")).isNull();
            assertThat(request.getSession().getAttribute("mfaSecret")).isNull();
            assertThat(request.getSession().getAttribute("pendingMfaLoginCheck")).isNull();
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("should redirect to login failure when MFA session is missing")
    void shouldRedirectToLoginFailure_whenMfaSessionIsMissing() throws Exception {
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
    }

    @Test
    @DisplayName("should clear stale partial pending MFA session when challenge is invalid")
    void shouldClearStalePartialPendingMfaSession_whenChallengeIsInvalid() throws Exception {
        request.getSession().setAttribute(Login2Action.PENDING_MFA_AUTH_ATTR, Boolean.TRUE);
        request.getSession().setAttribute("mfaSecret", "stale-secret");
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");

        try (LogCapture capture = LogCapture.forLogger(Login2Action.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/loginfailed");
            assertPendingMfaCleared();
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("without valid pending challenge");
            });
        }
    }

    @Test
    @DisplayName("should clear pending MFA session when security record is missing")
    void shouldClearPendingMfaSession_whenSecurityRecordIsMissing() throws Exception {
        stagePendingMfaChallenge(12345, "999998", STR_AUTH, null);
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertPendingMfaCleared();
    }

    @Test
    @DisplayName("should clear pending MFA session when challenge token is expired")
    void shouldClearPendingMfaSession_whenChallengeTokenIsExpired() throws Exception {
        request.getSession().setAttribute(Login2Action.PENDING_MFA_AUTH_ATTR, Boolean.TRUE);
        request.getSession().setAttribute(PENDING_MFA_PROVIDER_NO_ATTR, "999998");
        request.getSession().setAttribute(PENDING_MFA_TOKEN_ATTR, "missing-token");
        request.getSession().setAttribute(PENDING_MFA_ATTEMPTS_ATTR, 2);
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertPendingMfaCleared();
    }

    @Test
    @DisplayName("should clear pending MFA session when challenge token was replayed")
    void shouldClearPendingMfaSession_whenChallengeTokenWasReplayed() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        String token = currentPendingMfaToken();
        assertThat(PendingMfaChallengeCache.getInstance().consume(token)).isNotNull();
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).contains("/loginfailed");
        assertPendingMfaCleared();
        verify(mfaManager, never()).getMfaSecret(security);
    }

    @Test
    @DisplayName("should clear pending MFA session when security lookup fails")
    void shouldClearPendingMfaSession_whenSecurityLookupFails() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        when(securityDao.find(security.getSecurityNo())).thenThrow(new IllegalStateException("db unavailable"));
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(request.getAttribute("errormsg")).isNotNull();
        assertPendingMfaCleared();
    }

    @Test
    @DisplayName("should clear pending MFA session when secret lookup fails")
    void shouldClearPendingMfaSession_whenSecretLookupFails() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        when(mfaManager.getMfaSecret(security)).thenThrow(new IllegalStateException("secret lookup failed"));
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(request.getAttribute("errormsg")).isNotNull();
        assertPendingMfaCleared();
    }

    @Test
    @DisplayName("should ignore request registration flag when challenge has no registration secret")
    void shouldIgnoreRequestRegistrationFlag_whenChallengeHasNoRegistrationSecret() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("000000")) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");
            action.setMfaRegistrationFlow(true);

            String result = action.execute();

            assertThat(result).isEqualTo("mfaHandler");
            assertThat(request.getAttribute("mfaRegistrationRequired")).isNull();
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(request.getSession(false).getAttribute(PENDING_MFA_TOKEN_ATTR)).isInstanceOf(String.class);
            verify(mfaManager, never()).getQRCodeImageData(any(), anyString());
        }
    }

    @Test
    @DisplayName("should clear pending MFA session when TOTP key is invalid")
    void shouldClearPendingMfaSession_whenTotpKeyIsInvalid() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        String token = currentPendingMfaToken();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored =
                     mockConstruction(TimeBasedOneTimePasswordGenerator.class, (mock, context) -> {
                         when(mock.getAlgorithm()).thenReturn("HmacSHA1");
                         when(mock.getTimeStep()).thenReturn(java.time.Duration.ofSeconds(30));
                         when(mock.generateOneTimePasswordString(any(), any()))
                                 .thenThrow(new InvalidKeyException("bad key"));
                     })) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo("error");
            assertThat(request.getAttribute("errormsg")).isNotNull();
            assertPendingMfaCleared();
        }
    }

    @Test
    @DisplayName("should clear pending MFA session when stored secret is malformed")
    void shouldClearPendingMfaSession_whenStoredSecretIsMalformed() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        when(mfaManager.getMfaSecret(security)).thenReturn("");
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(request.getAttribute("errormsg")).isNotNull();
        assertPendingMfaCleared();
    }

    @Test
    @DisplayName("should keep pending MFA session when submitted code is invalid")
    void shouldKeepPendingMfaSession_whenSubmittedCodeIsInvalid() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        String token = currentPendingMfaToken();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored =
                     mockConstruction(TimeBasedOneTimePasswordGenerator.class, (mock, context) -> {
                         when(mock.getAlgorithm()).thenReturn("HmacSHA1");
                         when(mock.getTimeStep()).thenReturn(java.time.Duration.ofSeconds(30));
                         when(mock.generateOneTimePasswordString(any(), any())).thenReturn("654321");
                     })) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo("mfaHandler");
            assertThat(request.getSession(false).getAttribute(Login2Action.PENDING_MFA_AUTH_ATTR))
                    .isEqualTo(Boolean.TRUE);
            assertThat(request.getSession(false).getAttribute(PENDING_MFA_ATTEMPTS_ATTR)).isEqualTo(1);
            assertThat(PendingMfaChallengeCache.getInstance().peek(token)).isNotNull();
            logActionMock.verify(() -> LogAction.addLog("999998", "login", "mfa_failed", "mfa",
                    request.getRemoteAddr()));
        }
    }

    @Test
    @DisplayName("should clear pending MFA session when invalid code attempts are exhausted")
    void shouldClearPendingMfaSession_whenInvalidCodeAttemptsAreExhausted() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("654321")) {
            String result = null;
            for (int attempt = 0; attempt < 5; attempt++) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                result = action.execute();
            }

            assertThat(result).isEqualTo("error");
            assertPendingMfaCleared();
            logActionMock.verify(() -> LogAction.addLog("999998", "login", "mfa_locked", "mfa",
                    request.getRemoteAddr()));
        }
    }

    @Test
    @DisplayName("should regenerate QR data when MFA registration code is invalid")
    void shouldRegenerateQrData_whenMfaRegistrationCodeIsInvalid() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security, STR_AUTH, "JBSWY3DPEHPK3PXP");
        when(mfaManager.getQRCodeImageData(security.getId(), "JBSWY3DPEHPK3PXP")).thenReturn("qr-data");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored =
                     mockTotpReturning("654321")) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");
            action.setMfaRegistrationFlow(true);

            String result = action.execute();

            assertThat(result).isEqualTo("mfaHandler");
            assertThat(request.getAttribute("mfaRegistrationRequired")).isEqualTo(Boolean.TRUE);
            assertThat(request.getAttribute("qrData")).isEqualTo("qr-data");
            assertThat(request.getSession(false).getAttribute(Login2Action.PENDING_MFA_AUTH_ATTR))
                    .isEqualTo(Boolean.TRUE);
            logActionMock.verify(() -> LogAction.addLog("999998", "login", "mfa_failed", "mfa",
                    request.getRemoteAddr()));
        }
    }

    @Test
    @DisplayName("should complete login and clear pending MFA session when code is valid")
    void shouldCompleteLoginAndClearPendingMfaSession_whenCodeIsValid() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        String token = currentPendingMfaToken();
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored =
                     mockConstruction(TimeBasedOneTimePasswordGenerator.class, (mock, context) -> {
                         when(mock.getAlgorithm()).thenReturn("HmacSHA1");
                         when(mock.getTimeStep()).thenReturn(java.time.Duration.ofSeconds(30));
                         when(mock.generateOneTimePasswordString(any(), any())).thenReturn("123456");
                     })) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            assertThat(request.getSession(false).getAttribute(Login2Action.PENDING_MFA_AUTH_ATTR)).isNull();
            assertThat(request.getSession(false).getAttribute("user")).isEqualTo("999998");
            assertThat(PendingMfaChallengeCache.getInstance().peek(token)).isNull();
        }
    }

    @Test
    @DisplayName("should reject a TOTP code replayed for a second pending challenge within the validity window")
    void shouldRejectTotpCode_replayedForSecondPendingChallenge() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
            Login2Action firstAttempt = newAction(null, null, null);
            firstAttempt.setCode("123456");

            assertThat(firstAttempt.execute()).isEqualTo(ActionSupport.NONE);

            // A second, independent pending-MFA challenge replays the same observed code while it is
            // still within the TOTP validity window; it must be rejected as already used.
            stagePendingMfa(security);
            Login2Action replayAttempt = newAction(null, null, null);
            replayAttempt.setCode("123456");

            String result = replayAttempt.execute();

            assertThat(result).isEqualTo("mfaHandler");
            assertThat(request.getAttribute("mfaValidateCodeErr")).isEqualTo("Invalid MFA Code");
            logActionMock.verify(() -> LogAction.addLog("999998", "login", "mfa_failed", "mfa",
                    request.getRemoteAddr()));
        }
    }

    @Test
    @DisplayName("should redirect to facility selector when provider has multiple facilities")
    void shouldRedirectToFacilitySelector_whenProviderHasMultipleFacilities() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(providerDao.getFacilityIds("999998")).thenReturn(Arrays.asList(10, 11));
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/select_facility?nextPage=provider");
            assertThat(request.getSession(false).getAttribute(SessionConstants.PENDING_FACILITY_SELECTION))
                    .isEqualTo(Boolean.TRUE);
            assertPendingMfaCleared();
        }
    }

    @Test
    @DisplayName("should record facility assignment when provider has one facility")
    void shouldRecordFacilityAssignment_whenProviderHasOneFacility() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(providerDao.getFacilityIds("999998")).thenReturn(Collections.singletonList(10));
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            logActionMock.verify(() -> LogAction.addLog("999998", "log in", "login", "facilityId=10",
                    request.getRemoteAddr()));
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            assertThat(request.getSession(false).getAttribute(SessionConstants.PENDING_FACILITY_SELECTION)).isNull();
            assertPendingMfaCleared();
        }
    }

    @Test
    @DisplayName("should assign provider to first active facility when provider has none")
    void shouldAssignProviderToFirstActiveFacility_whenProviderHasNoFacilities() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        Facility fallbackFacility = new Facility();
        fallbackFacility.setId(42);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(facilityDao.findAll(true)).thenReturn(Collections.singletonList(fallbackFacility));
        when(facilityDao.find(42)).thenReturn(fallbackFacility);
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            verify(providerDao).addProviderToFacility("999998", 42);
            assertThat(request.getSession(false).getAttribute("currentFacility")).isSameAs(fallbackFacility);
            assertThat(request.getSession(false).getAttribute(SessionConstants.PENDING_FACILITY_SELECTION)).isNull();
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            logActionMock.verify(() -> LogAction.addLog("999998", "log in", "login", "facilityId=42",
                    request.getRemoteAddr()));
            assertPendingMfaCleared();
        }
    }

    @Test
    @DisplayName("should initialize CAISI session attributes when CAISI is enabled")
    void shouldInitializeCaisiSessionAttributes_whenCaisiIsEnabled() throws Exception {
        String originalCaisi = CarlosProperties.getInstance().getProperty("caisi");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        ProviderPreference preference = new ProviderPreference();
        preference.setDefaultCaisiPmm("enabled");
        preference.setDefaultNewOscarCme("enabled");
        preference.setNewTicklerWarningWindow("15");
        UserProperty ticklerProvider = new UserProperty();
        ticklerProvider.setValue("777777");
        stagePendingMfa(security);
        stubSuccessfulProviderLogin(preference);
        when(userPropertyDao.getProp("999998", UserProperty.PROVIDER_FOR_TICKLER_WARNING))
                .thenReturn(ticklerProvider);
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("caisi", "yes");
            try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo("caisiPMM");
                assertThat(request.getSession(false).getAttribute("tklerProviderNo")).isEqualTo("777777");
                assertThat(request.getSession(false).getAttribute("newticklerwarningwindow")).isEqualTo("15");
                assertThat(request.getSession(false).getAttribute("default_pmm")).isEqualTo("enabled");
                assertThat(request.getSession(false).getAttribute("caisiBillingPreferenceNotDelete")).isEqualTo("0");
                assertThat(request.getSession(false).getAttribute("CaseMgmtUsers"))
                        .asList()
                        .contains("999998");
            }
        } finally {
            restoreProperty("caisi", originalCaisi);
        }
    }

    @Test
    @DisplayName("should filter mixed CAISI users and avoid duplicate provider")
    void shouldFilterMixedCaisiUsersAndAvoidDuplicateProvider_whenCaisiIsEnabled() throws Exception {
        String originalCaisi = CarlosProperties.getInstance().getProperty("caisi");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        ProviderPreference preference = new ProviderPreference();
        preference.setDefaultCaisiPmm("enabled");
        preference.setDefaultNewOscarCme("enabled");
        preference.setNewTicklerWarningWindow("15");
        request.getSession().getServletContext().setAttribute("CaseMgmtUsers",
                Arrays.asList("999998", Integer.valueOf(7), null, "777777"));
        stagePendingMfa(security);
        stubSuccessfulProviderLogin(preference);
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("caisi", "yes");
            try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456");
                 LogCapture capture = LogCapture.forLogger(Login2Action.class)) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo("caisiPMM");
                Object sessionCaseMgmtUsers = request.getSession(false).getAttribute("CaseMgmtUsers");
                Object contextCaseMgmtUsers = request.getSession(false).getServletContext()
                        .getAttribute("CaseMgmtUsers");
                assertThat(sessionCaseMgmtUsers)
                        .asList()
                        .containsExactly("999998", "777777");
                assertThat(sessionCaseMgmtUsers).isNotSameAs(contextCaseMgmtUsers);
                ((List<String>) contextCaseMgmtUsers).add("888888");
                assertThat(sessionCaseMgmtUsers).asList().containsExactly("999998", "777777");
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("Ignoring non-String CaseMgmtUsers entry")
                            .contains("java.lang.Integer");
                });
            }
        } finally {
            restoreProperty("caisi", originalCaisi);
        }
    }

    @Test
    @DisplayName("should rebuild CAISI users when context attribute is not a list")
    void shouldRebuildCaisiUsers_whenContextAttributeIsNotAList() throws Exception {
        String originalCaisi = CarlosProperties.getInstance().getProperty("caisi");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        ProviderPreference preference = new ProviderPreference();
        preference.setDefaultCaisiPmm("enabled");
        preference.setDefaultNewOscarCme("enabled");
        preference.setNewTicklerWarningWindow("15");
        request.getSession().getServletContext().setAttribute("CaseMgmtUsers", "not-a-list");
        stagePendingMfa(security);
        stubSuccessfulProviderLogin(preference);
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("caisi", "yes");
            try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456");
                 LogCapture capture = LogCapture.forLogger(Login2Action.class)) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo("caisiPMM");
                assertThat(request.getSession(false).getAttribute("CaseMgmtUsers"))
                        .asList()
                        .containsExactly("999998");
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("CaseMgmtUsers context attribute is not a List")
                            .contains("java.lang.String");
                });
            }
        } finally {
            restoreProperty("caisi", originalCaisi);
        }
    }

    @Test
    @DisplayName("should start BC alert timer when billing region is BC")
    void shouldStartBcAlertTimer_whenBillingRegionIsBc() throws Exception {
        String originalBillRegion = CarlosProperties.getInstance().getProperty("billregion");
        String originalAlertFrequency = CarlosProperties.getInstance().getProperty("ALERT_POLL_FREQUENCY");
        String originalAlerts = CarlosProperties.getInstance().getProperty("CDM_ALERTS");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("billregion", "BC");
            CarlosProperties.getInstance().setProperty("ALERT_POLL_FREQUENCY", "120000");
            CarlosProperties.getInstance().setProperty("CDM_ALERTS", "A,B");
            try (MockedStatic<AlertTimer> alertTimerMock = mockStatic(AlertTimer.class);
                 MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                alertTimerMock.when(() -> AlertTimer.getInstance(any(String[].class), eq(120000L)))
                        .thenReturn(mock(AlertTimer.class));
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo(ActionSupport.NONE);
                alertTimerMock.verify(() -> AlertTimer.getInstance(any(String[].class), eq(120000L)));
            }
        } finally {
            restoreProperty("billregion", originalBillRegion);
            restoreProperty("ALERT_POLL_FREQUENCY", originalAlertFrequency);
            restoreProperty("CDM_ALERTS", originalAlerts);
        }
    }

    @Test
    @DisplayName("should skip BC alert timer when polling frequency is invalid")
    void shouldSkipBcAlertTimer_whenPollingFrequencyIsInvalid() throws Exception {
        String originalBillRegion = CarlosProperties.getInstance().getProperty("billregion");
        String originalAlertFrequency = CarlosProperties.getInstance().getProperty("ALERT_POLL_FREQUENCY");
        String originalAlerts = CarlosProperties.getInstance().getProperty("CDM_ALERTS");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("billregion", "BC");
            CarlosProperties.getInstance().setProperty("ALERT_POLL_FREQUENCY", "not-a-number");
            CarlosProperties.getInstance().setProperty("CDM_ALERTS", "A,B");
            try (MockedStatic<AlertTimer> alertTimerMock = mockStatic(AlertTimer.class);
                 LogCapture capture = LogCapture.forLogger(Login2Action.class);
                 MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo(ActionSupport.NONE);
                assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
                alertTimerMock.verifyNoInteractions();
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("ALERT_POLL_FREQUENCY is invalid")
                            .contains("not-a-number");
                });
            }
        } finally {
            restoreProperty("billregion", originalBillRegion);
            restoreProperty("ALERT_POLL_FREQUENCY", originalAlertFrequency);
            restoreProperty("CDM_ALERTS", originalAlerts);
        }
    }

    @Test
    @DisplayName("should skip BC alert timer quietly when polling frequency is blank")
    void shouldSkipBcAlertTimerQuietly_whenPollingFrequencyIsBlank() throws Exception {
        String originalBillRegion = CarlosProperties.getInstance().getProperty("billregion");
        String originalAlertFrequency = CarlosProperties.getInstance().getProperty("ALERT_POLL_FREQUENCY");
        String originalAlerts = CarlosProperties.getInstance().getProperty("CDM_ALERTS");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("billregion", "BC");
            CarlosProperties.getInstance().setProperty("ALERT_POLL_FREQUENCY", "   ");
            CarlosProperties.getInstance().setProperty("CDM_ALERTS", "A,B");
            try (MockedStatic<AlertTimer> alertTimerMock = mockStatic(AlertTimer.class);
                 LogCapture capture = LogCapture.forLogger(Login2Action.class);
                 MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo(ActionSupport.NONE);
                assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
                alertTimerMock.verifyNoInteractions();
                assertThat(capture.events()).allSatisfy(event ->
                        assertThat(event.getLevel()).isNotEqualTo(Level.WARN));
            }
        } finally {
            restoreProperty("billregion", originalBillRegion);
            restoreProperty("ALERT_POLL_FREQUENCY", originalAlertFrequency);
            restoreProperty("CDM_ALERTS", originalAlerts);
        }
    }

    @Test
    @DisplayName("should skip BC alert timer quietly when polling frequency is missing")
    void shouldSkipBcAlertTimerQuietly_whenPollingFrequencyIsMissing() throws Exception {
        String originalBillRegion = CarlosProperties.getInstance().getProperty("billregion");
        String originalAlertFrequency = CarlosProperties.getInstance().getProperty("ALERT_POLL_FREQUENCY");
        String originalAlerts = CarlosProperties.getInstance().getProperty("CDM_ALERTS");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("billregion", "BC");
            CarlosProperties.getInstance().remove("ALERT_POLL_FREQUENCY");
            CarlosProperties.getInstance().setProperty("CDM_ALERTS", "A,B");
            try (MockedStatic<AlertTimer> alertTimerMock = mockStatic(AlertTimer.class);
                 LogCapture capture = LogCapture.forLogger(Login2Action.class);
                 MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo(ActionSupport.NONE);
                assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
                alertTimerMock.verifyNoInteractions();
                assertThat(capture.events()).allSatisfy(event ->
                        assertThat(event.getLevel()).isNotEqualTo(Level.WARN));
            }
        } finally {
            restoreProperty("billregion", originalBillRegion);
            restoreProperty("ALERT_POLL_FREQUENCY", originalAlertFrequency);
            restoreProperty("CDM_ALERTS", originalAlerts);
        }
    }

    @Test
    @DisplayName("should skip BC alert timer when alert codes are missing")
    void shouldSkipBcAlertTimer_whenAlertCodesAreMissing() throws Exception {
        String originalBillRegion = CarlosProperties.getInstance().getProperty("billregion");
        String originalAlertFrequency = CarlosProperties.getInstance().getProperty("ALERT_POLL_FREQUENCY");
        String originalAlerts = CarlosProperties.getInstance().getProperty("CDM_ALERTS");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("billregion", "BC");
            CarlosProperties.getInstance().setProperty("ALERT_POLL_FREQUENCY", "120000");
            CarlosProperties.getInstance().setProperty("CDM_ALERTS", "");
            try (MockedStatic<AlertTimer> alertTimerMock = mockStatic(AlertTimer.class);
                 LogCapture capture = LogCapture.forLogger(Login2Action.class);
                 MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo(ActionSupport.NONE);
                assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
                alertTimerMock.verifyNoInteractions();
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("CDM_ALERTS is not configured");
                });
            }
        } finally {
            restoreProperty("billregion", originalBillRegion);
            restoreProperty("ALERT_POLL_FREQUENCY", originalAlertFrequency);
            restoreProperty("CDM_ALERTS", originalAlerts);
        }
    }

    @Test
    @DisplayName("should skip BC alert timer when startup fails")
    void shouldSkipBcAlertTimer_whenStartupFails() throws Exception {
        String originalBillRegion = CarlosProperties.getInstance().getProperty("billregion");
        String originalAlertFrequency = CarlosProperties.getInstance().getProperty("ALERT_POLL_FREQUENCY");
        String originalAlerts = CarlosProperties.getInstance().getProperty("CDM_ALERTS");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("billregion", "BC");
            CarlosProperties.getInstance().setProperty("ALERT_POLL_FREQUENCY", "120000");
            CarlosProperties.getInstance().setProperty("CDM_ALERTS", "A,B");
            try (MockedStatic<AlertTimer> alertTimerMock = mockStatic(AlertTimer.class);
                 LogCapture capture = LogCapture.forLogger(Login2Action.class);
                 MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                alertTimerMock.when(() -> AlertTimer.getInstance(any(String[].class), eq(120000L)))
                        .thenThrow(new RuntimeException("timer failed"));
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo(ActionSupport.NONE);
                assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("AlertTimer startup failed");
                });
            }
        } finally {
            restoreProperty("billregion", originalBillRegion);
            restoreProperty("ALERT_POLL_FREQUENCY", originalAlertFrequency);
            restoreProperty("CDM_ALERTS", originalAlerts);
        }
    }

    @Test
    @DisplayName("should route patient intake users to patient intake result")
    void shouldRoutePatientIntakeUsers_toPatientIntakeResult() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security, new String[]{"999998", "Test", "Provider", "", "Patient Intake", "0"});
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo("patientIntake");
            assertPendingMfaCleared();
        }
    }

    @Test
    @DisplayName("should persist oauth token provider when program location login completes")
    void shouldPersistOauthTokenProvider_whenProgramLocationLoginCompletes() throws Exception {
        String originalUseProgramLocation = CarlosProperties.getInstance().getProperty("useProgramLocation");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        ServiceRequestToken token = new ServiceRequestToken();
        request.setParameter("oauth_token", "oauth-123");
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(serviceRequestTokenDao.findByTokenId("oauth-123")).thenReturn(token);
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("useProgramLocation", "true");
            try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo("programLocation");
                assertThat(token.getProviderNo()).isEqualTo("999998");
                verify(serviceRequestTokenDao).merge(token);
            }
        } finally {
            restoreProperty("useProgramLocation", originalUseProgramLocation);
        }
    }

    @Test
    @DisplayName("should reject malformed oauth token without lookup")
    void shouldRejectMalformedOauthToken_withoutLookup() throws Exception {
        String originalUseProgramLocation = CarlosProperties.getInstance().getProperty("useProgramLocation");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        request.setParameter("oauth_token", "bad token <script>");
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("useProgramLocation", "true");
            try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isEqualTo("programLocation");
                verify(serviceRequestTokenDao, never()).findByTokenId(anyString());
                verify(serviceRequestTokenDao, never()).merge(any());
                logActionMock.verify(() -> LogAction.addLog("999998", "log in", "login",
                        "invalid_oauth_token", request.getRemoteAddr()));
            }
        } finally {
            restoreProperty("useProgramLocation", originalUseProgramLocation);
        }
    }

    @Test
    @DisplayName("should write AJAX JSON when login completes with ajax response")
    void shouldWriteAjaxJson_whenLoginCompletesWithAjaxResponse() throws Exception {
        String originalUseProgramLocation = CarlosProperties.getInstance().getProperty("useProgramLocation");
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        request.setParameter("ajaxResponse", "true");
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try {
            CarlosProperties.getInstance().setProperty("useProgramLocation", "true");
            try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
                Login2Action action = newAction(null, null, null);
                action.setCode("123456");

                String result = action.execute();

                assertThat(result).isNull();
                assertThat(response.getContentType()).isEqualTo("application/json");
                assertThat(response.getContentAsString())
                        .contains("\"success\":true")
                        .contains("\"providerNo\":\"999998\"");
            }
        } finally {
            restoreProperty("useProgramLocation", originalUseProgramLocation);
        }
    }

    @Test
    @DisplayName("should rotate existing session and preserve only OAuth consent nonce")
    void shouldRotateExistingSession_andPreserveOnlyOauthConsentNonce() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        MockHttpSession pendingSession = (MockHttpSession) request.getSession();
        pendingSession.setAttribute("oauthState", "keep-me");
        pendingSession.setAttribute("oauth.authorize.nonce.oauth-123", "nonce-123");
        request.setParameter("invalidate_session", "false");
        stagePendingMfa(security);
        stubSuccessfulProviderLogin();
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored = mockTotpReturning("123456")) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(pendingSession.isInvalid()).isTrue();
            assertThat(request.getSession(false)).isNotSameAs(pendingSession);
            assertThat(request.getSession(false).getAttribute("oauthState")).isNull();
            assertThat(request.getSession(false).getAttribute("oauth.authorize.nonce.oauth-123"))
                    .isEqualTo("nonce-123");
            assertThat(request.getSession(false).getAttribute("user")).isEqualTo("999998");
            assertPendingMfaCleared();
        }
    }

    @Test
    @DisplayName("should clear pending MFA session when registration save fails")
    void shouldClearPendingMfaSession_whenMfaRegistrationSaveFails() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security, STR_AUTH, "JBSWY3DPEHPK3PXP");
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");
        action.setMfaRegistrationFlow(true);
        doThrow(new IllegalStateException("save failed"))
                .when(mfaManager).saveMfaSecret(any(), any(), anyString());

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored =
                     mockConstruction(TimeBasedOneTimePasswordGenerator.class, (mock, context) -> {
                         when(mock.getAlgorithm()).thenReturn("HmacSHA1");
                         when(mock.getTimeStep()).thenReturn(java.time.Duration.ofSeconds(30));
                         when(mock.generateOneTimePasswordString(any(), any())).thenReturn("123456");
                     })) {
            String result = action.execute();

            assertThat(result).isEqualTo("error");
            assertThat(request.getAttribute("errormsg")).isNotNull();
            assertPendingMfaCleared();
            logActionMock.verify(() -> LogAction.addLog("999998", "login", "mfa_failed",
                    "mfa_registration_persist", request.getRemoteAddr()));
            logActionMock.verify(() -> LogAction.addLog("999998", "login", "mfa_success", "mfa",
                    request.getRemoteAddr()), never());
        }
    }

    @Test
    @DisplayName("should save registration secret and complete login when MFA registration code is valid")
    void shouldSaveRegistrationSecretAndCompleteLogin_whenMfaRegistrationCodeIsValid() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security, STR_AUTH, "JBSWY3DPEHPK3PXP");
        String token = currentPendingMfaToken();
        stubSuccessfulProviderLogin();
        Login2Action action = newAction(null, null, null);
        action.setCode("123456");
        action.setMfaRegistrationFlow(true);

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored =
                     mockConstruction(TimeBasedOneTimePasswordGenerator.class, (mock, context) -> {
                         when(mock.getAlgorithm()).thenReturn("HmacSHA1");
                         when(mock.getTimeStep()).thenReturn(java.time.Duration.ofSeconds(30));
                         when(mock.generateOneTimePasswordString(any(), any())).thenReturn("123456");
                     })) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            verify(mfaManager).saveMfaSecret(any(), eq(security), eq("JBSWY3DPEHPK3PXP"));
            assertThat(PendingMfaChallengeCache.getInstance().peek(token)).isNull();
            assertPendingMfaCleared();
        }
    }

    @Test
    @DisplayName("should fail safely when provider record is missing after MFA")
    void shouldFailSafely_whenProviderRecordIsMissingAfterMfa() throws Exception {
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stagePendingMfa(security);
        MockHttpSession pendingSession = (MockHttpSession) request.getSession(false);
        when(mfaManager.getMfaSecret(security)).thenReturn("JBSWY3DPEHPK3PXP");
        when(providerManager.getProvider("999998")).thenReturn(null);
        when(providerPreferenceDao.find("999998")).thenReturn(new ProviderPreference());

        try (MockedConstruction<TimeBasedOneTimePasswordGenerator> ignored =
                     mockConstruction(TimeBasedOneTimePasswordGenerator.class, (mock, context) -> {
                         when(mock.getAlgorithm()).thenReturn("HmacSHA1");
                         when(mock.getTimeStep()).thenReturn(java.time.Duration.ofSeconds(30));
                         when(mock.generateOneTimePasswordString(any(), any())).thenReturn("123456");
                     })) {
            Login2Action action = newAction(null, null, null);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo("error");
            assertThat(request.getAttribute("errormsg")).isNotNull();
            assertThat(pendingSession.isInvalid()).isTrue();
        }
    }

    @Test
    @DisplayName("should stage forced reset when mandatory reset property is missing")
    void shouldStageForcedReset_whenMandatoryResetPropertyIsMissing() throws Exception {
        String originalMandatoryReset = CarlosProperties.getInstance().getProperty("mandatory_password_reset");
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        Provider provider = activeProvider();

        request.setParameter("forcedpasswordchange", "false");
        CarlosProperties.getInstance().remove("mandatory_password_reset");
        when(securityManager.encodePassword(password)).thenReturn(ENCODED_OLD_PASSWORD);
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(provider);

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(STR_AUTH);
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR))
                    .isInstanceOf(String.class);
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
        } finally {
            if (originalMandatoryReset == null) {
                CarlosProperties.getInstance().remove("mandatory_password_reset");
            } else {
                CarlosProperties.getInstance().setProperty("mandatory_password_reset", originalMandatoryReset);
            }
        }
    }

    @Test
    @DisplayName("should stage forced reset when mandatory reset property uses numeric true")
    void shouldStageForcedReset_whenMandatoryResetPropertyUsesNumericTrue() throws Exception {
        String originalMandatoryReset = CarlosProperties.getInstance().getProperty("mandatory_password_reset");
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        Provider provider = activeProvider();

        request.setParameter("forcedpasswordchange", "false");
        CarlosProperties.getInstance().setProperty("mandatory_password_reset", "1");
        when(securityManager.encodePassword(password)).thenReturn(ENCODED_OLD_PASSWORD);
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(provider);

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(STR_AUTH);
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR))
                    .isInstanceOf(String.class);
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
        } finally {
            if (originalMandatoryReset == null) {
                CarlosProperties.getInstance().remove("mandatory_password_reset");
            } else {
                CarlosProperties.getInstance().setProperty("mandatory_password_reset", originalMandatoryReset);
            }
        }
    }

    @Test
    @DisplayName("should stage forced reset when mandatory reset property is unrecognized")
    void shouldStageForcedReset_whenMandatoryResetPropertyIsUnrecognized() throws Exception {
        String originalMandatoryReset = CarlosProperties.getInstance().getProperty("mandatory_password_reset");
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        Provider provider = activeProvider();

        request.setParameter("forcedpasswordchange", "false");
        CarlosProperties.getInstance().setProperty("mandatory_password_reset", "ture");
        when(securityManager.encodePassword(password)).thenReturn(ENCODED_OLD_PASSWORD);
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(provider);

        try (LogCapture capture = LogCapture.forLogger(Login2Action.class);
             MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(STR_AUTH);
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/forcepasswordreset");
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR))
                    .isInstanceOf(String.class);
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("Unrecognized mandatory_password_reset value");
            });
        } finally {
            if (originalMandatoryReset == null) {
                CarlosProperties.getInstance().remove("mandatory_password_reset");
            } else {
                CarlosProperties.getInstance().setProperty("mandatory_password_reset", originalMandatoryReset);
            }
        }
    }

    @Test
    @DisplayName("should skip forced reset when mandatory reset property is disabled")
    void shouldSkipForcedReset_whenMandatoryResetPropertyIsDisabled() throws Exception {
        String originalMandatoryReset = CarlosProperties.getInstance().getProperty("mandatory_password_reset");
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        Provider provider = activeProvider();

        request.setParameter("forcedpasswordchange", "false");
        CarlosProperties.getInstance().setProperty("mandatory_password_reset", "false");
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(provider);
        stubSuccessfulProviderLogin();

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(STR_AUTH);
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
        } finally {
            if (originalMandatoryReset == null) {
                CarlosProperties.getInstance().remove("mandatory_password_reset");
            } else {
                CarlosProperties.getInstance().setProperty("mandatory_password_reset", originalMandatoryReset);
            }
        }
    }

    @Test
    @DisplayName("should discard invalid next page when staging forced reset credentials")
    void shouldDiscardInvalidNextPage_whenStagingForcedResetCredentials() throws Exception {
        String password = VALID_PASSWORD;
        when(securityManager.encodePassword(password)).thenReturn(ENCODED_OLD_PASSWORD);
        Login2Action action = newAction(null, null, null);

        ReflectionTestUtils.invokeMethod(action, "setUserInfoToSession", request, USERNAME, password, "2026",
                "https://evil.example/phish");

        String token = (String) request.getSession(false)
                .getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR);
        assertThat(LoginCredentialCache.getInstance().peek(token).getNextPage()).isNull();
        LoginCredentialCache.getInstance().invalidate(token);
    }

    @Test
    @DisplayName("should invalidate previous credential token when staging forced reset credentials")
    void shouldInvalidatePreviousCredentialToken_whenStagingForcedResetCredentials() {
        MockHttpSession originalSession = (MockHttpSession) request.getSession(false);
        String oldToken = LoginCredentialCache.getInstance().store(
                new LoginCredentialCache.LoginCredentials(USERNAME, "old-hash", "2026", null));
        request.getSession().setAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR, oldToken);
        when(securityManager.encodePassword(VALID_PASSWORD)).thenReturn(ENCODED_OLD_PASSWORD);
        Login2Action action = newAction(null, null, null);

        ReflectionTestUtils.invokeMethod(action, "setUserInfoToSession", request, USERNAME,
                VALID_PASSWORD, "2026", "/provider/providercontrol.jsp");

        String newToken = (String) request.getSession(false)
                .getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR);
        assertThat(originalSession.isInvalid()).isTrue();
        assertThat(request.getSession(false)).isNotSameAs(originalSession);
        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(300);
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(LoginCredentialCache.getInstance().peek(oldToken)).isNull();
        assertThat(LoginCredentialCache.getInstance().peek(newToken)).isNotNull();
        LoginCredentialCache.getInstance().invalidate(newToken);
    }

    @Test
    @DisplayName("should return generic fallback when message key is missing everywhere")
    void shouldReturnGenericFallback_whenMessageKeyIsMissingEverywhere() {
        try (LogCapture capture = LogCapture.forLogger(Login2Action.class)) {
            assertThat(Login2Action.message(request, "login.missing.test.key"))
                    .isEqualTo("Unable to process your request. Please try again.");
            assertThat(capture.events()).filteredOn(event -> event.getLevel().equals(Level.WARN))
                    .extracting(event -> event.getMessage().getFormattedMessage())
                    .contains("Missing localized message: bundle=oscarResources locale=en key=login.missing.test.key");
            assertThat(capture.events()).filteredOn(event -> event.getLevel().equals(Level.ERROR))
                    .extracting(event -> event.getMessage().getFormattedMessage())
                    .contains("Missing default message: bundle=oscarResources locale=en key=login.missing.test.key");
        }
    }

    @Test
    @DisplayName("should write localized invalid credentials JSON when AJAX login fails")
    void shouldWriteLocalizedInvalidCredentialsJson_whenAjaxLoginFails() throws Exception {
        request.setParameter("forcedpasswordchange", "false");
        request.setParameter("ajaxResponse", "true");
        String password = VALID_PASSWORD;

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.isBlock(request.getRemoteAddr(), USERNAME)).thenReturn(false);
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenReturn(new String[]{"failed"});
                })) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isNull();
            assertThat(response.getContentType()).isEqualTo("application/json");
            assertThat(response.getContentAsString())
                    .contains("\"success\":false")
                    .contains("\"error\":\"Invalid credentials.\"");
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("should count failed login when authentication provider throws")
    void shouldCountFailedLogin_whenAuthenticationProviderThrows() throws Exception {
        request.setParameter("forcedpasswordchange", "false");
        String password = VALID_PASSWORD;

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.isBlock(request.getRemoteAddr(), USERNAME)).thenReturn(false);
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr()))
                            .thenThrow(new RuntimeException("auth backend down"));
                })) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/loginfailed");
            verify(mockedLoginChecks.constructed().get(0)).updateLoginList(request.getRemoteAddr(), USERNAME);
        }
    }

    @Test
    @DisplayName("should reject facility selection on login route")
    void shouldRejectFacilitySelection_onLoginRoute() throws Exception {
        request.setParameter("forcedpasswordchange", "false");
        request.addParameter("nextPage", "provider");
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "10");
        String password = VALID_PASSWORD;

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class)) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/loginfailed");
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
            verify(providerDao, never()).getFacilityIds(org.mockito.ArgumentMatchers.anyString());
            logActionMock.verify(() -> LogAction.addLog(USERNAME, "log in", "login",
                    "facility_selection_on_login_rejected", request.getRemoteAddr()));
        }
    }

    @Test
    @DisplayName("should allow empty next page on login route")
    void shouldAllowEmptyNextPage_onLoginRoute() throws Exception {
        request.setParameter("forcedpasswordchange", "false");
        request.addParameter("nextPage", "");
        String password = VALID_PASSWORD;
        Security security = forcedResetSecurity();
        security.setForcePasswordReset(Boolean.FALSE);
        stubSuccessfulProviderLogin();
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));

        try (MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.isBlock(request.getRemoteAddr(), USERNAME)).thenReturn(false);
                    when(mock.auth(USERNAME, password, "2026", request.getRemoteAddr())).thenReturn(STR_AUTH);
                })) {
            Login2Action action = newAction(null, null, null);
            action.setUsername(USERNAME);
            action.setPassword(password);
            action.setPin("2026");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
            verify(providerDao).getFacilityIds("999998");
        }
    }

    @Test
    @DisplayName("should persist valid forced reset password when MFA code parameter is also present")
    void shouldPersistValidForcedResetPassword_whenMfaCodeParameterAlsoPresent() throws Exception {
        String token = cacheCredentials();
        String newPassword = VALID_PASSWORD;
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
        LoginCredentialCache credentialCacheSpy = spy(LoginCredentialCache.getInstance());

        try (MockedStatic<LoginCredentialCache> credentialCacheMock = mockStatic(LoginCredentialCache.class);
             MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, newPassword, "2026", request.getRemoteAddr()))
                            .thenReturn(new String[]{"999998", "Test", "Provider", "", "doctor", "0"});
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            credentialCacheMock.when(LoginCredentialCache::getInstance).thenReturn(credentialCacheSpy);
            Login2Action action = newAction(OLD_PASSWORD, newPassword, newPassword);
            action.setCode("123456");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            assertThat(LoginCredentialCache.getInstance().peek(token)).isNull();
            assertThat(request.getSession(false).getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR)).isNull();
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
            InOrder inOrder = inOrder(credentialCacheSpy, securityDao);
            inOrder.verify(credentialCacheSpy).peek(token);
            inOrder.verify(credentialCacheSpy).consume(token);
            inOrder.verify(securityDao).saveEntity(securityCaptor.capture());
        }

        Security persistedSecurity = securityCaptor.getValue();
        assertThat(persistedSecurity).isSameAs(security);
        assertThat(persistedSecurity.getPassword()).isEqualTo(encodedNewPassword);
        assertThat(persistedSecurity.isForcePasswordReset()).isFalse();
        assertThat(persistedSecurity.getPasswordUpdateDate()).isNotNull();
    }

    @Test
    @DisplayName("should end login when forced reset cleanup has session state failure")
    void shouldEndLogin_whenForcedResetCleanupHasSessionStateFailure() throws Exception {
        MockHttpSession cleanupFailingSession = new RemoveAttributeFailingSession();
        request.setSession(cleanupFailingSession);
        String token = cacheCredentials();
        String newPassword = VALID_PASSWORD;
        String encodedNewPassword = "encoded-new-password";
        Security security = forcedResetSecurity();
        Provider provider = activeProvider();

        when(securityManager.matchesPassword(OLD_PASSWORD, ENCODED_OLD_PASSWORD)).thenReturn(true);
        when(securityManager.encodePassword(newPassword)).thenReturn(encodedNewPassword);
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        when(providerDao.getProvider("999998")).thenReturn(provider);
        when(providerManager.getProvider("999998")).thenReturn(provider);
        when(providerPreferenceDao.find("999998")).thenReturn(new ProviderPreference());
        when(providerDao.getFacilityIds("999998")).thenReturn(Collections.emptyList());
        when(facilityDao.findAll(true)).thenReturn(Collections.emptyList());

        try (LogCapture capture = LogCapture.forLogger(Login2Action.class);
             MockedConstruction<LoginCheckLogin> mockedLoginChecks = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, newPassword, "2026", request.getRemoteAddr()))
                            .thenReturn(new String[]{"999998", "Test", "Provider", "", "doctor", "0"});
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            Login2Action action = newAction(OLD_PASSWORD, newPassword, newPassword);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/loginfailed");
            assertThat(decodedRedirect()).contains("Password updated. Please log in again.");
            assertThat(cleanupFailingSession.isInvalid()).isTrue();
            assertThat(LoginCredentialCache.getInstance().peek(token)).isNull();
            assertThat(request.getSession(false)).isNull();
            assertThat(mockedLoginChecks.constructed()).hasSize(1);
            verify(mockedLoginChecks.constructed().get(0), never())
                    .auth(anyString(), anyString(), anyString(), anyString());
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("session cleanup failed with java.lang.IllegalStateException")
                        .contains("ending current login flow");
            });
            logActionMock.verify(() -> LogAction.addLog(USERNAME, "login",
                    "forced_password_reset_completed", "cleanup_failure_relogin"));
        }
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

    private String decodedRedirect() {
        return URLDecoder.decode(response.getRedirectedUrl(), StandardCharsets.UTF_8);
    }

    private String currentPendingMfaToken() {
        return (String) request.getSession(false).getAttribute(PENDING_MFA_TOKEN_ATTR);
    }

    private void stagePendingMfa(Security security) {
        stagePendingMfa(security, STR_AUTH, null);
    }

    private void stagePendingMfa(Security security, String[] strAuth) {
        stagePendingMfa(security, strAuth, null);
    }

    private void stagePendingMfa(Security security, String[] strAuth, String registrationSecret) {
        stagePendingMfaChallenge(security.getSecurityNo(), security.getProviderNo(),
                strAuth, registrationSecret);
        when(securityDao.find(security.getSecurityNo())).thenReturn(security);
    }

    private String stagePendingMfaChallenge(Integer securityNo, String providerNo, String[] strAuth,
                                            String registrationSecret) {
        String token = PendingMfaChallengeCache.getInstance().store(
                new PendingMfaChallengeCache.PendingMfaChallenge(
                        securityNo, providerNo, strAuth, registrationSecret));
        PendingMfaChallenges.stage(request.getSession(), providerNo, token, 0);
        pendingMfaTokens.add(token);
        return token;
    }

    private void assertPendingMfaCleared() {
        assertThat(request.getSession(false).getAttribute(Login2Action.PENDING_MFA_AUTH_ATTR)).isNull();
        assertThat(request.getSession(false).getAttribute(PENDING_MFA_PROVIDER_NO_ATTR)).isNull();
        assertThat(request.getSession(false).getAttribute(PENDING_MFA_TOKEN_ATTR)).isNull();
        assertThat(request.getSession(false).getAttribute(PENDING_MFA_ATTEMPTS_ATTR)).isNull();
        assertThat(request.getSession(false).getAttribute("mfaSecret")).isNull();
    }

    private void stubSuccessfulProviderLogin() {
        stubSuccessfulProviderLogin(new ProviderPreference());
    }

    private void stubSuccessfulProviderLogin(ProviderPreference providerPreference) {
        Provider provider = activeProvider();
        when(providerManager.getProvider("999998")).thenReturn(provider);
        when(providerDao.getProvider("999998")).thenReturn(provider);
        when(providerPreferenceDao.find("999998")).thenReturn(providerPreference);
        when(providerDao.getFacilityIds("999998")).thenReturn(Collections.emptyList());
        when(facilityDao.findAll(true)).thenReturn(Collections.emptyList());
    }

    private MockedConstruction<TimeBasedOneTimePasswordGenerator> mockTotpReturning(String generatedCode) {
        return mockConstruction(TimeBasedOneTimePasswordGenerator.class, (mock, context) -> {
            when(mock.getAlgorithm()).thenReturn("HmacSHA1");
            when(mock.getTimeStep()).thenReturn(java.time.Duration.ofSeconds(30));
            when(mock.generateOneTimePasswordString(any(), any())).thenReturn(generatedCode);
        });
    }

    private void restoreProperty(String key, String originalValue) {
        if (originalValue == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, originalValue);
        }
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

    private static final class RemoveAttributeFailingSession extends MockHttpSession {
        @Override
        public void removeAttribute(String name) {
            throw new IllegalStateException("cleanup failed");
        }
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
