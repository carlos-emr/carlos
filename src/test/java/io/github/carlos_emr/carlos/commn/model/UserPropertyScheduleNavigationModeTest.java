/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.model;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserProperty schedule navigation mode")
class UserPropertyScheduleNavigationModeTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should use saved mode when valid")
    void shouldUseSavedMode_whenValid() {
        String mode = UserProperty.resolveScheduleNavigationMode(
                UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED, true, "123456");

        assertThat(mode).isEqualTo(UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED);
    }

    @Test
    @DisplayName("should fallback to popup when saved mode is invalid")
    void shouldFallbackToPopup_whenSavedModeInvalid() {
        String mode = UserProperty.resolveScheduleNavigationMode("unexpected", true, "123456");

        assertThat(mode).isEqualTo(UserProperty.SCHEDULE_NAVIGATION_MODE_POPUP);
    }

    @Test
    @DisplayName("should preserve legacy tab behavior when no mode is saved")
    void shouldPreserveLegacyTabs_whenModeMissing() {
        String mode = UserProperty.resolveScheduleNavigationMode(null, true, "123456");

        assertThat(mode).isEqualTo(UserProperty.SCHEDULE_NAVIGATION_MODE_TAB);
    }

    @Test
    @DisplayName("should default Carlosdoc to focused when no mode is saved")
    void shouldDefaultCarlosdocToFocused_whenModeMissing() {
        String mode = UserProperty.resolveScheduleNavigationMode(null, false, "999998");

        assertThat(mode).isEqualTo(UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED);
    }

    @Test
    @DisplayName("should default other providers to popup when no mode is saved")
    void shouldDefaultOtherProvidersToPopup_whenModeMissing() {
        String mode = UserProperty.resolveScheduleNavigationMode(null, false, "123456");

        assertThat(mode).isEqualTo(UserProperty.SCHEDULE_NAVIGATION_MODE_POPUP);
    }
}
