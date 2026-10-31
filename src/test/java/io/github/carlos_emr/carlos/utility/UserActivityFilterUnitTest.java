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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Unit coverage for {@link UserActivityFilter}'s inactivity policy (issue #5305): activity
 * tracking, the autoRefresh/heartbeat exemptions, the logout-flow exemptions that stop a redirect
 * loop, one audit row per inactive session, and never creating a session.
 *
 * <p>Container behaviour (mapping order, REQUEST-only dispatch, Struts actions) is covered by
 * {@code RequestBoundaryFilterOrderIntegrationTest}.</p>
 */
@Tag("unit")
@Tag("filter")
@DisplayName("UserActivityFilter inactivity policy")
class UserActivityFilterUnitTest extends CarlosUnitTestBase {

    private static final String CONTEXT = "/carlos";
    private static final int TWO_HOURS = 7200;
    private static final long STALE = 3 * 60 * 60 * 1000L;

    private UserActivityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserActivityFilter();
    }

    @Test
    @DisplayName("should not create a session for an anonymous request")
    void shouldNotCreateSession_whenRequestIsAnonymous() throws Exception {
        MockHttpServletRequest request = request("/tickler/ticklerMain");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getSession(false)).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("should leave an unauthenticated session alone, even with a stale timestamp")
    void shouldIgnoreSession_whenSessionIsNotAuthenticated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(300);
        long stale = System.currentTimeMillis() - STALE;
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, stale);
        MockHttpServletRequest request = request("/mfa", session);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(session.getAttribute(UserActivityFilter.LAST_USER_ACTIVITY)).isEqualTo(stale);
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should treat a blank user attribute as unauthenticated")
    void shouldIgnoreSession_whenUserAttributeIsBlank() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "  ");
        MockHttpServletRequest request = request("/tickler/ticklerMain", session);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.getAttribute(UserActivityFilter.LAST_USER_ACTIVITY)).isNull();
    }

    @Test
    @DisplayName("should record activity for an authenticated user's own request")
    void shouldRecordActivity_whenAuthenticatedUserRequests() throws Exception {
        MockHttpSession session = authenticatedSession();
        MockHttpServletRequest request = request("/tickler/ticklerMain", session);
        MockFilterChain chain = new MockFilterChain();
        long before = System.currentTimeMillis();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat((Long) session.getAttribute(UserActivityFilter.LAST_USER_ACTIVITY))
                .isBetween(before, System.currentTimeMillis());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tickler/ticklerMain", "/form/cdsImportStatus"})
    @DisplayName("should not count an autoRefresh=true request as activity")
    void shouldNotRecordActivity_whenRequestAutoRefreshes(String path) throws Exception {
        MockHttpSession session = authenticatedSession();
        long recent = System.currentTimeMillis() - 60_000L;
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, recent);
        MockHttpServletRequest request = request(path, session);
        request.setParameter("autoRefresh", "true");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(session.getAttribute(UserActivityFilter.LAST_USER_ACTIVITY)).isEqualTo(recent);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/status/SessionHeartbeat", "/csrfguard", "/JavaScriptServlet",
            "/provider/ViewTabAlertsRefresh", "/provider/providercontrol", "/SystemMessage", "/FacilityMessage",
            "/library/jquery/jquery-3.7.1.min.js"})
    @DisplayName("should not count polls, LoginFilter's timer-exempt paths or CSRF script requests as activity")
    void shouldNotRecordActivity_forBackgroundPaths(String path) throws Exception {
        MockHttpSession session = authenticatedSession();
        long recent = System.currentTimeMillis() - 60_000L;
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, recent);
        MockHttpServletRequest request = request(path, session);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(session.getAttribute(UserActivityFilter.LAST_USER_ACTIVITY)).isEqualTo(recent);
    }

    @Test
    @DisplayName("should start the timer on the first authenticated request, even a background poll")
    void shouldStartTimer_whenFirstAuthenticatedRequestIsBackgroundPoll() throws Exception {
        MockHttpSession session = authenticatedSession();
        MockHttpServletRequest heartbeat = request("/status/SessionHeartbeat", session);
        heartbeat.setParameter("autoRefresh", "true");
        long before = System.currentTimeMillis();

        filter.doFilter(heartbeat, new MockHttpServletResponse(), new MockFilterChain());

        assertThat((Long) session.getAttribute(UserActivityFilter.LAST_USER_ACTIVITY))
                .as("a session that only ever polls must still time out")
                .isBetween(before, System.currentTimeMillis());
    }

    @Test
    @DisplayName("should never move the activity time backwards")
    void shouldKeepNewestActivity_whenOlderRequestFinishesLast() throws Exception {
        MockHttpSession session = authenticatedSession();
        long newer = System.currentTimeMillis() + 60_000L;
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, newer);

        filter.doFilter(request("/tickler/ticklerMain", session), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.getAttribute(UserActivityFilter.LAST_USER_ACTIVITY)).isEqualTo(newer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tickler/ticklerMain", "/form/cdsImportStatus"})
    @DisplayName("should redirect an inactive session to the logout page before the chain runs")
    void shouldRedirectToLogoutPage_whenSessionIsInactive(String path) throws Exception {
        MockHttpSession session = authenticatedSession();
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, System.currentTimeMillis() - STALE);
        MockHttpServletRequest request = request(path, session);
        request.setParameter("autoRefresh","true");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).as("the action must not run").isNull();
        assertThat(response.getRedirectedUrl())
                .isEqualTo(CONTEXT + "/logoutPage?autoLogout=true&errorMessage=logged%20out%20due%20to%20inactivity");
        logActionMock.verify(() -> LogAction.addLog("999998", LogConst.LOGOUT, LogConst.CON_LOGIN,
                "logged out due to inactivity", "192.0.2.10"));
    }

    @Test
    @DisplayName("should audit an inactive session once, however many requests it makes")
    void shouldAuditInactivityOnce_whenInactiveSessionKeepsPolling() throws Exception {
        MockHttpSession session = authenticatedSession();
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, System.currentTimeMillis() - STALE);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest heartbeat = request("/status/SessionHeartbeat", session);
            heartbeat.setParameter("autoRefresh", "true");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(heartbeat, response, new MockFilterChain());
            assertThat(response.getRedirectedUrl()).startsWith(CONTEXT + "/logoutPage?");
        }

        logActionMock.verify(() -> LogAction.addLog(anyString(), anyString(), anyString(), anyString(),
                ArgumentMatchers.<String>any()), times(1));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/logout", "/logoutPage", "/index", "/loginfailed", "/logout/"})
    @DisplayName("should never redirect the logout flow or the public login pages")
    void shouldNotRedirect_forLogoutFlowAndPublicPages(String path) throws Exception {
        MockHttpSession session = authenticatedSession();
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, System.currentTimeMillis() - STALE);
        MockHttpServletRequest request = request(path, session);
        request.setMethod("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getRedirectedUrl()).isNull();
        logActionMock.verify(() -> LogAction.addLog(anyString(), anyString(), anyString(), anyString(),
                ArgumentMatchers.<String>any()), never());
    }

    @ParameterizedTest(name = "maxInactiveInterval={0}")
    @ValueSource(ints = {0, -1})
    @DisplayName("should never judge a non-expiring session inactive")
    void shouldNotRedirect_whenSessionNeverExpires(int maxInactiveInterval) throws Exception {
        MockHttpSession session = authenticatedSession();
        session.setMaxInactiveInterval(maxInactiveInterval);
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, System.currentTimeMillis() - STALE);
        MockHttpServletRequest request = request("/tickler/ticklerMain", session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should keep an active session's request flowing")
    void shouldContinueChain_whenSessionIsWithinTimeout() throws Exception {
        MockHttpSession session = authenticatedSession();
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, System.currentTimeMillis() - 60_000L);
        MockHttpServletRequest request = request("/tickler/ticklerMain", session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getRedirectedUrl()).isNull();
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should audit with the session user when LoggedInInfo is missing")
    void shouldAuditWithUserAttribute_whenLoggedInInfoIsMissing() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(TWO_HOURS);
        session.setAttribute("user", "123456");
        session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY, System.currentTimeMillis() - STALE);
        MockHttpServletRequest request = request("/tickler/ticklerMain", session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).startsWith(CONTEXT + "/logoutPage?");
        logActionMock.verify(() -> LogAction.addLog(ArgumentMatchers.eq("123456"), anyString(), anyString(),
                anyString(), ArgumentMatchers.<String>any()));
    }

    @Test
    @DisplayName("should pass a chain failure through unchanged")
    void shouldPropagateChainFailure_whenDownstreamThrows() {
        MockHttpSession session = authenticatedSession();
        MockHttpServletRequest request = request("/tickler/ticklerMain", session);
        ServletException failure = new ServletException("downstream");
        FilterChain chain = (req, res) -> {
            throw failure;
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("should judge inactivity strictly after the session timeout")
    void shouldJudgeInactivity_onlyAfterTimeoutElapses() {
        long now = 10_000_000L;
        assertThat(UserActivityFilter.isInactive(now, now - 7_200_000L, TWO_HOURS)).isFalse();
        assertThat(UserActivityFilter.isInactive(now, now - 7_200_001L, TWO_HOURS)).isTrue();
        assertThat(UserActivityFilter.isInactive(now, 0L, 0)).isFalse();
    }

    private static MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(TWO_HOURS);
        session.setAttribute("user", "999998");
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);
        return session;
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", CONTEXT + path);
        request.setContextPath(CONTEXT);
        request.setServletPath(path);
        return request;
    }

    private static MockHttpServletRequest request(String path, MockHttpSession session) {
        MockHttpServletRequest request = request(path);
        request.setSession(session);
        return request;
    }
}
