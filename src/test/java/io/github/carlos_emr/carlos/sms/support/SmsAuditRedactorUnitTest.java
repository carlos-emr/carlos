package io.github.carlos_emr.carlos.sms.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("support")
class SmsAuditRedactorUnitTest {
    @Test
    @DisplayName("phone redaction keeps only a short non-identifying suffix")
    void shouldRedactPhoneNumber_whenValueContainsFullPhoneNumber() {
        String redacted = SmsAuditRedactor.redactPhoneNumber("+1 416 555 1212");

        assertThat(redacted)
                .isEqualTo("[redacted-phone:****1212]")
                .doesNotContain("416", "555");
    }

    @Test
    @DisplayName("message previews do not include message body text")
    void shouldRedactMessagePreview_whenBodyContainsPhi() {
        String preview = SmsAuditRedactor.safeMessagePreview("Patient appointment is tomorrow at 9 AM");

        assertThat(preview)
                .startsWith("[redacted-message length=")
                .contains("sha256=")
                .doesNotContain("Patient", "appointment", "tomorrow");
    }

    @Test
    @DisplayName("digest length stays inside supported SHA-256 hex bounds")
    void shouldClampDigestLength_whenRequestedLengthIsOutsideBounds() {
        assertThat(SmsAuditRedactor.digest("value", 0)).hasSize(1);
        assertThat(SmsAuditRedactor.digest("value", 100)).hasSize(64);
    }
}
