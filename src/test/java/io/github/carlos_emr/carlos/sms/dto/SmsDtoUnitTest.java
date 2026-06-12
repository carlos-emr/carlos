package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
                "CONSENT_MODEL_PENDING",
                null
        );

        SmsSendResultDto result = SmsSendResultDto.consentBlocked(decision);

        assertThat(result)
                .extracting(SmsSendResultDto::accepted, SmsSendResultDto::status, SmsSendResultDto::messages)
                .containsExactly(false, SmsStatus.CONSENT_BLOCKED, List.of());
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
}
