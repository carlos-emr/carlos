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
import io.github.carlos_emr.carlos.commn.model.ServiceRequestToken;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import jakarta.servlet.http.HttpSession;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the concurrent-session policy in the login flow (issue #3980).
 *
 * <p>The invariants under test: the default policy changes nothing; the chooser is reached only
 * after every credential check and before any authenticated session exists; the pre-login session
 * holds nothing but an opaque single-use token; the submit route is POST-only, token-bound and
 * rejects replays and unknown answers; and every keep/sign-out decision is audited.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("Login2Action concurrent-session policy")
@Isolated
class Login2ActionConcurrentSessionUnitTest extends CarlosUnitTestBase {

    private static final String USERNAME = "carlosdoc";
    private static final String PASSWORD = String.join("", "Unit", "concurrent", "2026", "!");
    private static final String PIN = "2026";
    private static final Integer SECURITY_NO = 12345;
    private static final String PROVIDER_NO = "999998";
    private static final String[] STR_AUTH = {PROVIDER_NO, "Test", "Provider", "", "doctor", "0"};

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private AutoCloseable mockitoCloseable;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private String originalPolicy;
    private String originalMax;
    private final List<String> stagedTokens = new ArrayList<>();

    @Mock private ProviderManager providerManager;
    @Mock private FacilityDao facilityDao;
    @Mock private ProviderPreferenceDao providerPreferenceDao;
    @Mock private ProviderDao providerDao;
    @Mock private UserPropertyDAO userPropertyDao;
    @Mock private ServiceRequestTokenDao serviceRequestTokenDao;
    @Mock private SecurityManager securityManager;
    @Mock private SecurityDao securityDao;
    @Mock private UserSessionManager userSessionManager;
    @Mock private MfaManager mfaManager;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(ProviderManager.class, providerManager);
        registerMock(FacilityDao.class, facilityDao);
        registerMock(ProviderPreferenceDao.class, providerPreferenceDao);
        registerMock(ProviderDao.class, providerDao);
        registerMock(UserPropertyDAO.class, userPropertyDao);
        registerMock(ServiceRequestTokenDao.class, serviceRequestTokenDao);
        registerMock(SecurityManager.class, securityManager);
        registerMock(SecurityDao.class, securityDao);
        registerMock(UserSessionManager.class, userSessionManager);
        registerMock(MfaManager.class, mfaManager);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/login");
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("user-agent", "Mozilla/5.0");
        request.addHeader("Accept", "text/html");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        originalPolicy = CarlosProperties.getInstance().getProperty(ConcurrentSessionPolicy.POLICY_PROPERTY);
        originalMax = CarlosProperties.getInstance().getProperty(ConcurrentSessionPolicy.MAX_PROPERTY);
        stubProvider();
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreProperty(ConcurrentSessionPolicy.POLICY_PROPERTY, originalPolicy);
        restoreProperty(ConcurrentSessionPolicy.MAX_PROPERTY, originalMax);
        stagedTokens.forEach(token -> PendingSessionChoiceCache.getInstance().invalidate(token));
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    @Nested
    @DisplayName("sign-in under each policy")
    class SignIn {

        @Test
        @DisplayName("should complete login without consulting other sessions under the default policy")
        void shouldCompleteLoginUnchanged_underDefaultPolicy() throws Exception {
            setPolicy(null, null);

            String result = signIn();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            assertThat(request.getSession(false).getAttribute("user")).isEqualTo(PROVIDER_NO);
            verify(userSessionManager).registerUserSession(eq(SECURITY_NO), any(HttpSession.class), eq("10.1.2.3"));
            verify(userSessionManager, never()).countOtherActiveSessions(any(), any());
            verify(userSessionManager, never()).invalidateOtherSessions(any(), any());
        }

        @Test
        @DisplayName("should complete login when prompt policy finds no other sessions")
        void shouldCompleteLogin_whenPromptPolicyFindsNoOtherSessions() throws Exception {
            setPolicy("prompt", null);
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(0);

            String result = signIn();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(request.getSession(false).getAttribute("user")).isEqualTo(PROVIDER_NO);
            logActionMock.verify(() -> LogAction.addLog(anyString(), eq(LogConst.LOGIN),
                    eq("concurrent_sessions_prompted"), anyString(), anyString()), never());
        }

        @Test
        @DisplayName("should stage the chooser with only an opaque token when prompt policy finds other sessions")
        void shouldStageChooserWithOpaqueToken_whenPromptPolicyFindsOtherSessions() throws Exception {
            setPolicy("prompt", null);
            MockHttpSession preLoginSession = (MockHttpSession) request.getSession(true);
            preLoginSession.setAttribute("oauth.authorize.nonce.oauth-123", "nonce-123");
            preLoginSession.setAttribute("attackerPlanted", "x");
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(2);
            when(userSessionManager.describeOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(List.of(
                    new UserSessionManager.SessionInfo(Instant.ofEpochMilli(0), Instant.ofEpochMilli(1_000), "10.9.9.9"),
                    new UserSessionManager.SessionInfo(Instant.ofEpochMilli(0), Instant.ofEpochMilli(500), null)));

            String result = signIn();

            assertThat(result).isEqualTo(Login2Action.SESSION_CHOICE_RESULT);
            HttpSession staged = request.getSession(false);
            assertThat(preLoginSession.isInvalid()).as("a fixed pre-login session id cannot finish the login").isTrue();
            assertThat(staged).isNotSameAs(preLoginSession);
            assertThat(staged.getAttribute("user")).as("no authenticated session yet").isNull();
            assertThat(staged.getAttribute("attackerPlanted")).isNull();
            assertThat(staged.getAttribute("oauth.authorize.nonce.oauth-123")).isEqualTo("nonce-123");
            assertThat(Collections.list(staged.getAttributeNames()))
                    .containsExactlyInAnyOrder(PendingSessionChoices.TOKEN_ATTR, "oauth.authorize.nonce.oauth-123");
            assertThat(staged.getMaxInactiveInterval()).isEqualTo(300);
            String token = PendingSessionChoices.getToken(staged);
            stagedTokens.add(token);
            assertThat(PendingSessionChoiceCache.getInstance().peek(token).providerNo()).isEqualTo(PROVIDER_NO);

            ConcurrentSessionChoiceViewModel viewModel =
                    (ConcurrentSessionChoiceViewModel) request.getAttribute(ConcurrentSessionChoiceViewModel.REQUEST_ATTR);
            assertThat(viewModel.getOtherSessionCount()).isEqualTo(2);
            assertThat(viewModel.isSignOutRequired()).isFalse();
            assertThat(viewModel.getOtherSessions().get(0).getRemoteAddr()).isEqualTo("10.9.9.9");
            verify(userSessionManager, never()).registerUserSession(any(), any(), any());
            logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.LOGIN,
                    "concurrent_sessions_prompted", "2", "10.1.2.3"));
        }

        @Test
        @DisplayName("should require signing others out when the allow policy reaches its limit")
        void shouldRequireSignOut_whenAllowPolicyReachesLimit() throws Exception {
            setPolicy("allow", "2");
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(2);
            when(userSessionManager.describeOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(List.of(
                    new UserSessionManager.SessionInfo(Instant.EPOCH, Instant.EPOCH, null)));

            String result = signIn();

            assertThat(result).isEqualTo(Login2Action.SESSION_CHOICE_RESULT);
            stagedTokens.add(PendingSessionChoices.getToken(request.getSession(false)));
            ConcurrentSessionChoiceViewModel viewModel =
                    (ConcurrentSessionChoiceViewModel) request.getAttribute(ConcurrentSessionChoiceViewModel.REQUEST_ATTR);
            assertThat(viewModel.isSignOutRequired()).isTrue();
            assertThat(viewModel.getMaxSessions()).isEqualTo(2);
        }

        @Test
        @DisplayName("should sign out other sessions automatically and audit it under the single policy")
        void shouldSignOutOthersAutomatically_underSinglePolicy() throws Exception {
            setPolicy("single", null);
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(1);
            when(userSessionManager.invalidateOtherSessions(eq(SECURITY_NO), any())).thenReturn(1);

            String result = signIn();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            HttpSession authenticated = request.getSession(false);
            assertThat(authenticated.getAttribute("user")).isEqualTo(PROVIDER_NO);
            verify(userSessionManager).invalidateOtherSessions(SECURITY_NO, authenticated);
            logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.LOGIN,
                    "concurrent_sessions_revoked_auto", "1", "10.1.2.3"));
        }

        @Test
        @DisplayName("should not sign out older sessions under single when the new login fails setup")
        void shouldNotSignOutOthers_whenSingleLoginFailsSetup() throws Exception {
            setPolicy("single", null);
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(1);
            when(providerManager.getProvider(PROVIDER_NO)).thenReturn(null);

            signIn();

            verify(userSessionManager, never()).invalidateOtherSessions(any(), any());
            logActionMock.verify(() -> LogAction.addLog(eq(PROVIDER_NO), eq(LogConst.LOGIN),
                    eq("concurrent_sessions_revoked_auto"), anyString(), anyString()), never());
        }

        @Test
        @DisplayName("should keep sessions for an AJAX client that cannot be asked")
        void shouldKeepSessions_forAjaxClientUnderPromptPolicy() throws Exception {
            setPolicy("prompt", null);
            request.setParameter("ajaxResponse", "true");
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(1);

            String result = signIn();

            // The chooser is skipped; completion then follows the existing post-login routing
            // (the provider schedule redirects even for AJAX clients).
            assertThat(result).isNotEqualTo(Login2Action.SESSION_CHOICE_RESULT);
            assertThat(request.getSession(false).getAttribute("user")).isEqualTo(PROVIDER_NO);
            verify(userSessionManager, never()).invalidateOtherSessions(any(), any());
            logActionMock.verify(() -> LogAction.addLog(eq(PROVIDER_NO), eq(LogConst.LOGIN),
                    eq("concurrent_sessions_kept"), anyString(), eq("10.1.2.3")));
        }

        @Test
        @DisplayName("should refuse an AJAX login rather than silently sign out sessions at the limit")
        void shouldRefuseAjaxLogin_whenLimitRequiresSignOut() throws Exception {
            setPolicy("prompt", "1");
            request.setParameter("ajaxResponse", "true");
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(1);

            String result = signIn();

            assertThat(result).isNull();
            assertThat(response.getContentAsString()).contains("\"success\":false");
            assertThat(request.getSession(false) == null
                    || request.getSession(false).getAttribute("user") == null).isTrue();
            verify(userSessionManager, never()).invalidateOtherSessions(any(), any());
            verify(userSessionManager, never()).registerUserSession(any(), any(), any());
            logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.LOGIN,
                    "concurrent_sessions_limit_refused", "1", "10.1.2.3"));
        }
    }

    @Nested
    @DisplayName("chooser submit")
    class Submit {

        @BeforeEach
        void routeToChooser() {
            request.setRequestURI("/carlos/login/sessionChoice");
            setPolicy("prompt", null);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"GET", "HEAD"})
        @DisplayName("should reject non-POST before reading the pending login")
        void shouldRejectNonPost_beforeReadingPendingLogin(String method) throws Exception {
            String token = stagePendingChoice();
            request.setMethod(method);

            String result = newAction(Login2Action.SESSION_CHOICE_SIGN_OUT).submitSessionChoice();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(PendingSessionChoiceCache.getInstance().peek(token)).as("token untouched").isNotNull();
            verifyNoInteractions(userSessionManager, securityDao);
        }

        @Test
        @DisplayName("should return to login when no pending login is staged")
        void shouldRedirectToLogin_whenNoPendingLoginIsStaged() throws Exception {
            request.getSession(true);

            String result = newAction(Login2Action.SESSION_CHOICE_KEEP).submitSessionChoice();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(decodedRedirect()).contains("/loginfailed");
            verifyNoInteractions(userSessionManager);
        }

        @ParameterizedTest(name = "choice=\"{0}\"")
        @ValueSource(strings = {"", "invalidate", "KEEP", "maybe"})
        @DisplayName("should end the pending login when the answer is not recognised")
        void shouldEndPendingLogin_whenChoiceIsUnrecognised(String choice) throws Exception {
            String token = stagePendingChoice();

            String result = newAction(choice).submitSessionChoice();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(decodedRedirect()).contains("/loginfailed");
            assertThat(PendingSessionChoiceCache.getInstance().peek(token)).isNull();
            verify(userSessionManager, never()).invalidateOtherSessions(any(), any());
            logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.LOGIN,
                    "concurrent_sessions_invalid_choice", "", "10.1.2.3"));
        }

        @Test
        @DisplayName("should end the pending login when the answer is missing")
        void shouldEndPendingLogin_whenChoiceIsMissing() throws Exception {
            String token = stagePendingChoice();

            String result = newAction(null).submitSessionChoice();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(PendingSessionChoiceCache.getInstance().peek(token)).isNull();
        }

        @Test
        @DisplayName("should sign out other sessions, finish login and audit the revocation")
        void shouldSignOutOtherSessions_andFinishLogin() throws Exception {
            String token = stagePendingChoice();
            when(userSessionManager.invalidateOtherSessions(eq(SECURITY_NO), any())).thenReturn(2);

            String result = newAction(Login2Action.SESSION_CHOICE_SIGN_OUT).submitSessionChoice();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).contains("/provider/providercontrol");
            HttpSession authenticated = request.getSession(false);
            assertThat(authenticated.getAttribute("user")).isEqualTo(PROVIDER_NO);
            assertThat(authenticated.getAttribute(PendingSessionChoices.TOKEN_ATTR)).isNull();
            assertThat(PendingSessionChoiceCache.getInstance().peek(token)).isNull();
            verify(userSessionManager).registerUserSession(SECURITY_NO, authenticated, "10.1.2.3");
            verify(userSessionManager).invalidateOtherSessions(SECURITY_NO, authenticated);
            logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.LOGIN,
                    "concurrent_sessions_revoked", "2", "10.1.2.3"));
        }

        @Test
        @DisplayName("should keep other sessions, finish login and audit the decision")
        void shouldKeepOtherSessions_andFinishLogin() throws Exception {
            stagePendingChoice();
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(1);

            String result = newAction(Login2Action.SESSION_CHOICE_KEEP).submitSessionChoice();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(request.getSession(false).getAttribute("user")).isEqualTo(PROVIDER_NO);
            verify(userSessionManager, never()).invalidateOtherSessions(any(), any());
            logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.LOGIN,
                    "concurrent_sessions_kept", "1", "10.1.2.3"));
        }

        @Test
        @DisplayName("should restore the original OAuth token binding after the chooser")
        void shouldBindOriginalOauthToken_afterChooser() throws Exception {
            ServiceRequestToken serviceRequestToken = new ServiceRequestToken();
            when(serviceRequestTokenDao.findByTokenId("oauth-123")).thenReturn(serviceRequestToken);
            stagePendingChoice("oauth-123");

            newAction(Login2Action.SESSION_CHOICE_SIGN_OUT).submitSessionChoice();

            assertThat(serviceRequestToken.getProviderNo()).isEqualTo(PROVIDER_NO);
            verify(serviceRequestTokenDao).merge(serviceRequestToken);
        }

        @Test
        @DisplayName("should still audit a malformed OAuth token that waited behind the chooser")
        void shouldAuditMalformedOauthToken_afterChooser() throws Exception {
            setPolicy("prompt", null);
            request.setRequestURI("/carlos/login");
            request.setParameter("oauth_token", "bad token <script>");
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(1);
            assertThat(signIn()).isEqualTo(Login2Action.SESSION_CHOICE_RESULT);
            String token = PendingSessionChoices.getToken(request.getSession(false));
            stagedTokens.add(token);
            assertThat(PendingSessionChoiceCache.getInstance().peek(token).oauthToken())
                    .doesNotContain("script");

            request.removeParameter("oauth_token");
            request.setRequestURI("/carlos/login/sessionChoice");
            newAction(Login2Action.SESSION_CHOICE_KEEP).submitSessionChoice();

            verify(serviceRequestTokenDao, never()).findByTokenId(anyString());
            logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.LOGIN, LogConst.CON_LOGIN,
                    "invalid_oauth_token", "10.1.2.3"));
        }

        @Test
        @DisplayName("should not sign out other sessions when the chosen login fails setup")
        void shouldNotSignOutOthers_whenChosenLoginFailsSetup() throws Exception {
            stagePendingChoice();
            // The pre-check (ProviderDao) still sees an active provider; the login's own provider
            // load then fails, which invalidates the new session and returns an error.
            when(providerManager.getProvider(PROVIDER_NO)).thenReturn(null);

            newAction(Login2Action.SESSION_CHOICE_SIGN_OUT).submitSessionChoice();

            verify(userSessionManager, never()).invalidateOtherSessions(any(), any());
            logActionMock.verify(() -> LogAction.addLog(eq(PROVIDER_NO), eq(LogConst.LOGIN),
                    eq("concurrent_sessions_revoked"), anyString(), anyString()), never());
        }

        @Test
        @DisplayName("should reject a replayed chooser token")
        void shouldRejectReplayedToken_afterLoginCompletes() throws Exception {
            String token = stagePendingChoice();
            newAction(Login2Action.SESSION_CHOICE_KEEP).submitSessionChoice();

            // Replay the same token from a session that still carries it.
            response = new MockHttpServletResponse();
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
            MockHttpSession replaySession = new MockHttpSession();
            replaySession.setAttribute(PendingSessionChoices.TOKEN_ATTR, token);
            request.setSession(replaySession);

            String result = newAction(Login2Action.SESSION_CHOICE_SIGN_OUT).submitSessionChoice();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(decodedRedirect()).contains("/loginfailed");
            assertThat(replaySession.getAttribute("user")).isNull();
            verify(userSessionManager, never()).invalidateOtherSessions(any(), any());
        }

        @Test
        @DisplayName("should refuse keep and show the chooser again when the limit was reached meanwhile")
        void shouldRefuseKeepAndReshowChooser_whenLimitReachedMeanwhile() throws Exception {
            setPolicy("prompt", "2");
            String token = stagePendingChoice();
            when(userSessionManager.countOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(2);
            when(userSessionManager.describeOtherActiveSessions(eq(SECURITY_NO), any())).thenReturn(List.of(
                    new UserSessionManager.SessionInfo(Instant.EPOCH, Instant.EPOCH, null)));

            String result = newAction(Login2Action.SESSION_CHOICE_KEEP).submitSessionChoice();

            assertThat(result).isEqualTo(Login2Action.SESSION_CHOICE_RESULT);
            assertThat(request.getAttribute("sessionChoiceKeepRefused")).isEqualTo(Boolean.TRUE);
            assertThat(((ConcurrentSessionChoiceViewModel) request.getAttribute(
                    ConcurrentSessionChoiceViewModel.REQUEST_ATTR)).isSignOutRequired()).isTrue();
            assertThat(PendingSessionChoiceCache.getInstance().peek(token)).as("retryable: token stays live").isNotNull();
            assertThat(request.getSession(false).getAttribute("user")).isNull();
            verify(userSessionManager, never()).registerUserSession(any(), any(), any());
        }

        @Test
        @DisplayName("should not finish login when the provider was deactivated while the chooser was open")
        void shouldNotFinishLogin_whenProviderDeactivatedMeanwhile() throws Exception {
            String token = stagePendingChoice();
            Provider inactive = activeProvider();
            inactive.setStatus("0");
            when(providerDao.getProvider(PROVIDER_NO)).thenReturn(inactive);

            String result = newAction(Login2Action.SESSION_CHOICE_SIGN_OUT).submitSessionChoice();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(decodedRedirect()).contains("/loginfailed");
            assertThat(PendingSessionChoiceCache.getInstance().peek(token)).isNull();
            verify(userSessionManager, never()).invalidateOtherSessions(any(), any());
            verify(userSessionManager, never()).registerUserSession(any(), any(), any());
        }
    }

    private String signIn() throws Exception {
        Security security = security();
        when(securityManager.encodePassword(PASSWORD)).thenReturn("encoded");
        when(securityDao.findByUserName(USERNAME)).thenReturn(Collections.singletonList(security));
        try (MockedConstruction<LoginCheckLogin> ignored = mockConstruction(LoginCheckLogin.class,
                (mock, context) -> {
                    when(mock.auth(USERNAME, PASSWORD, PIN, "10.1.2.3")).thenReturn(STR_AUTH.clone());
                    when(mock.getSecurity()).thenReturn(security);
                    when(mock.isBlock(anyString(), anyString())).thenReturn(false);
                })) {
            Login2Action action = new Login2Action();
            action.setUsername(USERNAME);
            action.setPassword(PASSWORD);
            action.setPin(PIN);
            return action.execute();
        }
    }

    private String stagePendingChoice() {
        return stagePendingChoice(null);
    }

    private String stagePendingChoice(String oauthToken) {
        String token = PendingSessionChoiceCache.getInstance().store(
                new PendingSessionChoiceCache.PendingSessionChoice(SECURITY_NO, PROVIDER_NO, STR_AUTH, false, null,
                        oauthToken));
        PendingSessionChoices.stage(request.getSession(true), token);
        stagedTokens.add(token);
        return token;
    }

    private Login2Action newAction(String choice) {
        Login2Action action = new Login2Action();
        action.setSessionChoice(choice);
        return action;
    }

    private void stubProvider() {
        Provider provider = activeProvider();
        when(providerManager.getProvider(PROVIDER_NO)).thenReturn(provider);
        when(providerDao.getProvider(PROVIDER_NO)).thenReturn(provider);
        when(providerPreferenceDao.find(PROVIDER_NO)).thenReturn(new ProviderPreference());
        when(providerDao.getFacilityIds(PROVIDER_NO)).thenReturn(Collections.emptyList());
        when(facilityDao.findAll(true)).thenReturn(Collections.emptyList());
        when(securityDao.find(SECURITY_NO)).thenReturn(security());
        when(userSessionManager.countOtherActiveSessions(any(), any())).thenReturn(0);
        when(userSessionManager.invalidateOtherSessions(any(), any())).thenReturn(0);
    }

    private void setPolicy(String policy, String max) {
        restoreProperty(ConcurrentSessionPolicy.POLICY_PROPERTY, policy);
        restoreProperty(ConcurrentSessionPolicy.MAX_PROPERTY, max);
    }

    private void restoreProperty(String key, String value) {
        if (value == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, value);
        }
    }

    private String decodedRedirect() {
        return URLDecoder.decode(response.getRedirectedUrl(), StandardCharsets.UTF_8);
    }

    private static Security security() {
        Security security = new Security();
        security.setSecurityNo(SECURITY_NO);
        security.setUserName(USERNAME);
        security.setProviderNo(PROVIDER_NO);
        security.setPassword("encoded");
        security.setForcePasswordReset(Boolean.FALSE);
        return security;
    }

    private static Provider activeProvider() {
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER_NO);
        provider.setFirstName("Test");
        provider.setLastName("Provider");
        provider.setStatus("1");
        return provider;
    }
}
