/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.web;

import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    private Logger logger;
    private MockedStatic<MiscUtils> miscUtilsMock;

    @BeforeEach
    void setUp() {
        casemgmtNoteLockDao = mock(CasemgmtNoteLockDao.class);
        registerMock(CasemgmtNoteLockDao.class, casemgmtNoteLockDao);
        registerMock(UserSessionManager.class, mock(UserSessionManager.class));
        logger = mock(Logger.class);
        miscUtilsMock = mockStatic(MiscUtils.class);
        miscUtilsMock.when(MiscUtils::getLogger).thenReturn(logger);
    }

    @AfterEach
    void tearDown() {
        if (miscUtilsMock != null) {
            miscUtilsMock.close();
        }
    }

    @Test
    @DisplayName("should redact session identifier when session is created")
    void shouldRedactSessionIdentifier_whenSessionIsCreated() {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(RAW_SESSION_ID);

        new OscarSessionListener().sessionCreated(new HttpSessionEvent(session));

        verify(logger).info("Creating new OSCAR session.");
        verify(logger).info("Session id: {}", EXPECTED_SESSION_LOG_REFERENCE);
        verify(logger, never()).info(contains(RAW_SESSION_ID));
        verify(logger, never()).info(anyString(), eq(RAW_SESSION_ID));
    }

    @Test
    @DisplayName("should redact session identifier when session is destroyed")
    void shouldRedactSessionIdentifier_whenSessionIsDestroyed() {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(RAW_SESSION_ID);
        when(casemgmtNoteLockDao.findBySession(RAW_SESSION_ID)).thenReturn(Collections.emptyList());

        new OscarSessionListener().sessionDestroyed(new HttpSessionEvent(session));

        verify(logger).info("session is being destroyed - {}", EXPECTED_SESSION_LOG_REFERENCE);
        verify(logger, never()).info(contains(RAW_SESSION_ID));
        verify(logger, never()).info(anyString(), eq(RAW_SESSION_ID));
    }
}
