package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SmsSendService {
    private static final SmsProviderType DEFAULT_PROVIDER_TYPE = SmsProviderType.STUB;
    private static final String DIRECT_PROVIDER_EXCEPTION_CODE = "DIRECT_PROVIDER_EXCEPTION";
    private static final String DIRECT_PROVIDER_EXCEPTION_MESSAGE =
            "SMS direct send failed because the provider client threw an exception.";

    private final SmsSendValidator validator;
    private final SmsConsentService consentService;
    private final SmsProviderResolver providerResolver;
    private final SmsTransactionRecorder transactionRecorder;

    public SmsSendService(
            SmsSendValidator validator,
            SmsConsentService consentService,
            SmsProviderResolver providerResolver,
            SmsTransactionRecorder transactionRecorder
    ) {
        this.validator = validator;
        this.consentService = consentService;
        this.providerResolver = providerResolver;
        this.transactionRecorder = transactionRecorder;
    }

    public SmsSendResultDto send(SmsSendCommand command) {
        SmsSendValidator.Result validation = validator.validate(command);
        if (!validation.valid()) {
            return SmsSendResultDto.validationFailed(validation.messages());
        }

        SmsTransaction transaction = transactionRecorder.recordOutboundAttempt(command, DEFAULT_PROVIDER_TYPE);
        SmsConsentDecisionDto consentDecision = consentService.evaluate(command);
        if (!consentDecision.allowed()) {
            transactionRecorder.markConsentBlocked(transaction, consentDecision);
            return SmsSendResultDto.consentBlocked(consentDecision);
        }

        SmsProviderSendResultDto providerResult;
        try {
            SmsProviderClient providerClient = providerResolver.resolve(DEFAULT_PROVIDER_TYPE);
            providerResult = providerClient.send(command, clientReferenceId(transaction));
        } catch (RuntimeException e) {
            providerResult = SmsProviderSendResultDto.failed(
                    DIRECT_PROVIDER_EXCEPTION_CODE,
                    DIRECT_PROVIDER_EXCEPTION_MESSAGE
            );
        }
        transactionRecorder.markProviderResult(transaction, providerResult);
        return SmsSendResultDto.fromProvider(providerResult);
    }

    private String clientReferenceId(SmsTransaction transaction) {
        Long transactionId = Objects.requireNonNull(
                transaction.getId(),
                "sms_transaction id is required before provider send"
        );
        return "sms-transaction-" + transactionId;
    }
}
