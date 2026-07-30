package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class SmsQueueService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final int IMMEDIATE_WAKE_BATCH_SIZE = 1;

    private final SmsSendValidator validator;
    private final SmsConsentService consentService;
    private final SmsTransactionRecorder transactionRecorder;
    private final SmsQueueWorker smsQueueWorker;
    private final SmsProviderSelector providerSelector;

    public SmsQueueService(
            SmsSendValidator validator,
            SmsConsentService consentService,
            SmsTransactionRecorder transactionRecorder,
            SmsQueueWorker smsQueueWorker,
            SmsProviderSelector providerSelector
    ) {
        this.validator = validator;
        this.consentService = consentService;
        this.transactionRecorder = transactionRecorder;
        this.smsQueueWorker = smsQueueWorker;
        this.providerSelector = providerSelector;
    }

    public SmsSendResultDto enqueue(SmsSendCommand command) {
        SmsSendValidator.Result validation = validator.validate(command);
        if (!validation.valid()) {
            return SmsSendResultDto.validationFailed(validation.messages());
        }

        SmsProviderType providerType = providerSelector.configuredDefault();
        SmsTransaction transaction = transactionRecorder.recordOutboundAttempt(command, providerType);
        SmsConsentDecisionDto consentDecision = consentService.evaluate(command);
        if (!consentDecision.allowed()) {
            transactionRecorder.markConsentBlocked(transaction, consentDecision);
            return SmsSendResultDto.consentBlocked(consentDecision);
        }

        return SmsSendResultDto.queued();
    }

    public SmsSendResultDto enqueueAndProcessNow(SmsSendCommand command) {
        SmsSendResultDto result = enqueue(command);
        if (result.accepted() && result.status() == SmsStatus.QUEUED) {
            wakeWorkerSafely();
        }
        return result;
    }

    private void wakeWorkerSafely() {
        try {
            smsQueueWorker.processDueMessages(IMMEDIATE_WAKE_BATCH_SIZE);
        } catch (RuntimeException e) {
            LOGGER.warn(
                    "SMS queue immediate worker wake failed; scheduler will retry queued work; exceptionClass={}",
                    exceptionClass(e)
            );
        }
    }

    private static String exceptionClass(RuntimeException e) {
        return e == null ? "unknown" : e.getClass().getName();
    }
}
