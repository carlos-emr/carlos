package io.github.carlos_emr.carlos.email.helpers;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SMTP email sender specialized for local (localhost) mail server delivery.
 *
 * <p>Extends {@link SMTPEmailSender} with a custom TLS mail sender configuration that
 * restricts the SMTP host to localhost variants only (localhost, 127.0.0.1, ::1) for
 * security. Disables SMTP authentication and STARTTLS since communication occurs
 * on the local machine.</p>
 *
 * @see SMTPEmailSender
 * @see EmailConfig
 * @since 2026-01-24
 */
public class LocalSMTPEmailSender extends SMTPEmailSender {

    /**
     * Constructs a local SMTP email sender with the specified parameters.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param emailConfig EmailConfig the email configuration with local SMTP settings
     * @param recipients String[] array of recipient email addresses
     * @param subject String the email subject line
     * @param body String the email body content
     * @param attachments List of EmailAttachment objects to include, or null if none
     */
    public LocalSMTPEmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig,
                                String[] recipients, String subject, String body,
                                List<EmailAttachment> attachments) {
        super(loggedInInfo, emailConfig, recipients, subject, body, attachments);
    }

    /**
     * Creates a JavaMailSender configured for local SMTP delivery without TLS or authentication.
     *
     * <p>Validates that the configured host is a localhost variant before proceeding.
     * Throws {@link EmailSendingException} if the host is not a recognized localhost address.</p>
     *
     * @param emailConfig EmailConfig containing the local SMTP server configuration
     * @return JavaMailSender configured for local delivery
     * @throws EmailSendingException if the host is not localhost or configuration is invalid
     */
    @Override
    protected JavaMailSender createTLSMailSender(EmailConfig emailConfig) throws EmailSendingException {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(emailConfig.getConfigDetailsJson());
            String host = jsonNode.get("host").asText();
            String port = jsonNode.get("port").asText();

            // SECURITY: Only allow localhost variations
            if (!isLocalhost(host)) {
                throw new EmailSendingException("local provider can only use localhost, got: " + host);
            }
            
            mailSender.setHost(host);
            mailSender.setPort(Integer.parseInt(port));
            
            // LOCAL provider - no authentication needed
            // Username/password can be optional or ignored
            if (jsonNode.has("username")) {
                mailSender.setUsername(jsonNode.get("username").asText());
            }
            if (jsonNode.has("password")) {
                mailSender.setPassword(jsonNode.get("password").asText());
            }

            Properties properties = new Properties();
            properties.put("mail.transport.protocol", "smtp");
            properties.put("mail.smtp.auth", "false");
            properties.put("mail.smtp.starttls.enable", "false");
            properties.put("mail.smtp.starttls.required", "false");
            properties.put("mail.debug", "false");

            mailSender.setJavaMailProperties(properties);
        } catch (IOException e) {
            throw new EmailSendingException("Invalid credentials configured for " + emailConfig.getSenderEmail(), e);
        }
        return mailSender;
    }

    private boolean isLocalhost(String host) {
        return "localhost".equalsIgnoreCase(host) || 
            "127.0.0.1".equals(host) || 
            "::1".equals(host);
    }
}