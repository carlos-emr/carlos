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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the failed-login lockout counter.
 *
 * <p>These tests pin brute-force protection behaviour, so they assert the exact number of
 * failures a username or IP gets before {@code status} flips to blocked (0).
 */
@Tag("unit")
@Tag("login")
@Tag("security")
@DisplayName("LoginInfoBean")
class LoginInfoBeanUnitTest {

    private static final int MAX_FAILED_TIMES = 3;
    private static final int MAX_DURATION_MINUTES = 10;

    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_BLOCKED = 0;

    private static GregorianCalendar at(int minutesFromWindowStart) {
        GregorianCalendar time = new GregorianCalendar(2026, Calendar.MAY, 31, 9, 0, 0);
        time.add(Calendar.MINUTE, minutesFromWindowStart);
        return time;
    }

    /** A bean in the state {@code LoginCheckLogin} leaves after a first failed attempt. */
    private static LoginInfoBean afterFirstFailure() {
        return new LoginInfoBean(at(0), MAX_FAILED_TIMES, MAX_DURATION_MINUTES);
    }

    @Nested
    @DisplayName("initialLoginInfoBean")
    class InitialLoginInfoBean {

        @Test
        @DisplayName("should reset start time, attempt count, and lockout status when initialized")
        void shouldResetStartTimeAndAttemptCountAndStatus_whenInitialized() {
            LoginInfoBean bean = new LoginInfoBean();
            GregorianCalendar startTime = at(0);

            bean.setTimes(4);
            bean.setStatus(STATUS_BLOCKED);

            bean.initialLoginInfoBean(startTime);

            assertThat(bean.getStarttime()).isSameAs(startTime);
            assertThat(bean.getTimes()).isZero();
            assertThat(bean.getStatus()).isEqualTo(STATUS_NORMAL);
        }
    }

    @Nested
    @DisplayName("updateLoginInfoBean")
    class UpdateLoginInfoBean {

        @Test
        @DisplayName("should block after the configured number of failures within one window")
        void shouldBlock_whenMaxFailedTimesReachedWithinWindow() {
            LoginInfoBean bean = afterFirstFailure();

            bean.updateLoginInfoBean(at(1), 1);
            assertThat(bean.getStatus()).isEqualTo(STATUS_NORMAL);

            bean.updateLoginInfoBean(at(2), 1);

            assertThat(bean.getTimes()).isEqualTo(MAX_FAILED_TIMES);
            assertThat(bean.getStatus()).isEqualTo(STATUS_BLOCKED);
        }

        @Test
        @DisplayName("should count the timeout-triggering failure as the first attempt of the new window")
        void shouldCountTriggeringFailure_whenWindowExpired() {
            LoginInfoBean bean = afterFirstFailure();
            GregorianCalendar afterExpiry = at(MAX_DURATION_MINUTES + 1);

            bean.updateLoginInfoBean(afterExpiry, 1);

            assertThat(bean.getStarttime()).isSameAs(afterExpiry);
            assertThat(bean.getTimes()).isOne();
            assertThat(bean.getStatus()).isEqualTo(STATUS_NORMAL);
        }

        @Test
        @DisplayName("should still block after the configured number of failures in a window that replaced an expired one")
        void shouldBlock_whenMaxFailedTimesReachedInReplacementWindow() {
            LoginInfoBean bean = afterFirstFailure();

            // Expires the first window; this failure opens the replacement window.
            bean.updateLoginInfoBean(at(MAX_DURATION_MINUTES + 1), 1);
            bean.updateLoginInfoBean(at(MAX_DURATION_MINUTES + 2), 1);
            assertThat(bean.getStatus()).isEqualTo(STATUS_NORMAL);

            bean.updateLoginInfoBean(at(MAX_DURATION_MINUTES + 3), 1);

            assertThat(bean.getTimes()).isEqualTo(MAX_FAILED_TIMES);
            assertThat(bean.getStatus()).isEqualTo(STATUS_BLOCKED);
        }

        @Test
        @DisplayName("should release an existing lockout when the window has expired")
        void shouldReleaseLockout_whenWindowExpired() {
            LoginInfoBean bean = afterFirstFailure();
            bean.updateLoginInfoBean(at(1), 1);
            bean.updateLoginInfoBean(at(2), 1);
            assertThat(bean.getStatus()).isEqualTo(STATUS_BLOCKED);

            bean.updateLoginInfoBean(at(MAX_DURATION_MINUTES + 3), 1);

            assertThat(bean.getTimes()).isOne();
            assertThat(bean.getStatus()).isEqualTo(STATUS_NORMAL);
        }
    }
}
