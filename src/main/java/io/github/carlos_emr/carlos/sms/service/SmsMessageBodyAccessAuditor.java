package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

public interface SmsMessageBodyAccessAuditor {
    void recordFullBodyRead(SmsTransaction transaction, LoggedInInfo loggedInInfo, String reasonCode);
}
