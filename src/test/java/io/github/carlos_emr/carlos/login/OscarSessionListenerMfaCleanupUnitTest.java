/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.web.OscarSessionListener;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Session-listener coverage for pending MFA cache cleanup on timeout or container teardown.
 */
@Tag("unit")
@Tag("security")
@DisplayName("OscarSessionListener pending MFA cleanup")
class OscarSessionListenerMfaCleanupUnitTest extends CarlosUnitTestBase {
    private CasemgmtNoteLockDao casemgmtNoteLockDao;

    @BeforeEach
    void setUp() {
        casemgmtNoteLockDao = mock(CasemgmtNoteLockDao.class);
        registerMock(CasemgmtNoteLockDao.class, casemgmtNoteLockDao);
        registerMock(UserSessionManager.class, mock(UserSessionManager.class));
    }

    @Test
    @DisplayName("should invalidate pending MFA cache when session is destroyed")
    void shouldInvalidatePendingMfaCache_whenSessionIsDestroyed() {
        MockHttpSession session = new MockHttpSession();
        String token = PendingMfaChallengeCache.getInstance().store(challenge());
        session.setAttribute(PendingMfaChallenges.AUTH_ATTR, Boolean.TRUE);
        session.setAttribute(PendingMfaChallenges.PROVIDER_NO_ATTR, "999998");
        session.setAttribute(PendingMfaChallenges.TOKEN_ATTR, token);
        when(casemgmtNoteLockDao.findBySession(session.getId())).thenReturn(Collections.emptyList());

        try {
            new OscarSessionListener().sessionDestroyed(new HttpSessionEvent(session));

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
