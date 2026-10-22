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
package io.github.carlos_emr.carlos.form;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FrmRourke2006Record#reopenPage(String)}: the page a saved Rourke 2006 record reopens on (issue #4595).
 *
 * @since 2026-10-22
 */
@DisplayName("Rourke 2006 reopen page")
@Tag("unit")
@Tag("form")
class FrmRourke2006RecordReopenPageUnitTest {

    @ParameterizedTest(name = "\"{0}\" reopens on {1}")
    @CsvSource({"p1,p1", "p2,p2", "p3,p3", "p4,p4", "'p1/',p1", "'p3/',p3", "'p4/',p4", "' p2 ',p2"})
    @DisplayName("should reopen on the stored page, without the slash older saves kept")
    void shouldReopenOnStoredPage_whenStoredValueNamesAPage(String stored, String expected) {
        assertThat(FrmRourke2006Record.reopenPage(stored)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"p5", "p0", "P1", "p1//", "../x", "p1/../../x", "1", "p12"})
    @DisplayName("should reopen on page 1 when the stored value names no page")
    void shouldReopenOnFirstPage_whenStoredValueNamesNoPage(String stored) {
        assertThat(FrmRourke2006Record.reopenPage(stored)).isEqualTo("p1");
    }

    @Test
    @DisplayName("should keep the include on a Rourke 2006 page file")
    void shouldKeepIncludeOnRourke2006Page_forAnyStoredValue() {
        for (String stored : new String[] {"p2", "p4/", "formrourke2006p1", "x/../../WEB-INF/web.xml"}) {
            assertThat("formrourke2006" + FrmRourke2006Record.reopenPage(stored) + ".jsp").matches("formrourke2006p[1-4]\\.jsp");
        }
    }
}
