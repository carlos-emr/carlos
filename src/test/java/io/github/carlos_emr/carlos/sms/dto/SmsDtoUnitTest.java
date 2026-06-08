package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
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
    @DisplayName("inbound webhook metadata allows nullable provider values and remains immutable")
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
        assertThatThrownBy(() -> dto.providerMetadata().put("next", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("delivery webhook metadata allows nullable provider values and remains immutable")
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
        assertThatThrownBy(() -> dto.providerMetadata().put("next", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
