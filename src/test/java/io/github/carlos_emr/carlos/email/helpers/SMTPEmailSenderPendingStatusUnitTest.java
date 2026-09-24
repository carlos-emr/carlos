/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.helpers;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
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
class SMTPEmailSenderPendingStatusUnitTest extends CarlosUnitTestBase {

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
        createAndRegisterMock(io.github.carlos_emr.carlos.managers.NioFileManager.class);
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
                "Subject", "Body", List.of());

        assertThatThrownBy(() -> sender.createTLSMailSender(emailConfig))
                .isInstanceOf(EmailSendingException.class)
                .hasMessage("Invalid credentials configured for clinic@example.invalid")
                .hasMessageNotContaining("smtp.internal")
                .hasMessageNotContaining("not-a-port");
    }

    @Test
    @DisplayName("should pin all-or-nothing recipients, which the refusal rule relies on")
    void shouldDisablePartialSends_forEveryTransport() {
        Properties properties = new Properties();

        SMTPEmailSender.applyAllOrNothingRecipients(properties);

        assertThat(properties)
                .containsEntry("mail.smtp.sendpartial", "false")
                .containsEntry("mail.smtp.reportsuccess", "false");
    }

    @Test
    @DisplayName("should classify an SMTP failure after dispatch as an uncertain outcome")
    void shouldClassifyPostDispatchSmtpFailure_asUncertainOutcome() {
        assertThatThrownBy(senderThrowing(new MailSendException("response timed out"))::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isTrue())
                .hasMessage("SMTP transport did not confirm whether the message was accepted.")
                .hasMessageNotContaining("response timed out");
    }

    @Test
    @DisplayName("should classify a server refusing every recipient as definitely unsent")
    void shouldClassifyRefusedRecipients_asDefinitelyUnsent() throws Exception {
        SendFailedException refused = new SendFailedException("Invalid Addresses", null,
                null, null, new Address[] {new InternetAddress("patient@example.invalid")});

        assertThatThrownBy(senderFailingWith(refused)::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isFalse())
                .hasMessage("SMTP failed before accepting the message.");
    }

    @Test
    @DisplayName("should keep the outcome uncertain when any recipient was sent to")
    void shouldClassifyPartialAcceptance_asUncertainOutcome() throws Exception {
        SendFailedException partial = new SendFailedException("Invalid Addresses", null,
                new Address[] {new InternetAddress("accepted@example.invalid")}, null,
                new Address[] {new InternetAddress("refused@example.invalid")});

        assertThatThrownBy(senderFailingWith(partial)::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isTrue());
    }

    @Test
    @DisplayName("should keep the outcome uncertain when a send failure names no refused address")
    void shouldClassifySendFailureWithoutInvalidAddresses_asUncertainOutcome() {
        SendFailedException noAddresses = new SendFailedException("550 rejected after DATA");

        assertThatThrownBy(senderFailingWith(noAddresses)::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isTrue());
    }

    @Test
    @DisplayName("should classify a temporary refusal of every recipient as definitely unsent")
    void shouldClassifyTemporaryRefusal_asDefinitelyUnsent() throws Exception {
        // A 4xx at RCPT TO (greylisting, mailbox busy) lists the address as valid but unsent.
        SendFailedException deferred = new SendFailedException("Invalid Addresses", null,
                null, new Address[] {new InternetAddress("patient@example.invalid")}, null);

        assertThatThrownBy(senderFailingWith(deferred)::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isFalse());
    }

    @Test
    @DisplayName("should still classify refused recipients as unsent when closing the connection also fails")
    void shouldClassifyRefusedRecipients_whenConnectionCloseAlsoFails() throws Exception {
        SendFailedException refused = refusal(550);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        MailSendException failure = new MailSendException(
                "Failed to close server connection after message failures",
                new MessagingException("close failed"), Map.<Object, Exception>of(mimeMessage, refused));

        assertThatThrownBy(senderThrowing(failure, mimeMessage)::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isFalse());
    }

    @Test
    @DisplayName("should keep a DATA-stage rejection uncertain even when it lists refused addresses")
    void shouldClassifyDataStageRejection_asUncertainOutcome() throws Exception {
        // The shape issueSendCommand builds after a partial RCPT and a non-250 reply to ".":
        // no valid-sent address, invalid addresses present, but DATA was already sent.
        SMTPSendFailedException afterData = new SMTPSendFailedException(".", 554, "554 rejected", null,
                null, null, new Address[] {new InternetAddress("refused@example.invalid")});

        assertThatThrownBy(senderFailingWith(afterData)::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isTrue());
    }

    @Test
    @DisplayName("should log refusal reply codes without the address or the server text")
    void shouldLogReplyCodes_withoutAddressOrServerText() throws Exception {
        try (LogCapture capture = LogCapture.forLogger(SMTPEmailSender.class)) {
            assertThatThrownBy(senderFailingWith(refusal(550))::send).isInstanceOf(EmailSendingException.class);

            assertThat(capture.messages())
                    .anySatisfy(message -> assertThat(message)
                            .contains("refusedRecipients=1").contains("replyCodes=[550]"))
                    .noneSatisfy(message -> assertThat(message)
                            .containsAnyOf("patient@example.invalid", "User unknown"));
        }
    }

    /** The exception Angus throws from rcptTo, with the per-address failure chained as next. */
    private static SendFailedException refusal(int replyCode) throws Exception {
        InternetAddress patient = new InternetAddress("patient@example.invalid");
        SMTPAddressFailedException perAddress = new SMTPAddressFailedException(patient, "RCPT TO:<patient@example.invalid>",
                replyCode, replyCode + " 5.1.1 User unknown");
        return new SendFailedException("Invalid Addresses", perAddress, null, null, new Address[] {patient});
    }

    /**
     * Builds a sender whose transport fails the way Spring's JavaMailSenderImpl reports a
     * per-message failure: a MailSendException keyed by message, with no top-level cause.
     */
    private SMTPEmailSender senderFailingWith(Exception messageFailure) {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        return senderThrowing(new MailSendException(Map.<Object, Exception>of(mimeMessage, messageFailure)), mimeMessage);
    }

    private SMTPEmailSender senderThrowing(RuntimeException failure) {
        return senderThrowing(failure, new MimeMessage(Session.getInstance(new Properties())));
    }

    private SMTPEmailSender senderThrowing(RuntimeException failure, MimeMessage mimeMessage) {
        JavaMailSender transport = mock(JavaMailSender.class);
        when(transport.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(failure).when(transport).send(mimeMessage);
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderEmail("clinic@example.invalid");
        return new SMTPEmailSender(
                new LoggedInInfo(), emailConfig, new String[] {"patient@example.invalid"},
                "Subject", "Body", List.of()) {
            @Override
            protected JavaMailSender createTLSMailSender(EmailConfig ignored) {
                return transport;
            }
        };
    }

    @Test
    @DisplayName("should classify SMTP authentication failure as definitely unsent")
    void shouldClassifyAuthenticationFailure_asDefinitelyUnsent() {
        assertThatThrownBy(senderThrowing(new MailAuthenticationException("secret diagnostic"))::send)
                .isInstanceOfSatisfying(EmailSendingException.class,
                        exception -> assertThat(exception.isDeliveryOutcomeUncertain()).isFalse())
                .hasMessage("SMTP failed before accepting the message.")
                .hasMessageNotContaining("secret diagnostic");
    }
}
