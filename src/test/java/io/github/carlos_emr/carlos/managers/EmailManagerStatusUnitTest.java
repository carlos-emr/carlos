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
    @DisplayName("should resolve a failed log while preserving its send time and clearing its error")
    void shouldResolveFailedEmail_preservingSendTime() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        EmailLog log = new EmailLog();
        ReflectionTestUtils.setField(log, "id", 7003);
        log.setStatus(EmailStatus.FAILED);
        log.setErrorMessage("FAKE delivery failure");
        Date timestamp = new Date(1760000000000L);
        log.setTimestamp(timestamp);
        when(emailLogDao.find(Integer.valueOf(7003))).thenReturn(log);
        when(emailLogDao.updateEmailStatus(7003, EmailStatus.RESOLVED, null, timestamp)).thenReturn(1);

        assertThat(emailManager.updateEmailStatus(loggedInInfo, 7003, EmailStatus.RESOLVED, null)).isSameAs(log);

        verify(emailLogDao).updateEmailStatus(7003, EmailStatus.RESOLVED, null, timestamp);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.RESOLVED);
        assertThat(log.getErrorMessage()).isNull();
        assertThat(log.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("should leave the log unchanged when the conditional resolution updates no row")
    void shouldKeepStatus_whenResolutionDoesNotUpdateRow() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        EmailLog log = new EmailLog();
        log.setStatus(EmailStatus.SUCCESS);
        log.setErrorMessage("FAKE original message");
        when(emailLogDao.find(Integer.valueOf(7003))).thenReturn(log);

        assertThat(emailManager.updateEmailStatus(loggedInInfo, 7003, EmailStatus.RESOLVED, null)).isSameAs(log);

        assertThat(log.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        assertThat(log.getErrorMessage()).isEqualTo("FAKE original message");
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
    @DisplayName("should deny both status-update entry points before accessing the database when write privilege is missing")
    void shouldDenyStatusUpdates_whenWritePrivilegeMissing() {
        assertThatThrownBy(() -> emailManager.updateEmailStatus(loggedInInfo, 7003, EmailStatus.RESOLVED, null))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_email)");
        assertThatThrownBy(() -> emailManager.updateEmailStatus(loggedInInfo, new EmailLog(), EmailStatus.RESOLVED, null))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_email)");
        verifyNoInteractions(emailLogDao);
    }
}
