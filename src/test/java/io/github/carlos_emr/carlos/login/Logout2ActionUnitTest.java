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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

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

    private static PendingMfaChallengeCache.PendingMfaChallenge challenge() {
        return new PendingMfaChallengeCache.PendingMfaChallenge(
                12345, "999998", new String[]{"999998", "Test"}, "secret");
    }
}
