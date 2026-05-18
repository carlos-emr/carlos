/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the opaque pending-MFA challenge cache.
 */
@Tag("unit")
@Tag("security")
@DisplayName("PendingMfaChallengeCache")
class PendingMfaChallengeCacheUnitTest {

    @Test
    @DisplayName("should store pending challenge behind opaque token")
    void shouldStorePendingChallenge_behindOpaqueToken() {
        PendingMfaChallengeCache cache = PendingMfaChallengeCache.getInstance();
        String[] authResult = {"999998", "Test", "Provider", "", "doctor", "0"};
        PendingMfaChallengeCache.PendingMfaChallenge challenge =
                new PendingMfaChallengeCache.PendingMfaChallenge(12345, "999998", authResult, "secret");

        String token = cache.store(challenge);

        try {
            assertThat(token).isNotBlank();
            PendingMfaChallengeCache.PendingMfaChallenge cached = cache.peek(token);
            assertThat(cached).isNotNull();
            assertThat(cached.securityNo()).isEqualTo(12345);
            assertThat(cached.providerNo()).isEqualTo("999998");
            assertThat(cached.authResult()).containsExactly(authResult);
            assertThat(cached.registrationSecret()).isEqualTo("secret");
        } finally {
            cache.invalidate(token);
        }
    }

    @Test
    @DisplayName("should return defensive copies of auth result")
    void shouldReturnDefensiveCopies_ofAuthResult() {
        PendingMfaChallengeCache.PendingMfaChallenge challenge =
                new PendingMfaChallengeCache.PendingMfaChallenge(
                        12345, "999998", new String[]{"999998", "Test"}, null);

        String[] first = challenge.authResult();
        first[0] = "mutated";

        assertThat(challenge.authResult()).containsExactly("999998", "Test");
    }

    @Test
    @DisplayName("should ignore blank token lookups")
    void shouldIgnoreBlankTokenLookups_withoutThrowing() {
        PendingMfaChallengeCache cache = PendingMfaChallengeCache.getInstance();

        assertThat(cache.peek(null)).isNull();
        assertThat(cache.peek("")).isNull();

        cache.invalidate(null);
        cache.invalidate("");
    }
}
