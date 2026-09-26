/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for logout cleanup that is security-sensitive but independent of Struts views.
 */
@Tag("unit")
@Tag("security")
@DisplayName("Logout2Action")
class Logout2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletActionContextMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/logout");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should invalidate pending MFA cache when logging out")
    void shouldInvalidatePendingMfaCache_whenLoggingOut() {
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        String token = PendingMfaChallengeCache.getInstance().store(challenge());
        session.setAttribute(PendingMfaChallenges.AUTH_ATTR, Boolean.TRUE);
        session.setAttribute(PendingMfaChallenges.PROVIDER_NO_ATTR, "999998");
        session.setAttribute(PendingMfaChallenges.TOKEN_ATTR, token);

        try {
            String result = new Logout2Action().logout();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(session.isInvalid()).isTrue();
            assertThat(PendingMfaChallengeCache.getInstance().peek(token)).isNull();
        } finally {
            PendingMfaChallengeCache.getInstance().invalidate(token);
        }
    }

    @Test
    @DisplayName("should still delete cookies when a chooser login already invalidated the session")
    void shouldDeleteCookies_whenSessionAlreadyInvalidated() {
        HttpSession invalidated = mock(HttpSession.class);
        when(invalidated.getAttribute(anyString())).thenThrow(new IllegalStateException("invalidated"));
        doThrow(new IllegalStateException("invalidated")).when(invalidated).invalidate();
        request.setSession(invalidated);
        request.setCookies(new Cookie("JSESSIONID", "stale"));

        String result = new Logout2Action().logout();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        Cookie deletion = response.getCookie("JSESSIONID");
        assertThat(deletion).isNotNull();
        assertThat(deletion.getMaxAge()).isZero();
        assertThat(deletion.getValue()).isEmpty();
    }

    private static PendingMfaChallengeCache.PendingMfaChallenge challenge() {
        return new PendingMfaChallengeCache.PendingMfaChallenge(
                12345, "999998", new String[]{"999998", "Test"}, "secret");
    }
}
