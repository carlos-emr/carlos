/** Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.email.core.*;
import io.github.carlos_emr.carlos.integration.patientportal.*;
import io.github.carlos_emr.carlos.managers.OutboundEmailArchiveService.SendOutcome;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class EmailManagerPortalDeliveryUnitTest extends CarlosUnitTestBase {
    private static final int PATIENT = 123;
    private static final int LOG_ID = 45;
    private static final int ARCHIVE_ID = 91;
    private static final long SECRET_ID = 77L;

    private SecurityInfoManager security;
    private EmailLogDaoImpl logs;
    private EmailConsentResolver consent;
    private EmailSenderFactory factory;
    private EmailSender sender;
    private OutboundEmailArchiveService archives;
    private PatientPortalService portal;
    private EmailManager manager;
    private LoggedInInfo user;
    private EmailConfig config;
    private final AtomicReference<String> encryptedWith = new AtomicReference<>();
    private MockedStatic<PortalEmailDeliveryService> enabled;
    private MockedStatic<PatientPortalSettings> configuration;

    @BeforeEach
    void setUp() throws Exception {
        security = mock(SecurityInfoManager.class);
        logs = mock(EmailLogDaoImpl.class);
        consent = mock(EmailConsentResolver.class);
        factory = mock(EmailSenderFactory.class);
        sender = mock(EmailSender.class);
        archives = mock(OutboundEmailArchiveService.class);
        portal = mock(PatientPortalService.class);
        var configs = mock(EmailConfigDaoImpl.class);
        var demographics = mock(DemographicManager.class);
        var providers = mock(ProviderManager2.class);
        var settings = mock(PatientPortalSettings.class);
        manager = spy(new EmailManager(consent, factory, security, archives));
        injectDependency(manager, "oscarLogDao", mock(OscarLogDao.class));
        injectDependency(manager, "emailLogDao", logs);
        injectDependency(manager, "emailConfigDao", configs);
        injectDependency(manager, "demographicManager", demographics);
        injectDependency(manager, "providerManager", providers);
        injectDependency(manager, "portalEmailDelivery", new PortalEmailDeliveryService(security, logs));
        mockedBeans.put(PatientPortalService.class, portal);

        user = new LoggedInInfo();
        var provider = new Provider();
        provider.setProviderNo("999998");
        user.setLoggedInProvider(provider);
        var patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setEmail("patient@example.org");
        config = new EmailConfig();
        config.setSenderEmail("clinic@example.org");

        when(security.hasPrivilege(eq(user), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(true);
        when(configs.findActiveEmailConfigById(10)).thenReturn(config);
        when(demographics.getDemographic(user, PATIENT)).thenReturn(patient);
        when(providers.getProvider(user, "999998")).thenReturn(provider);
        when(settings.clinicId()).thenReturn("clinic");
        when(settings.baseUrl()).thenReturn("https://portal.example.org");
        when(portal.findAccount(eq(PATIENT), any()))
                .thenReturn(new PatientPortalAccountDto(1, "clinic", PATIENT, "active", false, false, null, null));
        when(portal.createUnlockSecret(eq(PATIENT), anyString(), anyString(), any())).thenAnswer(call ->
                new PatientPortalUnlockSecretDto(SECRET_ID, true, PortalSecret.of("strong-portal-password"),
                        call.getArgument(1), "pending"));
        when(logs.transitionEmailStatus(any(), any(), any(), anyString(), any())).thenReturn(1);
        when(logs.initializePortalDelivery(any())).thenReturn(true);
        when(logs.transitionPortalDelivery(any(), any(), any(), nullable(Long.class))).thenReturn(true);
        doAnswer(call -> {
            EmailLog log = call.getArgument(0);
            assertThat(log.getPassword()).isEmpty();
            assertThat(log.getPasswordClue()).isEmpty();
            ReflectionTestUtils.setField(log, "id", LOG_ID);
            return null;
        }).when(logs).persist(any(EmailLog.class));
        doAnswer(call -> {
            encryptedWith.set(((EmailData) call.getArgument(0)).getPassword());
            return null;
        }).when(manager).encryptEmail(any());
        when(factory.create(eq(user), eq(config), any())).thenAnswer(call -> {
            assertThat(((EmailData) call.getArgument(2)).getPassword()).isEmpty();
            return sender;
        });
        var archive = new OutboundEmailArchive();
        archive.setId(ARCHIVE_ID);
        when(archives.archive(eq(user), any())).thenReturn(archive);

        enabled = mockStatic(PortalEmailDeliveryService.class);
        enabled.when(PortalEmailDeliveryService::isEnabled).thenReturn(true);
        configuration = mockStatic(PatientPortalSettings.class);
        configuration.when(PatientPortalSettings::fromCarlosProperties).thenReturn(settings);
    }

    @AfterEach
    void tearDown() {
        configuration.close();
        enabled.close();
    }

    @Test
    @DisplayName("should archive, send and then publish the portal password, never persisting it")
    void shouldArchiveSendAndPublish_whenConsentIsExplicit() throws Exception {
        givenConsent(EmailLog.EmailConsentStatus.OPT_IN);
        var data = encryptedEmail();

        var result = manager.sendEmail(user, data);

        assertThat(encryptedWith.get()).isEqualTo("strong-portal-password");
        assertThat(result.getStatus()).isEqualTo(EmailLog.EmailStatus.SUCCESS);
        assertThat(result.getPortalDeliveryState()).isEqualTo(EmailLog.PortalDeliveryState.PUBLISHED);
        var order = inOrder(sender, archives, portal, logs);
        order.verify(sender).prepareOutboundArchive(any());
        order.verify(archives).archive(eq(user), any());
        order.verify(archives).recordSendOutcome(user, ARCHIVE_ID, SendOutcome.ATTEMPTED);
        order.verify(sender).sendPrepared();
        order.verify(portal).publishUnlockSecret(eq(SECRET_ID), any());
        order.verify(logs).transitionEmailStatus(eq(LOG_ID), eq(EmailLog.EmailStatus.PENDING),
                eq(EmailLog.EmailStatus.SUCCESS), anyString(), any());
        order.verify(archives).recordSendOutcome(user, ARCHIVE_ID, SendOutcome.ACCEPTED);
        verify(sender, never()).send();
        assertThat(data.getPassword()).isEmpty();
    }

    @Test
    @DisplayName("should not call the portal or the sender when consent blocks the email")
    void shouldNotCallPortalOrSender_whenConsentBlocksEmail() throws Exception {
        givenConsent(EmailLog.EmailConsentStatus.OPT_OUT);
        var data = encryptedEmail();

        var result = manager.sendEmail(user, data);

        assertThat(result.getStatus()).isEqualTo(EmailLog.EmailStatus.BLOCKED);
        verifyNoInteractions(portal, factory, sender, archives);
        assertThat(data.getPassword()).isEmpty();
    }

    @Test
    @DisplayName("should revoke the password and propagate when the send is refused before transport")
    void shouldRevokePasswordAndPropagate_whenSendPrivilegeIsRevokedMidSend() throws Exception {
        givenConsent(EmailLog.EmailConsentStatus.OPT_IN);
        doThrow(new SecurityException("missing required sec object (_email)")).when(sender).sendPrepared();

        assertThatThrownBy(() -> manager.sendEmail(user, encryptedEmail()))
                .isInstanceOf(SecurityException.class);

        verify(portal).revokeUnlockSecret(eq(SECRET_ID), eq("email_not_sent"), any());
        verify(portal, never()).publishUnlockSecret(anyLong(), any());
        verify(logs).transitionEmailStatus(eq(LOG_ID), eq(EmailLog.EmailStatus.PENDING),
                eq(EmailLog.EmailStatus.FAILED), anyString(), any());
        verify(archives, never()).recordSendOutcome(user, ARCHIVE_ID, SendOutcome.ACCEPTED);
    }

    @Test
    @DisplayName("should record the refusal and propagate when portal authorization fails")
    void shouldRecordFailureAndPropagate_whenPortalAuthorizationIsRefused() throws Exception {
        givenConsent(EmailLog.EmailConsentStatus.OPT_IN);
        when(security.hasPrivilege(eq(user), eq(PortalStaffContextResolver.OBJECT_SECRET),
                eq(SecurityInfoManager.WRITE), eq(String.valueOf(PATIENT)))).thenReturn(false);

        assertThatThrownBy(() -> manager.sendEmail(user, encryptedEmail()))
                .isInstanceOf(SecurityException.class);

        verify(logs).transitionEmailStatus(eq(LOG_ID), eq(EmailLog.EmailStatus.PENDING),
                eq(EmailLog.EmailStatus.FAILED), anyString(), any());
        verifyNoInteractions(portal, factory, sender, archives);
    }

    @Test
    @DisplayName("should report not sent and revoke the password when the sender cannot be built")
    void shouldReportNotSentAndRevoke_whenSenderCannotBeBuilt() throws Exception {
        givenConsent(EmailLog.EmailConsentStatus.OPT_IN);
        when(factory.create(eq(user), eq(config), any())).thenThrow(new IllegalStateException("bad sender config"));

        var result = manager.sendEmail(user, encryptedEmail());

        assertThat(result.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
        assertThat(result.getPortalDeliveryState()).isEqualTo(EmailLog.PortalDeliveryState.REVOKED);
        verify(portal).revokeUnlockSecret(eq(SECRET_ID), eq("email_not_sent"), any());
        verifyNoInteractions(sender, archives);
    }

    @Test
    @DisplayName("should refuse to encrypt with a blank password")
    void shouldRefuseToEncrypt_whenPasswordIsBlank() {
        var data = encryptedEmail();
        data.setPassword("");

        assertThatThrownBy(() -> new EmailManager(consent, factory, security, archives).encryptEmail(data))
                .isInstanceOf(io.github.carlos_emr.carlos.utility.EmailSendingException.class);
    }

    private void givenConsent(EmailLog.EmailConsentStatus state) {
        when(consent.resolve(user, PATIENT)).thenReturn(new EmailConsentResult("Email", state, 1, new Date()));
    }

    private EmailData encryptedEmail() {
        var data = new EmailData();
        data.setDemographicNo(PATIENT);
        data.setProviderNo("999998");
        data.setSenderConfigId(10);
        data.setBody("An encrypted message is attached.");
        data.setSubject("Synthetic message");
        data.setInternalComment("");
        data.setAdditionalParams("");
        data.setRecipients(new String[]{"patient@example.org"});
        data.setIsEncrypted(true);
        data.setEncryptedMessage("Synthetic private text");
        data.setPassword("submitted-password-must-be-ignored");
        data.setPasswordClue("submitted hint");
        data.setAttachments(List.of());
        data.setChartDisplayOption(EmailLog.ChartDisplayOption.WITHOUT_NOTE);
        data.setTransactionType(EmailLog.TransactionType.DIRECT);
        return data;
    }
}
