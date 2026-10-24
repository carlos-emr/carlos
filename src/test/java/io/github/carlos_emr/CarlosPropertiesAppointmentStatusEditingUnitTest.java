/**
 * Copyright (c) 2026 CARLOS Contributors.
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
package io.github.carlos_emr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link CarlosProperties#isAppointmentStatusEditingEnabled()}: appointment status editing
 * is on unless {@code ENABLE_EDIT_APPT_STATUS} is explicitly set to a non-active value.
 *
 * <p>{@link Isolated}: the tests mutate the process-wide {@link CarlosProperties} singleton,
 * and Surefire runs test classes in parallel.</p>
 *
 * @since 2026-09-28
 */
@DisplayName("CarlosProperties appointment status editing default")
@Tag("unit")
@Isolated
class CarlosPropertiesAppointmentStatusEditingUnitTest {

    private static final String KEY = "ENABLE_EDIT_APPT_STATUS";

    /** Original raw value on the shared singleton, restored after each test. */
    private String original;

    @BeforeEach
    void saveOriginal() {
        // Hashtable read: getProperty(String) would log a missing-key warning and apply defaults.
        original = (String) CarlosProperties.getInstance().get(KEY);
    }

    @AfterEach
    void restoreOriginal() {
        if (original == null) {
            CarlosProperties.getInstance().remove(KEY);
        } else {
            CarlosProperties.getInstance().setProperty(KEY, original);
        }
    }

    @Test
    @DisplayName("should be enabled when the key is absent")
    void shouldBeEnabled_whenKeyAbsent() {
        CarlosProperties.getInstance().remove(KEY);

        assertThat(CarlosProperties.getInstance().isAppointmentStatusEditingEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "yes", "YES", "true", "on"})
    @DisplayName("should be enabled when the value is blank or active")
    void shouldBeEnabled_whenValueBlankOrActive(String value) {
        CarlosProperties.getInstance().setProperty(KEY, value);

        assertThat(CarlosProperties.getInstance().isAppointmentStatusEditingEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"no", "false", "off"})
    @DisplayName("should be disabled when the value is explicitly inactive")
    void shouldBeDisabled_whenValueExplicitlyInactive(String value) {
        CarlosProperties.getInstance().setProperty(KEY, value);

        assertThat(CarlosProperties.getInstance().isAppointmentStatusEditingEnabled()).isFalse();
    }
}
