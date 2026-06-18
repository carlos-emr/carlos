package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class SmsMessageBodyReader {
    private final SmsMessageBodyAccessAuthorizer accessAuthorizer;
    private final SmsMessageBodyAccessAuditor accessAuditor;

    public SmsMessageBodyReader(
            SmsMessageBodyAccessAuthorizer accessAuthorizer,
            SmsMessageBodyAccessAuditor accessAuditor
    ) {
        this.accessAuthorizer = accessAuthorizer;
        this.accessAuditor = accessAuditor;
    }

    public Optional<String> readFullMessageBody(
            SmsTransaction transaction,
            LoggedInInfo loggedInInfo,
            String reasonCode
    ) {
        Objects.requireNonNull(transaction, "transaction is required");
        accessAuthorizer.assertCanReadFullBody(transaction, loggedInInfo);
        return transaction.readFullMessageBodyWithAudit(
                () -> accessAuditor.recordFullBodyRead(transaction, loggedInInfo, reasonCode)
        );
    }
}
