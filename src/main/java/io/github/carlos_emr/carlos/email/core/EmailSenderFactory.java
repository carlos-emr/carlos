package io.github.carlos_emr.carlos.email.core;

import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Creates EmailSender instances behind an injectable boundary for send-path tests.
 */
@Component
public class EmailSenderFactory {
    public EmailSender create(LoggedInInfo loggedInInfo, EmailConfig emailConfig, EmailData emailData) {
        return new EmailSender(loggedInInfo, emailConfig, emailData);
    }
}
