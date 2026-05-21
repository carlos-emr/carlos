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
    @DisplayName("should default known providers to focused when no mode is saved")
    void shouldDefaultKnownProvidersToFocused_whenModeMissing() {
        String mode = UserProperty.resolveScheduleNavigationMode(null, false, "999998");

        assertThat(mode).isEqualTo(UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED);
    }

    @Test
    @DisplayName("should default other providers to focused when no mode is saved")
    void shouldDefaultOtherProvidersToFocused_whenModeMissing() {
        String mode = UserProperty.resolveScheduleNavigationMode(null, false, "123456");

        assertThat(mode).isEqualTo(UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED);
    }
}
