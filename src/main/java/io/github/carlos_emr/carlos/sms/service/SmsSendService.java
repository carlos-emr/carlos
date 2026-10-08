package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SmsSendService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final String DIRECT_PROVIDER_EXCEPTION_CODE = "DIRECT_PROVIDER_EXCEPTION";
    /** Returned, without recording anything, while SMS is turned off in Administration &gt; SMS. */
    public static final String SMS_TURNED_OFF_MESSAGE = "SMS sending is turned off in Administration > SMS.";
    /**
     * Returned, without recording anything, while the provider cannot send: a credential or the sender number it
     * needs is not saved, or a saved credential cannot be read.
     */
    public static final String SMS_PROVIDER_NOT_READY_MESSAGE = "The SMS provider is not set up: a credential or "
            + "the sender number it needs is missing or cannot be read. Check Administration > SMS.";
    /** Fixed, synthetic body for the Administration &gt; SMS system test; never patient content. */
    static final String SYSTEM_TEST_BODY = "CARLOS SMS system test. No reply needed.";

    private final SmsSendValidator validator;
    private final SmsConsentService consentService;
    private final SmsProviderClientResolver providerResolver;
    private final SmsTransactionService transactionRecorder;
    private final SmsSendRateLimitService rateLimiter;
    private final SmsDefaultProviderResolver providerSelector;
    private final SmsConfigService configService;

    /** For tests: no stored settings, so sending is always on. */
    SmsSendService(
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
        this.configService = null;
    }

    @Autowired
    public SmsSendService(
            SmsSendValidator validator,
            SmsConsentService consentService,
            SmsProviderClientResolver providerResolver,
            SmsTransactionService transactionRecorder,
            SmsSendRateLimitService rateLimiter,
            SmsDefaultProviderResolver providerSelector,
            SmsConfigService configService
    ) {
        this.validator = validator;
        this.consentService = consentService;
        this.providerResolver = providerResolver;
        this.transactionRecorder = transactionRecorder;
        this.rateLimiter = rateLimiter;
        this.providerSelector = providerSelector;
        this.configService = configService;
    }

    /**
     * Sends the fixed system-test text to a number an administrator typed, through the STUB provider only,
     * so Administration &gt; SMS can be checked before any real provider is set up. It is sent even while
     * SMS is turned off (checking the setup is the point), but still honours {@code sms.systemTest.enabled}
     * through the consent service. There is no patient; callers must hold {@code _admin.sms} write and
     * must not take the number from a chart.
     *
     * @param recipientPhoneNumber  the number the administrator typed
     * @param requestedByProviderNo the administrator's provider number
     * @param requestedBySecurityNo the administrator's security record
     * @return the send result; {@code CONSENT_BLOCKED} when system tests are switched off
     */
    public SmsSendResultDto sendSystemTest(String recipientPhoneNumber, String requestedByProviderNo,
                                           Integer requestedBySecurityNo) {
        SmsSendCommand command = new SmsSendCommand(
                null,
                recipientPhoneNumber,
                SmsRecipientPhoneType.CELL,
                SYSTEM_TEST_BODY,
                SmsMessagePurpose.SYSTEM_TEST,
                requestedByProviderNo,
                requestedBySecurityNo,
                null
        );
        return sendThrough(command, SmsProviderType.STUB);
    }

    /**
     * Sends directly through the SMS provider. If the SMS-provider rate limiter denies the attempt, the
     * row is left {@code QUEUED} (due now) and returned as queued; draining it then depends on the queue
     * scheduler ({@code sms.queue.scheduler.enabled}) or an explicit worker run, so that scheduler must
     * be enabled wherever this path is used. The claim is renewed after the permit and nothing is sent
     * unless that succeeds, because stale recovery may take over a row whose permit wait was long. If the
     * row cannot be handed back to the queue after a denial, limiter error or failed renewal, an exception
     * reaches the caller. A confirmed release returns queued, so the caller does not retry a request that
     * was already accepted. A concurrent update can reject the release; the returned result then reflects
     * the current state and never assumes it is queued.
     */
    public SmsSendResultDto send(SmsSendCommand command) {
        if (configService != null && !configService.sendingEnabled()) {
            return SmsSendResultDto.validationFailed(List.of(SMS_TURNED_OFF_MESSAGE));
        }
        return sendThrough(command, null);
    }

    /** @param forcedProvider the provider to use, or {@code null} for the configured default */
    private SmsSendResultDto sendThrough(SmsSendCommand command, SmsProviderType forcedProvider) {
        SmsSendValidator.Result validation = validator.validate(command);
        if (!validation.valid()) {
            return SmsSendResultDto.validationFailed(validation.messages());
        }

        SmsProviderType providerType = forcedProvider != null ? forcedProvider : providerSelector.configuredDefault();
        SmsProviderSettings settings;
        try {
            settings = configService == null ? SmsProviderSettings.none(providerType)
                    : configService.readyProviderSettings(providerType);
        } catch (SmsProviderNotReadyException e) {
            // Nothing is recorded, so nothing waits in the queue on settings that cannot work until an
            // administrator fixes them.
            LOGGER.error("SMS not sent: SMS provider {} is not ready: {}.", providerType, e.getMessage());
            return SmsSendResultDto.validationFailed(List.of(SMS_PROVIDER_NOT_READY_MESSAGE));
        }
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

        // Claim before taking a permit, as the queue worker does, so a claim conflict never burns one. Nothing
        // has been sent yet, so anything short of a permit hands the row back as QUEUED (due now) for the queue
        // scheduler/worker; a row left SENDING would go to stale recovery as if its outcome were unknown.
        boolean permitted;
        try {
            permitted = rateLimiter.tryAcquire(providerType);
        } catch (RuntimeException e) {
            return releaseClaimAfterFailure(transaction, "rate limiter", e);
        }
        if (!permitted) {
            // A release can lose a version race to another worker or callback. Use the returned row's
            // state instead of assuming the handoff succeeded; release exceptions still reach the caller.
            return releasedClaimResult(transactionRecorder.releaseClaim(transaction, new Date()));
        }
        // The permit wait can outlast the stale-send timeout, and stale recovery may then have taken the row over
        // and found it unsent at the SMS provider. Send only on a renewed claim; otherwise nothing was sent, so
        // hand the row back as above and report whatever state it is now in.
        try {
            transaction = transactionRecorder.renewClaim(transaction, new Date());
        } catch (RuntimeException e) {
            return releaseClaimAfterFailure(transaction, "claim renewal", e);
        }

        SmsProviderSendResultDto providerResult;
        try {
            SmsProviderClient providerClient = providerResolver.resolve(providerType);
            // The recorded row, not the caller's command: the provider gets the number in E.164 form, exactly
            // as the queue worker hands it over.
            providerResult = Objects.requireNonNull(
                    providerClient.send(transaction.toSendCommand(), clientReferenceId(transaction), settings),
                    "SMS provider result is required");
        } catch (RuntimeException e) {
            providerResult = SmsProviderSendResultDto.uncertain(DIRECT_PROVIDER_EXCEPTION_CODE);
        }
        SmsTransaction recorded = transactionRecorder.markProviderResult(transaction, providerResult);
        return SmsSendResultDto.fromTransaction(recorded);
    }

    private SmsSendResultDto releaseClaimAfterFailure(SmsTransaction transaction, String failedStep,
                                                      RuntimeException failure) {
        try {
            SmsSendResultDto result = releasedClaimResult(transactionRecorder.releaseClaim(transaction, new Date()));
            // Database exceptions can contain query parameters. Keep diagnostics to status and type.
            LOGGER.warn("SMS {} failed before sending; claim release returned {}. Failure type: {}",
                    failedStep, result.status(), failure.getClass().getSimpleName());
            return result;
        } catch (RuntimeException releaseFailure) {
            // Keep the original failure as the one reported; stale recovery still covers the row.
            if (releaseFailure != failure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }

    private SmsSendResultDto releasedClaimResult(SmsTransaction released) {
        Objects.requireNonNull(released, "SMS claim release result is required");
        if (released.getStatus() == SmsStatus.QUEUED) {
            return SmsSendResultDto.queued();
        }
        if (released.getStatus() == SmsStatus.SENDING) {
            // A version conflict (or removed row) prevented a confirmed handoff. Preserve the newer
            // claim and tell the caller to reconcile its outcome before creating another send.
            return new SmsSendResultDto(false, SmsStatus.SENDING, released.getProviderMessageId(),
                    List.of(SmsProviderSendResultDto.OUTCOME_UNKNOWN_MESSAGE));
        }
        return SmsSendResultDto.fromTransaction(released);
    }

    private String clientReferenceId(SmsTransaction transaction) {
        return Objects.requireNonNull(
                transaction.providerClientReferenceId(),
                "sms_transaction client reference id is required before SMS provider send"
        );
    }
}
