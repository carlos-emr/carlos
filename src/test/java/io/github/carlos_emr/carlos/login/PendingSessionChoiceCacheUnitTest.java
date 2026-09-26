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
package io.github.carlos_emr.carlos.login;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for the opaque store that holds a login while the concurrent-session chooser is
 * open (issue #3980).
 */
@Tag("unit")
@Tag("security")
@DisplayName("PendingSessionChoiceCache")
class PendingSessionChoiceCacheUnitTest {

    private static final String[] AUTH = {"999998", "Test", "Provider", "", "doctor", "0"};

    @Test
    @DisplayName("should drop a staged token only under the user's admission lock")
    void shouldClearTokenUnderAdmissionLock_whenSubmitHoldsIt() throws Exception {
        // A cancel (logout) must not drop the token while a submit for the same user holds the
        // admission lock; it waits, so it cannot land between the submit's consume and its login.
        PendingSessionChoiceCache cache = PendingSessionChoiceCache.getInstance();
        String token = cache.store(pending());
        MockHttpSession session = new MockHttpSession();
        PendingSessionChoices.stage(session, token);
        ReentrantLock lock = ConcurrentSessionAdmission.lockFor(12345);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        lock.lock();
        try {
            Future<?> cancel = pool.submit(() -> PendingSessionChoices.clearFromSession(session));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!lock.hasQueuedThreads() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(lock.hasQueuedThreads()).as("cancel waits for the submit").isTrue();
            assertThat(cache.peek(token)).as("token still staged while the submit runs").isNotNull();
            lock.unlock();
            cancel.get(5, TimeUnit.SECONDS);
            assertThat(cache.peek(token)).isNull();
            assertThat(PendingSessionChoices.getToken(session)).isNull();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            pool.shutdownNow();
            cache.invalidate(token);
        }
    }

    @Test
    @DisplayName("should still wait for the admission lock after the submit consumed the token")
    void shouldWaitForAdmissionLock_whenTokenAlreadyConsumed() throws Exception {
        // The submit consumes the token and then builds the new session while holding the lock. A
        // cancel arriving in between must still wait, even though the pending entry is gone.
        PendingSessionChoiceCache cache = PendingSessionChoiceCache.getInstance();
        String token = cache.store(pending());
        MockHttpSession session = new MockHttpSession();
        PendingSessionChoices.stage(session, token);
        ReentrantLock lock = ConcurrentSessionAdmission.lockFor(12345);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        lock.lock();
        try {
            assertThat(cache.consume(token)).isNotNull();
            assertThat(cache.ownerOf(token)).isEqualTo(12345);
            Future<?> cancel = pool.submit(() -> PendingSessionChoices.clearFromSession(session));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!lock.hasQueuedThreads() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(lock.hasQueuedThreads()).as("cancel waits for the completing submit").isTrue();
            lock.unlock();
            cancel.get(5, TimeUnit.SECONDS);
            assertThat(cache.ownerOf(token)).isNull();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            pool.shutdownNow();
            cache.invalidate(token);
        }
    }

    @Test
    @DisplayName("should keep an in-flight owner even after the cache entry is evicted")
    void shouldKeepInFlightOwner_whenCacheEntryIsGone() {
        // Simulates eviction under a burst of new pending logins: the consumed entry disappears
        // from the bounded cache, but the owner of a submit that is still completing must not.
        AtomicLong nanos = new AtomicLong();
        PendingSessionChoiceCache cache = new PendingSessionChoiceCache(nanos::get);
        String token = cache.store(pending());
        assertThat(cache.consume(token)).isNotNull();

        nanos.addAndGet(TimeUnit.MINUTES.toNanos(6));
        assertThat(cache.peek(token)).isNull();
        assertThat(cache.ownerOf(token)).as("in flight").isEqualTo(12345);

        cache.release(token);
        assertThat(cache.ownerOf(token)).as("released").isNull();
    }

    @Test
    @DisplayName("should ignore a session a completing login already invalidated")
    void shouldIgnoreInvalidatedSession_whenClearing() {
        MockHttpSession session = new MockHttpSession();
        session.invalidate();

        assertThatCode(() -> PendingSessionChoices.clearFromSession(session)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should let only one of two simultaneous submits consume the same token")
    void shouldConsumeOnce_whenTwoSubmitsRace() throws Exception {
        // Two chooser submits carrying the same token, released together: the cache's atomic
        // remove must hand the pending login to exactly one of them.
        PendingSessionChoiceCache cache = PendingSessionChoiceCache.getInstance();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 200; round++) {
                String token = cache.store(pending());
                CountDownLatch start = new CountDownLatch(1);
                Future<PendingSessionChoiceCache.PendingSessionChoice> first = pool.submit(() -> {
                    start.await();
                    return cache.consume(token);
                });
                Future<PendingSessionChoiceCache.PendingSessionChoice> second = pool.submit(() -> {
                    start.await();
                    return cache.consume(token);
                });
                start.countDown();
                int winners = (first.get(5, TimeUnit.SECONDS) != null ? 1 : 0)
                        + (second.get(5, TimeUnit.SECONDS) != null ? 1 : 0);
                assertThat(winners).as("round %d", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("should keep the pending login behind an opaque token and consume it once")
    void shouldConsumePendingLoginOnce_behindOpaqueToken() {
        PendingSessionChoiceCache cache = PendingSessionChoiceCache.getInstance();
        String token = cache.store(pending());

        try {
            assertThat(token).isNotBlank().doesNotContain("999998");
            assertThat(cache.peek(token)).isNotNull();
            PendingSessionChoiceCache.PendingSessionChoice consumed = cache.consume(token);
            assertThat(consumed.securityNo()).isEqualTo(12345);
            assertThat(consumed.authResult()).containsExactly(AUTH);
            assertThat(consumed.oauthToken()).isEqualTo("oauth-1");
            assertThat(cache.consume(token)).as("a replayed token completes nothing").isNull();
            assertThat(cache.peek(token)).isNull();
        } finally {
            cache.invalidate(token);
        }
    }

    @Test
    @DisplayName("should expire the pending login after five minutes")
    void shouldExpirePendingLogin_afterFiveMinutes() {
        AtomicLong nanos = new AtomicLong();
        PendingSessionChoiceCache cache = new PendingSessionChoiceCache(nanos::get);
        String token = cache.store(pending());

        nanos.addAndGet(TimeUnit.MINUTES.toNanos(4));
        assertThat(cache.peek(token)).isNotNull();
        nanos.addAndGet(TimeUnit.MINUTES.toNanos(2));
        assertThat(cache.peek(token)).isNull();
        assertThat(cache.consume(token)).isNull();
    }

    @Test
    @DisplayName("should copy the auth result in and out")
    void shouldCopyAuthResult_inAndOut() {
        String[] source = AUTH.clone();
        PendingSessionChoiceCache.PendingSessionChoice choice =
                new PendingSessionChoiceCache.PendingSessionChoice(1, "1", source, false, null, null);

        source[0] = "changed";
        choice.authResult()[1] = "changed";

        assertThat(choice.authResult()).containsExactly(AUTH);
    }

    @Test
    @DisplayName("should reject incomplete pending logins and ignore empty tokens")
    void shouldRejectIncompletePendingLogins_andIgnoreEmptyTokens() {
        assertThatThrownBy(() -> new PendingSessionChoiceCache.PendingSessionChoice(null, "1", AUTH, false, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PendingSessionChoiceCache.PendingSessionChoice(1, "1", new String[0], false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PendingSessionChoiceCache.getInstance().peek(null)).isNull();
        assertThat(PendingSessionChoiceCache.getInstance().consume("")).isNull();
    }

    @Test
    @DisplayName("should hold only the opaque token in the session and clear both on cleanup")
    void shouldHoldOnlyToken_inSessionUntilCleared() {
        MockHttpSession session = new MockHttpSession();
        String token = PendingSessionChoiceCache.getInstance().store(pending());
        PendingSessionChoices.stage(session, token);

        assertThat(PendingSessionChoices.getToken(session)).isEqualTo(token);
        assertThat(java.util.Collections.list(session.getAttributeNames()))
                .containsExactly(PendingSessionChoices.TOKEN_ATTR);

        PendingSessionChoices.clearFromSession(session);

        assertThat(PendingSessionChoices.getToken(session)).isNull();
        assertThat(PendingSessionChoiceCache.getInstance().peek(token)).isNull();
        PendingSessionChoices.clearFromSession(null);
    }

    @Test
    @DisplayName("should compare the auth result by content and keep it out of toString")
    void shouldCompareByContent_andRedactToString() {
        PendingSessionChoiceCache.PendingSessionChoice first = pending();
        PendingSessionChoiceCache.PendingSessionChoice second = pending();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(
                new PendingSessionChoiceCache.PendingSessionChoice(12345, "999998", new String[]{"x"}, false, null, "oauth-1"));
        assertThat(first.toString()).doesNotContain("999998").doesNotContain("oauth-1").contains("redacted");
    }

    private static PendingSessionChoiceCache.PendingSessionChoice pending() {
        return new PendingSessionChoiceCache.PendingSessionChoice(12345, "999998", AUTH, false, null, "oauth-1");
    }
}
