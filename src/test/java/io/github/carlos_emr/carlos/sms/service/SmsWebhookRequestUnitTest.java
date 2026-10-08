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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
@DisplayName("SMS callback request")
class SmsWebhookRequestUnitTest {

    @Test
    @DisplayName("should carry a GET callback's address values and find headers ignoring case")
    void shouldCarryQueryAndHeaders_whenBuiltFromAGetCallback() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Signature", "abc");
        headers.put(null, "dropped");
        List<String> withNull = new ArrayList<>(Arrays.asList("4165551212", null));

        SmsWebhookRequest request = new SmsWebhookRequest(" get ",
                Map.of("from", withNull, "message", List.of("FAKE reply"), "empty", List.of()), headers, null);

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.queryParameter("from")).contains("4165551212");
        assertThat(request.queryParameter("empty")).isEmpty();
        assertThat(request.queryParameter("missing")).isEmpty();
        assertThat(request.header("x-signature")).contains("abc");
        assertThat(request.body()).isEmpty();
        withNull.set(0, "changed later");
        assertThat(request.queryParameter("from")).as("copied").contains("4165551212");
    }

    @Test
    @DisplayName("should never show a value, since callbacks carry phone numbers and message text")
    void shouldRedactValues_whenPrinted() {
        SmsWebhookRequest request = new SmsWebhookRequest("POST", Map.of("from", List.of("4165551212")),
                Map.of("X-Token", "secret-token"), "{\"message\":\"FAKE reply\"}");

        assertThat(request.toString()).isEqualTo("SmsWebhookRequest[POST, redacted]");
    }
}
