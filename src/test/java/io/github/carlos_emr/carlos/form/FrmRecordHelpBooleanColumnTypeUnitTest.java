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
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one check that decides whether a legacy form column is a checkbox (issue #4466). Nineteen
 * {@code Frm*Record} classes used to compare the type name with {@code TINY}, a name the MySQL driver
 * never reports, so saved ticks were not shown again.
 *
 * @since 2026-10-19
 */
@DisplayName("Legacy form checkbox column detection")
@Tag("unit")
@Tag("form")
class FrmRecordHelpBooleanColumnTypeUnitTest {

    /** The names MySQL Connector/J 9 reports: BIT for tinyint(1) (tinyInt1isBit), TINYINT for the rest. */
    @ParameterizedTest(name = "{0} is a checkbox column")
    @ValueSource(strings = {"BIT", "bit", "TINYINT", "tinyint", "TINYINT UNSIGNED", "TINYINT(1)"})
    void shouldTreatAsCheckbox_forTheTypeNamesTheDriverReports(String typeName) {
        assertThat(FrmRecordHelp.isBooleanColumnType(typeName)).isTrue();
    }

    @ParameterizedTest(name = "{0} is not a checkbox column")
    @ValueSource(strings = {"TINY", "TINYTEXT", "TINYBLOB", "INT", "SMALLINT", "VARCHAR", "CHAR", "TEXT", "DATE",
            "TIMESTAMP", "DECIMAL", ""})
    void shouldNotTreatAsCheckbox_forOtherTypeNames(String typeName) {
        assertThat(FrmRecordHelp.isBooleanColumnType(typeName)).isFalse();
    }

    @Test
    void shouldNotTreatAsCheckbox_forMissingTypeName() {
        assertThat(FrmRecordHelp.isBooleanColumnType(null)).isFalse();
    }
}
