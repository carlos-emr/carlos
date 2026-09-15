/** Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.email.core.*;
import io.github.carlos_emr.carlos.integration.patientportal.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class EmailManagerPortalDeliveryUnitTest extends CarlosUnitTestBase {
    @Test void shouldRouteEncryptedEmailThroughPortalAndClearPasswordBeforePersistenceAndSend() throws Exception {
        exercise(EmailLog.EmailConsentStatus.OPT_IN, true);
    }
    @Test void shouldNotCallPortalOrSenderWhenConsentBlocksEmail() throws Exception {
        exercise(EmailLog.EmailConsentStatus.OPT_OUT, false);
    }

    private void exercise(EmailLog.EmailConsentStatus consentState, boolean expectedSend) throws Exception {
        var security = mock(SecurityInfoManager.class);
        var logs = mock(EmailLogDaoImpl.class);
        var configs = mock(EmailConfigDaoImpl.class);
        var demographics = mock(DemographicManager.class);
        var providers = mock(ProviderManager2.class);
        var consent = mock(EmailConsentResolver.class);
        var factory = mock(EmailSenderFactory.class);
        var sender = mock(EmailSender.class);
        var portal = mock(PatientPortalService.class);
        var settings = mock(PatientPortalSettings.class);
        var manager = spy(new EmailManager(consent, factory));
        var delivery = new PortalEmailDelivery(security, logs);
        injectDependency(manager, "securityInfoManager", security);
        injectDependency(manager, "emailLogDao", logs);
        injectDependency(manager, "emailConfigDao", configs);
        injectDependency(manager, "demographicManager", demographics);
        injectDependency(manager, "providerManager", providers);
        injectDependency(manager, "portalEmailDelivery", delivery);
        mockedBeans.put(PatientPortalService.class, portal);
        var user = new LoggedInInfo(); var provider = new Provider(); provider.setProviderNo("999998"); user.setLoggedInProvider(provider);
        var patient = new Demographic(); patient.setDemographicNo(123); patient.setEmail("patient@example.org");
        var config = new EmailConfig(); config.setSenderEmail("clinic@example.org");
        when(security.hasPrivilege(eq(user), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 123)).thenReturn(true);
        when(consent.resolve(user, 123)).thenReturn(new EmailConsentResult("Email", consentState, 1, new java.util.Date()));
        when(configs.findActiveEmailConfigById(10)).thenReturn(config);
        when(demographics.getDemographic(user, 123)).thenReturn(patient);
        when(providers.getProvider(user, "999998")).thenReturn(provider);
        when(settings.clinicId()).thenReturn("clinic"); when(settings.baseUrl()).thenReturn("https://portal.example.org");
        when(portal.findAccount(eq(123), any())).thenReturn(new PatientPortalAccountDto(1,"clinic",123,"active",false,false,null,null));
        when(portal.createUnlockSecret(eq(123), anyString(), anyString(), any())).thenAnswer(call ->
                new PatientPortalUnlockSecretDto(77,true,PortalSecret.of("strong-portal-password"),call.getArgument(1),"pending"));
        when(logs.initializePortalDelivery(any())).thenReturn(true);
        when(logs.transitionPortalDelivery(any(), any(), any(), nullable(Long.class))).thenReturn(true);
        doAnswer(call -> {
            EmailLog log = call.getArgument(0);
            assertThat(log.getPassword()).isEmpty();
            assertThat(log.getPasswordClue()).isEmpty();
            ReflectionTestUtils.setField(log,"id",45);
            return null;
        }).when(logs).persist(any(EmailLog.class));
        var encryptedWith = new AtomicReference<String>();
        doAnswer(call -> { encryptedWith.set(((EmailData)call.getArgument(0)).getPassword()); return null; }).when(manager).encryptEmail(any());
        when(factory.create(eq(user), eq(config), any())).thenAnswer(call -> {
            assertThat(((EmailData)call.getArgument(2)).getPassword()).isEmpty();
            return sender;
        });
        var data = new EmailData(); data.setDemographicNo(123); data.setProviderNo("999998"); data.setSenderConfigId(10);
        data.setBody("An encrypted message is attached."); data.setSubject("Synthetic message");
        data.setInternalComment(""); data.setAdditionalParams("");
        data.setRecipients(new String[]{"patient@example.org"}); data.setIsEncrypted(true); data.setEncryptedMessage("Synthetic private text");
        data.setPassword("submitted-password-must-be-ignored"); data.setPasswordClue("submitted hint"); data.setAttachments(List.of());
        data.setChartDisplayOption(EmailLog.ChartDisplayOption.WITHOUT_NOTE); data.setTransactionType(EmailLog.TransactionType.DIRECT);
        try (var enabled = mockStatic(PortalEmailDelivery.class); var configuration = mockStatic(PatientPortalSettings.class)) {
            enabled.when(PortalEmailDelivery::isEnabled).thenReturn(true);
            configuration.when(PatientPortalSettings::fromCarlosProperties).thenReturn(settings);
            var result = manager.sendEmail(user, data);
            if (expectedSend) {
                assertThat(encryptedWith.get()).isEqualTo("strong-portal-password");
                assertThat(result.getPortalDeliveryState()).isEqualTo(EmailLog.PortalDeliveryState.PUBLISHED);
                var order=inOrder(sender, portal); order.verify(sender).send(); order.verify(portal).publishUnlockSecret(eq(77L),any());
            } else {
                assertThat(result.getStatus()).isEqualTo(EmailLog.EmailStatus.BLOCKED);
                verifyNoInteractions(portal, factory, sender);
            }
        }
        assertThat(data.getPassword()).isEmpty();
    }
}
