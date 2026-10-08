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
package io.github.carlos_emr.carlos.webserv.oauth.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.webserv.oauth.util.OAuthInterceptor.FailureAuditBudget;
import io.github.carlos_emr.carlos.webserv.oauth.util.OAuthInterceptor.FailureAuditBudget.Decision;

import static org.assertj.core.api.Assertions.assertThat;

/** The bound on OAUTH_LOGIN_FAILURE audit rows (#4429), driven with a fixed clock. */
@DisplayName("OAuthInterceptor failure audit budget")
@Tag("unit")
@Tag("security")
class OAuthInterceptorFailureAuditBudgetUnitTest {

    private final AtomicLong now = new AtomicLong(5_000_000L);
    private final FailureAuditBudget budget = new FailureAuditBudget(now::get);

    @Test
    @DisplayName("should audit up to the per-address limit, then one notice, then nothing")
    void shouldSuppressAddress_afterLimit() {
        List<Decision> decisions = new ArrayList<>();
        for (int i = 0; i < FailureAuditBudget.PER_ADDRESS_LIMIT + 3; i++) {
            decisions.add(budget.admit("198.51.100.1"));
        }

        assertThat(decisions.subList(0, FailureAuditBudget.PER_ADDRESS_LIMIT)).containsOnly(Decision.AUDIT);
        assertThat(decisions.get(FailureAuditBudget.PER_ADDRESS_LIMIT)).isEqualTo(Decision.SUPPRESS_ADDRESS_FROM_NOW);
        assertThat(decisions.subList(FailureAuditBudget.PER_ADDRESS_LIMIT + 1, decisions.size()))
                .containsOnly(Decision.SUPPRESS);
    }

    @Test
    @DisplayName("should keep separate budgets for separate addresses")
    void shouldAuditOtherAddress_whenOneIsSuppressed() {
        for (int i = 0; i < FailureAuditBudget.PER_ADDRESS_LIMIT + 2; i++) {
            budget.admit("198.51.100.1");
        }

        assertThat(budget.admit("198.51.100.2")).isEqualTo(Decision.AUDIT);
    }

    @Test
    @DisplayName("should treat a missing address as one shared address")
    void shouldShareBudget_forMissingAddresses() {
        for (int i = 0; i < FailureAuditBudget.PER_ADDRESS_LIMIT; i++) {
            budget.admit(i % 2 == 0 ? null : " ");
        }

        assertThat(budget.admit(null)).isEqualTo(Decision.SUPPRESS_ADDRESS_FROM_NOW);
    }

    @Test
    @DisplayName("should cap rows server-wide when every request comes from a new address")
    void shouldSuppressAll_afterServerWideLimit() {
        List<Decision> decisions = new ArrayList<>();
        for (int i = 0; i < FailureAuditBudget.SERVER_WIDE_LIMIT + 3; i++) {
            decisions.add(budget.admit("2001:db8::" + Integer.toHexString(i)));
        }

        assertThat(decisions.subList(0, FailureAuditBudget.SERVER_WIDE_LIMIT)).containsOnly(Decision.AUDIT);
        assertThat(decisions.get(FailureAuditBudget.SERVER_WIDE_LIMIT)).isEqualTo(Decision.SUPPRESS_ALL_FROM_NOW);
        assertThat(decisions.subList(FailureAuditBudget.SERVER_WIDE_LIMIT + 1, decisions.size()))
                .containsOnly(Decision.SUPPRESS);
    }

    @Test
    @DisplayName("should open a fresh window once the window length has passed")
    void shouldResetBudget_whenWindowElapses() {
        for (int i = 0; i < FailureAuditBudget.PER_ADDRESS_LIMIT + 2; i++) {
            budget.admit("198.51.100.1");
        }

        now.addAndGet(FailureAuditBudget.WINDOW_MILLIS - 1);
        assertThat(budget.admit("198.51.100.1")).isEqualTo(Decision.SUPPRESS);
        now.addAndGet(1);
        assertThat(budget.admit("198.51.100.1")).isEqualTo(Decision.AUDIT);
    }

    @Test
    @DisplayName("should open a fresh window when the clock goes backwards")
    void shouldResetBudget_whenClockMovesBackwards() {
        for (int i = 0; i < FailureAuditBudget.PER_ADDRESS_LIMIT + 2; i++) {
            budget.admit("198.51.100.1");
        }

        now.addAndGet(-1_000L);
        assertThat(budget.admit("198.51.100.1")).isEqualTo(Decision.AUDIT);
    }

    @Test
    @DisplayName("should hand out exactly one suppression notice per window under concurrency")
    void shouldIssueOneNotice_whenAdmittedConcurrently() throws Exception {
        int threads = 8;
        int perThread = 50;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<List<Decision>>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    List<Decision> mine = new ArrayList<>();
                    for (int i = 0; i < perThread; i++) {
                        mine.add(budget.admit("198.51.100.9"));
                    }
                    return mine;
                }));
            }
            start.countDown();
            List<Decision> all = new ArrayList<>();
            for (java.util.concurrent.Future<List<Decision>> f : futures) {
                all.addAll(f.get());
            }

            assertThat(all).filteredOn(d -> d == Decision.AUDIT).hasSize(FailureAuditBudget.PER_ADDRESS_LIMIT);
            assertThat(all).filteredOn(d -> d == Decision.SUPPRESS_ADDRESS_FROM_NOW).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
