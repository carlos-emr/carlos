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
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how {@link ColumnContinuation} reads a column's opt-in settings: a well-formed line is
 * printed by it, and a malformed one is logged and left to {@link FrmPDFServlet}'s plain column.
 *
 * @since 2026-10-15
 */
@DisplayName("ColumnContinuation unit tests")
@Tag("unit")
@Tag("fast")
class ColumnContinuationUnitTest extends CarlosUnitTestBase {

    private static final String COLUMN = "left, 36, 261, 0, BaseFont.HELVETICA, 8, _, 576, 630, 10, ";
    private static final float LETTER_HEIGHT = 792;

    @Test
    void shouldPrintOptedInColumn_whenSettingsAreWellFormed() throws Exception {
        ColumnContinuation plan = plan(COLUMN + "continue=Notes (continued), fallback=7/8/261");

        assertThat(plan.prints(1, "notes")).isTrue();
        assertThat(plan.prints(1, "other")).isFalse();
        assertThat(plan.pages()).isZero();
    }

    @Test
    void shouldLeaveColumnToThePlainPrint_whenASettingIsMalformed() throws Exception {
        try (LogCapture log = LogCapture.forLogger(ColumnContinuation.class)) {
            ColumnContinuation plan = plan(COLUMN + "continue=Notes (continued), fallback=x/8/214");

            assertThat(plan.prints(1, "notes")).isFalse();
            assertThat(plan.pages()).isZero();
            assertThat(log.messages()).anySatisfy(message ->
                    assertThat(message).contains("notes").contains("malformed continuation setting"));
        }
    }

    private static ColumnContinuation plan(String notesLine) throws Exception {
        Properties config = new Properties();
        config.setProperty("notes", notesLine);
        Properties values = new Properties();
        values.setProperty("notes", "FAKE short note.");
        return ColumnContinuation.plan(new Properties[] {config}, 1, values, values::getProperty, LETTER_HEIGHT, 2);
    }
}
