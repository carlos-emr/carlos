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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one rule for a final OBX result status, in every handler's vocabulary (#6122 review).
 *
 * @since 2026-11-11
 */
@Tag("unit")
@Tag("lab")
@DisplayName("OBX result status: final or not")
@Isolated("One test changes the default locale")
class ObxResultStatusUnitTest {

    @ParameterizedTest(name = "[{0}] is final")
    @ValueSource(strings = {"F", "f", "C", "c", " F ", "Final", "FINAL", "final", "Final ", "Corrected", "CORRECTED",
            "corrected", "Edited", "edited"})
    void shouldBeFinal_forTheCodesAndWordsHandlersReport(String status) {
        assertThat(ObxResultStatus.isFinal(status)).isTrue();
    }

    @ParameterizedTest(name = "[{0}] is not final")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "P", "p", "I", "i", "S", "R", "X", "D", "W", "U", "N", "Pending", "Preliminary",
            "Partial", "Changed to Final", "Entered - Not Verified", "DNS", "FF", "Finalized", "F C"})
    void shouldNotBeFinal_forEveryOtherStatus(String status) {
        assertThat(ObxResultStatus.isFinal(status)).isFalse();
    }

    @Test
    void shouldReadTheStatusTheSameWay_whenTheServerLocaleIsTurkish() {
        Locale original = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(ObxResultStatus.isFinal("final")).isTrue();
            assertThat(ObxResultStatus.isFinal("EDITED")).isTrue();
            // A dotted capital I is not an ASCII letter: no case rule turns it into "Edited".
            assertThat(ObxResultStatus.isFinal("Edİted")).isFalse();
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }
}
