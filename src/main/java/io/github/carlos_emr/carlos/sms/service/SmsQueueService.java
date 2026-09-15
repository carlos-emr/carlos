package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SmsQueueService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final int IMMEDIATE_WAKE_BATCH_SIZE = 1;

    private final SmsSendValidator validator;
    private final SmsConsentService consentService;
    private final SmsTransactionService transactionRecorder;
    private final SmsQueueProcessingService smsQueueWorker;
    private final SmsDefaultProviderResolver providerSelector;

    public SmsQueueService(
            SmsSendValidator validator,
            SmsConsentService consentService,
            SmsTransactionService transactionRecorder,
            SmsQueueProcessingService smsQueueWorker,
            SmsDefaultProviderResolver providerSelector
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
        SmsConsentDecisionDto consentDecision = Objects.requireNonNull(
                consentService.evaluate(command), "SMS consent decision is required");
        transactionRecorder.recordOutboundAttempt(command, providerType, consentDecision);
        if (!consentDecision.allowed()) {
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
