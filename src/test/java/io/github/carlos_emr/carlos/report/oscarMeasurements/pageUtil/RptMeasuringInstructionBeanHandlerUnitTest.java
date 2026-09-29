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

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that the CDM reports keep offering a measurement type's legacy measuring instructions,
 * so readings saved before an instruction change (AACP Yes/No, issue #3893) stay reportable, and
 * that the stored instructions are read in one query per report rather than one per type.
 *
 * <p>The list is shown clinic-wide to every {@code _report} reader while readings store whatever
 * the entry form posted, so only controlled instructions (type definitions and the retired seed
 * spellings) may be promoted from the {@code measurements} table into it.</p>
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
@Tag("security")
class RptMeasuringInstructionBeanHandlerUnitTest extends CarlosUnitTestBase {

    private final MeasurementGroupDao measurementGroupDao = mock(MeasurementGroupDao.class);
    private final MeasurementTypeDao measurementTypeDao = mock(MeasurementTypeDao.class);
    private final MeasurementDao measurementDao = mock(MeasurementDao.class);

    @BeforeEach
    void registerDaos() {
        registerMock(MeasurementGroupDao.class, measurementGroupDao);
        registerMock(MeasurementTypeDao.class, measurementTypeDao);
        registerMock(MeasurementDao.class, measurementDao);
    }

    @Test
    @DisplayName("should list the current AACP instruction first and the legacy Yes/No instruction after it")
    void shouldIncludeLegacyInstruction_forAacpReadingsSavedAsYesNo() {
        RptMeasuringInstructionBeanHandler handler = new RptMeasuringInstructionBeanHandler(
                List.of(type("AACP", "Asthma Action Plan ", "Provided/Revised/Reviewed")),
                Map.of("AACP", Arrays.asList("Yes/No", "Provided/Revised/Reviewed", "", null, "  ")));

        assertThat(instructions(handler)).containsExactly("Provided/Revised/Reviewed", "Yes/No");
    }

    @Test
    @DisplayName("should list only the current instruction when no reading uses another one")
    void shouldListCurrentInstructionOnly_whenNoLegacyReadingsExist() {
        RptMeasuringInstructionBeanHandler handler = new RptMeasuringInstructionBeanHandler(
                List.of(type("BP", "Blood Pressure", "sitting position")), Map.of());

        assertThat(instructions(handler)).containsExactly("sitting position");
    }

    @Test
    void shouldExcludeAacpLegacyInstructions_whenAnotherTypeStoresThoseStrings() {
        RptMeasuringInstructionBeanHandler handler = new RptMeasuringInstructionBeanHandler(
                List.of(type("WT", "Weight", "kg")), Map.of("WT", List.of("Yes/No", "Yes/No/NA")));

        assertThat(instructions(handler)).containsExactly("kg");
    }

    @Test
    @DisplayName("should read the stored instructions of every listed type in a single query")
    void shouldQueryStoredInstructionsOnce_forManyTypes() {
        List<MeasurementGroup> groups = new ArrayList<>();
        String[][] rows = {{"AACP", "Asthma Action Plan ", "Provided/Revised/Reviewed"},
                {"BP", "Blood Pressure", "sitting position"}, {"WT", "Weight", "in kg"}};
        for (String[] row : rows) {
            MeasurementGroup group = new MeasurementGroup();
            group.setName("FAKE-CDM");
            group.setTypeDisplayName(row[1]);
            groups.add(group);
            List<MeasurementType> types = List.of(type(row[0], row[1], row[2]));
            when(measurementTypeDao.findByTypeDisplayName(row[1])).thenReturn(types);
        }
        when(measurementGroupDao.findByName("FAKE-CDM")).thenReturn(groups);
        when(measurementDao.findDistinctMeasuringInstructionsByTypes(any()))
                .thenReturn(Map.of("AACP", List.of("Yes/No")));

        RptMeasurementTypesBeanHandler report = new RptMeasurementTypesBeanHandler("FAKE-CDM");

        verify(measurementDao, times(1)).findDistinctMeasuringInstructionsByTypes(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> queried = ArgumentCaptor.forClass(Collection.class);
        verify(measurementDao).findDistinctMeasuringInstructionsByTypes(queried.capture());
        assertThat(queried.getValue()).containsExactlyInAnyOrder("AACP", "BP", "WT");
        assertThat(report.getMeasurementTypeVector()).hasSize(3);
        assertThat(report.getMeasuringInstrcBeanVector()).hasSize(3);
        assertThat(instructions(report.getMeasuringInstrcBeanVector().get(0)))
                .containsExactly("Provided/Revised/Reviewed", "Yes/No");
        assertThat(instructions(report.getMeasuringInstrcBeanVector().get(1))).containsExactly("sitting position");
    }

    @Test
    @DisplayName("should never offer stored free text, only the definition and legacy Yes/No instructions")
    void shouldOfferControlledInstructionsOnly_whenReadingsHoldFreeText() {
        RptMeasuringInstructionBeanHandler handler = new RptMeasuringInstructionBeanHandler(
                List.of(type("AACP", "Asthma Action Plan ", "Provided/Revised/Reviewed")),
                Map.of("AACP", List.of(
                        "Yes/No",
                        "FAKE-Patient Smith asked about inhaler on 2026-01-02",
                        "' OR 1=1 --",
                        "Provided/Revised/Reviewed")));

        assertThat(instructions(handler)).containsExactly("Provided/Revised/Reviewed", "Yes/No");
    }

    @Test
    @DisplayName("should offer an instruction another definition row of the same type carries")
    void shouldOfferInstruction_fromSiblingDefinitionRow() {
        RptMeasuringInstructionBeanHandler handler = new RptMeasuringInstructionBeanHandler(
                List.of(type("WT", "Weight", "kg"), type("WT", "Weight", "lbs")),
                Map.of("WT", List.of("lbs", "weighed at home by FAKE-Patient")));

        assertThat(instructions(handler)).containsExactly("kg", "lbs");
    }

    @Test
    @DisplayName("should treat only definition and legacy seed instructions as controlled")
    void shouldClassifyInstructions_forControlledCheck() {
        Set<String> defined = Set.of("Provided/Revised/Reviewed");

        assertThat(RptMeasuringInstructionBeanHandler.isControlled("AACP", "Provided/Revised/Reviewed", defined)).isTrue();
        assertThat(RptMeasuringInstructionBeanHandler.isControlled("AACP", "Yes/No", defined)).isTrue();
        assertThat(RptMeasuringInstructionBeanHandler.isControlled("AACP", "Yes/No/NA", defined)).isTrue();
        assertThat(RptMeasuringInstructionBeanHandler.isControlled("AACP", "yes/no", defined)).isFalse();
        assertThat(RptMeasuringInstructionBeanHandler.isControlled("AACP", "Provided", defined)).isFalse();
        assertThat(RptMeasuringInstructionBeanHandler.isControlled("AACP", null, defined)).isFalse();
        assertThat(RptMeasuringInstructionBeanHandler.isControlled("AACP", "  ", defined)).isFalse();
    }

    /** A mock, because the entity has no id setter and the report bean unboxes the id. */
    private static MeasurementType type(String code, String displayName, String instruction) {
        MeasurementType mt = mock(MeasurementType.class);
        when(mt.getId()).thenReturn(Math.abs(code.hashCode()));
        when(mt.getType()).thenReturn(code);
        when(mt.getTypeDisplayName()).thenReturn(displayName);
        when(mt.getTypeDescription()).thenReturn(displayName);
        when(mt.getMeasuringInstruction()).thenReturn(instruction);
        return mt;
    }

    private static List<String> instructions(RptMeasuringInstructionBeanHandler handler) {
        return handler.getMeasuringInstrcVector().stream()
                .map(RptMeasuringInstructionBean::getMeasuringInstrc)
                .toList();
    }
}
