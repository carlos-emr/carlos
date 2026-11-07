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
    @Mock private io.github.carlos_emr.carlos.commn.dao.OscarLogDao oscarLogDao;
    private EmailManager emailManager;
    private final LoggedInInfo loggedInInfo = new LoggedInInfo();

    @org.junit.jupiter.api.BeforeEach
    void wireManager() {
        emailManager = new EmailManager(org.mockito.Mockito.mock(io.github.carlos_emr.carlos.email.core.EmailConsentResolver.class),
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.email.core.EmailSenderFactory.class), securityInfoManager,
                org.mockito.Mockito.mock(OutboundEmailArchiveService.class));
        ReflectionTestUtils.setField(emailManager, "emailLogDao", emailLogDao);
        ReflectionTestUtils.setField(emailManager, "oscarLogDao", oscarLogDao);
    }

    private EmailLog stored(EmailStatus status) {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        EmailLog log = new EmailLog();
        ReflectionTestUtils.setField(log, "id", 7003);
        log.setTransactionType(EmailLog.TransactionType.DIRECT);
        log.setStatus(status); log.setErrorMessage("FAKE delivery failure");
        log.setTimestamp(new Date(1760000000000L));
        when(emailLogDao.find(Integer.valueOf(7003))).thenReturn(log);
        return log;
    }

    @Test
    void shouldPreserveDiagnosticAndSendTime_whenFailedSendIsResolved() {
        EmailLog log = stored(EmailStatus.FAILED); Date timestamp = log.getTimestamp();
        when(emailLogDao.transitionEmailStatus(7003, EmailStatus.FAILED, EmailStatus.RESOLVED,
                "FAKE delivery failure", timestamp)).thenReturn(1);
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 7003)).isEqualTo(EmailManager.EmailResolutionResult.RESOLVED);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.RESOLVED);
        assertThat(log.getErrorMessage()).isEqualTo("FAKE delivery failure");
        assertThat(log.getTimestamp()).isEqualTo(timestamp);
        verify(emailLogDao).transitionEmailStatus(7003, EmailStatus.FAILED, EmailStatus.RESOLVED, "FAKE delivery failure", timestamp);
    }

    @Test
    void shouldLeaveSuccessfulDeliveryUnchanged_whenManualResolutionIsRefused() {
        EmailLog log = stored(EmailStatus.SUCCESS);
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 7003)).isEqualTo(EmailManager.EmailResolutionResult.NOT_RESOLVABLE);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        verify(emailLogDao).find(Integer.valueOf(7003)); verifyNoMoreInteractions(emailLogDao);
        verifyNoInteractions(oscarLogDao);
    }

    @Test
    void shouldReturnConflict_whenTransportWinsTheAtomicTransition() {
        EmailLog log = stored(EmailStatus.FAILED);
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 7003)).isEqualTo(EmailManager.EmailResolutionResult.CONFLICT);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("FAKE delivery failure");
        verifyNoInteractions(oscarLogDao);
    }

    @Test
    void shouldReturnNotFound_whenLogDoesNotExist() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 7004)).isEqualTo(EmailManager.EmailResolutionResult.NOT_FOUND);
        verify(emailLogDao).find(Integer.valueOf(7004)); verifyNoMoreInteractions(emailLogDao);
    }

    @Test
    void shouldRejectNullTransportStatus_beforeWriting() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        assertThatThrownBy(() -> emailManager.updateEmailStatus(loggedInInfo, new EmailLog(), null, null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(emailLogDao);
    }

    @Test
    void shouldDenyResolutionAndTransport_beforeDatabaseAccess() {
        assertThatThrownBy(() -> emailManager.resolveEmailStatus(loggedInInfo, 7003)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> emailManager.updateEmailStatus(loggedInInfo, new EmailLog(), EmailStatus.FAILED, null))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(emailLogDao);
    }
}
