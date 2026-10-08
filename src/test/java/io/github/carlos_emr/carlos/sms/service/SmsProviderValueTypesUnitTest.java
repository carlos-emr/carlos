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
    @DisplayName("no settings means no sender number and no credentials")
    void shouldHoldNothing_whenNone() {
        SmsProviderSettings none = SmsProviderSettings.none(SmsProviderType.STUB);

        assertThat(none.providerType()).isEqualTo(SmsProviderType.STUB);
        assertThat(none.senderNumber()).isEmpty();
        assertThat(none.credential("api_user")).isEmpty();
        assertThat(SmsProviderSettings.of(SmsProviderType.STUB, " ", null).senderNumber()).isEmpty();
    }
}
