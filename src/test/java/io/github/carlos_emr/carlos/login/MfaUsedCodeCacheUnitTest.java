/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the TOTP used-code replay cache.
 */
@Tag("unit")
@Tag("security")
@DisplayName("MfaUsedCodeCache")
class MfaUsedCodeCacheUnitTest {

    @Test
    @DisplayName("should accept a code once and reject the same code on replay")
    void shouldAcceptCodeOnce_andRejectReplay() {
        MfaUsedCodeCache cache = new MfaUsedCodeCache();

        assertThat(cache.recordIfUnused(12345, "123456")).isTrue();
        assertThat(cache.recordIfUnused(12345, "123456")).isFalse();
        assertThat(cache.recordIfUnused(12345, "123456")).isFalse();
    }

    @Test
    @DisplayName("should track codes independently per security record")
    void shouldTrackCodesIndependently_perSecurityRecord() {
        MfaUsedCodeCache cache = new MfaUsedCodeCache();

        assertThat(cache.recordIfUnused(12345, "123456")).isTrue();

        // A different security record using the same code is unaffected.
        assertThat(cache.recordIfUnused(67890, "123456")).isTrue();
        // A different code for the same security record is unaffected.
        assertThat(cache.recordIfUnused(12345, "654321")).isTrue();
    }

    @Test
    @DisplayName("should reject null or empty inputs without recording")
    void shouldRejectNullOrEmptyInputs_withoutRecording() {
        MfaUsedCodeCache cache = new MfaUsedCodeCache();

        assertThat(cache.recordIfUnused(null, "123456")).isFalse();
        assertThat(cache.recordIfUnused(12345, null)).isFalse();
        assertThat(cache.recordIfUnused(12345, "")).isFalse();

        // None of the rejected inputs should have been recorded, so a real code still succeeds.
        assertThat(cache.recordIfUnused(12345, "123456")).isTrue();
    }

    @Test
    @DisplayName("should allow the code again after the write TTL expires")
    void shouldAllowCodeAgain_afterWriteTtlExpires() {
        AtomicLong nanos = new AtomicLong();
        MfaUsedCodeCache cache = new MfaUsedCodeCache(nanos::get);

        assertThat(cache.recordIfUnused(12345, "123456")).isTrue();
        assertThat(cache.recordIfUnused(12345, "123456")).isFalse();

        nanos.addAndGet(TimeUnit.SECONDS.toNanos(90));

        assertThat(cache.recordIfUnused(12345, "123456")).isTrue();
    }

    @Test
    @DisplayName("should record a code for only one concurrent submitter")
    void shouldRecordCode_forOnlyOneConcurrentSubmitter() throws Exception {
        MfaUsedCodeCache cache = new MfaUsedCodeCache();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return cache.recordIfUnused(12345, "123456");
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return cache.recordIfUnused(12345, "123456");
            });

            start.countDown();

            List<Boolean> results = List.of(first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS));
            assertThat(results).filteredOn(accepted -> accepted).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("should expose a process-wide singleton instance")
    void shouldExposeProcessWideSingletonInstance() {
        assertThat(MfaUsedCodeCache.getInstance()).isSameAs(MfaUsedCodeCache.getInstance());
    }
}
