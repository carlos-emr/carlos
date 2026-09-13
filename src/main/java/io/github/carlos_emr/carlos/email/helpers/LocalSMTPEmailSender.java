package io.github.carlos_emr.carlos.email.helpers;

import java.util.List;
import java.util.Properties;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.fasterxml.jackson.databind.JsonNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class LocalSMTPEmailSender extends SMTPEmailSender {

    public LocalSMTPEmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, 
                                String[] recipients, String subject, String body, 
                                List<EmailAttachment> attachments) {
        super(loggedInInfo, emailConfig, recipients, subject, body, attachments);
    }

    @Override
    protected JavaMailSender createTLSMailSender(EmailConfig emailConfig) throws EmailSendingException {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        JsonNode jsonNode = parseConfig(emailConfig);
        String host = requiredText(jsonNode, "host", emailConfig);
        String port = requiredText(jsonNode, "port", emailConfig);

        // SECURITY: Only allow localhost variations
        if (!isLocalhost(host)) {
            throw new EmailSendingException("local provider can only use localhost, got: " + host);
        }

        mailSender.setHost(host);
        try {
            mailSender.setPort(Integer.parseInt(port));
        } catch (NumberFormatException e) {
            throw invalidConfiguration(emailConfig);
        }

        // LOCAL provider - no authentication needed. Username is optional; its password is not
        // read, decrypted, or copied into the JavaMail sender when this provider cannot use it.
        if (jsonNode.has("username")) {
            mailSender.setUsername(jsonNode.get("username").asText());
        }

        Properties properties = new Properties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "false");
        properties.put("mail.smtp.starttls.enable", "false");
        properties.put("mail.smtp.starttls.required", "false");
        properties.put("mail.debug", "false");

        mailSender.setJavaMailProperties(properties);
        return mailSender;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private boolean isLocalhost(String host) {
        return "localhost".equalsIgnoreCase(host) || 
            "127.0.0.1".equals(host) || 
            "::1".equals(host);
    }
}
