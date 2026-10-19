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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A saved checkbox must be shown ticked when its form is reopened (issue #4466). Nineteen record
 * classes compared the column type name with {@code TINY}; the MySQL driver reports {@code BIT} for
 * {@code tinyint(1)} and {@code TINYINT} for other widths, so every tick came back as the raw value
 * {@code 1}, which the JSP wrote into the tag as {@code 1=""} and the next Save stored as unticked.
 *
 * <p>Each record class reads the saved row through {@link LegacyJdbcQuery}; the row here answers
 * with the type names the driver reports today.
 *
 * @since 2026-10-19
 */
@DisplayName("Legacy form record checkbox restore")
@Tag("unit")
@Tag("form")
class FrmRecordCheckboxRestoreUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 42;
    private static final int SAVED_FORM = 7;
    private static final String CHECKED = "checked='checked'";

    /** {@code FrmRecord}'s constructor looks both of these up; reopening a saved form uses neither. */
    @BeforeEach
    void registerFrmRecordBeans() {
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
    }

    /** Every record class that used to compare the column type with {@code TINY}. */
    private static List<Named> recordClasses() {
        return List.of(
                new Named("Frm2MinWalkRecord", Frm2MinWalkRecord::new),
                new Named("FrmCESDRecord", FrmCESDRecord::new),
                new Named("FrmCaregiverRecord", FrmCaregiverRecord::new),
                new Named("FrmCostQuestionnaireRecord", FrmCostQuestionnaireRecord::new),
                new Named("FrmFallsRecord", FrmFallsRecord::new),
                new Named("FrmGripStrengthRecord", FrmGripStrengthRecord::new),
                new Named("FrmHomeFallsRecord", FrmHomeFallsRecord::new),
                new Named("FrmIntakeInfoRecord", FrmIntakeInfoRecord::new),
                new Named("FrmInternetAccessRecord", FrmInternetAccessRecord::new),
                new Named("FrmLateLifeFDIDisabilityRecord", FrmLateLifeFDIDisabilityRecord::new),
                new Named("FrmLateLifeFDIFunctionRecord", FrmLateLifeFDIFunctionRecord::new),
                new Named("FrmRhImmuneGlobulinRecord", FrmRhImmuneGlobulinRecord::new),
                new Named("FrmSF36CaregiverRecord", FrmSF36CaregiverRecord::new),
                new Named("FrmSF36Record", FrmSF36Record::new),
                new Named("FrmSatisfactionScaleRecord", FrmSatisfactionScaleRecord::new),
                new Named("FrmSelfAdministeredRecord", FrmSelfAdministeredRecord::new),
                new Named("FrmSelfEfficacyRecord", FrmSelfEfficacyRecord::new),
                new Named("FrmSelfManagementRecord", FrmSelfManagementRecord::new),
                new Named("FrmTreatmentPrefRecord", FrmTreatmentPrefRecord::new));
    }

    private record Named(String name, Supplier<FrmRecord> factory) {
    }

    private static Stream<Arguments> recordClassesAndCheckboxTypeNames() {
        return recordClasses().stream()
                .flatMap(formRecord -> Stream.of("BIT", "TINYINT", "TINYINT UNSIGNED")
                        .map(typeName -> Arguments.of(formRecord.name(), formRecord.factory(), typeName)));
    }

    private static Stream<Arguments> recordClassesOnly() {
        return recordClasses().stream().map(formRecord -> Arguments.of(formRecord.name(), formRecord.factory()));
    }

    /**
     * A saved form row: its ID, a ticked and an unticked box of the given driver type, and a text
     * answer.
     */
    private static ResultSet savedRow(String checkboxTypeName) throws Exception {
        String[] names = {"ID", "tickedBox", "untickedBox", "typedAnswer"};
        String[] types = {"INT", checkboxTypeName, checkboxTypeName, "VARCHAR"};

        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(names.length);
        for (int i = 0; i < names.length; i++) {
            when(metaData.getColumnName(i + 1)).thenReturn(names[i]);
            when(metaData.getColumnTypeName(i + 1)).thenReturn(types[i]);
            when(metaData.getScale(i + 1)).thenReturn(0);
        }

        ResultSet row = mock(ResultSet.class);
        when(row.next()).thenReturn(true, false);
        when(row.getMetaData()).thenReturn(metaData);
        when(row.getInt(1)).thenReturn(SAVED_FORM);
        when(row.getString(1)).thenReturn(String.valueOf(SAVED_FORM));
        when(row.getInt(2)).thenReturn(1);
        when(row.getString(2)).thenReturn("1");
        when(row.getInt(3)).thenReturn(0);
        when(row.getString(3)).thenReturn("0");
        when(row.getString(4)).thenReturn("typed text");
        return row;
    }

    private static Properties reopen(FrmRecord formRecord, ResultSet row) throws Exception {
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(anyString(), any(Object[].class))).thenReturn(row);
            return formRecord.getFormRecord(null, PATIENT, SAVED_FORM);
        }
    }

    @ParameterizedTest(name = "{0} shows a saved tick when the driver reports {2}")
    @MethodSource("recordClassesAndCheckboxTypeNames")
    void shouldShowSavedTickAsChecked_whenDriverReportsCheckboxTypeName(String recordName,
            Supplier<FrmRecord> factory, String typeName) throws Exception {
        Properties props = reopen(factory.get(), savedRow(typeName));

        assertThat(props.getProperty("tickedBox")).as("ticked box").isEqualTo(CHECKED);
    }

    @ParameterizedTest(name = "{0} leaves an unticked box blank")
    @MethodSource("recordClassesOnly")
    void shouldLeaveUntickedBoxBlank_whenSavedValueIsZero(String recordName, Supplier<FrmRecord> factory)
            throws Exception {
        Properties props = reopen(factory.get(), savedRow("BIT"));

        assertThat(props.getProperty("untickedBox")).as("unticked box").isEmpty();
    }

    @ParameterizedTest(name = "{0} still shows a text answer as typed")
    @MethodSource("recordClassesOnly")
    void shouldShowTextAnswerAsTyped_whenColumnIsNotACheckbox(String recordName, Supplier<FrmRecord> factory)
            throws Exception {
        Properties props = reopen(factory.get(), savedRow("BIT"));

        assertThat(props.getProperty("typedAnswer")).isEqualTo("typed text");
        assertThat(props.getProperty("ID")).isEqualTo(String.valueOf(SAVED_FORM));
    }

    /**
     * No Java source may go back to comparing a column type name with {@code TINY}: the next copy of an old
     * record class would bring the defect back.
     */
    @Test
    void shouldNotCompareColumnTypeWithTiny_anywhereInMainSources() throws IOException {
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            List<String> offenders = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8).contains("\"TINY\"");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(Path::toString)
                    .sorted()
                    .toList();

            assertThat(offenders).as("sources comparing a column type with \"TINY\"").isEmpty();
        }
    }
}
