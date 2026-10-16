/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("fast")
@DisplayName("SMS provider value types")
class SmsProviderValueTypesUnitTest {

    @ParameterizedTest(name = "name={0}")
    @NullAndEmptySource
    @ValueSource(strings = {"1user", "api user", "api-user", "api.user", "a\"b", "<b>"})
    @DisplayName("a credential field name must be safe in a parameter name and an HTML id")
    void shouldRefuseCredentialFieldName_whenNotLettersDigitsOrUnderscore(String name) {
        assertThatThrownBy(() -> new SmsCredentialField(name, "sms.provider.fake.user", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a credential field name is at most 64 characters")
    void shouldRefuseCredentialFieldName_whenLongerThan64() {
        assertThat(new SmsCredentialField("a" + "b".repeat(63), "sms.provider.fake.user", false).name()).hasSize(64);
        assertThatThrownBy(() -> new SmsCredentialField("a" + "b".repeat(64), "sms.provider.fake.user", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "labelKey={0}")
    @NullAndEmptySource
    @ValueSource(strings = {"noDot", "${param.x}", "sms.provider.<b>", ".leadingDot"})
    @DisplayName("a credential field label must be a dotted message key")
    void shouldRefuseCredentialFieldLabel_whenNotAMessageKey(String labelKey) {
        assertThatThrownBy(() -> new SmsCredentialField("api_user", labelKey, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * What the label check accepts. The verdicts are those of the regular expression the part-by-part
     * check replaced, {@code [A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+}, matched against the whole key.
     */
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "sms.provider.fake.user | true",
            "a.b | true",
            "a.b.c | true",
            "A1_.b2_ | true",
            "a._ | true",
            "a.1 | true",
            "a.b_c.D9 | true",
            "a.b.c.d.e.f.g.h | true",
            "_a.b | false",
            "1a.b | false",
            "a | false",
            "a. | false",
            "a..b | false",
            ".a | false",
            "a.b. | false",
            "'a b.c' | false",
            "'a.b c' | false",
            "a.b-c | false",
            "a$.b | false",
            "\u00e4.b | false",
            "a.\u00e4 | false",
            ".. | false",
    })
    @DisplayName("should accept exactly the dotted message keys the earlier pattern accepted")
    void shouldAcceptSameMessageKeys_asEarlierPattern(String key, boolean accepted) {
        assertThat(SmsCredentialField.isMessageKey(key)).isEqualTo(accepted);
    }

    @Test
    @DisplayName("should refuse a message key with a line break")
    void shouldRefuseMessageKey_withLineBreak() {
        assertThat(SmsCredentialField.isMessageKey("a.b\n")).isFalse();
    }

    @Test
    @DisplayName("should check a very long message key without overflowing the stack")
    void shouldCheckVeryLongMessageKey_withoutStackOverflow() {
        String longKey = "sms" + ".part".repeat(200_000);

        assertThat(SmsCredentialField.isMessageKey(longKey)).isTrue();
        assertThat(SmsCredentialField.isMessageKey(longKey + ".")).isFalse();
        assertThat(SmsCredentialField.isMessageKey(longKey + "..x")).isFalse();
        assertThat(new SmsCredentialField("api_user", longKey, true).labelKey()).isEqualTo(longKey);
    }

    @Test
    @DisplayName("a send rate allows at least one text in a window of at least one millisecond")
    void shouldRefuseSendRate_whenItCouldNeverSend() {
        assertThatThrownBy(() -> new SmsSendRateLimit(0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmsSendRateLimit(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmsSendRateLimit(1, null)).isInstanceOf(NullPointerException.class);
        assertThat(SmsSendRateLimit.DEFAULT).isEqualTo(new SmsSendRateLimit(5, Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("provider settings keep only real values and never show them")
    void shouldKeepOnlyRealValues_andRedactThem() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("api_user", "fake-user");
        credentials.put("api_password", " ");
        credentials.put("token", null);

        SmsProviderSettings settings = SmsProviderSettings.of(SmsProviderType.CLOUDLI, "+16135550100", credentials);
        credentials.put("api_user", "changed later");

        assertThat(settings.credential("api_user")).contains("fake-user");
        assertThat(settings.credential("api_password")).isEmpty();
        assertThat(settings.credential("token")).isEmpty();
        assertThat(settings.senderNumber()).contains("+16135550100");
        assertThat(settings.toString()).doesNotContain("fake-user").doesNotContain("+16135550100");
    }

    @Test
    void shouldCompareSettingsByValue_whenCheckingForChanges() {
        SmsProviderSettings original = SmsProviderSettings.of(SmsProviderType.CLOUDLI, "+16135550100",
                Map.of("api_user", "fake-user"));
        SmsProviderSettings same = SmsProviderSettings.of(SmsProviderType.CLOUDLI, "+16135550100",
                Map.of("api_user", "fake-user"));

        assertThat(original).isEqualTo(same).hasSameHashCodeAs(same);
        Object notSettings = "fake-user";
        assertThat(original).isNotEqualTo(null).isNotEqualTo(notSettings);
        assertThat(original).isNotEqualTo(SmsProviderSettings.of(SmsProviderType.VOIPMS, "+16135550100",
                Map.of("api_user", "fake-user")));
        assertThat(original).isNotEqualTo(SmsProviderSettings.of(SmsProviderType.CLOUDLI, "+16135550101",
                Map.of("api_user", "fake-user")));
        assertThat(original).isNotEqualTo(SmsProviderSettings.of(SmsProviderType.CLOUDLI, "+16135550100",
                Map.of("api_user", "fake-rotated-user")));
    }

    @Test
    @DisplayName("no settings means no sender number and no credentials")
    void shouldHoldNothing_whenNone() {
        SmsProviderSettings none = SmsProviderSettings.none(SmsProviderType.STUB);

        assertThat(none.providerType()).isEqualTo(SmsProviderType.STUB);
        assertThat(none.senderNumber()).isEmpty();
        assertThat(none.credential("api_user")).isEmpty();
        assertThat(SmsProviderSettings.of(SmsProviderType.STUB, " ", null).senderNumber()).isEmpty();
    }
}
