package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class SmsMessageBodyReader {
    private final SmsMessageBodyAccessAuditor accessAuditor;

    public SmsMessageBodyReader(SmsMessageBodyAccessAuditor accessAuditor) {
        this.accessAuditor = accessAuditor;
    }

    @SuppressWarnings("deprecation")
    public Optional<String> readFullMessageBody(
            SmsTransaction transaction,
            LoggedInInfo loggedInInfo,
            String reasonCode
    ) {
        Objects.requireNonNull(transaction, "transaction is required");
        accessAuditor.recordFullBodyRead(transaction, loggedInInfo, reasonCode);
        return Optional.ofNullable(transaction.getMessageBody());
    }
}
