/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.Date;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.managers.EmailManager.EmailResolutionResult;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("email")
@Tag("security")
@DisplayName("EmailManager status updates")
class EmailManagerStatusUnitTest {
    @Mock private EmailLogDaoImpl emailLogDao;
    @Mock private OscarLogDao oscarLogDao;
    @Mock private SecurityInfoManager securityInfoManager;
    @InjectMocks private EmailManager emailManager;
    private final LoggedInInfo loggedInInfo = new LoggedInInfo();

    @org.junit.jupiter.api.BeforeEach
    void injectLegacyCollaborators() {
        ReflectionTestUtils.setField(emailManager, "emailLogDao", emailLogDao);
        ReflectionTestUtils.setField(emailManager, "oscarLogDao", oscarLogDao);
    }

    private EmailLog failedLog() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        EmailLog log = new EmailLog();
        ReflectionTestUtils.setField(log, "id", 7003);
        log.setStatus(EmailStatus.FAILED);
        log.setErrorMessage("FAKE delivery failure");
        log.setTimestamp(new Date(1760000000000L));
        when(emailLogDao.find(Integer.valueOf(7003))).thenReturn(log);
        return log;
    }

    @Test
    @DisplayName("should preserve the diagnostic and send time when resolving a failed send")
    void shouldPreserveDiagnosticAndTime_whenResolvingFailedSend() {
        EmailLog log = failedLog();
        Date timestamp = log.getTimestamp();
        when(emailLogDao.transitionEmailStatus(7003, EmailStatus.FAILED, EmailStatus.RESOLVED,
                log.getErrorMessage(), timestamp)).thenReturn(1);

        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 7003)).isEqualTo(EmailResolutionResult.RESOLVED);

        verify(emailLogDao).transitionEmailStatus(7003, EmailStatus.FAILED, EmailStatus.RESOLVED,
                "FAKE delivery failure", timestamp);
        verify(oscarLogDao).persist(any());
        assertThat(log.getStatus()).isEqualTo(EmailStatus.RESOLVED);
        assertThat(log.getErrorMessage()).isEqualTo("FAKE delivery failure");
        assertThat(log.getTimestamp()).isSameAs(timestamp);
    }

    @Test
    @DisplayName("should leave a successful send unchanged when it cannot be resolved")
    void shouldKeepStatus_whenSendCannotBeResolved() {
        EmailLog log = failedLog();
        log.setStatus(EmailStatus.SUCCESS);
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 7003)).isEqualTo(EmailResolutionResult.NOT_RESOLVABLE);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        assertThat(log.getErrorMessage()).isEqualTo("FAKE delivery failure");
        verify(emailLogDao).find(Integer.valueOf(7003));
        verifyNoMoreInteractions(emailLogDao);
        verifyNoInteractions(oscarLogDao);
    }

    @Test
    @DisplayName("should report a conflict without changing the snapshot when another request wins")
    void shouldReportConflict_whenAnotherResolverUpdatesFirst() {
        EmailLog stale = failedLog();
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 7003)).isEqualTo(EmailResolutionResult.CONFLICT);
        assertThat(stale.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(stale.getErrorMessage()).isEqualTo("FAKE delivery failure");
        verifyNoInteractions(oscarLogDao);
    }

    @Test
    @DisplayName("should return not found without updating when the log does not exist")
    void shouldReturnNotFound_whenLogDoesNotExist() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 7004)).isEqualTo(EmailResolutionResult.NOT_FOUND);
        verify(emailLogDao).find(Integer.valueOf(7004));
        verifyNoMoreInteractions(emailLogDao);
    }

    @Test
    @DisplayName("should reject a null transport status before writing to the database")
    void shouldRejectNullStatus_beforeUpdatingLog() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        EmailLog log = new EmailLog();
        log.setStatus(EmailStatus.PENDING);
        assertThatThrownBy(() -> emailManager.updateEmailStatus(loggedInInfo, log, null, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(emailLogDao);
    }

    @Test
    @DisplayName("should deny resolution and transport updates before database access when write privilege is missing")
    void shouldDenyStatusUpdates_whenWritePrivilegeMissing() {
        assertThatThrownBy(() -> emailManager.resolveEmailStatus(loggedInInfo, 7003))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_email)");
        assertThatThrownBy(() -> emailManager.updateEmailStatus(loggedInInfo, new EmailLog(), EmailStatus.SUCCESS, null))
                .isInstanceOf(RuntimeException.class).hasMessage("missing required sec object (_email)");
        verifyNoInteractions(emailLogDao);
    }
}
