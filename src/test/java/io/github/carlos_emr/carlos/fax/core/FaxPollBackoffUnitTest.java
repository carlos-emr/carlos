/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.fax.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("fax")
class FaxPollBackoffUnitTest extends CarlosUnitTestBase {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    @Test
    void shouldCapExponentialDelay_withoutBlockingAnotherAccount() {
        var backoff = new FaxPollBackoff(Clock.fixed(NOW, ZoneOffset.UTC));
        for (int minutes : new int[]{1, 2, 4, 8, 16, 32, 60, 60}) {
            backoff.failed(1);
            assertThat(backoff.snapshot().get(1).nextRetry()).isEqualTo(NOW.plus(Duration.ofMinutes(minutes)));
            assertThat(backoff.isDue(1)).isFalse();
            assertThat(backoff.isDue(2)).isTrue();
        }
        assertThat(backoff.snapshot().get(1).attempts()).isEqualTo(8);
        assertThat(backoff.snapshot().get(1).firstFailure()).isEqualTo(NOW);
    }

    @Test
    void shouldAllowRetryAtBoundary_whenClockAdvances() {
        class MutableClock extends Clock {
            Instant now = NOW;
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return Clock.fixed(now, zone); }
            public Instant instant() { return now; }
        }
        var clock = new MutableClock();
        var backoff = new FaxPollBackoff(clock);
        assertThat(backoff.failed(1)).isTrue();
        clock.now = NOW.plusSeconds(59);
        assertThat(backoff.isDue(1)).isFalse();
        clock.now = NOW.plusSeconds(60);
        assertThat(backoff.isDue(1)).isTrue();
    }

    @Test
    void shouldRecoverAndPruneAccounts_withoutMutatingExistingSnapshots() {
        var backoff = new FaxPollBackoff(Clock.fixed(NOW, ZoneOffset.UTC));
        backoff.failed(1);
        backoff.failed(2);
        var snapshot = backoff.snapshot();
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(backoff.recovered(1)).isTrue();
        assertThat(backoff.recovered(1)).isFalse();
        assertThat(backoff.isDue(1)).isTrue();
        backoff.retainAccounts(Set.of(1));
        assertThat(backoff.snapshot()).isEmpty();
        assertThat(snapshot).hasSize(2);
        assertThat(backoff.failed(1)).isTrue();
        assertThat(backoff.snapshot().get(1).attempts()).isEqualTo(1);
    }
}
