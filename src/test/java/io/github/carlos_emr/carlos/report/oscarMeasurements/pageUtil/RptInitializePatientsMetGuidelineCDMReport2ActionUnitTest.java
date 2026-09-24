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

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the SQL-injection guard on the CDM "patients who met guideline" report: the
 * submitted above/below operator selects a constant statement and is never concatenated.
 */
@Tag("unit")
@Tag("report")
@Tag("security")
@DisplayName("RptInitializePatientsMetGuidelineCDMReport2Action comparator guard")
class RptInitializePatientsMetGuidelineCDMReport2ActionUnitTest {

    @Test
    @DisplayName("should accept the two operators the met-guideline form offers")
    void shouldAcceptFormOperators_forAboveAndBelow() {
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.guidelineComparator(">")).isEqualTo(">");
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.guidelineComparator("<")).isEqualTo("<");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "=", ">=", "<=", " >", "> ", "<>", "> 0 OR 1=1 OR dataField >", "<' OR '1'='1", ";DROP TABLE measurements;--"})
    @DisplayName("should reject any other comparator, including injection payloads")
    void shouldReturnNull_forUnsupportedComparator(String raw) {
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.guidelineComparator(raw)).isNull();
    }

    @Test
    @DisplayName("should bind the guideline and use only a fixed operator in every statement")
    void shouldUseFixedOperatorAndBoundGuideline_inEveryStatement() {
        List<String> statements = List.of(
                RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_ABOVE_WITH_INSTRUCTION,
                RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_BELOW_WITH_INSTRUCTION,
                RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_ABOVE,
                RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_BELOW);
        assertThat(statements).allSatisfy(sql -> assertThat(sql).matches(".* AND dataField [<>] :guideline"));
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_ABOVE_WITH_INSTRUCTION)
                .contains("measuringInstruction = :measuringInstruction").endsWith("> :guideline");
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_BELOW).endsWith("< :guideline")
                .doesNotContain("measuringInstruction");
    }
}
