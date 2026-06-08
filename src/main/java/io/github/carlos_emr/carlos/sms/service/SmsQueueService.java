package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import org.springframework.stereotype.Service;

@Service
public class SmsQueueService {
    private static final SmsProviderType DEFAULT_PROVIDER_TYPE = SmsProviderType.STUB;

    private final SmsSendValidator validator;
    private final SmsConsentService consentService;
    private final SmsTransactionRecorder transactionRecorder;

    public SmsQueueService(
            SmsSendValidator validator,
            SmsConsentService consentService,
            SmsTransactionRecorder transactionRecorder
    ) {
        this.validator = validator;
        this.consentService = consentService;
        this.transactionRecorder = transactionRecorder;
    }

    public SmsSendResultDto enqueue(SmsSendCommand command) {
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

        return SmsSendResultDto.queued();
    }
}
