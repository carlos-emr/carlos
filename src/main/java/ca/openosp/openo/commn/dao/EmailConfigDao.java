//CHECKSTYLE:OFF
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.EmailConfig;

import java.util.Collections;
import java.util.List;

/**
 * Data Access Object for managing EmailConfig entities in the OpenO EMR system.
 *
 * <p>This DAO provides methods to retrieve and manage email configuration settings
 * used for sending email notifications and communications within the healthcare system.
 * Email configurations support multiple providers (Gmail, Outlook, SendGrid, Local) and
 * types (SMTP, API) to accommodate various healthcare facility requirements.</p>
 *
 * <p>The DAO focuses on active email configurations to ensure the system uses only
 * currently valid and operational email settings for patient communications, appointment
 * reminders, and other healthcare-related notifications.</p>
 *
 * @see EmailConfig
 * @see ca.openosp.openo.commn.model.EmailLog
 * @since 2026-01-24
 */
public interface EmailConfigDao extends AbstractDao<EmailConfig> {
    /**
     * Finds an active email configuration matching the properties of the provided EmailConfig object.
     *
     * <p>This method searches for an active configuration that matches the attributes of the
     * given EmailConfig entity. It is typically used to verify if a configuration with specific
     * properties already exists in the system before creating a new one.</p>
     *
     * @param emailConfig EmailConfig the email configuration object containing the search criteria
     * @return EmailConfig the matching active email configuration, or null if no match is found
     */
    public EmailConfig findActiveEmailConfig(EmailConfig emailConfig);

    /**
     * Finds an active email configuration by sender email address.
     *
     * <p>This method retrieves the active email configuration associated with a specific
     * sender email address. This is useful for determining which email settings to use
     * when sending notifications from a particular email account or healthcare provider.</p>
     *
     * @param senderEmail String the sender email address to search for
     * @return EmailConfig the active email configuration for the specified sender email, or null if not found
     */
    public EmailConfig findActiveEmailConfig(String senderEmail);

    /**
     * Retrieves all active email configurations in the system.
     *
     * <p>This method returns a list of all currently active email configurations available
     * for use in the healthcare system. Active configurations are those marked with the
     * active flag set to true and can be used for sending emails. This is typically used
     * for administrative interfaces or when selecting an email configuration for sending
     * notifications.</p>
     *
     * @return List&lt;EmailConfig&gt; list of all active email configurations, or an empty list if none exist
     */
    @SuppressWarnings("unchecked")
    public List<EmailConfig> fillAllActiveEmailConfigs();
}
