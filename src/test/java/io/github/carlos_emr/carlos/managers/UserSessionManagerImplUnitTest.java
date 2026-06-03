/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.exception.UserSessionNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("security")
@DisplayName("UserSessionManagerImpl")
class UserSessionManagerImplUnitTest {

    @Test
    @DisplayName("should allow multiple sessions for the same user")
    void shouldAllowMultipleSessions_forSameUser() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 2565;
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();

        manager.registerUserSession(securityCode, firstSession);
        manager.registerUserSession(securityCode, secondSession);

        assertThatCode(firstSession::getId).doesNotThrowAnyException();
        assertThatCode(secondSession::getId).doesNotThrowAnyException();
        assertThat(firstSession.getAttribute(UserSessionManagerImpl.KEY_USER_SECURITY_CODE))
                .isEqualTo(securityCode);
        assertThat(secondSession.getAttribute(UserSessionManagerImpl.KEY_USER_SECURITY_CODE))
                .isEqualTo(securityCode);
    }

    @Test
    @DisplayName("should unregister only the destroyed session")
    void shouldUnregisterOnlyDestroyedSession_forSameUser() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 2566;
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();

        manager.registerUserSession(securityCode, firstSession);
        manager.registerUserSession(securityCode, secondSession);

        assertThat(manager.unregisterUserSession(securityCode, firstSession)).isSameAs(firstSession);

        assertThat(firstSession.getAttribute(UserSessionManagerImpl.KEY_USER_SECURITY_CODE)).isNull();
        assertThat(secondSession.getAttribute(UserSessionManagerImpl.KEY_USER_SECURITY_CODE))
                .isEqualTo(securityCode);
        assertThat(manager.getRegisteredSession(securityCode)).isSameAs(secondSession);
    }

    @Test
    @DisplayName("should throw when unregistering an unknown session")
    void shouldThrow_whenUnregisteringUnknownSession() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        MockHttpSession unknownSession = new MockHttpSession();

        assertThatThrownBy(() -> manager.unregisterUserSession(2567, unknownSession))
                .isInstanceOf(UserSessionNotFoundException.class);
    }
}
