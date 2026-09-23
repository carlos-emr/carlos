package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SmsSendService {
    private static final String DIRECT_PROVIDER_EXCEPTION_CODE = "DIRECT_PROVIDER_EXCEPTION";

    private final SmsSendValidator validator;
    private final SmsConsentService consentService;
    private final SmsProviderClientResolver providerResolver;
    private final SmsTransactionService transactionRecorder;
    private final SmsSendRateLimitService rateLimiter;
    private final SmsDefaultProviderResolver providerSelector;

    public SmsSendService(
            SmsSendValidator validator,
            SmsConsentService consentService,
            SmsProviderClientResolver providerResolver,
            SmsTransactionService transactionRecorder,
            SmsSendRateLimitService rateLimiter,
            SmsDefaultProviderResolver providerSelector
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
        SmsConsentDecisionDto consentDecision = Objects.requireNonNull(
                consentService.evaluate(command), "SMS consent decision is required");
        SmsTransaction transaction = transactionRecorder.recordOutboundAttempt(command, providerType, consentDecision);
        if (!consentDecision.allowed()) {
            return SmsSendResultDto.consentBlocked(consentDecision);
        }

        try {
            transaction = transactionRecorder.markSending(transaction, new Date());
        } catch (SmsTransactionClaimConflictException e) {
            return SmsSendResultDto.queued();
        }

        // Claim before taking a permit, as the queue worker does, so a claim conflict never burns one.
        if (!rateLimiter.tryAcquire(providerType)) {
            // Hand the row back as QUEUED (due now) for the queue scheduler/worker to drain rather than
            // exceeding the SMS provider rate limit here.
            transactionRecorder.releaseClaim(transaction, new Date());
            return SmsSendResultDto.queued();
        }

        SmsProviderSendResultDto providerResult;
        try {
            SmsProviderClient providerClient = providerResolver.resolve(providerType);
            providerResult = Objects.requireNonNull(providerClient.send(command, clientReferenceId(transaction)),
                    "SMS provider result is required");
        } catch (RuntimeException e) {
            providerResult = SmsProviderSendResultDto.uncertain(DIRECT_PROVIDER_EXCEPTION_CODE);
        }
        SmsTransaction recorded = transactionRecorder.markProviderResult(transaction, providerResult);
        return SmsSendResultDto.fromTransaction(recorded);
    }

    private String clientReferenceId(SmsTransaction transaction) {
        return Objects.requireNonNull(
                transaction.providerClientReferenceId(),
                "sms_transaction client reference id is required before SMS provider send"
        );
    }
}
