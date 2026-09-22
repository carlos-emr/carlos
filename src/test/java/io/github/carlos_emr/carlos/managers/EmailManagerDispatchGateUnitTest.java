/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailConsentResult;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/**
 * The dispatch gate lets a caller act between the durable outbox write and any transport work.
 *
 * <p>The patient portal invite workflow commits the invitation on the portal inside this gate: the
 * email must already be durable when the portal activates the token, and nothing may be sent if
 * that commit fails.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
class EmailManagerDispatchGateUnitTest extends CarlosUnitTestBase {

    private EmailManager emailManager;
    private EmailConfigDaoImpl emailConfigDao;
    private EmailLogDaoImpl emailLogDao;
    private EmailConsentResolver consentResolver;
    private LoggedInInfo loggedInInfo;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailLogDao = mock(EmailLogDaoImpl.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        ProviderManager2 providerManager = mock(ProviderManager2.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        Provider provider = new Provider("999998");

        consentResolver = mock(EmailConsentResolver.class);
        consentIs(EmailConsentStatus.OPT_IN);
        emailManager = new EmailManager(consentResolver, new EmailSenderFactory(), securityInfoManager,
                mock(OutboundEmailArchiveService.class));
        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "oscarLogDao", mock(OscarLogDao.class));
        injectDependency(emailManager, "caseManagementManager", mock(CaseManagementManager.class));
        injectDependency(emailManager, "demographicManager", demographicManager);
        injectDependency(emailManager, "documentAttachmentManager", mock(DocumentAttachmentManager.class));
        injectDependency(emailManager, "programManager", mock(ProgramManager.class));
        injectDependency(emailManager, "providerManager", providerManager);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_email"), anyString(),
                nullable(String.class))).thenReturn(true);
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(demographic);
        when(providerManager.getProvider(loggedInInfo, "999998")).thenReturn(provider);
        when(emailConfigDao.findActiveEmailConfigById(123)).thenReturn(activeSenderConfig());
        when(emailLogDao.transitionEmailStatus(nullable(Integer.class), any(EmailStatus.class),
                any(EmailStatus.class), nullable(String.class), any(Date.class))).thenReturn(1);
    }

    @Test
    @DisplayName("should run the gate on the pending outbox row before the transport sends")
    void shouldRunGate_beforeTransportSends() throws Exception {
        EmailSendResult result;
        try (MockedConstruction<EmailSender> senders = recordingSenders()) {
            result = emailManager.sendEmailWithResult(loggedInInfo, emailData(), emailLog -> {
                events.add("gate:" + emailLog.getStatus());
            });
        }

        assertThat(events).containsExactly("gate:PENDING", "send");
        assertThat(result.isTransportAccepted()).isTrue();
    }

    @Test
    @DisplayName("should record a definite failure and never send when the gate refuses")
    void shouldNotSend_whenGateRefuses() throws Exception {
        EmailSendResult result;
        try (MockedConstruction<EmailSender> senders = recordingSenders()) {
            result = emailManager.sendEmailWithResult(loggedInInfo, emailData(), emailLog -> {
                throw new EmailSendingException("portal did not commit the invitation");
            });
        }

        assertThat(events).doesNotContain("send");
        assertThat(result.isTransportAccepted()).isFalse();
        assertThat(result.isDeliveryUnconfirmed()).isFalse();
        verify(emailLogDao).transitionEmailStatus(nullable(Integer.class), eq(EmailStatus.PENDING),
                eq(EmailStatus.FAILED), anyString(), any(Date.class));
    }

    @Test
    @DisplayName("should not run the gate when consent blocks the email")
    void shouldNotRunGate_whenConsentBlocks() throws Exception {
        consentIs(EmailConsentStatus.OPT_OUT);

        try (MockedConstruction<EmailSender> senders = recordingSenders()) {
            emailManager.sendEmailWithResult(loggedInInfo, emailData(), emailLog -> events.add("gate"));
        }

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("should report the consent block message a send would record")
    void shouldReportConsentBlock_withoutSending() {
        assertThat(emailManager.consentBlockMessage(loggedInInfo, emailData())).isNull();

        consentIs(EmailConsentStatus.OPT_OUT);
        assertThat(emailManager.consentBlockMessage(loggedInInfo, emailData()))
                .isEqualTo("Email blocked: patient has explicitly opted out of email communication.");

        consentIs(EmailConsentStatus.NOT_CONFIGURED);
        assertThat(emailManager.consentBlockMessage(loggedInInfo, emailData()))
                .isEqualTo("Email blocked: patient email consent is not configured.");
    }

    @Test
    @DisplayName("should honour a documented override for unknown consent, as a send does")
    void shouldAllowUnknownConsent_withDocumentedOverride() {
        consentIs(EmailConsentStatus.UNKNOWN);
        EmailData data = emailData();
        assertThat(emailManager.consentBlockMessage(loggedInInfo, data)).isNotNull();

        data.setConsentOverride(true);
        data.setConsentOverrideReason("Verbal consent confirmed at the front desk");
        assertThat(emailManager.consentBlockMessage(loggedInInfo, data)).isNull();
    }

    private MockedConstruction<EmailSender> recordingSenders() {
        return mockConstruction(EmailSender.class, (sender, context) -> {
            org.mockito.Mockito.doAnswer(invocation -> {
                events.add("send");
                return null;
            }).when(sender).sendPrepared();
        });
    }

    private void consentIs(EmailConsentStatus status) {
        when(consentResolver.resolve(any(), any())).thenReturn(new EmailConsentResult("Email", status, null, null));
    }

    private static EmailConfig activeSenderConfig() {
        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.LOCAL,
                "sender@example.invalid");
        emailConfig.setActive(true);
        return emailConfig;
    }

    private static EmailData emailData() {
        EmailData emailData = new EmailData();
        emailData.setSenderConfigId(123);
        emailData.setRecipients(new String[] {"recipient@example.invalid"});
        emailData.setSubject("Subject");
        emailData.setBody("Body");
        emailData.setEncryptedMessage("");
        emailData.setPassword("");
        emailData.setPasswordClue("");
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
