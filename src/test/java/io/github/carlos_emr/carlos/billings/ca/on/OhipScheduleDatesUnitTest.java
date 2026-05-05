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
package io.github.carlos_emr.carlos.billings.ca.on;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit coverage for {@code OhipScheduleDates} effective/termination date translation rules. */
@DisplayName("OHIP Schedule of Benefits date parsing")
@Tag("unit")
@Tag("billing")
class OhipScheduleDatesUnitTest {

    @Test
    void shouldNormalizeTerminationDateSentinels_toCanonicalForm() {
        assertThat(OhipScheduleDates.terminationDate("99999999")).isEqualTo("9999-12-31");
        assertThat(OhipScheduleDates.terminationDate("20260400")).isEqualTo("2026-04-01");
    }

    @Test
    void shouldThrowIllegalArgumentException_whenTerminationDateHasInvalidCalendarDay() {
        assertThatThrownBy(() -> OhipScheduleDates.terminationDate("20260230"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid OHIP date");
    }
}
