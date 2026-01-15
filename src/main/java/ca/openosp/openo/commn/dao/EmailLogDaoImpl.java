//CHECKSTYLE:OFF
package ca.openosp.openo.commn.dao;

import java.util.Date;
import java.util.List;

import javax.persistence.Query;

import org.springframework.stereotype.Repository;

import ca.openosp.openo.commn.model.EmailLog;

@Repository
public class EmailLogDaoImpl extends AbstractDaoImpl<EmailLog> implements EmailLogDao {

    public EmailLogDaoImpl() {
        super(EmailLog.class);
    }

    /**
     * This method is used for retrieving email logs based on various filters such as date range, demographic number,
     * sender email address, and email status. It is called from the 'Admin > Emails > Manage Emails' page.
     *
     * @param dateBegin          The start date for filtering email logs.
     * @param dateEnd            The end date for filtering email logs.
     * @param demographicNo      The demographic number for demographic lastname and firstname.
     * @param senderEmailAddress The sender email address for filtering email logs.
     * @param emailStatus        The email status for filtering email logs.
     * @return A list of email logs that match the specified filters.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<EmailLog> getEmailStatusByDateDemographicSenderStatus(Date dateBegin, Date dateEnd, String demographicNo, String senderEmailAddress, String emailStatus) {
        String hql = "SELECT el FROM EmailLog el JOIN el.emailConfig ec JOIN el.demographic d JOIN el.provider p " +
                "WHERE 1=1 " +
                "AND el.demographic.DemographicNo = IFNULL(?1, el.demographic.DemographicNo) " +
                "AND el.status = IFNULL(?2, el.status) " +
                "AND el.fromEmail = IFNULL(?3, el.fromEmail) " +
                "AND DATE(el.timestamp) BETWEEN DATE(?4) AND DATE(?5) " +
                "ORDER BY el.timestamp DESC";

        Query query = entityManager.createQuery(hql);
        query.setParameter(4, dateBegin);
        query.setParameter(5, dateEnd);
        query.setParameter(1, demographicNo);
        query.setParameter(2, emailStatus);
        query.setParameter(3, senderEmailAddress);

        return query.getResultList();
    }

    /**
     * Updates only the status, errorMessage, and timestamp of EmailLog without touching LOB fields.
     *
     * @param id           The ID of the EmailLog to update.
     * @param status       The new status to set.
     * @param errorMessage The error message to set. If {@code null}, the existing error message will be cleared (set to {@code null}).
     * @param timestamp    The timestamp to set.
     * @return The number of rows updated (should be 1 if the record exists).
     */
    @Override
    public int updateEmailStatus(Integer id, EmailLog.EmailStatus status, String errorMessage, Date timestamp) {
        String hql = "UPDATE EmailLog e SET e.status = :status, e.errorMessage = :msg, e.timestamp = :ts WHERE e.id = :id";
        Query query = entityManager.createQuery(hql);
        query.setParameter("id", id);
        query.setParameter("status", status);
        query.setParameter("msg", errorMessage);
        query.setParameter("ts", timestamp);
        return query.executeUpdate();
    }
}
