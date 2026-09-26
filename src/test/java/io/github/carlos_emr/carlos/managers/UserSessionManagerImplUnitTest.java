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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    @DisplayName("should count only other live sessions for the user")
    void shouldCountOtherLiveSessions_forUser() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 3980;
        MockHttpSession current = new MockHttpSession();
        MockHttpSession other = new MockHttpSession();
        MockHttpSession expired = new MockHttpSession();
        manager.registerUserSession(securityCode, current);
        manager.registerUserSession(securityCode, other);
        manager.registerUserSession(securityCode, expired);
        manager.registerUserSession(3981, new MockHttpSession());
        expired.invalidate();

        assertThat(manager.countOtherActiveSessions(securityCode, current)).isEqualTo(1);
        assertThat(manager.countOtherActiveSessions(securityCode, null)).isEqualTo(2);
        assertThat(manager.countOtherActiveSessions(39800, current)).isZero();
        assertThat(manager.countOtherActiveSessions(null, current)).isZero();
    }

    @Test
    @DisplayName("should sign out other sessions and keep the current one")
    void shouldInvalidateOtherSessions_andKeepCurrentSession() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 3982;
        MockHttpSession keep = new MockHttpSession();
        MockHttpSession firstOther = new MockHttpSession();
        MockHttpSession secondOther = new MockHttpSession();
        MockHttpSession otherUser = new MockHttpSession();
        manager.registerUserSession(securityCode, keep);
        manager.registerUserSession(securityCode, firstOther);
        manager.registerUserSession(securityCode, secondOther);
        manager.registerUserSession(3983, otherUser);

        int revoked = manager.invalidateOtherSessions(securityCode, keep);

        assertThat(revoked).isEqualTo(2);
        assertThat(firstOther.isInvalid()).isTrue();
        assertThat(secondOther.isInvalid()).isTrue();
        assertThat(keep.isInvalid()).isFalse();
        assertThat(otherUser.isInvalid()).as("another user's session is never touched").isFalse();
        assertThat(manager.countOtherActiveSessions(securityCode, keep)).isZero();
        assertThat(RevokedUserSessions.consume(firstOther.getId())).isTrue();
        assertThat(RevokedUserSessions.consume(secondOther.getId())).isTrue();
        assertThat(RevokedUserSessions.consume(keep.getId())).isFalse();
    }

    @Test
    @DisplayName("should tolerate the session listener unregistering while other sessions are signed out")
    void shouldInvalidateOtherSessions_whenListenerUnregistersDuringInvalidate() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 3984;
        MockHttpSession keep = new MockHttpSession();
        AtomicReference<HttpSession> self = new AtomicReference<>();
        // Mirrors OscarSessionListener.sessionDestroyed(), which the container runs synchronously
        // inside invalidate() and which calls back into the registry for the same user.
        MockHttpSession other = new MockHttpSession() {
            @Override
            public void invalidate() {
                manager.unregisterUserSession(securityCode, self.get());
                super.invalidate();
            }
        };
        self.set(other);
        manager.registerUserSession(securityCode, keep);
        manager.registerUserSession(securityCode, other);

        assertThat(manager.invalidateOtherSessions(securityCode, keep)).isEqualTo(1);

        assertThat(other.isInvalid()).isTrue();
        assertThat(manager.getRegisteredSession(securityCode)).isSameAs(keep);
        RevokedUserSessions.consume(other.getId());
    }

    @Test
    @DisplayName("should not mark a session revoked when it was already invalidated")
    void shouldNotMarkRevoked_whenSessionAlreadyInvalidated() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 3985;
        HttpSession gone = mock(HttpSession.class);
        when(gone.getId()).thenReturn("gone-session-id");
        org.mockito.Mockito.doThrow(new IllegalStateException("already invalidated")).when(gone).invalidate();
        manager.registerUserSession(securityCode, gone);

        assertThat(manager.invalidateOtherSessions(securityCode, null)).isZero();
        assertThat(RevokedUserSessions.isRevoked("gone-session-id")).isFalse();
    }

    @Test
    @DisplayName("should describe other sessions newest first with their sign-in address")
    void shouldDescribeOtherSessions_withSignInAddress() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 3986;
        MockHttpSession current = new MockHttpSession();
        HttpSession older = describedSession("older-3986", 500L, 1_000L, "10.0.0.2");
        HttpSession newer = describedSession("newer-3986", 600L, 2_000L, null);
        manager.registerUserSession(securityCode, current, "10.0.0.1");
        manager.registerUserSession(securityCode, older, "10.0.0.2");
        manager.registerUserSession(securityCode, newer);

        List<UserSessionManager.SessionInfo> described = manager.describeOtherActiveSessions(securityCode, current);

        assertThat(described).hasSize(2);
        assertThat(described.get(0).lastActiveAt().toEpochMilli()).isEqualTo(2_000L);
        assertThat(described.get(0).signedInAt().toEpochMilli()).isEqualTo(600L);
        assertThat(described.get(0).remoteAddr()).isNull();
        assertThat(described.get(1).remoteAddr()).isEqualTo("10.0.0.2");
        assertThat(current.getAttribute(UserSessionManagerImpl.KEY_LOGIN_REMOTE_ADDR)).isEqualTo("10.0.0.1");
        verify(older).setAttribute(UserSessionManagerImpl.KEY_LOGIN_REMOTE_ADDR, "10.0.0.2");
    }

    private static HttpSession describedSession(String id, long createdAt, long lastAccessedAt, String remoteAddr) {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getCreationTime()).thenReturn(createdAt);
        when(session.getLastAccessedTime()).thenReturn(lastAccessedAt);
        when(session.getAttribute(UserSessionManagerImpl.KEY_LOGIN_REMOTE_ADDR)).thenReturn(remoteAddr);
        return session;
    }

    @Test
    @DisplayName("should publish the revocation marker before the session dies")
    void shouldPublishMarker_beforeInvalidating() {
        UserSessionManagerImpl manager = new UserSessionManagerImpl();
        Integer securityCode = 3987;
        MockHttpSession keep = new MockHttpSession();
        java.util.concurrent.atomic.AtomicBoolean markedWhileDying = new java.util.concurrent.atomic.AtomicBoolean();
        MockHttpSession other = new MockHttpSession() {
            @Override
            public void invalidate() {
                // What an old browser racing the destroy would see from the rejection path.
                markedWhileDying.set(RevokedUserSessions.isRevoked(getId()));
                super.invalidate();
            }
        };
        manager.registerUserSession(securityCode, keep);
        manager.registerUserSession(securityCode, other);

        manager.invalidateOtherSessions(securityCode, keep);

        assertThat(markedWhileDying.get()).isTrue();
        RevokedUserSessions.consume(other.getId());
    }
}
