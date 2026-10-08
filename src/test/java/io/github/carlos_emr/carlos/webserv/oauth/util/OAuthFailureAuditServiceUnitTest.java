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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Ticker;

import io.github.carlos_emr.carlos.webserv.oauth.util.OAuthFailureAuditService.Decision;

/** Unit tests for the rejected-call audit budget (issue #4429). */
@DisplayName("OAuthFailureAuditService")
@Tag("unit")
@Tag("security")
class OAuthFailureAuditServiceUnitTest {

    private final AtomicLong nanos = new AtomicLong();
    private OAuthFailureAuditService service;

    @BeforeEach
    void setUp() {
        Ticker ticker = nanos::get;
        service = new OAuthFailureAuditService(ticker);
    }

    private void advanceSeconds(long s) {
        nanos.addAndGet(s * 1_000_000_000L);
    }

    @Test
    void shouldWriteIndividualRows_withinPerAddressBudget() {
        for (int i = 0; i < OAuthFailureAuditService.MAX_ROWS_PER_ADDRESS; i++) {
            assertThat(service.admit("203.0.113.7")).isEqualTo(Decision.WRITE);
        }
    }

    @Test
    void shouldWriteOneSummaryThenSuppress_afterPerAddressBudget() {
        for (int i = 0; i < OAuthFailureAuditService.MAX_ROWS_PER_ADDRESS; i++) {
            service.admit("203.0.113.7");
        }
        assertThat(service.admit("203.0.113.7")).isEqualTo(Decision.WRITE_SUMMARY);
        for (int i = 0; i < 1_000; i++) {
            assertThat(service.admit("203.0.113.7")).isEqualTo(Decision.SUPPRESS);
        }
    }

    @Test
    void shouldNotSuppressOtherAddresses_whenOneAddressIsFlooding() {
        for (int i = 0; i < 50; i++) {
            service.admit("203.0.113.7");
        }
        assertThat(service.admit("198.51.100.9")).isEqualTo(Decision.WRITE);
    }

    @Test
    void shouldRestoreBudget_afterWindowElapses() {
        for (int i = 0; i < 50; i++) {
            service.admit("203.0.113.7");
        }
        advanceSeconds(OAuthFailureAuditService.WINDOW_SECONDS + 1);
        assertThat(service.admit("203.0.113.7")).isEqualTo(Decision.WRITE);
    }

    @Test
    void shouldNotExtendWindow_whenAddressKeepsFailing() {
        // A steady attacker must not keep itself silenced forever: the window is fixed from first failure.
        for (int i = 0; i < 50; i++) {
            service.admit("203.0.113.7");
        }
        for (int s = 0; s < OAuthFailureAuditService.WINDOW_SECONDS + 1; s += 10) {
            advanceSeconds(10);
            service.admit("203.0.113.7");
        }
        assertThat(service.admit("203.0.113.7")).isNotEqualTo(Decision.SUPPRESS);
    }

    @Test
    void shouldBoundTotalRows_acrossRotatingAddresses() {
        int written = 0;
        int summaries = 0;
        for (int i = 0; i < 5_000; i++) {
            Decision d = service.admit("10.0." + (i / 250) + "." + (i % 250));
            if (d == Decision.WRITE) {
                written++;
            } else if (d == Decision.WRITE_SUMMARY) {
                summaries++;
            }
        }
        assertThat(written).isEqualTo(OAuthFailureAuditService.MAX_ROWS_GLOBAL);
        assertThat(summaries).isEqualTo(1);
    }

    @Test
    void shouldTreatMissingAddressAsOneBucket() {
        for (int i = 0; i < OAuthFailureAuditService.MAX_ROWS_PER_ADDRESS; i++) {
            assertThat(service.admit(null)).isEqualTo(Decision.WRITE);
        }
        assertThat(service.admit(null)).isEqualTo(Decision.WRITE_SUMMARY);
    }

    @Test
    void shouldNotLetOneFlooderStarveOtherAddresses_ofGlobalBudget() {
        for (int i = 0; i < 100_000; i++) {
            service.admit("203.0.113.7");
        }
        assertThat(service.admit("198.51.100.9")).isEqualTo(Decision.WRITE);
    }
}
