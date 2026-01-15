//CHECKSTYLE:OFF
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.EmailLog;

import java.util.Date;
import java.util.List;

public interface EmailLogDao extends AbstractDao<EmailLog> {
    public List<EmailLog> getEmailStatusByDateDemographicSenderStatus(Date dateBegin, Date dateEnd, String demographicNo, String senderEmailAddress, String emailStatus);
    public int updateEmailStatus(Integer id, EmailLog.EmailStatus status, String errorMessage, Date timestamp);
}
