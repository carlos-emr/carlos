package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;

@Service
public class SmsSendService {
    private static final String DIRECT_PROVIDER_EXCEPTION_CODE = "DIRECT_PROVIDER_EXCEPTION";
    private static final String DIRECT_PROVIDER_EXCEPTION_MESSAGE =
            "SMS direct send failed because the SMS provider client threw an exception.";

    private final SmsSendValidator validator;
    private final SmsConsentService consentService;
    private final SmsProviderResolver providerResolver;
    private final SmsTransactionRecorder transactionRecorder;
    private final SmsSendRateLimiter rateLimiter;
    private final SmsProviderSelector providerSelector;

    public SmsSendService(
            SmsSendValidator validator,
            SmsConsentService consentService,
            SmsProviderResolver providerResolver,
            SmsTransactionRecorder transactionRecorder,
            SmsSendRateLimiter rateLimiter,
            SmsProviderSelector providerSelector
    ) {
        this.validator = validator;
        this.consentService = consentService;
        this.providerResolver = providerResolver;
        this.transactionRecorder = transactionRecorder;
        this.rateLimiter = rateLimiter;
        this.providerSelector = providerSelector;
    }

    /**
     * Sends directly through the SMS provider. If the SMS-provider rate limiter denies the attempt, the
     * row is left {@code QUEUED} (due now) and returned as queued; draining it then depends on the queue
     * scheduler ({@code sms.queue.scheduler.enabled}) or an explicit worker run, so that scheduler must
     * be enabled wherever this path is used.
     */
    public SmsSendResultDto send(SmsSendCommand command) {
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

        if (!rateLimiter.tryAcquire(providerType)) {
            // The row is already persisted as QUEUED (due now), so leave it for the queue
            // scheduler/worker to drain rather than exceeding the SMS provider rate limit here.
            return SmsSendResultDto.queued();
        }

        try {
            transaction = transactionRecorder.markSending(transaction, new Date());
        } catch (SmsTransactionClaimConflictException e) {
            return SmsSendResultDto.queued();
        }

        SmsProviderSendResultDto providerResult;
        try {
            SmsProviderClient providerClient = providerResolver.resolve(providerType);
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
        return Objects.requireNonNull(
                transaction.providerClientReferenceId(),
                "sms_transaction client reference id is required before SMS provider send"
        );
    }
}
