/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailConsentResult;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.email.core.EmailStatusResult;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailManager")
class EmailManagerUnitTest extends CarlosUnitTestBase {

    private EmailManager emailManager;
    private EmailConfigDaoImpl emailConfigDao;
    private EmailLogDaoImpl emailLogDao;
    private OscarLogDao oscarLogDao;
    private DemographicManager demographicManager;
    private ProviderManager2 providerManager;
    private SecurityInfoManager securityInfoManager;
    private EmailConsentResolver emailConsentResolver;
    private EmailSenderFactory emailSenderFactory;
    private EmailSender emailSender;
    private LoggedInInfo loggedInInfo;
    private Demographic demographic;
    private Provider provider;

    @BeforeEach
    void setUp() {
        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailLogDao = mock(EmailLogDaoImpl.class);
        oscarLogDao = mock(OscarLogDao.class);
        demographicManager = mock(DemographicManager.class);
        providerManager = mock(ProviderManager2.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        emailConsentResolver = mock(EmailConsentResolver.class);
        emailSenderFactory = mock(EmailSenderFactory.class);
        emailSender = mock(EmailSender.class);
        loggedInInfo = mock(LoggedInInfo.class);
        demographic = new Demographic();
        demographic.setDemographicNo(123);
        demographic.setFirstName("Patient");
        demographic.setLastName("Example");
        provider = new Provider("999998");
        provider.setFirstName("Provider");
        provider.setLastName("Example");

        emailManager = new EmailManager(emailConsentResolver, emailSenderFactory,
                mock(OutboundEmailArchiveService.class));
        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "oscarLogDao", oscarLogDao);
        injectDependency(emailManager, "caseManagementManager", mock(CaseManagementManager.class));
        injectDependency(emailManager, "demographicManager", demographicManager);
        injectDependency(emailManager, "documentAttachmentManager", mock(DocumentAttachmentManager.class));
        injectDependency(emailManager, "programManager", mock(ProgramManager.class));
        injectDependency(emailManager, "providerManager", providerManager);
        injectDependency(emailManager, "securityInfoManager", securityInfoManager);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_email"), anyString(), nullable(String.class)))
                .thenReturn(true);
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(demographic);
        when(providerManager.getProvider(loggedInInfo, "999998")).thenReturn(provider);
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult(
                        "Email", EmailConsentStatus.OPT_IN, null, null));
        when(emailSenderFactory.create(any(), any(), any())).thenReturn(emailSender);
        when(emailLogDao.transitionEmailStatus(
                nullable(Integer.class), any(EmailStatus.class), any(EmailStatus.class),
                nullable(String.class), any(Date.class))).thenReturn(1);
    }

    @Test
    @DisplayName("should return failed result when sender config id is missing")
    void shouldReturnFailedResult_whenSenderConfigIdIsMissing() {
        EmailData emailData = emailData(null);
        emailData.setAttachments(Collections.singletonList(new EmailAttachment("lab.pdf", "/tmp/lab.pdf", DocumentType.LAB, 99)));

        EmailLog result = emailManager.sendEmail(loggedInInfo, emailData);

        assertMisconfiguredSenderFailure(result);
        verify(emailConfigDao, never()).findActiveEmailConfigById(anyInt());
        verify(emailLogDao, never()).persist(any(EmailLog.class));
        verify(demographicManager, never()).getDemographic(any(LoggedInInfo.class), anyInt());
        verify(providerManager, never()).getProvider(any(LoggedInInfo.class), anyString());
        verifyNoInteractions(emailConsentResolver, emailSenderFactory, emailSender);
        assertThat(result.getDemographic()).isNull();
        assertThat(result.getProvider()).isNull();
        assertThat(result.getEmailAttachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.getEmailLog()).isSameAs(result);
            assertThat(attachment.getFileName()).isEqualTo("lab.pdf");
            assertThat(attachment.getFilePath()).isEqualTo("/tmp/lab.pdf");
            assertThat(attachment.getDocumentType()).isEqualTo(DocumentType.LAB);
            assertThat(attachment.getDocumentId()).isEqualTo(99);
        });
    }

    @Test
    @DisplayName("should return failed result when sender config is missing or inactive")
    void shouldReturnFailedResult_whenSenderConfigIsMissingOrInactive() {
        EmailData emailData = emailData(123);
        when(emailConfigDao.findActiveEmailConfigById(123)).thenReturn(null);

        EmailLog result = emailManager.sendEmail(loggedInInfo, emailData);

        assertMisconfiguredSenderFailure(result);
        verify(emailConfigDao).findActiveEmailConfigById(123);
        verify(emailLogDao, never()).persist(any(EmailLog.class));
        verify(demographicManager, never()).getDemographic(any(LoggedInInfo.class), anyInt());
        verify(providerManager, never()).getProvider(any(LoggedInInfo.class), anyString());
        verifyNoInteractions(emailConsentResolver, emailSenderFactory, emailSender);
        assertThat(result.getDemographic()).isNull();
        assertThat(result.getProvider()).isNull();
    }

    @Test
    @DisplayName("should return failed result when sender config is missing and optional fields are unset")
    void shouldReturnFailedResult_whenSenderConfigIsMissingAndOptionalFieldsAreUnset() {
        EmailData emailData = new EmailData();
        emailData.setRecipients(new String[] {"recipient@example.invalid"});
        emailData.setSubject("Subject");
        emailData.setBody("Body");

        EmailLog result = emailManager.sendEmail(loggedInInfo, emailData);

        assertMisconfiguredSenderFailure(result);
        verify(emailConfigDao, never()).findActiveEmailConfigById(anyInt());
        verify(emailLogDao, never()).persist(any(EmailLog.class));
        verify(demographicManager, never()).getDemographic(any(LoggedInInfo.class), anyInt());
        verify(providerManager, never()).getProvider(any(LoggedInInfo.class), anyString());
        verifyNoInteractions(emailConsentResolver, emailSenderFactory, emailSender);
        assertThat(result.getEmailAttachments()).isEmpty();
    }

    @Test
    @DisplayName("should not expose raw config or secrets in sender config failure")
    void shouldNotExposeRawConfigOrSecrets_whenSenderConfigFails() {
        EmailData emailData = emailData(456);
        emailData.setRecipients(new String[] {"patient@example.invalid"});
        emailData.setAdditionalParams("sendgridApiKey=SG.raw-secret-token;smtpPassword=raw-password");
        when(emailConfigDao.findActiveEmailConfigById(456)).thenReturn(null);

        EmailLog result = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(result.getErrorMessage()).isEqualTo(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
        assertThat(result.getErrorMessage())
                .doesNotContain("456")
                .doesNotContain("patient@example.invalid")
                .doesNotContain("SG.raw-secret-token")
                .doesNotContain("raw-password")
                .doesNotContain("sendgridApiKey")
                .doesNotContain("smtpPassword");
        verifyNoInteractions(emailConsentResolver, emailSenderFactory, emailSender);
        ArgumentCaptor<OscarLog> auditCaptor = ArgumentCaptor.forClass(OscarLog.class);
        verify(oscarLogDao).persist(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction())
                .isEqualTo("EmailManager.preTransportFailure");
        assertThat(auditCaptor.getValue().getData())
                .contains("reason=sender_configuration_unavailable")
                .contains("senderConfigId=456")
                .doesNotContain("SG.raw-secret-token")
                .doesNotContain("raw-password")
                .doesNotContain("patient@example.invalid");
    }

    @Test
    @DisplayName("should persist pending before transport and record success afterward")
    void shouldPersistPendingBeforeTransport_andRecordSuccessAfterward() throws EmailSendingException {
        EmailData emailData = emailData(123);
        when(emailConfigDao.findActiveEmailConfigById(123)).thenReturn(activeSenderConfig());
        doAnswer(invocation -> {
            EmailLog persisted = invocation.getArgument(0);
            assertThat(persisted.getStatus()).isEqualTo(EmailStatus.PENDING);
            assertThat(persisted.getErrorMessage()).isNull();
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        EmailLog result = emailManager.sendEmail(loggedInInfo, emailData);

        org.mockito.InOrder lifecycle = inOrder(emailLogDao, emailSender);
        lifecycle.verify(emailLogDao).persist(any(EmailLog.class));
        lifecycle.verify(emailSender).send();
        lifecycle.verify(emailLogDao).transitionEmailStatus(
                nullable(Integer.class), eq(EmailStatus.PENDING), eq(EmailStatus.SUCCESS),
                eq(""), any(Date.class));

        assertThat(result.getStatus()).isEqualTo(EmailStatus.SUCCESS);
    }

    @Test
    @DisplayName("should record failed when transport reports a send failure")
    void shouldRecordFailed_whenTransportReportsFailure() throws EmailSendingException {
        EmailData emailData = emailData(123);
        when(emailConfigDao.findActiveEmailConfigById(123)).thenReturn(activeSenderConfig());
        doAnswer(invocation -> {
            assertThat(((EmailLog) invocation.getArgument(0)).getStatus())
                    .isEqualTo(EmailStatus.PENDING);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        doThrow(new EmailSendingException("transport failed")).when(emailSender).send();

        EmailLog result = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(result.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(result.getErrorMessage())
                .isEqualTo("Failed to send email (uncategorized delivery failure)");
        verify(emailLogDao).transitionEmailStatus(
                nullable(Integer.class), eq(EmailStatus.PENDING), eq(EmailStatus.FAILED),
                eq("Failed to send email (uncategorized delivery failure)"), any(Date.class));
    }

    @Test
    @DisplayName("should audit a definite failure when its status cannot be persisted")
    void shouldAuditDefiniteFailure_whenFailedStatusWriteFails() throws EmailSendingException {
        EmailData emailData = emailData(123);
        when(emailConfigDao.findActiveEmailConfigById(123)).thenReturn(activeSenderConfig());
        doThrow(new IllegalStateException("database unavailable"))
                .when(emailLogDao).transitionEmailStatus(
                        nullable(Integer.class), eq(EmailStatus.PENDING), eq(EmailStatus.FAILED),
                        eq("Failed to send email (uncategorized delivery failure)"), any(Date.class));

        doThrow(new EmailSendingException("transport failed")).when(emailSender).send();

        EmailSendResult result = emailManager.sendEmailWithResult(loggedInInfo, emailData);

        assertThat(result.getEmailLog().getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(result.isTransportOutcomeRecorded()).isFalse();
        ArgumentCaptor<OscarLog> auditCaptor = ArgumentCaptor.forClass(OscarLog.class);
        verify(oscarLogDao).persist(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction())
                .isEqualTo("EmailManager.transportOutcomeNotRecorded");
        assertThat(auditCaptor.getValue().getData())
                .isEqualTo("transportOutcome=FAILED; statusRecorded=false");
    }

    @Test
    @DisplayName("should keep pending when transport cannot confirm whether dispatch succeeded")
    void shouldKeepPending_whenTransportOutcomeIsUnconfirmed() throws EmailSendingException {
        EmailData emailData = emailData(123);
        when(emailConfigDao.findActiveEmailConfigById(123)).thenReturn(activeSenderConfig());

        doThrow(new EmailSendingException(
                "transport timed out", new java.io.IOException("timeout"), true))
                .when(emailSender).send();

        EmailSendResult result = emailManager.sendEmailWithResult(loggedInInfo, emailData);

        assertThat(result.isDeliveryUnconfirmed()).isTrue();
        assertThat(result.isTransportAccepted()).isFalse();
        assertThat(result.isTransportOutcomeRecorded()).isFalse();
        assertThat(result.getEmailLog().getStatus()).isEqualTo(EmailStatus.PENDING);
        verify(emailLogDao, never()).transitionEmailStatus(
                nullable(Integer.class), any(EmailStatus.class), any(EmailStatus.class),
                nullable(String.class), any(Date.class));
    }

    @Test
    @DisplayName("should report accepted without inviting retry when success status cannot be persisted")
    void shouldReportAccepted_whenSuccessStatusWriteFails() {
        EmailData emailData = emailData(123);
        when(emailConfigDao.findActiveEmailConfigById(123)).thenReturn(activeSenderConfig());
        AtomicReference<EmailLog> persistedLog = new AtomicReference<>();
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.PENDING);
            persistedLog.set(emailLog);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));
        doThrow(new IllegalStateException("database unavailable"))
                .when(emailLogDao).transitionEmailStatus(
                        nullable(Integer.class), eq(EmailStatus.PENDING), eq(EmailStatus.SUCCESS),
                        eq(""), any(Date.class));

        EmailSendResult result = emailManager.sendEmailWithResult(loggedInInfo, emailData);

        assertThat(result.isTransportAccepted()).isTrue();
        assertThat(result.isTransportOutcomeRecorded()).isFalse();
        assertThat(persistedLog.get()).isNotNull();
        assertThat(persistedLog.get().getStatus()).isEqualTo(EmailStatus.PENDING);
    }

    @Test
    @DisplayName("should block send and skip sender when patient opted out")
    void shouldBlockSendAndSkipSender_whenPatientOptedOut() {
        EmailData emailData = emailData(10);
        when(emailConfigDao.findActiveEmailConfigById(10)).thenReturn(activeSenderConfig());
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult(
                        "Email", EmailConsentStatus.OPT_OUT, 55, new Date()));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.OPT_OUT);
        assertThat(emailLog.getConsentId()).isEqualTo(55);
        verify(emailLogDao).persist(any(EmailLog.class));
        verify(emailLogDao).merge(emailLog);
        verify(emailLogDao).transitionEmailStatus(
                eq(emailLog.getId()), eq(EmailStatus.PENDING), eq(EmailStatus.BLOCKED),
                anyString(), any(Date.class));
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should block send when patient opted out even with override")
    void shouldBlockSend_whenPatientOptedOutEvenWithOverride() {
        EmailData emailData = emailData(10);
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("Patient verbally confirmed email consent");
        when(emailConfigDao.findActiveEmailConfigById(10)).thenReturn(activeSenderConfig());
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult(
                        "Email", EmailConsentStatus.OPT_OUT, 55, new Date()));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentOverride()).isFalse();
        assertThat(emailLog.getConsentOverrideReason()).isEmpty();
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should block send when email consent is not configured even with override")
    void shouldBlockSend_whenEmailConsentNotConfiguredEvenWithOverride() {
        EmailData emailData = emailData(10);
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("Patient verbally confirmed email consent");
        when(emailConfigDao.findActiveEmailConfigById(10)).thenReturn(activeSenderConfig());
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult(
                        "", EmailConsentStatus.NOT_CONFIGURED, null, null));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.NOT_CONFIGURED);
        assertThat(emailLog.getConsentOverride()).isFalse();
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should block send and skip sender when consent is unknown without override")
    void shouldBlockSendAndSkipSender_whenConsentUnknownWithoutOverride() {
        EmailData emailData = emailData(10);
        when(emailConfigDao.findActiveEmailConfigById(10)).thenReturn(activeSenderConfig());
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult(
                        "Email", EmailConsentStatus.UNKNOWN, null, null));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.UNKNOWN);
        assertThat(emailLog.getConsentOverride()).isFalse();
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should block send when unknown-consent override reason is blank")
    void shouldBlockSend_whenUnknownConsentOverrideReasonIsBlank() {
        EmailData emailData = emailData(10);
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("   ");
        when(emailConfigDao.findActiveEmailConfigById(10)).thenReturn(activeSenderConfig());
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult(
                        "Email", EmailConsentStatus.UNKNOWN, null, null));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should send and persist override when consent is unknown with override")
    void shouldSendAndPersistOverride_whenConsentUnknownWithOverride()
            throws EmailSendingException {
        EmailData emailData = emailData(10);
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("Patient verbally confirmed email consent");
        when(emailConfigDao.findActiveEmailConfigById(10)).thenReturn(activeSenderConfig());
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult(
                        "Email", EmailConsentStatus.UNKNOWN, null, null));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.UNKNOWN);
        assertThat(emailLog.getConsentOverride()).isTrue();
        assertThat(emailLog.getConsentOverrideReason())
                .isEqualTo("Patient verbally confirmed email consent");
        verify(emailSender).send();
        verify(emailLogDao).transitionEmailStatus(
                eq(emailLog.getId()), eq(EmailStatus.PENDING), eq(EmailStatus.SUCCESS),
                eq(""), any(Date.class));
    }

    @Test
    @DisplayName("should resolve failed email atomically while preserving diagnostic and auditing actor")
    void shouldResolveFailedEmail_atomicallyAndWithAudit() {
        EmailLog failed = mock(EmailLog.class);
        Date originalTimestamp = new Date(1_700_000_000_000L);
        when(failed.getId()).thenReturn(42);
        when(failed.getStatus()).thenReturn(EmailStatus.FAILED);
        when(failed.getErrorMessage()).thenReturn("SMTP 550 rejected");
        when(failed.getTimestamp()).thenReturn(originalTimestamp);
        when(failed.getDemographic()).thenReturn(demographic);
        when(emailLogDao.find((Object) 42)).thenReturn(failed);

        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 42))
                .isEqualTo(EmailManager.EmailResolutionResult.RESOLVED);

        verify(emailLogDao).transitionEmailStatus(
                42, EmailStatus.FAILED, EmailStatus.RESOLVED,
                "SMTP 550 rejected", originalTimestamp);
        verify(failed).setStatus(EmailStatus.RESOLVED);
        ArgumentCaptor<OscarLog> auditCaptor = ArgumentCaptor.forClass(OscarLog.class);
        verify(oscarLogDao).persist(auditCaptor.capture());
        OscarLog audit = auditCaptor.getValue();
        assertThat(audit.getAction()).isEqualTo("EmailManager.resolveEmailStatus");
        assertThat(audit.getContent()).isEqualTo("Email");
        assertThat(audit.getContentId()).isEqualTo("42");
        assertThat(audit.getDemographicId()).isEqualTo(123);
        assertThat(audit.getData())
                .isEqualTo("previousStatus=FAILED; diagnosticPreserved=true");
    }

    @Test
    @DisplayName("should declare resolution transactional and propagate audit failures")
    void shouldDeclareResolutionTransactional_andPropagateAuditFailure() throws NoSuchMethodException {
        assertThat(EmailManager.class.getMethod(
                "resolveEmailStatus", LoggedInInfo.class, Integer.class)
                .getAnnotation(Transactional.class)).isNotNull();

        EmailLog failed = mock(EmailLog.class);
        when(failed.getId()).thenReturn(42);
        when(failed.getStatus()).thenReturn(EmailStatus.FAILED);
        when(failed.getTimestamp()).thenReturn(new Date());
        when(emailLogDao.find((Object) 42)).thenReturn(failed);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(oscarLogDao).persist(any(OscarLog.class));

        assertThatThrownBy(() -> emailManager.resolveEmailStatus(loggedInInfo, 42))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
    }

    @Test
    @DisplayName("should distinguish missing, active, and conclusive records during resolution")
    void shouldRejectResolution_whenRecordIsMissingActiveOrConclusive() {
        when(emailLogDao.find((Object) 42)).thenReturn(null);
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 42))
                .isEqualTo(EmailManager.EmailResolutionResult.NOT_FOUND);

        EmailLog active = mock(EmailLog.class);
        when(active.getStatus()).thenReturn(EmailStatus.PENDING);
        when(active.getTimestamp()).thenReturn(new Date());
        when(emailLogDao.find((Object) 43)).thenReturn(active);
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 43))
                .isEqualTo(EmailManager.EmailResolutionResult.PENDING_TOO_RECENT);

        EmailLog successful = mock(EmailLog.class);
        when(successful.getStatus()).thenReturn(EmailStatus.SUCCESS);
        when(emailLogDao.find((Object) 44)).thenReturn(successful);
        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 44))
                .isEqualTo(EmailManager.EmailResolutionResult.NOT_RESOLVABLE);

        verify(emailLogDao, never()).transitionEmailStatus(
                anyInt(), any(EmailStatus.class), any(EmailStatus.class),
                nullable(String.class), any(Date.class));
    }

    @Test
    @DisplayName("should expose recovery only after a pending record is stale")
    void shouldAllowResolution_whenPendingRecordIsStale() {
        EmailLog fresh = mock(EmailLog.class);
        when(fresh.getStatus()).thenReturn(EmailStatus.PENDING);
        when(fresh.getTimestamp()).thenReturn(new Date());

        EmailLog stale = mock(EmailLog.class);
        when(stale.getStatus()).thenReturn(EmailStatus.PENDING);
        when(stale.getTimestamp()).thenReturn(new Date(
                System.currentTimeMillis() - EmailManager.PENDING_RESOLUTION_MIN_AGE_MILLIS - 1));

        assertThat(emailManager.isManuallyResolvable(fresh)).isFalse();
        assertThat(emailManager.isManuallyResolvable(stale)).isTrue();
    }

    @Test
    @DisplayName("should report conflict when compare-and-set resolution loses a race")
    void shouldReportConflict_whenResolutionLosesRace() {
        EmailLog failed = mock(EmailLog.class);
        when(failed.getId()).thenReturn(42);
        when(failed.getStatus()).thenReturn(EmailStatus.FAILED);
        when(failed.getTimestamp()).thenReturn(new Date());
        when(emailLogDao.find((Object) 42)).thenReturn(failed);
        when(emailLogDao.transitionEmailStatus(
                eq(42), eq(EmailStatus.FAILED), eq(EmailStatus.RESOLVED),
                nullable(String.class), any(Date.class))).thenReturn(0);

        assertThat(emailManager.resolveEmailStatus(loggedInInfo, 42))
                .isEqualTo(EmailManager.EmailResolutionResult.CONFLICT);

        verify(failed, never()).setStatus(EmailStatus.RESOLVED);
    }

    @Test
    @DisplayName("should not overwrite a concurrent manual resolution with transport completion")
    void shouldKeepPersistedStatus_whenTransportTransitionLosesRace() {
        EmailLog sending = mock(EmailLog.class);
        when(sending.getId()).thenReturn(42);
        when(sending.getStatus()).thenReturn(EmailStatus.PENDING);
        EmailLog resolved = mock(EmailLog.class);
        when(resolved.getId()).thenReturn(42);
        when(resolved.getStatus()).thenReturn(EmailStatus.RESOLVED);
        when(resolved.getErrorMessage()).thenReturn("original diagnostic");
        Date resolvedTimestamp = new Date(1_700_000_000_000L);
        when(resolved.getTimestamp()).thenReturn(resolvedTimestamp);
        when(emailLogDao.transitionEmailStatus(
                eq(42), eq(EmailStatus.PENDING), eq(EmailStatus.FAILED),
                eq("SMTP rejected patient@example.invalid"), any(Date.class))).thenReturn(0);
        when(emailLogDao.find((Object) 42)).thenReturn(resolved);

        EmailLog result = emailManager.updateEmailStatus(
                loggedInInfo, sending, EmailStatus.FAILED,
                "SMTP rejected patient@example.invalid");

        assertThat(result).isSameAs(sending);
        verify(sending).setStatus(EmailStatus.RESOLVED);
        verify(sending).setErrorMessage("original diagnostic");
        verify(sending).setTimestamp(resolvedTimestamp);
        ArgumentCaptor<OscarLog> auditCaptor = ArgumentCaptor.forClass(OscarLog.class);
        verify(oscarLogDao).persist(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction())
                .isEqualTo("EmailManager.transportOutcomeAfterResolution");
        assertThat(auditCaptor.getValue().getContentId()).isEqualTo("42");
        assertThat(auditCaptor.getValue().getData())
                .isEqualTo("transportOutcome=FAILED; diagnosticPresent=true")
                .doesNotContain("patient@example.invalid");
    }

    @Test
    @DisplayName("should preserve accepted transport outcome when resolution wins the status race")
    void shouldPreserveAcceptedOutcome_whenResolutionWinsStatusRace() {
        EmailData emailData = emailData(123);
        when(emailConfigDao.findActiveEmailConfigById(123)).thenReturn(activeSenderConfig());
        EmailLog resolved = mock(EmailLog.class);
        when(resolved.getStatus()).thenReturn(EmailStatus.RESOLVED);
        when(resolved.getTimestamp()).thenReturn(new Date());
        when(emailLogDao.transitionEmailStatus(
                nullable(Integer.class), eq(EmailStatus.PENDING), eq(EmailStatus.SUCCESS),
                eq(""), any(Date.class))).thenReturn(0);
        when(emailLogDao.find(nullable(Integer.class))).thenReturn(resolved);

        EmailSendResult result = emailManager.sendEmailWithResult(loggedInInfo, emailData);

        assertThat(result.isTransportAccepted()).isTrue();
        assertThat(result.isTransportOutcomeRecorded()).isFalse();
        assertThat(result.getEmailLog().getStatus()).isEqualTo(EmailStatus.RESOLVED);
    }

    @Test
    @DisplayName("should return email status result when sender config association is missing")
    void shouldReturnEmailStatusResult_whenSenderConfigAssociationIsMissing() {
        EmailLog emailLog = new EmailLog(null, "", new String[] {"recipient@example.invalid"}, "Subject", "Body", EmailStatus.FAILED);
        emailLog.setDemographic(demographic);
        emailLog.setProvider(provider);
        emailLog.setErrorMessage(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
        when(emailLogDao.getEmailStatusByDateDemographicSenderStatus(any(), any(), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(Collections.singletonList(emailLog));

        List<EmailStatusResult> results = emailManager.getEmailStatusByDateDemographicSenderStatus(loggedInInfo, "2026-07-16", "2026-07-16", null, null, "FAILED");

        assertThat(results).hasSize(1);
        EmailStatusResult result = results.get(0);
        assertThat(result.getSenderFullName()).isEqualTo("Unknown Sender");
        assertThat(result.getSenderEmail()).isEmpty();
        assertThat(result.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
    }

    @Test
    @DisplayName("should include alias for email status result when demographic has legal name")
    void shouldIncludeAlias_whenEmailStatusDemographicHasLegalName() {
        demographic.setAlias("  CJ Patient  ");
        EmailLog emailLog = new EmailLog(null, "", new String[] {"recipient@example.invalid"}, "Subject", "Body", EmailStatus.FAILED);
        emailLog.setDemographic(demographic);
        emailLog.setProvider(provider);
        emailLog.setErrorMessage(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
        when(emailLogDao.getEmailStatusByDateDemographicSenderStatus(any(), any(), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(Collections.singletonList(emailLog));

        List<EmailStatusResult> results = emailManager.getEmailStatusByDateDemographicSenderStatus(loggedInInfo, "2026-07-16", "2026-07-16", null, null, "FAILED");

        assertThat(results).hasSize(1);
        EmailStatusResult result = results.get(0);
        assertThat(result.getRecipientFirstName()).isEqualTo("Patient");
        assertThat(result.getRecipientLastName()).isEqualTo("Example (CJ Patient)");
        assertThat(result.getRecipientFullName()).isEqualTo("Patient Example (CJ Patient)");
    }

    @Test
    @DisplayName("should use alias for email status result when demographic has no legal name")
    void shouldUseAlias_whenEmailStatusDemographicHasNoLegalName() {
        Demographic aliasOnlyDemographic = new Demographic();
        aliasOnlyDemographic.setAlias("  CJ Patient  ");
        EmailLog emailLog = new EmailLog(null, "", new String[] {"recipient@example.invalid"}, "Subject", "Body", EmailStatus.FAILED);
        emailLog.setDemographic(aliasOnlyDemographic);
        emailLog.setProvider(provider);
        emailLog.setErrorMessage(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
        when(emailLogDao.getEmailStatusByDateDemographicSenderStatus(any(), any(), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(Collections.singletonList(emailLog));

        List<EmailStatusResult> results = emailManager.getEmailStatusByDateDemographicSenderStatus(loggedInInfo, "2026-07-16", "2026-07-16", null, null, "FAILED");

        assertThat(results).hasSize(1);
        EmailStatusResult result = results.get(0);
        assertThat(result.getRecipientFirstName()).isEmpty();
        assertThat(result.getRecipientLastName()).isEqualTo("(CJ Patient)");
        assertThat(result.getRecipientFullName()).isEqualTo("(CJ Patient)");
    }

    private void assertMisconfiguredSenderFailure(EmailLog result) {
        assertThat(result.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
        assertThat(result.getEmailConfig()).isNull();
    }

    private EmailConfig activeSenderConfig() {
        EmailConfig emailConfig = new EmailConfig(
                EmailConfig.EmailType.SMTP,
                EmailConfig.EmailProvider.LOCAL,
                "sender@example.invalid");
        emailConfig.setActive(true);
        return emailConfig;
    }

    private EmailData emailData(Integer senderConfigId) {
        EmailData emailData = new EmailData();
        emailData.setSenderConfigId(senderConfigId);
        emailData.setRecipients(new String[] {"recipient@example.invalid"});
        emailData.setSubject("Subject");
        emailData.setBody("Body");
        emailData.setEncryptedMessage("");
        emailData.setPassword("");
        emailData.setPasswordClue("");
        emailData.setIsEncrypted(false);
        emailData.setIsAttachmentEncrypted(false);
        emailData.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        emailData.setInternalComment("");
        emailData.setTransactionType(TransactionType.DIRECT);
        emailData.setDemographicNo(123);
        emailData.setProviderNo("999998");
        emailData.setAdditionalParams("");
        emailData.setAttachments(Collections.emptyList());
        return emailData;
    }
}
