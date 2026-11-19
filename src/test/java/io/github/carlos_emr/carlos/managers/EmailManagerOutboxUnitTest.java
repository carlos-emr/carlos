/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (C) 2026 CARLOS Contributors */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("email")
@DisplayName("EmailManager outbox lifecycle")
class EmailManagerOutboxUnitTest extends CarlosUnitTestBase {
    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EmailConfigDaoImpl emailConfigDao;
    @Mock private EmailLogDaoImpl emailLogDao;
    @Mock private DemographicManager demographicManager;
    @Mock private ProviderManager2 providerManager;
    @InjectMocks private EmailManager emailManager;

    @Test
    @DisplayName("should retain the unconfirmed outbox row if sending exits before recording an outcome")
    void shouldRetainUnconfirmedDelivery_whenTransportIsInterrupted() {
        LoggedInInfo user = new LoggedInInfo();
        when(securityInfoManager.hasPrivilege(user, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(emailConfigDao.findActiveEmailConfigById(1)).thenReturn(new EmailConfig(
                EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.LOCAL, "sender@example.invalid"));
        when(demographicManager.getDemographic(user, 7311)).thenReturn(new Demographic(7311));
        when(providerManager.getProvider(user, "999998")).thenReturn(new Provider("999998"));

        EmailData data = new EmailData();
        data.setSenderConfigId(1);
        data.setDemographicNo(7311);
        data.setProviderNo("999998");
        data.setRecipients(new String[]{"recipient@example.invalid"});
        data.setSubject("Synthetic subject");
        data.setBody("Synthetic body");
        data.setEncryptedMessage("");
        data.setInternalComment("");
        data.setAttachments(List.of());
        data.setChartDisplayOption(EmailLog.ChartDisplayOption.WITHOUT_NOTE);
        IllegalStateException interrupted = new IllegalStateException("Synthetic interrupted send");
        try (var senders = mockConstruction(EmailSender.class,
                (sender, context) -> doThrow(interrupted).when(sender).send())) {
            assertThatThrownBy(() -> emailManager.sendEmail(user, data)).isSameAs(interrupted);
            assertThat(senders.constructed()).hasSize(1);
        }

        ArgumentCaptor<EmailLog> outbox = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogDao).persist(outbox.capture());
        assertThat(outbox.getValue().getStatus()).isEqualTo(EmailLog.EmailStatus.UNCONFIRMED);
        assertThat(outbox.getValue().getErrorMessage()).isNull();
        verifyNoMoreInteractions(emailLogDao);
    }
}
