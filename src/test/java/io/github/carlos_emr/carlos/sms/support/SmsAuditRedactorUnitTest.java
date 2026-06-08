package io.github.carlos_emr.carlos.sms.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsAuditRedactorUnitTest {
    @Test
    @DisplayName("phone redaction keeps only a short non-identifying suffix")
    void redactPhoneNumberAvoidsFullPhoneNumber() {
        String redacted = SmsAuditRedactor.redactPhoneNumber("+1 416 555 1212");

        assertThat(redacted).isEqualTo("[redacted-phone:****1212]");
        assertThat(redacted).doesNotContain("416");
        assertThat(redacted).doesNotContain("555");
    }

    @Test
    @DisplayName("message previews do not include message body text")
    void safeMessagePreviewAvoidsMessageBody() {
        String preview = SmsAuditRedactor.safeMessagePreview("Patient appointment is tomorrow at 9 AM");

        assertThat(preview).startsWith("[redacted-message length=");
        assertThat(preview).contains("sha256=");
        assertThat(preview).doesNotContain("Patient");
        assertThat(preview).doesNotContain("appointment");
        assertThat(preview).doesNotContain("tomorrow");
    }
}
