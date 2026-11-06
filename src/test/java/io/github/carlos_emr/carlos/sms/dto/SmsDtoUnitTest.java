package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsProviderErrorCode;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("dto")
class SmsDtoUnitTest {
    @Test
    @DisplayName("consent-blocked send result tolerates missing operator message")
    void shouldCreateConsentBlockedResult_whenOperatorMessageIsMissing() {
        SmsConsentDecisionDto decision = SmsConsentDecisionDto.blocked(
                SmsStatus.CONSENT_BLOCKED,
                "SMS_CONSENT_UNKNOWN",
                null
        );

        SmsSendResultDto result = SmsSendResultDto.consentBlocked(decision);

        assertThat(result)
                .extracting(SmsSendResultDto::accepted, SmsSendResultDto::status, SmsSendResultDto::messages)
                .containsExactly(false, SmsStatus.CONSENT_BLOCKED, List.of());
    }

    @Test
    @DisplayName("permitted consent decision carries the consent record it relied on")
    void shouldCarryConsentSnapshot_whenDecisionIsPermitted() {
        Instant editedAt = Instant.parse("2026-09-01T14:30:00Z");

        SmsConsentDecisionDto decision = SmsConsentDecisionDto.permitted(SmsConsentStatus.OPT_IN, 4321, editedAt);

        assertThat(decision)
                .extracting(
                        SmsConsentDecisionDto::allowed,
                        SmsConsentDecisionDto::blockedStatus,
                        SmsConsentDecisionDto::consentStatus,
                        SmsConsentDecisionDto::consentId,
                        SmsConsentDecisionDto::consentLastUpdateDate
                )
                .containsExactly(true, null, SmsConsentStatus.OPT_IN, 4321, editedAt);
    }

    @Test
    @DisplayName("blocked consent decision carries the consent record it relied on")
    void shouldCarryConsentSnapshot_whenDecisionIsBlocked() {
        Instant editedAt = Instant.parse("2026-09-01T14:30:00Z");

        SmsConsentDecisionDto decision = SmsConsentDecisionDto.blocked(
                SmsStatus.OPTOUT_BLOCKED,
                "SMS_CONSENT_OPTED_OUT",
                "Patient opted out",
                SmsConsentStatus.OPT_OUT,
                4321,
                editedAt
        );

        assertThat(decision)
                .extracting(
                        SmsConsentDecisionDto::allowed,
                        SmsConsentDecisionDto::blockedStatus,
                        SmsConsentDecisionDto::consentStatus,
                        SmsConsentDecisionDto::consentId,
                        SmsConsentDecisionDto::consentLastUpdateDate
                )
                .containsExactly(false, SmsStatus.OPTOUT_BLOCKED, SmsConsentStatus.OPT_OUT, 4321, editedAt);
    }

    @Test
    @DisplayName("the snapshot-free blocked factory leaves the consent snapshot empty")
    void shouldLeaveConsentSnapshotEmpty_forSnapshotFreeBlockedFactory() {
        assertThat(SmsConsentDecisionDto.blocked(SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", "blocked"))
                .extracting(
                        SmsConsentDecisionDto::consentStatus,
                        SmsConsentDecisionDto::consentId,
                        SmsConsentDecisionDto::consentLastUpdateDate
                )
                .containsExactly(null, null, null);
    }

    @Test
    @DisplayName("queued send result reports accepted queue state")
    void shouldCreateQueuedResult_whenMessageIsQueued() {
        SmsSendResultDto result = SmsSendResultDto.queued();

        assertThat(result)
                .extracting(SmsSendResultDto::accepted, SmsSendResultDto::status, SmsSendResultDto::messages)
                .containsExactly(true, SmsStatus.QUEUED, List.of());
    }

    @Test
    @DisplayName("accepted SMS provider results require SMS provider id and send-success status")
    void shouldRejectAcceptedProviderResult_whenRequiredFieldsAreInvalid() {
        assertThatThrownBy(() -> SmsProviderSendResultDto.accepted(null, SmsStatus.SENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerMessageId");
        assertThatThrownBy(() -> SmsProviderSendResultDto.accepted(" ", SmsStatus.SENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerMessageId");
        assertThatThrownBy(() -> SmsProviderSendResultDto.accepted("provider-1", SmsStatus.FAILED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SENT or DELIVERED");
    }

    @Test
    @DisplayName("blocked consent decisions require a blocking SMS status")
    void shouldRejectConsentBlockedDecision_whenStatusIsNotBlocking() {
        assertThatThrownBy(() -> SmsConsentDecisionDto.blocked(null, "MISSING", "blocked"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blockedStatus");
        assertThatThrownBy(() -> SmsConsentDecisionDto.blocked(SmsStatus.FAILED, "FAILED", "blocked"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONSENT_BLOCKED or OPTOUT_BLOCKED");
    }

    @Test
    @DisplayName("inbound webhook metadata allows nullable SMS provider values and remains immutable")
    void shouldPreserveInboundMetadata_whenProviderValueIsNull() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("nullable", null);

        SmsInboundWebhookDto dto = new SmsInboundWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                "+14165551212",
                "+14165550000",
                "reply",
                Instant.EPOCH,
                metadata
        );

        assertThat(dto.providerMetadata()).containsEntry("nullable", null);
        Map<String, String> providerMetadata = dto.providerMetadata();
        assertThatThrownBy(() -> providerMetadata.put("next", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("delivery webhook metadata allows nullable SMS provider values and remains immutable")
    void shouldPreserveDeliveryMetadata_whenProviderValueIsNull() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("nullable", null);

        SmsDeliveryWebhookDto dto = new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                SmsStatus.DELIVERED,
                Instant.EPOCH,
                null,
                null,
                metadata
        );

        assertThat(dto.providerMetadata()).containsEntry("nullable", null);
        Map<String, String> providerMetadata = dto.providerMetadata();
        assertThatThrownBy(() -> providerMetadata.put("next", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("webhook metadata drops sensitive keys and bounds large values")
    void shouldSanitizeWebhookMetadata_whenProviderIncludesSensitiveOrLargeValues() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Authorization", "Bearer secret");
        metadata.put("providerStatus", "delivered");
        metadata.put("api_key", "secret");
        metadata.put("s\u00e9cret", "hidden");
        metadata.put("auth t\u00f3ken", "hidden");
        metadata.put("longValue", "x".repeat(600));

        SmsInboundWebhookDto dto = new SmsInboundWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                "+14165551212",
                "+14165550000",
                "reply",
                Instant.EPOCH,
                metadata
        );

        assertThat(dto.providerMetadata()).containsOnlyKeys("providerStatus", "longValue");
        assertThat(dto.providerMetadata().get("longValue")).hasSize(512);
    }

    @Test
    @DisplayName("webhook metadata keeps a bounded number of entries")
    void shouldLimitWebhookMetadataEntries_whenProviderIncludesTooManyValues() {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            metadata.put("safe-" + i, "value-" + i);
        }

        SmsDeliveryWebhookDto dto = new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                SmsStatus.SENT,
                Instant.EPOCH,
                null,
                null,
                metadata
        );

        assertThat(dto.providerMetadata()).hasSize(25);
        assertThat(dto.providerMetadata()).containsKey("safe-24");
        assertThat(dto.providerMetadata()).doesNotContainKey("safe-25");
    }

    @Test
    @DisplayName("a definite failure is recorded with a CARLOS code and its fixed message, never the provider's words")
    void shouldRecordCarlosCode_whenFailureCarriesProvidersOwnWording() {
        assertThat(SmsProviderSendResultDto.failed(SmsProviderErrorCode.INVALID_RECIPIENT))
                .extracting(SmsProviderSendResultDto::status, SmsProviderSendResultDto::errorCode,
                        SmsProviderSendResultDto::errorMessage)
                .containsExactly(SmsStatus.FAILED, "INVALID_RECIPIENT", SmsProviderErrorCode.INVALID_RECIPIENT.message());

        SmsProviderSendResultDto mapped = SmsProviderSendResultDto.failed("ACCOUNT_LIMIT", "Balance too low: 0.02")
                .withCarlosErrorCode();
        assertThat(mapped.errorMessage()).isEqualTo(SmsProviderErrorCode.ACCOUNT_LIMIT.message());

        SmsProviderSendResultDto unmapped = SmsProviderSendResultDto.failed("invalid_dst", "4165551212 is not valid")
                .withCarlosErrorCode();
        assertThat(unmapped).extracting(SmsProviderSendResultDto::errorCode, SmsProviderSendResultDto::errorMessage)
                .containsExactly("REJECTED_OTHER", SmsProviderErrorCode.REJECTED_OTHER.message());
    }

    @Test
    @DisplayName("accepted and uncertain results keep their meaning, without any wording of the provider's")
    void shouldStripProviderWording_whenResultIsNotADefiniteFailure() {
        SmsProviderSendResultDto accepted = SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT);
        SmsProviderSendResultDto uncertain = SmsProviderSendResultDto.uncertain("PROVIDER_TIMEOUT");
        assertThat(accepted.withCarlosErrorCode()).isSameAs(accepted);
        assertThat(uncertain.withCarlosErrorCode()).isSameAs(uncertain);

        SmsProviderSendResultDto acceptedWithText = new SmsProviderSendResultDto(true, "provider-2", SmsStatus.SENT,
                "OK", "sent to 4165551212");
        assertThat(acceptedWithText.withCarlosErrorCode())
                .extracting(SmsProviderSendResultDto::providerMessageId, SmsProviderSendResultDto::errorCode,
                        SmsProviderSendResultDto::errorMessage)
                .containsExactly("provider-2", null, null);

        SmsProviderSendResultDto uncertainWithText = new SmsProviderSendResultDto(false, null, SmsStatus.SENDING,
                "PROVIDER_TIMEOUT", "no answer for 4165551212");
        assertThat(uncertainWithText.withCarlosErrorCode())
                .extracting(SmsProviderSendResultDto::errorCode, SmsProviderSendResultDto::errorMessage)
                .containsExactly("PROVIDER_TIMEOUT", SmsProviderSendResultDto.OUTCOME_UNKNOWN_MESSAGE);

        SmsProviderSendResultDto uncertainWithTextAsCode = SmsProviderSendResultDto.uncertain("HTTP 504 for +14165551212");
        assertThat(uncertainWithTextAsCode.withCarlosErrorCode().errorCode())
                .as("anything that does not look like a code may be the provider's text")
                .isEqualTo(SmsProviderSendResultDto.OUTCOME_UNKNOWN_CODE);
    }

    @Test
    @DisplayName("a failure keeps the provider's message id, so later reports still match the text")
    void shouldKeepMessageId_whenFailureIsNormalised() {
        SmsProviderSendResultDto failed = new SmsProviderSendResultDto(false, "provider-3", SmsStatus.FAILED,
                "undeliverable", "carrier text");

        assertThat(failed.withCarlosErrorCode())
                .extracting(SmsProviderSendResultDto::providerMessageId, SmsProviderSendResultDto::errorCode)
                .containsExactly("provider-3", "REJECTED_OTHER");
    }

    @Test
    @DisplayName("a failed delivery report is recorded with a CARLOS code; sent and delivered ones carry no error")
    void shouldRecordCarlosCode_whenDeliveryReportFails() {
        SmsDeliveryWebhookDto failed = new SmsDeliveryWebhookDto(SmsProviderType.STUB, "provider-1", SmsStatus.FAILED,
                Instant.EPOCH, "RECIPIENT_OPTED_OUT", "carrier text", "sms-transaction-1", Map.of("k", "v"));
        SmsDeliveryWebhookDto delivered = new SmsDeliveryWebhookDto(SmsProviderType.STUB, "provider-1",
                SmsStatus.DELIVERED, Instant.EPOCH, null, null, Map.of());

        assertThat(failed.withCarlosErrorCode())
                .extracting(SmsDeliveryWebhookDto::errorCode, SmsDeliveryWebhookDto::errorMessage,
                        SmsDeliveryWebhookDto::clientReferenceId, SmsDeliveryWebhookDto::providerMetadata)
                .containsExactly("RECIPIENT_OPTED_OUT", SmsProviderErrorCode.RECIPIENT_OPTED_OUT.message(),
                        "sms-transaction-1", Map.of("k", "v"));
        assertThat(delivered.withCarlosErrorCode()).isSameAs(delivered);
        SmsDeliveryWebhookDto deliveredWithText = new SmsDeliveryWebhookDto(SmsProviderType.STUB, "provider-1",
                SmsStatus.DELIVERED, Instant.EPOCH, "000", "delivered to 4165551212", Map.of());
        assertThat(deliveredWithText.withCarlosErrorCode())
                .extracting(SmsDeliveryWebhookDto::errorCode, SmsDeliveryWebhookDto::errorMessage)
                .containsExactly(null, null);
    }

    @Test
    @DisplayName("each CARLOS code says whether retrying can ever help, and whether it affects every text")
    void shouldMarkOnlyHopelessCodesPermanent_forEveryCode() {
        assertThat(Arrays.stream(SmsProviderErrorCode.values()).filter(SmsProviderErrorCode::permanent))
                .containsExactlyInAnyOrder(SmsProviderErrorCode.INVALID_RECIPIENT,
                        SmsProviderErrorCode.RECIPIENT_OPTED_OUT, SmsProviderErrorCode.MESSAGE_REJECTED);
        assertThat(Arrays.stream(SmsProviderErrorCode.values()).filter(code -> !code.affectsEveryText()))
                .containsExactlyInAnyOrder(SmsProviderErrorCode.INVALID_RECIPIENT,
                        SmsProviderErrorCode.RECIPIENT_OPTED_OUT, SmsProviderErrorCode.MESSAGE_REJECTED,
                        SmsProviderErrorCode.REJECTED_OTHER);
        assertThat(SmsProviderErrorCode.fromCode("QUEUE_PROVIDER_EXCEPTION")).isEmpty();
        assertThat(SmsProviderErrorCode.fromCode(null)).isEmpty();
        assertThat(SmsProviderErrorCode.values()).allSatisfy(code -> assertThat(code.name()).hasSizeLessThanOrEqualTo(64));
    }
}
