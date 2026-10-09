/*
 * Copyright (c) 2026 CARLOS Contributors.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterSnapshot;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LoggedInUserFilter;
import io.github.carlos_emr.carlos.utility.UserActivityFilter;
import io.github.carlos_emr.carlos.webserv.WebServiceSessionInvalidatingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Regression for the session creator encountered before the signed footer servlet. */
@Tag("unit")
@Tag("security")
class UserActivityFooterSessionUnitTest {
    private static final String FOOTER_PATH = "/ws/portal/email-footer";
    private static final String NONCE = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String LAST_ACTIVITY = "LAST_USER_ACTIVITY";

    /** Detect creation even if a later web-service filter invalidates the session. */
    private static final class NonCreatingRequest extends MockHttpServletRequest {
        NonCreatingRequest(String method) {
            super(method, "/carlos" + FOOTER_PATH);
            setContextPath("/carlos");
            setServletPath(FOOTER_PATH);
        }

        @Override public HttpSession getSession() {
            throw new AssertionError("The signed footer request must never create a session");
        }

        @Override public HttpSession getSession(boolean create) {
            if (create) throw new AssertionError("The signed footer request must never create a session");
            return super.getSession(false);
        }
    }

    @ParameterizedTest
    @CsvSource({"GET, valid, 200", "GET, unauthorized, 401", "GET, root, 401",
            "GET, duplicate, 401", "GET, nonce, 400", "GET, unavailable, 503",
            "HEAD, valid, 405", "POST, valid, 405", "PUT, valid, 405",
            "DELETE, valid, 405", "OPTIONS, valid, 405"})
    void shouldKeepSessionRelevantFilterChainStateless_andPreserveServletChecks(
            String method, String scenario, int expectedStatus) throws Exception {
        var settings = new PatientPortalSettings("https://portal.example.test", "clinic-a",
                PortalSecret.of("FAKE-only-footer-root-token-2026-10-09"),
                PortalSecret.of(PortalTestKeys.PRIVATE_KEY), "fake-rfc8032",
                Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5),
                Set.of(PortalTestKeys.UNUSED_TLS_PIN));
        var request = new NonCreatingRequest(method);
        String bearer = "Bearer " + PortalClinicEmailFooterProtocol.readToken(settings).expose();
        request.addHeader("Authorization", scenario.equals("root")
                ? "Bearer FAKE-only-footer-root-token-2026-10-09"
                : scenario.equals("unauthorized") ? "Bearer invalid" : bearer);
        if (scenario.equals("duplicate")) request.addHeader("Authorization", bearer);
        request.setQueryString(scenario.equals("nonce") ? "nonce=invalid" : "nonce=" + NONCE);
        var response = new MockHttpServletResponse();
        var servlet = new PortalClinicEmailFooterServlet(() -> settings,
                () -> new ClinicEmailFooterSnapshot(scenario.equals("unavailable") ? "" : "FAKE Clinic", null));
        // Production order of the filters that read, create, or invalidate sessions.
        new LoggedInUserFilter().doFilter(request, response, (r1, s1) ->
                new WebServiceSessionInvalidatingFilter().doFilter(r1, s1, (r2, s2) ->
                        new UserActivityFilter().doFilter(r2, s2, (r3, s3) ->
                                servlet.service(request, response))));
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(response.getHeader("Location")).isNull();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        if (expectedStatus == 405) assertThat(response.getHeader("Allow")).isEqualTo("GET");
    }

    private MockHttpServletRequest browserRequest(String path, long lastActivity) {
        var request = new MockHttpServletRequest("GET", "/carlos" + path);
        request.setContextPath("/carlos");
        request.setServletPath(path);
        var session = request.getSession();
        session.setMaxInactiveInterval(60);
        session.setAttribute(LAST_ACTIVITY, lastActivity);
        var loggedIn = mock(LoggedInInfo.class);
        when(loggedIn.getLoggedInProviderNo()).thenReturn("999997");
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedIn);
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/provider/ViewAppointmentAdminDay", "/ws/portal/email-footer/",
            "/ws/portal/email-footer-extra", "/ws/portal/email-footer/extra", "/ws/rs/other"})
    void shouldPreserveBrowserActivity_forEveryOtherRoute(String path) throws Exception {
        var request = browserRequest(path, System.currentTimeMillis() - 10000);
        long previous = (Long) request.getSession(false).getAttribute(LAST_ACTIVITY);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new UserActivityFilter().doFilter(request, response, chain);
        assertThat((Long) request.getSession(false).getAttribute(LAST_ACTIVITY)).isGreaterThan(previous);
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldNotTreatExtraPathInfo_asTheExactFooterServlet() throws Exception {
        var request = browserRequest(FOOTER_PATH, System.currentTimeMillis() - 10000);
        request.setPathInfo("/extra");
        long previous = (Long) request.getSession(false).getAttribute(LAST_ACTIVITY);
        new UserActivityFilter().doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat((Long) request.getSession(false).getAttribute(LAST_ACTIVITY)).isGreaterThan(previous);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/provider/ViewAppointmentAdminDay", "/ws/portal/email-footer-extra"})
    void shouldPreserveInactivityLogout_forBrowserAndNeighboringRoutes(String path) throws Exception {
        var request = browserRequest(path, System.currentTimeMillis() - 120000);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        try (var logging = mockStatic(LogAction.class)) {
            new UserActivityFilter().doFilter(request, response, chain);
        }
        assertThat(response.getRedirectedUrl()).startsWith("/carlos/logoutPage?autoLogout=true");
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"autoRefresh", "JavaScriptServlet"})
    void shouldPreserveAutomaticRequestActivityExclusions(String scenario) throws Exception {
        var request = browserRequest(scenario.equals("JavaScriptServlet")
                ? "/JavaScriptServlet" : "/provider/ViewAppointmentAdminDay", System.currentTimeMillis() - 10000);
        if (scenario.equals("autoRefresh")) request.addParameter("autoRefresh", "true");
        Object previous = request.getSession(false).getAttribute(LAST_ACTIVITY);
        new UserActivityFilter().doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(request.getSession(false).getAttribute(LAST_ACTIVITY)).isEqualTo(previous);
    }

    @Test
    void shouldLeaveExistingStaffActivityUntouched_forExactFooterRoute() throws Exception {
        var request = browserRequest(FOOTER_PATH, System.currentTimeMillis() - 120000);
        Object previous = request.getSession(false).getAttribute(LAST_ACTIVITY);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new UserActivityFilter().doFilter(request, response, chain);
        assertThat(request.getSession(false).getAttribute(LAST_ACTIVITY)).isEqualTo(previous);
        assertThat(response.getRedirectedUrl()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldRenderHistorySeparators_evenWhenJasperDecodesTheFragmentAsLatin1() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("src/main/webapp/WEB-INF/jsp/admin/portalFooterAudit.jspf"));
        String decoded = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(org.jsoup.Jsoup.parse(decoded).select("summary").last().text()).contains("\u2014");
        assertThat(decoded).doesNotContain("\u00e2\u0080\u0094");
    }
}
