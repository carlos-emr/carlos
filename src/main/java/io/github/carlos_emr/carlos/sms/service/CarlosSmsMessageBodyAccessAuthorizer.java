package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class CarlosSmsMessageBodyAccessAuthorizer implements SmsMessageBodyAccessAuthorizer {
    static final String SMS_MESSAGE_SECURITY_OBJECT = "_msgSMS";
    static final String DEMOGRAPHIC_SECURITY_OBJECT = "_demographic";

    private final SecurityInfoManager securityInfoManager;

    public CarlosSmsMessageBodyAccessAuthorizer(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public void assertCanReadFullBody(SmsTransaction transaction, LoggedInInfo loggedInInfo) {
        Objects.requireNonNull(transaction, "transaction is required");
        Integer demographicNo = transaction.getDemographicNo();

        if (!hasReadPrivilege(loggedInInfo, SMS_MESSAGE_SECURITY_OBJECT, demographicNo)) {
            throw accessDenied(SMS_MESSAGE_SECURITY_OBJECT, demographicNo);
        }
        if (demographicNo != null && !hasReadPrivilege(loggedInInfo, DEMOGRAPHIC_SECURITY_OBJECT, demographicNo)) {
            throw accessDenied(DEMOGRAPHIC_SECURITY_OBJECT, demographicNo);
        }
    }

    private boolean hasReadPrivilege(LoggedInInfo loggedInInfo, String securityObject, Integer demographicNo) {
        if (loggedInInfo == null) {
            return false;
        }
        if (demographicNo == null) {
            return securityInfoManager.hasPrivilege(
                    loggedInInfo,
                    securityObject,
                    SecurityInfoManager.READ,
                    (String) null
            );
        }
        return securityInfoManager.hasPrivilege(loggedInInfo, securityObject, SecurityInfoManager.READ, demographicNo);
    }

    private AccessDeniedException accessDenied(String securityObject, Integer demographicNo) {
        if (demographicNo == null) {
            return new AccessDeniedException(securityObject, SecurityInfoManager.READ);
        }
        return new AccessDeniedException(securityObject, SecurityInfoManager.READ, demographicNo);
    }
}
