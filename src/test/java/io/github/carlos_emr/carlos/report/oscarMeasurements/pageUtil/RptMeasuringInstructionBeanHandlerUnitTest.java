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
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Pins that the CDM reports keep offering a measurement type's legacy measuring instructions,
 * so readings saved before an instruction change (AACP Yes/No, issue #3893) stay reportable.
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class RptMeasuringInstructionBeanHandlerUnitTest {

    private final MeasurementTypeDao measurementTypeDao = mock(MeasurementTypeDao.class);
    private final MeasurementDao measurementDao = mock(MeasurementDao.class);

    @Test
    @DisplayName("should list the current AACP instruction first and the legacy Yes/No instruction after it")
    void shouldIncludeLegacyInstruction_forAacpReadingsSavedAsYesNo() {
        when(measurementTypeDao.findByTypeDisplayName("Asthma Action Plan "))
                .thenReturn(List.of(type("AACP", "Provided/Revised/Reviewed")));
        when(measurementDao.findDistinctMeasuringInstructionsByType("AACP"))
                .thenReturn(Arrays.asList("Yes/No", "Provided/Revised/Reviewed", "", null, "  "));

        List<String> instructions = instructions(new RptMeasuringInstructionBeanHandler(
                "Asthma Action Plan ", measurementTypeDao, measurementDao));

        assertThat(instructions).containsExactly("Provided/Revised/Reviewed", "Yes/No");
    }

    @Test
    @DisplayName("should list only the current instruction when no reading uses another one")
    void shouldListCurrentInstructionOnly_whenNoLegacyReadingsExist() {
        when(measurementTypeDao.findByTypeDisplayName("Blood Pressure"))
                .thenReturn(List.of(type("BP", "sitting position")));
        when(measurementDao.findDistinctMeasuringInstructionsByType("BP")).thenReturn(List.of());

        assertThat(instructions(new RptMeasuringInstructionBeanHandler(
                "Blood Pressure", measurementTypeDao, measurementDao))).containsExactly("sitting position");
    }

    @Test
    @DisplayName("should list nothing and query no readings for an unknown display name")
    void shouldListNothing_forUnknownDisplayName() {
        when(measurementTypeDao.findByTypeDisplayName("Unknown")).thenReturn(List.of());

        assertThat(instructions(new RptMeasuringInstructionBeanHandler(
                "Unknown", measurementTypeDao, measurementDao))).isEmpty();
        verify(measurementDao, never()).findDistinctMeasuringInstructionsByType(anyString());
    }

    private static MeasurementType type(String code, String instruction) {
        MeasurementType mt = new MeasurementType();
        mt.setType(code);
        mt.setMeasuringInstruction(instruction);
        return mt;
    }

    private static List<String> instructions(RptMeasuringInstructionBeanHandler handler) {
        return handler.getMeasuringInstrcVector().stream()
                .map(RptMeasuringInstructionBean::getMeasuringInstrc)
                .toList();
    }
}
