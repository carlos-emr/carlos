package io.github.carlos_emr.carlos.sms.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.SmsTransactionType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.support.SmsAuditRedactor;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

@Entity
@Table(
        name = "sms_transaction",
        uniqueConstraints = {
                // Mirrors sms_transaction_provider_message_uidx in the migration: one row per SMS-provider message id
                // so retried/redelivered webhooks reconcile onto the existing row instead of duplicating it.
                @UniqueConstraint(
                        name = "sms_transaction_provider_message_uidx",
                        columnNames = {"provider_type", "provider_message_id"}
                ),
                // The CARLOS client reference is known before provider send, so early delivery webhooks can
                // reconcile to the outbound row even before provider_message_id is written back.
                @UniqueConstraint(
                        name = "sms_transaction_client_reference_uidx",
                        columnNames = {"provider_type", "client_reference_id"}
                )
        }
)
public class SmsTransaction extends AbstractModel<Long> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_PHONE_LENGTH = 32;
    private static final int MAX_REQUESTED_BY_HEALTHCARE_PROVIDER_NO_LENGTH = 16;
    private static final int MAX_PROVIDER_MESSAGE_ID_LENGTH = 128;
    private static final int MAX_CLIENT_REFERENCE_ID_LENGTH = 64;
    private static final int MAX_REASON_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;
    private static final int MAX_CLAIM_TOKEN_LENGTH = 64;
    private static final String WEBHOOK_REQUIRED_MESSAGE = "webhook is required";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private SmsDirection direction = SmsDirection.OUTBOUND;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 16)
    private SmsProviderType providerType = SmsProviderType.STUB;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private SmsTransactionType transactionType = SmsTransactionType.DIRECT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SmsStatus status = SmsStatus.QUEUED;

    @Column(name = "demographic_no")
    private Integer demographicNo;

    @Column(name = "requested_by_healthcare_provider_no", length = MAX_REQUESTED_BY_HEALTHCARE_PROVIDER_NO_LENGTH)
    private String requestedByHealthcareProviderNo;

    @Column(name = "requested_by_security_no")
    private Integer requestedBySecurityNo;

    @Column(name = "appointment_no")
    private Integer appointmentNo;

    @Column(name = "from_phone_number", length = MAX_PHONE_LENGTH)
    private String fromPhoneNumber;

    @Column(name = "to_phone_number", length = MAX_PHONE_LENGTH)
    private String toPhoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_phone_type", length = 16)
    private SmsRecipientPhoneType recipientPhoneType;

    @Column(name = "provider_message_id", length = MAX_PROVIDER_MESSAGE_ID_LENGTH)
    private String providerMessageId;

    @Column(name = "client_reference_id", length = MAX_CLIENT_REFERENCE_ID_LENGTH)
    private String clientReferenceId;

    @Lob
    @Column(name = "message_body", columnDefinition = "TEXT")
    private String messageBody;

    @Column(name = "message_body_sha256", nullable = false, length = 64)
    private String messageBodySha256 = SmsAuditRedactor.digest("", 64);

    @Column(name = "message_body_length", nullable = false)
    private int messageBodyLength;

    @Column(name = "consent_reason_code", length = MAX_REASON_CODE_LENGTH)
    private String consentReasonCode;

    @Column(name = "error_code", length = MAX_REASON_CODE_LENGTH)
    private String errorCode;

    @Column(name = "error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String errorMessage;

    @Column(name = "provider_metadata", columnDefinition = "TEXT")
    private String providerMetadata;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "next_attempt_at")
    private Date nextAttemptAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_attempt_at")
    private Date lastAttemptAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "sent_at")
    private Date sentAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "delivered_at")
    private Date deliveredAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "received_at")
    private Date receivedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "provider_event_at")
    private Date providerEventAt;

    @Column(name = "claim_token", length = MAX_CLAIM_TOKEN_LENGTH)
    private String claimToken;

    // Optimistic-lock guard. The queue worker claims a row, calls the SMS provider, then writes the
    // result in a separate transaction; that detached write can race a delivery/inbound webhook updating
    // the same row. @Version turns a silent lost update into a detectable conflict so the recorder can
    // drop the stale worker write and let the most recent authoritative write stand.
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public SmsTransaction() {
        // Required by JPA.
    }

    public static SmsTransaction outboundAttempt(SmsSendCommand command, SmsProviderType providerType) {
        Objects.requireNonNull(command, "command is required");
        SmsTransaction transaction = new SmsTransaction();
        transaction.direction = SmsDirection.OUTBOUND;
        transaction.providerType = providerType == null ? SmsProviderType.STUB : providerType;
        transaction.status = SmsStatus.QUEUED;
        transaction.demographicNo = command.demographicNo();
        transaction.requestedByHealthcareProviderNo = trimTo(
                command.requestedByHealthcareProviderNo(),
                MAX_REQUESTED_BY_HEALTHCARE_PROVIDER_NO_LENGTH
        );
        transaction.requestedBySecurityNo = command.requestedBySecurityNo();
        transaction.appointmentNo = command.appointmentNo();
        transaction.transactionType = command.transactionType();
        transaction.toPhoneNumber = normalizePhone(command.recipientPhoneNumber());
        transaction.recipientPhoneType = command.recipientPhoneType();
        transaction.messageBody = command.body();
        transaction.nextAttemptAt = new Date();
        transaction.refreshBodyAudit();
        return transaction;
    }

    public static SmsTransaction inboundMessage(SmsInboundWebhookDto webhook) {
        Objects.requireNonNull(webhook, WEBHOOK_REQUIRED_MESSAGE);
        SmsTransaction transaction = new SmsTransaction();
        transaction.direction = SmsDirection.INBOUND;
        transaction.providerType = webhook.providerType() == null ? SmsProviderType.STUB : webhook.providerType();
        transaction.transactionType = SmsTransactionType.DIRECT;
        transaction.status = SmsStatus.RECEIVED;
        transaction.providerMessageId = trimTo(webhook.providerMessageId(), MAX_PROVIDER_MESSAGE_ID_LENGTH);
        transaction.fromPhoneNumber = normalizePhone(webhook.fromPhoneNumber());
        transaction.toPhoneNumber = normalizePhone(webhook.toPhoneNumber());
        transaction.messageBody = webhook.body();
        transaction.providerMetadata = providerMetadataJson(webhook.providerMetadata());
        transaction.receivedAt = webhook.receivedAt() == null ? new Date() : Date.from(webhook.receivedAt());
        transaction.refreshBodyAudit();
        return transaction;
    }

    public static SmsTransaction deliveryEvent(SmsDeliveryWebhookDto webhook) {
        Objects.requireNonNull(webhook, WEBHOOK_REQUIRED_MESSAGE);
        if (isBlank(webhook.providerMessageId()) && isBlank(webhook.clientReferenceId())) {
            throw new IllegalArgumentException(
                    "providerMessageId or clientReferenceId is required for delivery webhooks"
            );
        }
        SmsTransaction transaction = new SmsTransaction();
        transaction.direction = SmsDirection.OUTBOUND;
        transaction.providerType = webhook.providerType() == null ? SmsProviderType.STUB : webhook.providerType();
        transaction.transactionType = SmsTransactionType.DIRECT;
        transaction.providerMessageId = trimTo(webhook.providerMessageId(), MAX_PROVIDER_MESSAGE_ID_LENGTH);
        transaction.clientReferenceId = trimTo(webhook.clientReferenceId(), MAX_CLIENT_REFERENCE_ID_LENGTH);
        transaction.markDeliveryEvent(webhook);
        return transaction;
    }

    public void markConsentBlocked(SmsConsentDecisionDto decision) {
        Objects.requireNonNull(decision, "decision is required");
        status = decision.blockedStatus();
        consentReasonCode = trimTo(decision.reasonCode(), MAX_REASON_CODE_LENGTH);
        errorMessage = trimTo(decision.operatorMessage(), MAX_ERROR_MESSAGE_LENGTH);
        clearClaim();
        touch();
    }

    public void markSending(Date attemptAt) {
        Date safeAttemptAt = copyOf(attemptAt);
        if (safeAttemptAt == null) {
            safeAttemptAt = new Date();
        }
        status = SmsStatus.SENDING;
        attemptCount++;
        lastAttemptAt = safeAttemptAt;
        nextAttemptAt = null;
        touch();
    }

    /**
     * Tags the row with the worker-run claim token for traceability. Set after {@link #markSending}
     * or {@link #markStaleRecoveryStarted} when a worker claims the row.
     */
    public void assignClaimToken(String claimToken) {
        this.claimToken = trimTo(claimToken, MAX_CLAIM_TOKEN_LENGTH);
        touch();
    }

    public void assignClientReferenceId(String clientReferenceId) {
        this.clientReferenceId = trimTo(blankToNull(clientReferenceId), MAX_CLIENT_REFERENCE_ID_LENGTH);
        touch();
    }

    /**
     * Reverts a claim that was taken but never sent (e.g. the SMS-provider rate limiter denied the
     * token after the row was already claimed). The row returns to {@link SmsStatus#QUEUED}, the
     * attempt increment from the claim is rolled back, and the row is made due again so the worker
     * or scheduler retries it without burning a real attempt.
     */
    public void markClaimReleased(Date dueAt) {
        Date safeDueAt = copyOf(dueAt);
        if (safeDueAt == null) {
            safeDueAt = new Date();
        }
        status = SmsStatus.QUEUED;
        if (attemptCount > 0) {
            attemptCount--;
        }
        nextAttemptAt = safeDueAt;
        clearClaim();
        touch();
    }

    public void markStaleRecoveryStarted(Date recoveryAt) {
        Date safeRecoveryAt = copyOf(recoveryAt);
        if (safeRecoveryAt == null) {
            safeRecoveryAt = new Date();
        }
        status = SmsStatus.SENDING;
        lastAttemptAt = safeRecoveryAt;
        nextAttemptAt = null;
        touch();
    }

    public void markProviderResult(SmsProviderSendResultDto providerResult) {
        Objects.requireNonNull(providerResult, "providerResult is required");
        status = providerResult.status() == null ? SmsStatus.FAILED : providerResult.status();
        String resultProviderMessageId = trimTo(providerResult.providerMessageId(), MAX_PROVIDER_MESSAGE_ID_LENGTH);
        if (!isBlank(resultProviderMessageId) || isBlank(providerMessageId)) {
            providerMessageId = resultProviderMessageId;
        }
        errorCode = trimTo(providerResult.errorCode(), MAX_REASON_CODE_LENGTH);
        errorMessage = trimTo(providerResult.errorMessage(), MAX_ERROR_MESSAGE_LENGTH);
        nextAttemptAt = null;
        clearClaim();
        if (status == SmsStatus.SENT && sentAt == null) {
            sentAt = new Date();
        }
        if (status == SmsStatus.DELIVERED && deliveredAt == null) {
            deliveredAt = new Date();
        }
        touch();
    }

    public void markRetryScheduled(SmsProviderSendResultDto providerResult, Date nextAttemptAt) {
        Objects.requireNonNull(providerResult, "providerResult is required");
        status = SmsStatus.QUEUED;
        providerMessageId = trimTo(providerResult.providerMessageId(), MAX_PROVIDER_MESSAGE_ID_LENGTH);
        errorCode = trimTo(providerResult.errorCode(), MAX_REASON_CODE_LENGTH);
        errorMessage = trimTo(providerResult.errorMessage(), MAX_ERROR_MESSAGE_LENGTH);
        this.nextAttemptAt = copyOf(nextAttemptAt);
        clearClaim();
        touch();
    }

    public void markDeliveryEvent(SmsDeliveryWebhookDto webhook) {
        Objects.requireNonNull(webhook, WEBHOOK_REQUIRED_MESSAGE);
        SmsStatus webhookStatus = webhook.status() == null ? SmsStatus.FAILED : webhook.status();
        Date webhookEventAt = webhook.eventAt() == null ? null : Date.from(webhook.eventAt());
        if (shouldIgnoreDeliveryEvent(webhookStatus, webhookEventAt)) {
            return;
        }

        Date appliedEventAt = webhookEventAt == null ? new Date() : webhookEventAt;
        status = webhookStatus;
        if (!isBlank(webhook.providerMessageId()) && isBlank(providerMessageId)) {
            providerMessageId = trimTo(webhook.providerMessageId(), MAX_PROVIDER_MESSAGE_ID_LENGTH);
        }
        if (!isBlank(webhook.clientReferenceId()) && isBlank(clientReferenceId)) {
            clientReferenceId = trimTo(webhook.clientReferenceId(), MAX_CLIENT_REFERENCE_ID_LENGTH);
        }
        errorCode = trimTo(webhook.errorCode(), MAX_REASON_CODE_LENGTH);
        errorMessage = trimTo(webhook.errorMessage(), MAX_ERROR_MESSAGE_LENGTH);
        String providerMetadataJson = providerMetadataJson(webhook.providerMetadata());
        if (providerMetadataJson != null) {
            providerMetadata = providerMetadataJson;
        }
        if (webhookEventAt != null || providerEventAt == null) {
            providerEventAt = appliedEventAt;
        }
        if (status == SmsStatus.DELIVERED && (deliveredAt == null || webhookEventAt != null)) {
            deliveredAt = appliedEventAt;
        }
        clearClaim();
        touch();
    }

    @PrePersist
    void beforePersist() {
        Date now = new Date();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        refreshBodyAudit();
    }

    @PreUpdate
    void beforeUpdate() {
        touch();
        refreshBodyAudit();
    }

    private void touch() {
        updatedAt = new Date();
    }

    private void refreshBodyAudit() {
        String body = messageBody == null ? "" : messageBody;
        messageBodyLength = body.length();
        messageBodySha256 = SmsAuditRedactor.digest(body, 64);
    }

    private void clearClaim() {
        claimToken = null;
    }

    private boolean shouldIgnoreDeliveryEvent(SmsStatus webhookStatus, Date webhookEventAt) {
        if (webhookEventAt != null && providerEventAt != null && webhookEventAt.before(providerEventAt)) {
            return true;
        }
        if (status == SmsStatus.DELIVERED && webhookStatus != SmsStatus.DELIVERED) {
            return true;
        }
        return status == SmsStatus.DELIVERED
                && webhookEventAt != null
                && deliveredAt != null
                && webhookEventAt.before(deliveredAt);
    }

    private static String normalizePhone(String phoneNumber) {
        return SmsPhoneNumbers.normalizeToE164(phoneNumber)
                .orElse(trimTo(phoneNumber, MAX_PHONE_LENGTH));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static String trimTo(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String providerMetadataJson(Map<String, String> providerMetadata) {
        if (providerMetadata == null || providerMetadata.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(new TreeMap<>(providerMetadata));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("providerMetadata must be serializable", e);
        }
    }

    public SmsSendCommand toSendCommand() {
        return new SmsSendCommand(
                demographicNo,
                toPhoneNumber,
                recipientPhoneType,
                messageBody,
                transactionType,
                requestedByHealthcareProviderNo,
                requestedBySecurityNo,
                appointmentNo
        );
    }

    @Override
    public Long getId() {
        return id;
    }

    public SmsDirection getDirection() {
        return direction;
    }

    public SmsProviderType getProviderType() {
        return providerType;
    }

    public SmsTransactionType getTransactionType() {
        return transactionType;
    }

    public SmsStatus getStatus() {
        return status;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public String getRequestedByHealthcareProviderNo() {
        return requestedByHealthcareProviderNo;
    }

    public Integer getRequestedBySecurityNo() {
        return requestedBySecurityNo;
    }

    public Integer getAppointmentNo() {
        return appointmentNo;
    }

    public String getFromPhoneNumber() {
        return fromPhoneNumber;
    }

    public String getToPhoneNumber() {
        return toPhoneNumber;
    }

    public SmsRecipientPhoneType getRecipientPhoneType() {
        return recipientPhoneType;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getClientReferenceId() {
        return clientReferenceId;
    }

    public String providerClientReferenceId() {
        if (!isBlank(clientReferenceId)) {
            return clientReferenceId;
        }
        if (id == null) {
            return null;
        }
        return clientReferenceIdFor(id);
    }

    public static String clientReferenceIdFor(Long transactionId) {
        Objects.requireNonNull(transactionId, "sms_transaction id is required before SMS provider send");
        return "sms-transaction-" + transactionId;
    }

    /**
     * Runs the caller-provided audit action before exposing the stored full body.
     */
    public Optional<String> readFullMessageBodyWithAudit(Runnable auditAction) {
        Objects.requireNonNull(auditAction, "auditAction is required");
        auditAction.run();
        return Optional.ofNullable(messageBody);
    }

    public String getMessageBodySha256() {
        return messageBodySha256;
    }

    public int getMessageBodyLength() {
        return messageBodyLength;
    }

    public String getConsentReasonCode() {
        return consentReasonCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getProviderMetadata() {
        return providerMetadata;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Date getCreatedAt() {
        return copyOf(createdAt);
    }

    public Date getUpdatedAt() {
        return copyOf(updatedAt);
    }

    public Date getNextAttemptAt() {
        return copyOf(nextAttemptAt);
    }

    public Date getLastAttemptAt() {
        return copyOf(lastAttemptAt);
    }

    public Date getSentAt() {
        return copyOf(sentAt);
    }

    public Date getDeliveredAt() {
        return copyOf(deliveredAt);
    }

    public Date getReceivedAt() {
        return copyOf(receivedAt);
    }

    public Date getProviderEventAt() {
        return copyOf(providerEventAt);
    }

    public String getClaimToken() {
        return claimToken;
    }

    public long getVersion() {
        return version;
    }

    private static Date copyOf(Date date) {
        return date == null ? null : new Date(date.getTime());
    }
}
