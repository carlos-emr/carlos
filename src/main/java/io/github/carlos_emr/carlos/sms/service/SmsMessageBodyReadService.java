package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class SmsMessageBodyReadService {
    private final SmsMessageBodyAuthorizationService accessAuthorizer;
    private final SmsMessageBodyAuditPersister accessAuditor;

    public SmsMessageBodyReadService(
            SmsMessageBodyAuthorizationService accessAuthorizer,
            SmsMessageBodyAuditPersister accessAuditor
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
