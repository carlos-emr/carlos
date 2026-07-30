package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class OscarLogSmsMessageBodyAccessAuditor implements SmsMessageBodyAccessAuditor {
    static final String ACTION = "SmsMessageBody.readFullBody";
    static final String CONTENT = "sms_transaction";
    private static final int MAX_REASON_CODE_LENGTH = 64;

    private final OscarLogDao oscarLogDao;

    public OscarLogSmsMessageBodyAccessAuditor(OscarLogDao oscarLogDao) {
        this.oscarLogDao = oscarLogDao;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFullBodyRead(SmsTransaction transaction, LoggedInInfo loggedInInfo, String reasonCode) {
        Objects.requireNonNull(transaction, "transaction is required");
        OscarLog log = new OscarLog();
        if (loggedInInfo != null) {
            if (loggedInInfo.getLoggedInSecurity() != null) {
                log.setSecurityId(loggedInInfo.getLoggedInSecurity().getSecurityNo());
            }
            if (loggedInInfo.getLoggedInProvider() != null) {
                log.setProviderNo(loggedInInfo.getLoggedInProviderNo());
            }
            log.setIp(loggedInInfo.getIp());
        }
        log.setAction(ACTION);
        log.setContent(CONTENT);
        log.setContentId(transaction.getId() == null ? null : transaction.getId().toString());
        log.setDemographicId(transaction.getDemographicNo());
        log.setData(dataFor(transaction, reasonCode));
        oscarLogDao.persist(log);
        oscarLogDao.flush();
    }

    private String dataFor(SmsTransaction transaction, String reasonCode) {
        return "smsTransactionId=" + nullSafe(transaction.getId())
                + " demographicNo=" + nullSafe(transaction.getDemographicNo())
                + " bodySha256=" + nullSafe(transaction.getMessageBodySha256())
                + " bodyLength=" + transaction.getMessageBodyLength()
                + " reasonCode=" + nullSafe(trimTo(reasonCode, MAX_REASON_CODE_LENGTH));
    }

    private static String trimTo(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }
}
