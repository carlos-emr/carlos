/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shared TOTP acceptance window to the generator CARLOS actually validates against.
 *
 * <p>{@code TotpWindow.TIME_STEP} mirrors the generator default as a literal so it is usable during
 * class initialisation. These tests are what keep the mirror honest: if the library default or the
 * tolerance changes, the replay cache TTL would silently stop matching the window MFA codes are
 * accepted over, and one of these assertions fails instead.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("TotpWindow")
class TotpWindowUnitTest {

    @Test
    @DisplayName("should mirror the time step of the generator used for validation")
    void shouldMirrorTimeStep_ofValidationGenerator() {
        assertThat(TotpWindow.TIME_STEP).isEqualTo(new TimeBasedOneTimePasswordGenerator().getTimeStep());
    }

    @Test
    @DisplayName("should span the tolerated steps on both sides of the current one")
    void shouldSpanToleratedSteps_onBothSidesOfCurrent() {
        assertThat(TotpWindow.STEP_TOLERANCE).isOne();
        assertThat(TotpWindow.ACCEPTANCE).isEqualTo(Duration.ofSeconds(90));
        assertThat(TotpWindow.ACCEPTANCE)
                .isEqualTo(TotpWindow.TIME_STEP.multipliedBy(2L * TotpWindow.STEP_TOLERANCE + 1));
    }
}
