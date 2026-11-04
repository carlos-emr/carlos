/*
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
package io.github.carlos_emr.carlos.fax.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import io.github.carlos_emr.carlos.fax.core.FaxImporter.PollingFailure;

/**
 * Process-local retry state for inbound fax polling. No credentials or provider response
 * text are retained. The importer serializes polls; synchronized access also gives admin
 * requests a consistent, immutable snapshot without waiting for a provider network call.
 */
final class FaxPollBackoff {
    private static final long MAX_DELAY_MINUTES = 60;
    private final Clock clock;
    private final Map<Integer, PollingFailure> failures = new HashMap<>();

    FaxPollBackoff(Clock clock) {
        this.clock = clock;
    }

    synchronized boolean isDue(Integer accountId) {
        PollingFailure failure = failures.get(accountId);
        return failure == null || !clock.instant().isBefore(failure.nextRetry());
    }

    synchronized boolean failed(Integer accountId) {
        PollingFailure previous = failures.get(accountId);
        Instant now = clock.instant();
        // Saturate the counter as well as the delay for indefinitely unavailable accounts.
        int count = previous == null ? 1 : (int) Math.min(Integer.MAX_VALUE, (long) previous.attempts() + 1);
        long delay = Math.min(MAX_DELAY_MINUTES, 1L << Math.min(count - 1, 6));
        failures.put(accountId, new PollingFailure(count, previous == null ? now : previous.firstFailure(),
                now.plus(Duration.ofMinutes(delay))));
        return previous == null;
    }

    synchronized boolean recovered(Integer accountId) {
        return failures.remove(accountId) != null;
    }

    synchronized void retainAccounts(Set<Integer> accountIds) {
        failures.keySet().retainAll(accountIds);
    }

    synchronized Map<Integer, PollingFailure> snapshot() {
        return Map.copyOf(failures);
    }
}
