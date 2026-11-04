/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.managers;

import java.util.Date;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
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
    @Mock private SecurityInfoManager securityInfoManager;
    @InjectMocks private EmailManager emailManager;
    private final LoggedInInfo loggedInInfo = new LoggedInInfo();

    @Test
    @DisplayName("should return the stored resolution with the latest send time instead of the old snapshot")
    void shouldReturnStoredResolution_whenSendTimeChanged() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        EmailLog log = new EmailLog();
        ReflectionTestUtils.setField(log, "id", 7003);
        log.setStatus(EmailStatus.FAILED);
        log.setErrorMessage("FAKE delivery failure");
        Date timestamp = new Date(1760000000000L);
        log.setTimestamp(timestamp);
        EmailLog stored = new EmailLog();
        stored.setStatus(EmailStatus.RESOLVED);
        Date latestTime = new Date(timestamp.getTime() + 60000);
        stored.setTimestamp(latestTime);
        when(emailLogDao.find(Integer.valueOf(7003))).thenReturn(log);
        when(emailLogDao.findFresh(7003)).thenReturn(stored);
        when(emailLogDao.updateEmailStatus(7003, EmailStatus.RESOLVED, null, null)).thenReturn(1);

        EmailLog result = emailManager.updateEmailStatus(loggedInInfo, 7003, EmailStatus.RESOLVED, null);

        verify(emailLogDao).updateEmailStatus(7003, EmailStatus.RESOLVED, null, null);
        assertThat(result).isSameAs(stored);
        assertThat(result.getStatus()).isEqualTo(EmailStatus.RESOLVED);
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getTimestamp()).isEqualTo(latestTime);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(log.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("should leave the log unchanged when the conditional resolution updates no row")
    void shouldKeepStatus_whenResolutionDoesNotUpdateRow() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        EmailLog log = new EmailLog();
        ReflectionTestUtils.setField(log, "id", 7003);
        log.setStatus(EmailStatus.SUCCESS);
        log.setErrorMessage("FAKE original message");
        when(emailLogDao.find(Integer.valueOf(7003))).thenReturn(log);
        when(emailLogDao.findFresh(7003)).thenReturn(log);

        assertThat(emailManager.updateEmailStatus(loggedInInfo, 7003, EmailStatus.RESOLVED, null)).isSameAs(log);

        assertThat(log.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        assertThat(log.getErrorMessage()).isEqualTo("FAKE original message");
    }

    @Test
    @DisplayName("should return the stored state when another resolver wins the conditional update")
    void shouldReloadStatus_whenAnotherResolverUpdatesFirst() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        EmailLog stale = new EmailLog();
        ReflectionTestUtils.setField(stale, "id", 7003);
        stale.setStatus(EmailStatus.FAILED);
        EmailLog resolved = new EmailLog();
        resolved.setStatus(EmailStatus.RESOLVED);
        when(emailLogDao.find(Integer.valueOf(7003))).thenReturn(stale);
        when(emailLogDao.findFresh(7003)).thenReturn(resolved);

        assertThat(emailManager.updateEmailStatus(loggedInInfo, 7003, EmailStatus.RESOLVED, null)).isSameAs(resolved);
        assertThat(stale.getStatus()).isEqualTo(EmailStatus.FAILED);
    }

    @Test
    @DisplayName("should return not found without updating when the log does not exist")
    void shouldReturnNotFound_whenLogDoesNotExist() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);

        assertThat(emailManager.updateEmailStatus(loggedInInfo, 7004, EmailStatus.RESOLVED, null)).isNull();

        verify(emailLogDao).find(Integer.valueOf(7004));
        verifyNoMoreInteractions(emailLogDao);
    }

    @Test
    @DisplayName("should reject a null status before writing to the database")
    void shouldRejectNullStatus_beforeUpdatingLog() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);

        EmailLog log = new EmailLog();
        assertThatThrownBy(() -> emailManager.updateEmailStatus(loggedInInfo, log, null, null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(emailLogDao);
    }

    @Test
    @DisplayName("should deny both status-update entry points before accessing the database when write privilege is missing")
    void shouldDenyStatusUpdates_whenWritePrivilegeMissing() {
        EmailLog log = new EmailLog();
        assertThatThrownBy(() -> emailManager.updateEmailStatus(loggedInInfo, 7003, EmailStatus.RESOLVED, null))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_email)");
        assertThatThrownBy(() -> emailManager.updateEmailStatus(loggedInInfo, log, EmailStatus.RESOLVED, null))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_email)");
        verifyNoInteractions(emailLogDao);
    }
}
