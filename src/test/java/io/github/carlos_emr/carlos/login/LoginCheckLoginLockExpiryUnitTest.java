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

import io.github.carlos_emr.CarlosProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for username-based lockout expiry in {@link LoginCheckLogin}.
 *
 * <p>{@code Login2Action} returns as soon as {@link LoginCheckLogin#isBlock(String, String)}
 * reports a block, so {@code updateLockList} never runs for a blocked username. The block
 * check itself therefore has to expire the entry, otherwise a lockout outlives
 * {@code login_max_duration} and only an administrator can clear it.
 *
 * <p>These tests drive the production API and mutate the application-scoped
 * {@link CarlosProperties} and {@link LoginList} singletons, restoring both afterwards.
 * {@code @Isolated} keeps them off the same process as other tests under parallel Surefire,
 * matching how {@code StartupUnitTest} guards its own {@code CarlosProperties} mutation.
 */
@Isolated
@Tag("unit")
@Tag("login")
@Tag("security")
@DisplayName("LoginCheckLogin username lockout expiry")
class LoginCheckLoginLockExpiryUnitTest {

    private static final String WAN_IP = "203.0.113.5";
    private static final String USER_NAME = "lockexpiryuser";

    private static final int MAX_FAILED_TIMES = 3;
    private static final int MAX_DURATION_MINUTES = 10;

    private static final String[] TOUCHED_PROPERTIES = {
            "login_lock", "login_max_failed_times", "login_max_duration", "login_local_ip"
    };

    private final Map<String, String> previousProperties = new HashMap<>();

    @BeforeEach
    void enableUsernameLocking() {
        CarlosProperties properties = CarlosProperties.getInstance();
        for (String key : TOUCHED_PROPERTIES) {
            previousProperties.put(key, properties.getProperty(key));
        }
        properties.setProperty("login_lock", "true");
        properties.setProperty("login_max_failed_times", String.valueOf(MAX_FAILED_TIMES));
        properties.setProperty("login_max_duration", String.valueOf(MAX_DURATION_MINUTES));
        // Keeps WAN_IP off the LAN allowlist so brute force protection stays active.
        properties.setProperty("login_local_ip", "10.255");

        LoginList.getLoginListInstance().remove(USER_NAME);
    }

    @AfterEach
    void restoreProperties() {
        CarlosProperties properties = CarlosProperties.getInstance();
        previousProperties.forEach((key, value) -> {
            if (value == null) {
                properties.remove(key);
            } else {
                properties.setProperty(key, value);
            }
        });
        previousProperties.clear();

        LoginList.getLoginListInstance().remove(USER_NAME);
    }

    /** Drives the production failure path until the username is locked out. */
    private LoginCheckLogin lockOutUserName() {
        LoginCheckLogin loginCheck = new LoginCheckLogin();
        // The block check is also what initializes the shared LoginList, as in Login2Action.
        assertThat(loginCheck.isBlock(WAN_IP, USER_NAME)).isFalse();

        for (int attempt = 0; attempt < MAX_FAILED_TIMES; attempt++) {
            loginCheck.updateLoginList(WAN_IP, USER_NAME);
        }
        return loginCheck;
    }

    private static LoginInfoBean trackedEntry() {
        return (LoginInfoBean) LoginList.getLoginListInstance().get(USER_NAME);
    }

    /** Backdates the tracking window so it looks older than {@code login_max_duration}. */
    private static void expireTrackingWindow() {
        GregorianCalendar past = new GregorianCalendar();
        past.add(Calendar.MINUTE, -(MAX_DURATION_MINUTES + 1));
        trackedEntry().setStarttime(past);
    }

    @Test
    @DisplayName("should block the username while the tracking window is still open")
    void shouldBlockUserName_whenWindowStillOpen() {
        LoginCheckLogin loginCheck = lockOutUserName();

        assertThat(trackedEntry()).isNotNull();
        assertThat(loginCheck.isBlock(WAN_IP, USER_NAME)).isTrue();
    }

    @Test
    @DisplayName("should release the username lockout once the tracking window has elapsed")
    void shouldReleaseUserNameLockout_whenWindowElapsed() {
        LoginCheckLogin loginCheck = lockOutUserName();
        assertThat(loginCheck.isBlock(WAN_IP, USER_NAME)).isTrue();

        expireTrackingWindow();

        assertThat(loginCheck.isBlock(WAN_IP, USER_NAME)).isFalse();
        assertThat(trackedEntry()).isNull();
    }

    @Test
    @DisplayName("should require the full attempt allowance again after a lockout expires")
    void shouldRequireFullAllowanceAgain_whenLockoutExpired() {
        LoginCheckLogin loginCheck = lockOutUserName();
        expireTrackingWindow();
        assertThat(loginCheck.isBlock(WAN_IP, USER_NAME)).isFalse();

        for (int attempt = 0; attempt < MAX_FAILED_TIMES - 1; attempt++) {
            loginCheck.updateLoginList(WAN_IP, USER_NAME);
            assertThat(loginCheck.isBlock(WAN_IP, USER_NAME)).isFalse();
        }

        loginCheck.updateLoginList(WAN_IP, USER_NAME);

        assertThat(trackedEntry().getTimes()).isEqualTo(MAX_FAILED_TIMES);
        assertThat(loginCheck.isBlock(WAN_IP, USER_NAME)).isTrue();
    }
}
