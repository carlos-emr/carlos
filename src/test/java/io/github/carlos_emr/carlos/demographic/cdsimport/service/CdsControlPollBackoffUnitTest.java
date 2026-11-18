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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Pins the shared pacing of durable owner/cancellation reads during bounded import I/O. */
@Tag("unit")
class CdsControlPollBackoffUnitTest {
    private static final long MS = 1_000_000L;

    @Test
    void shouldDoubleTheWaitAfterEachSuccess_upToTheTwoSecondCap() {
        var polls = new CdsControlPollBackoff();
        assertThat(polls.due(false, 0)).isTrue();
        polls.succeeded(0);
        long now = 0;
        for (long wait : new long[] {250, 500, 1000, 2000, 2000, 2000}) {
            assertThat(polls.due(false, now + wait * MS - 1)).as("before %dms", wait).isFalse();
            now += wait * MS;
            assertThat(polls.due(false, now)).as("at %dms", wait).isTrue();
            polls.succeeded(now);
        }
    }

    @Test
    void shouldReadAtOnce_whenForcedOrAfterAnUnrecordedRead() {
        var polls = new CdsControlPollBackoff();
        polls.succeeded(0);
        assertThat(polls.due(true, 1)).isTrue();
        assertThat(polls.due(false, 1)).isFalse();
        // A read that started but never proved ownership must not be remembered as a success.
        polls.expire();
        assertThat(polls.due(false, 1)).isTrue();
    }

    @Test
    void shouldCompareElapsedTime_acrossNegativeAndWrappingClockValues() {
        for (long start : new long[] {-1_000_000_000L, Long.MAX_VALUE - 100 * MS}) {
            var polls = new CdsControlPollBackoff();
            polls.succeeded(start);
            assertThat(polls.due(false, start + 250 * MS - 1)).isFalse();
            assertThat(polls.due(false, start + 250 * MS)).isTrue();
        }
    }
}
