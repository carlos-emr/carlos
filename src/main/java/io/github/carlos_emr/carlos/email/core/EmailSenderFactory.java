package io.github.carlos_emr.carlos.email.core;

import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Creates EmailSender instances behind an injectable boundary for send-path tests.
 *
 * @since 2026-07-06
 */
@Component
public class EmailSenderFactory {
    /**
     * Creates a sender for a validated email configuration and prepared message.
     *
     * @param loggedInInfo the current provider session
     * @param emailConfig the active outbound email configuration
     * @param emailData the message to transmit
     * @return a sender ready to transmit the message
     */
    public EmailSender create(LoggedInInfo loggedInInfo, EmailConfig emailConfig, EmailData emailData) {
        return new EmailSender(loggedInInfo, emailConfig, emailData);
    }
}
