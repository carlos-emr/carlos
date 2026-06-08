package io.github.carlos_emr.carlos.sms.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SmsPhoneNumbersUnitTest {
    @ParameterizedTest
    @CsvSource({
            "416-555-1212,+14165551212",
            "(416) 555-1212,+14165551212",
            "1 416 555 1212,+14165551212",
            "+1 416 555 1212,+14165551212"
    })
    @DisplayName("normalizeToE164 normalizes common NANPA phone formats")
    void normalizeToE164NormalizesCommonNanpaFormats(String input, String expected) {
        assertThat(SmsPhoneNumbers.normalizeToE164(input)).contains(expected);
    }

    @Test
    @DisplayName("normalizeToE164 rejects blank, short, and vanity values")
    void normalizeToE164RejectsInvalidValues() {
        assertThat(SmsPhoneNumbers.normalizeToE164("")).isEmpty();
        assertThat(SmsPhoneNumbers.normalizeToE164("555-1212")).isEmpty();
        assertThat(SmsPhoneNumbers.normalizeToE164("1-800-CARLOS")).isEmpty();
    }

    @Test
    @DisplayName("isLikelyE164 accepts only plus-prefixed international numbers")
    void isLikelyE164RequiresPlusPrefixedNumber() {
        assertThat(SmsPhoneNumbers.isLikelyE164("+14165551212")).isTrue();
        assertThat(SmsPhoneNumbers.isLikelyE164("14165551212")).isFalse();
        assertThat(SmsPhoneNumbers.isLikelyE164("+0123456789")).isFalse();
    }
}
