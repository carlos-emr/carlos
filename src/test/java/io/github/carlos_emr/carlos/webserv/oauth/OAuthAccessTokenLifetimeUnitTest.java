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
package io.github.carlos_emr.carlos.webserv.oauth;

import io.github.carlos_emr.carlos.commn.model.ServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O19 client settings are durations, not suggestions to clamp or replace. */
@DisplayName("OAuth access token O19 lifetime compatibility")
@Tag("unit")
@Tag("security")
class OAuthAccessTokenLifetimeUnitTest {
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 59, 60, 900, 28800, 86400, Integer.MAX_VALUE})
    @DisplayName("should retain the configured duration including the O19 non-expiring sentinel")
    void shouldPreserveLifetime_whenConfigured(int seconds) {
        ServiceClient client = new ServiceClient();
        client.setLifetime(seconds);
        assertThat(OAuthAccessTokenLifetime.forClient(client)).isEqualTo((long) seconds);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "100, -1, 999999, false", "100, 900, 999, false", "100, 900, 1000, false",
        "100, 900, 1001, true", "100, 0, 100, false", "100, 0, 101, true",
        "100, -2, 99, true", "0, -1, 100, true",
        "9223372036854775807, 1, 100, true"
    })
    @DisplayName("should preserve the O19 expiry boundary and reject malformed token metadata")
    void shouldValidateStoredToken_whenClockAdvances(long issued, long lifetime, long now, boolean expired) {
        assertThat(OAuthAccessTokenLifetime.isExpired(issued, lifetime, now)).isEqualTo(expired);
    }

    @Test
    @DisplayName("should refuse an unknown client rather than invent a default")
    void shouldRejectIssuance_whenClientMissing() {
        assertThatThrownBy(() -> OAuthAccessTokenLifetime.forClient(null))
                .isInstanceOf(OAuth1Exception.class).hasMessage("invalid_consumer");
    }

    @Test
    @DisplayName("should report an absent stored setting rather than grant an extra hour")
    void shouldRejectIssuance_whenLifetimeMissing() {
        ServiceClient client = new ServiceClient();
        assertThatThrownBy(() -> OAuthAccessTokenLifetime.forClient(client))
                .isInstanceOf(OAuth1Exception.class).hasMessage("invalid_token_lifetime");
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, Integer.MIN_VALUE})
    @DisplayName("should never interpret another negative lifetime as unlimited")
    void shouldRejectIssuance_whenLifetimeInvalid(int seconds) {
        ServiceClient client = new ServiceClient();
        client.setLifetime(seconds);
        assertThatThrownBy(() -> OAuthAccessTokenLifetime.forClient(client))
                .isInstanceOf(OAuth1Exception.class).hasMessage("invalid_token_lifetime");
    }
}
