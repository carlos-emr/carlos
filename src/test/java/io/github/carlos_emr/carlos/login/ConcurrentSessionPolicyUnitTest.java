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

import io.github.carlos_emr.carlos.login.ConcurrentSessionPolicy.Decision;
import io.github.carlos_emr.carlos.login.ConcurrentSessionPolicy.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the concurrent-session policy contract (issue #3980): configuration parsing that never locks
 * users out on a typo, and the decision table the login flow follows.
 */
@Tag("unit")
@Tag("security")
@DisplayName("ConcurrentSessionPolicy")
class ConcurrentSessionPolicyUnitTest {

    @Test
    @DisplayName("should default to allow with no limit when properties are missing")
    void shouldDefaultToAllow_whenPropertiesMissing() {
        assertThat(ConcurrentSessionPolicy.fromProperties(new Properties())).isEqualTo(ConcurrentSessionPolicy.DEFAULT);
        assertThat(ConcurrentSessionPolicy.fromProperties(null)).isEqualTo(ConcurrentSessionPolicy.DEFAULT);
        assertThat(ConcurrentSessionPolicy.DEFAULT.mode()).isEqualTo(Mode.ALLOW);
        assertThat(ConcurrentSessionPolicy.DEFAULT.maxSessions()).isZero();
    }

    @ParameterizedTest(name = "policy=\"{0}\" max=\"{1}\" -> {2}/{3}")
    @CsvSource({
            "allow, 0, ALLOW, 0",
            "PROMPT, 3, PROMPT, 3",
            "' single ', ' 1 ', SINGLE, 1",
            "prompt, -2, PROMPT, 0",
            "prompt, many, PROMPT, 0",
            "sometimes, 2, ALLOW, 2",
            "'', '', ALLOW, 0"
    })
    @DisplayName("should parse policy and limit, falling back to defaults for invalid values")
    void shouldParsePolicyAndLimit_withSafeFallbacks(String policy, String max, Mode expectedMode, int expectedMax) {
        Properties properties = new Properties();
        properties.setProperty(ConcurrentSessionPolicy.POLICY_PROPERTY, policy);
        properties.setProperty(ConcurrentSessionPolicy.MAX_PROPERTY, max);

        ConcurrentSessionPolicy parsed = ConcurrentSessionPolicy.fromProperties(properties);

        assertThat(parsed.mode()).isEqualTo(expectedMode);
        assertThat(parsed.maxSessions()).isEqualTo(expectedMax);
    }

    @ParameterizedTest(name = "{0} max={1} with {2} other(s) -> {3}")
    @CsvSource({
            "ALLOW, 0, 0, PROCEED",
            "ALLOW, 0, 5, PROCEED",
            "ALLOW, 2, 1, PROCEED",
            "ALLOW, 2, 2, ASK_SIGN_OUT_REQUIRED",
            "PROMPT, 0, 0, PROCEED",
            "PROMPT, 0, 1, ASK",
            "PROMPT, 3, 1, ASK",
            "PROMPT, 2, 1, ASK",
            "PROMPT, 2, 2, ASK_SIGN_OUT_REQUIRED",
            "PROMPT, 1, 4, ASK_SIGN_OUT_REQUIRED",
            "SINGLE, 0, 0, PROCEED",
            "SINGLE, 0, 1, SIGN_OUT_OTHERS",
            "SINGLE, 5, 3, SIGN_OUT_OTHERS"
    })
    @DisplayName("should decide what the login must do from the number of other sessions")
    void shouldDecideLoginAction_fromOtherSessionCount(Mode mode, int max, int others, Decision expected) {
        assertThat(new ConcurrentSessionPolicy(mode, max).decide(others)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should treat the new session as counting toward the limit")
    void shouldCountNewSessionTowardLimit_forMaxSessions() {
        ConcurrentSessionPolicy twoSessions = new ConcurrentSessionPolicy(Mode.PROMPT, 2);

        // max=2: one other session plus the new one is two, which is allowed; a third is not.
        assertThat(twoSessions.isLimitReached(1)).isFalse();
        assertThat(twoSessions.isLimitReached(2)).isTrue();
        assertThat(new ConcurrentSessionPolicy(Mode.PROMPT, 1).isLimitReached(1)).isTrue();
        assertThat(new ConcurrentSessionPolicy(Mode.PROMPT, 0).isLimitReached(100)).isFalse();
    }

    @Test
    @DisplayName("should reject a null mode or a negative limit")
    void shouldRejectInvalidConstruction_forNullModeOrNegativeLimit() {
        assertThatThrownBy(() -> new ConcurrentSessionPolicy(null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConcurrentSessionPolicy(Mode.ALLOW, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
