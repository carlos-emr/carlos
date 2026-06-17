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

import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @DisplayName("should invalidate all sessions when unregistering a security code")
    void shouldInvalidateAllSessions_whenUnregisteringSecurityCode() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 2569;
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();

        manager.registerUserSession(securityCode, firstSession);
        manager.registerUserSession(securityCode, secondSession);

        HttpSession unregisteredSession = manager.unregisterUserSession(securityCode);

        assertThat(unregisteredSession).isIn(firstSession, secondSession);
        assertThat(firstSession.isInvalid()).isTrue();
        assertThat(secondSession.isInvalid()).isTrue();
        assertThat(manager.getRegisteredSession(securityCode)).isNull();
    }

    @Test
    @DisplayName("should unregister matching session id when session object differs")
    void shouldUnregisterMatchingSessionId_whenSessionObjectDiffers() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 2570;
        HttpSession registeredSession = mock(HttpSession.class);
        HttpSession destroyedSession = mock(HttpSession.class);
        when(registeredSession.getId()).thenReturn("same-session-id");
        when(destroyedSession.getId()).thenReturn("same-session-id");

        manager.registerUserSession(securityCode, registeredSession);

        assertThat(manager.unregisterUserSession(securityCode, destroyedSession)).isSameAs(destroyedSession);

        assertThat(manager.getRegisteredSession(securityCode)).isNull();
        verify(destroyedSession).removeAttribute(UserSessionManagerImpl.KEY_USER_SECURITY_CODE);
    }

    @Test
    @DisplayName("should return null when no session is registered")
    void shouldReturnNull_whenNoSessionIsRegistered() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();

        assertThat(manager.getRegisteredSession(2568)).isNull();
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
