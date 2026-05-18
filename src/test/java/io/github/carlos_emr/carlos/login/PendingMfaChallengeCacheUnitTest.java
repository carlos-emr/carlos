/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
        assertThat(cache.consume(null)).isNull();
        assertThat(cache.consume("")).isNull();

        cache.invalidate(null);
        cache.invalidate("");
    }

    @Test
    @DisplayName("should consume pending challenge once")
    void shouldConsumePendingChallenge_once() {
        PendingMfaChallengeCache cache = PendingMfaChallengeCache.getInstance();
        PendingMfaChallengeCache.PendingMfaChallenge challenge = challenge();
        String token = cache.store(challenge);

        try {
            assertThat(cache.consume(token)).isNotNull();
            assertThat(cache.consume(token)).isNull();
            assertThat(cache.peek(token)).isNull();
        } finally {
            cache.invalidate(token);
        }
    }

    @Test
    @DisplayName("should consume pending challenge once across concurrent submitters")
    void shouldConsumePendingChallenge_onceAcrossConcurrentSubmitters() throws Exception {
        PendingMfaChallengeCache cache = PendingMfaChallengeCache.getInstance();
        String token = cache.store(challenge());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<PendingMfaChallengeCache.PendingMfaChallenge> first = executor.submit(() -> {
                start.await();
                return cache.consume(token);
            });
            Future<PendingMfaChallengeCache.PendingMfaChallenge> second = executor.submit(() -> {
                start.await();
                return cache.consume(token);
            });

            start.countDown();

            List<PendingMfaChallengeCache.PendingMfaChallenge> results = Arrays.asList(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS));
            assertThat(results).filteredOn(result -> result != null).hasSize(1);
            assertThat(cache.peek(token)).isNull();
        } finally {
            executor.shutdownNow();
            cache.invalidate(token);
        }
    }

    @Test
    @DisplayName("should return null for unknown token")
    void shouldReturnNull_forUnknownToken() {
        PendingMfaChallengeCache cache = PendingMfaChallengeCache.getInstance();

        assertThat(cache.peek("unknown-token")).isNull();
        assertThat(cache.consume("unknown-token")).isNull();
    }

    @Test
    @DisplayName("should expire challenge after write TTL")
    void shouldExpireChallenge_afterWriteTtl() {
        AtomicLong nanos = new AtomicLong();
        PendingMfaChallengeCache cache = new PendingMfaChallengeCache(nanos::get);
        String token = cache.store(challenge());

        assertThat(cache.peek(token)).isNotNull();

        nanos.addAndGet(TimeUnit.MINUTES.toNanos(5));

        assertThat(cache.peek(token)).isNull();
        assertThat(cache.consume(token)).isNull();
    }

    private static PendingMfaChallengeCache.PendingMfaChallenge challenge() {
        return new PendingMfaChallengeCache.PendingMfaChallenge(
                12345, "999998", new String[]{"999998", "Test"}, "secret");
    }
}
