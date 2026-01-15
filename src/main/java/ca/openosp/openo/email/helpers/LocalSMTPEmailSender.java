package ca.openosp.openo.email.helpers;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import ca.openosp.openo.commn.model.EmailAttachment;
import ca.openosp.openo.commn.model.EmailConfig;
import ca.openosp.openo.utility.EmailSendingException;
import ca.openosp.openo.utility.LoggedInInfo;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LocalSMTPEmailSender extends SMTPEmailSender {

    public LocalSMTPEmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, 
                                String[] recipients, String subject, String body, 
                                List<EmailAttachment> attachments) {
        super(loggedInInfo, emailConfig, recipients, subject, body, attachments);
    }

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