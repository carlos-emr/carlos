package io.github.carlos_emr.carlos.commn.dao;

import java.util.Collections;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data Access Object implementation for managing email configuration entities in the OpenO EMR system.
 *
 * <p>This DAO provides persistence operations for email configurations used throughout the healthcare
 * system, including SMTP settings, sender credentials, and email provider configurations. Email
 * configurations are used for clinical notifications, appointment reminders, lab result notifications,
 * and other healthcare communication workflows.</p>
 *
 * <p>The implementation supports multiple active email configurations to enable different email
 * providers and sender addresses for various clinical workflows and departments within a healthcare
 * facility.</p>
 *
 * <p><strong>Security Considerations:</strong> Email configurations contain sensitive authentication
 * credentials and must be handled according to PHI protection standards. Access to email configuration
 * data should be restricted to authorized system administrators.</p>
 *
 * @see EmailConfigDao
 * @see EmailConfig
 * @see AbstractDaoImpl
 * @since 2026-01-23
 */
@Repository
public class EmailConfigDaoImpl extends AbstractDaoImpl<EmailConfig> implements EmailConfigDao {

    /**
     * Constructs a new EmailConfigDaoImpl instance.
     *
     * <p>Initializes the parent AbstractDaoImpl with the EmailConfig entity class to enable
     * generic persistence operations for email configuration entities.</p>
     */
    public EmailConfigDaoImpl() {
        super(EmailConfig.class);
    }

    /**
     * Finds an active email configuration matching the specified criteria.
     *
     * <p>Searches for an active email configuration that matches the sender email, email type,
     * and email provider from the provided EmailConfig object. This method is used when multiple
     * criteria need to be satisfied to identify a specific email configuration.</p>
     *
     * @param emailConfig EmailConfig the email configuration object containing search criteria
     *                    (senderEmail, emailType, emailProvider)
     * @return EmailConfig the matching active email configuration, or null if no match is found
     */
    @Override
    @Transactional(readOnly = true)
    public EmailConfig findActiveEmailConfig(EmailConfig emailConfig) {
        Query query = entityManager.createQuery("SELECT e FROM EmailConfig e WHERE e.senderEmail = ?1 AND e.emailType = ?2 AND e.emailProvider = ?3 AND e.active = true");

        query.setParameter(1, emailConfig.getSenderEmail());
        query.setParameter(2, emailConfig.getEmailType());
        query.setParameter(3, emailConfig.getEmailProvider());

        return getSingleResultOrNull(query);
    }

    /**
     * Finds an active email configuration by sender email address.
     *
     * <p>Retrieves the active email configuration associated with the specified sender email address.
     * This is a simplified lookup method used when only the sender email is known and other criteria
     * (email type, provider) are not required for the search.</p>
     *
     * <p>This method is commonly used for validating outbound email configurations and retrieving
     * SMTP credentials for sending clinical notifications, appointment reminders, and other healthcare
     * communications.</p>
     *
     * @param senderEmail String the sender email address to search for
     * @return EmailConfig the matching active email configuration, or null if no match is found
     */
    @Override
    @Transactional(readOnly = true)
    public EmailConfig findActiveEmailConfig(String senderEmail) {
        Query query = entityManager.createQuery("SELECT e FROM EmailConfig e WHERE e.senderEmail = ?1 AND e.active = true");
        query.setParameter(1, senderEmail);
        return getSingleResultOrNull(query);
    }

    /**
     * Finds an active email configuration by its database ID.
     *
     * <p>Retrieves the email configuration matching the specified primary key, only if it is
     * currently active. This method is used when the caller already knows the specific
     * configuration ID (e.g., from a user-selected dropdown) and needs to verify it is
     * still active before sending.</p>
     *
     * @param id int the primary key of the email configuration
     * @return EmailConfig the matching active email configuration, or null if no active config exists with this ID
     * @since 2026-02-25
     */
    @Override
    @Transactional(readOnly = true)
    public EmailConfig findActiveEmailConfigById(int id) {
        Query query = entityManager.createQuery("SELECT e FROM EmailConfig e WHERE e.id = ?1 AND e.active = true");
        query.setParameter(1, id);
        return getSingleResultOrNull(query);
    }

    /**
     * Retrieves all active email configurations in the system.
     *
     * <p>Returns a list of all email configurations that are currently marked as active. This method
     * is used for administrative purposes to display all available email configurations, and for
     * system processes that need to iterate through all active email providers.</p>
     *
     * <p>The method ensures a non-null return value by returning an empty list if no active
     * configurations are found, preventing NullPointerException in calling code.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Administrative interfaces for managing email configurations</li>
     *   <li>System health checks to verify available email providers</li>
     *   <li>Batch processing workflows that need to send emails through multiple providers</li>
     * </ul>
     *
     * @return List&lt;EmailConfig&gt; list of all active email configurations, or an empty list if none exist
     */
    @SuppressWarnings("unchecked")
    public List<EmailConfig> fillAllActiveEmailConfigs() {
        Query query = entityManager.createQuery("SELECT e FROM EmailConfig e WHERE e.active = true");

        List<EmailConfig> emailConfigs = query.getResultList();
        if (emailConfigs == null) {
            emailConfigs = Collections.emptyList();
        }
        return emailConfigs;
    }

}
