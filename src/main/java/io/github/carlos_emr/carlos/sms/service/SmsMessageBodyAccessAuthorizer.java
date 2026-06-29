package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

public interface SmsMessageBodyAccessAuthorizer {
    void assertCanReadFullBody(SmsTransaction transaction, LoggedInInfo loggedInInfo);
}
