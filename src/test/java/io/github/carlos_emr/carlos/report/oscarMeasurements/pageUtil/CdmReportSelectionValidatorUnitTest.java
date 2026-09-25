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

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The CDM report forms echo each row's type and instruction in hidden fields. The validator
 * accepts a row only when those echoes match what the server rendered for that row.
 */
@Tag("unit")
@Tag("report")
@Tag("security")
@DisplayName("CdmReportSelectionValidator")
class CdmReportSelectionValidatorUnitTest extends CarlosUnitTestBase {

    private RptMeasurementTypesBeanHandler definitions;

    @BeforeEach
    void buildDefinitions() {
        MeasurementGroupDao groupDao = createAndRegisterMock(MeasurementGroupDao.class);
        MeasurementTypeDao typeDao = createAndRegisterMock(MeasurementTypeDao.class);
        MeasurementDao measurementDao = createAndRegisterMock(MeasurementDao.class);

        MeasurementGroup aacpRow = new MeasurementGroup();
        aacpRow.setName("FAKE-CDM");
        aacpRow.setTypeDisplayName("Asthma Action Plan");
        MeasurementGroup bpRow = new MeasurementGroup();
        bpRow.setName("FAKE-CDM");
        bpRow.setTypeDisplayName("Blood Pressure");
        when(groupDao.findByName("FAKE-CDM")).thenReturn(new ArrayList<>(List.of(aacpRow, bpRow)));
        // Build the stubbed types before stubbing the DAO: nesting mock setup inside when() is
        // reported by Mockito as unfinished stubbing.
        MeasurementType aacp = type("AACP", "Asthma Action Plan", "Provided/Revised/Reviewed");
        MeasurementType bp = type("BP", "Blood Pressure", "Sitting");
        when(typeDao.findByTypeDisplayName("Asthma Action Plan")).thenReturn(List.of(aacp));
        when(typeDao.findByTypeDisplayName("Blood Pressure")).thenReturn(List.of(bp));
        when(measurementDao.findDistinctMeasuringInstructionsByTypes(anyCollection()))
                .thenReturn(Map.of("AACP", List.of("Yes/No")));

        definitions = new RptMeasurementTypesBeanHandler("FAKE-CDM");
    }

    @Test
    @DisplayName("should accept the type and instructions rendered for a row")
    void shouldAcceptSelectors_whenTheyMatchTheRenderedRow() {
        CdmReportSelectionValidator validator = new CdmReportSelectionValidator(definitions);

        assertThat(validator.isMeasurementType(0, "AACP")).isTrue();
        assertThat(validator.isMeasurementType(1, "BP")).isTrue();
        assertThat(validator.isMeasuringInstruction(0, "Provided/Revised/Reviewed")).isTrue();
        assertThat(validator.isMeasuringInstruction(0, "Yes/No")).isTrue();
        assertThat(validator.isMeasuringInstruction(1, "Sitting")).isTrue();
        assertThat(validator.acceptedMeasurementType(0, "AACP")).isEqualTo("AACP");
        assertThat(validator.acceptedMeasuringInstruction(0, "Yes/No")).isEqualTo("Yes/No");
    }

    @Test
    @DisplayName("should reject a type or instruction that is not part of the rendered row")
    void shouldRejectSelectors_whenTheyDoNotMatchTheRenderedRow() {
        CdmReportSelectionValidator validator = new CdmReportSelectionValidator(definitions);

        assertThat(validator.isMeasurementType(0, "BP")).as("type of another row").isFalse();
        assertThat(validator.isMeasurementType(0, "HIV")).as("type outside the group").isFalse();
        assertThat(validator.isMeasurementType(0, "aacp")).as("case differs").isFalse();
        assertThat(validator.isMeasurementType(2, "AACP")).as("row index past the end").isFalse();
        assertThat(validator.isMeasurementType(-1, "AACP")).isFalse();
        assertThat(validator.isMeasurementType(0, null)).isFalse();
        assertThat(validator.isMeasuringInstruction(1, "Yes/No")).as("instruction of another row").isFalse();
        assertThat(validator.isMeasuringInstruction(0, "%")).as("wildcard").isFalse();
        assertThat(validator.isMeasuringInstruction(0, "Sitting' OR '1'='1")).isFalse();
        assertThat(validator.isMeasuringInstruction(5, "Sitting")).isFalse();
        assertThat(validator.acceptedMeasurementType(0, "HIV")).isNull();
        assertThat(validator.acceptedMeasuringInstruction(0, "%")).isNull();
    }

    @Test
    @DisplayName("should pass an unticked (null) instruction through unchanged")
    void shouldReturnNull_forUntickedInstruction() {
        CdmReportSelectionValidator validator = new CdmReportSelectionValidator(definitions);

        assertThat(validator.acceptedMeasuringInstruction(0, null)).isNull();
    }

    @Test
    @DisplayName("should accept nothing when the session holds no definitions")
    void shouldRejectEverything_whenSessionHasNoDefinitions() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("measurementTypes")).thenReturn("not a handler");

        CdmReportSelectionValidator validator = CdmReportSelectionValidator.fromSession(request);

        assertThat(validator.isMeasurementType(0, "AACP")).isFalse();
        assertThat(validator.isMeasuringInstruction(0, "Yes/No")).isFalse();
        assertThat(CdmReportSelectionValidator.fromSession(null).isMeasurementType(0, "AACP")).isFalse();
    }

    @Test
    @DisplayName("should read the definitions the CDM setup page stored in the session")
    void shouldUseSessionDefinitions_whenPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("measurementTypes")).thenReturn(definitions);

        CdmReportSelectionValidator validator = CdmReportSelectionValidator.fromSession(request);

        assertThat(validator.isMeasurementType(1, "BP")).isTrue();
        assertThat(validator.isMeasurementType(1, "AACP")).isFalse();
    }

    private static MeasurementType type(String code, String displayName, String instruction) {
        MeasurementType mt = mock(MeasurementType.class);
        when(mt.getId()).thenReturn(1);
        when(mt.getType()).thenReturn(code);
        when(mt.getTypeDisplayName()).thenReturn(displayName);
        when(mt.getTypeDescription()).thenReturn(displayName);
        when(mt.getMeasuringInstruction()).thenReturn(instruction);
        when(mt.getValidation()).thenReturn("1");
        return mt;
    }
}
