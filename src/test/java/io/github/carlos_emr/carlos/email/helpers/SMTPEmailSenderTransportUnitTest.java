/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.helpers;

import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("SMTPEmailSender")
class SMTPEmailSenderTransportUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), any(String.class), any(String.class),
                nullable(String.class)))
                .thenReturn(true);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(JavaMailSender.class, mock(JavaMailSender.class));
        registerMock(NioFileManager.class, mock(NioFileManager.class));
    }

    @Test
    @DisplayName("should apply bounded connection, read, and write timeouts")
    void shouldApplyBoundedSmtpTimeouts_withDefaultProperties() {
        Properties properties = new Properties();

        SMTPEmailSender.applySmtpTimeouts(properties);

        assertThat(properties)
                .containsEntry("mail.smtp.connectiontimeout", "30000")
                .containsEntry("mail.smtp.timeout", "60000")
                .containsEntry("mail.smtp.writetimeout", "60000");
    }

    @Test
    @DisplayName("should normalize malformed active SMTP configuration without exposing values")
    void shouldNormalizeMalformedActiveSmtpConfiguration_withoutExposingValues() {
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderEmail("clinic@example.invalid");
        emailConfig.setConfigDetailsJson("{\"host\":\"smtp.internal\",\"port\":\"not-a-port\"}");
        SMTPEmailSender sender = new SMTPEmailSender(
                new LoggedInInfo(), emailConfig, new String[] {"patient@example.invalid"},
                "Subject", "Body", java.util.List.of());

        assertThatThrownBy(() -> sender.createTLSMailSender(emailConfig))
                .isInstanceOf(EmailSendingException.class)
                .hasMessage("The active SMTP sender configuration is invalid.")
                .hasMessageNotContaining("smtp.internal")
                .hasMessageNotContaining("not-a-port");
    }

    @Test
    @DisplayName("should classify an SMTP failure after dispatch as an uncertain outcome")
    void shouldClassifyPostDispatchSmtpFailure_asUncertainOutcome() {
        JavaMailSender transport = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(transport.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("response timed out"))
                .when(transport).send(mimeMessage);
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderEmail("clinic@example.invalid");
        SMTPEmailSender sender = new SMTPEmailSender(
                new LoggedInInfo(), emailConfig, new String[] {"patient@example.invalid"},
                "Subject", "Body", java.util.List.of()) {
            @Override
            protected JavaMailSender createTLSMailSender(EmailConfig ignored) {
                return transport;
            }
        };

        assertThatThrownBy(sender::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isTrue())
                .hasMessage("SMTP transport did not confirm whether the message was accepted.")
                .hasMessageNotContaining("response timed out");
    }

    @Test
    @DisplayName("should classify SMTP authentication failure as definitely unsent")
    void shouldClassifyAuthenticationFailure_asDefinitelyUnsent() {
        JavaMailSender transport = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(transport.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailAuthenticationException("secret diagnostic"))
                .when(transport).send(mimeMessage);
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderEmail("clinic@example.invalid");
        SMTPEmailSender sender = new SMTPEmailSender(
                new LoggedInInfo(), emailConfig, new String[] {"patient@example.invalid"},
                "Subject", "Body", java.util.List.of()) {
            @Override
            protected JavaMailSender createTLSMailSender(EmailConfig ignored) {
                return transport;
            }
        };

        assertThatThrownBy(sender::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isFalse())
                .hasMessage("SMTP failed before accepting the message.")
                .hasMessageNotContaining("secret diagnostic");
    }
}
