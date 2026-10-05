/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.web;

import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.fax.action.Fax2Action;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("OscarSessionListener logging")
class OscarSessionListenerLoggingUnitTest extends CarlosUnitTestBase {

    private static final String RAW_SESSION_ID = "abcdefgh1234567890";
    private static final String EXPECTED_SESSION_LOG_REFERENCE = "abcdefgh...";

    private CasemgmtNoteLockDao casemgmtNoteLockDao;
    private LogCapture logs;

    @BeforeEach
    void setUp() {
        casemgmtNoteLockDao = mock(CasemgmtNoteLockDao.class);
        registerMock(CasemgmtNoteLockDao.class, casemgmtNoteLockDao);
        registerMock(UserSessionManager.class, mock(UserSessionManager.class));
        registerMock(EFormRenderApprovalService.class, mock(EFormRenderApprovalService.class));
        // Session destruction also initializes Fax2Action. Mocking MiscUtils.getLogger()
        // here would permanently replace that class's static logger with the test mock.
        logs = LogCapture.forLogger(OscarSessionListener.class);
    }

    @AfterEach
    void tearDown() {
        if (logs != null) {
            logs.close();
        }
    }

    @Test
    @DisplayName("should redact session identifier when session is created")
    void shouldRedactSessionIdentifier_whenSessionIsCreated() {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(RAW_SESSION_ID);

        new OscarSessionListener().sessionCreated(new HttpSessionEvent(session));

        assertThat(logs.messages()).contains("Creating new OSCAR session.",
                "Session id: " + EXPECTED_SESSION_LOG_REFERENCE);
        assertThat(logs.messages().toString()).doesNotContain(RAW_SESSION_ID);
        assertThat(logs.events()).allMatch(event -> event.getThrown() == null);
    }

    @Test
    @DisplayName("should keep lock details private when destroying a session with an open chart")
    void shouldKeepLockDetailsPrivate_whenSessionHasOpenChart() {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(RAW_SESSION_ID);
        CasemgmtNoteLock lock = new CasemgmtNoteLock();
        lock.setId(7L);
        lock.setSessionId(RAW_SESSION_ID);
        lock.setDemographicNo(987654);
        lock.setProviderNo("1001");
        lock.setNoteId(0L);
        lock.setIpAddress("192.0.2.123");
        when(casemgmtNoteLockDao.findBySession(RAW_SESSION_ID)).thenReturn(List.of(lock));
        when(casemgmtNoteLockDao.removeAllForSession(RAW_SESSION_ID)).thenReturn(1);

        new OscarSessionListener().sessionDestroyed(new HttpSessionEvent(session));

        assertThat(logs.messages().toString()).doesNotContain(RAW_SESSION_ID, "987654", "192.0.2.123");
        assertThat(logs.messages()).contains("Removed 1 note locks for destroyed session");
        assertThat(logs.events()).allMatch(event -> event.getThrown() == null);
        verify(casemgmtNoteLockDao).removeAllForSession(RAW_SESSION_ID);
        verify(casemgmtNoteLockDao, never()).findBySession(RAW_SESSION_ID);
        verify(casemgmtNoteLockDao, never()).remove(lock.getId());
    }

    @Test
    @DisplayName("should redact session identifier when session is destroyed")
    void shouldRedactSessionIdentifier_whenSessionIsDestroyed() {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(RAW_SESSION_ID);

        new OscarSessionListener().sessionDestroyed(new HttpSessionEvent(session));

        assertThat(logs.messages()).contains("session is being destroyed - " + EXPECTED_SESSION_LOG_REFERENCE);
        assertThat(logs.messages().toString()).doesNotContain(RAW_SESSION_ID);
        assertThat(logs.events()).allMatch(event -> event.getThrown() == null);

        // Exercise real fax diagnostics after the exact session-destruction sequence
        // that formerly poisoned its static logger in a shared Surefire fork.
        try (var faxLogs = LogCapture.forLogger(Fax2Action.class)) {
            Fax2Action action = mock(Fax2Action.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(action,
                    "deleteRejectedClaimedFaxFile", "PRIVATE_CLINICAL_PATH\u0000.pdf");
            assertThat(faxLogs.messages()).anyMatch(message -> message.contains("InvalidPathException"));
            assertThat(faxLogs.messages().toString()).doesNotContain("PRIVATE_CLINICAL_PATH");
            assertThat(faxLogs.events()).allMatch(event -> event.getThrown() == null);
        }
    }
}
