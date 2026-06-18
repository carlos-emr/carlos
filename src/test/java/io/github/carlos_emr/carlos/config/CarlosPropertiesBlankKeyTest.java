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
package io.github.carlos_emr.carlos.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Guards the blank-key contract of {@link CarlosProperties#getProperty(String)}.
 *
 * @since 2026-06-18
 */
@DisplayName("CARLOS property blank-key handling")
@Tag("unit")
@Tag("read")
class CarlosPropertiesBlankKeyTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should reject a null key with IllegalArgumentException")
    void shouldRejectNullKey() {
        assertThatThrownBy(() -> CarlosProperties.getInstance().getProperty(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Property key cannot be blank.");
    }

    @Test
    @DisplayName("should reject a whitespace-only key with IllegalArgumentException")
    void shouldRejectBlankKey() {
        assertThatThrownBy(() -> CarlosProperties.getInstance().getProperty("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Property key cannot be blank.");
    }
}
