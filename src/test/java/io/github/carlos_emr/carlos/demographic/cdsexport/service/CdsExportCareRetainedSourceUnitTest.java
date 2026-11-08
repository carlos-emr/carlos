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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

@Tag("unit") @Tag("cds-export")
class CdsExportCareRetainedSourceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111", HASH = "a".repeat(64);
    private final List<CdsExportRetainedFieldDto> fields = new ArrayList<>();
    private final Map<String, CdsCareElementDto> prepared = new HashMap<>();
    private CdsExportRetainedSectionWriter.Values values;
    private int shift;
    @BeforeEach void setup() throws Exception {
        shift = 0; values = mock(CdsExportRetainedSectionWriter.Values.class);
        when(values.text(any())).thenAnswer(call -> {
            var f = call.getArgument(0, CdsExportRetainedFieldDto.class);
            return new StringReader(f.logicalInline() == null ? f.originalInline() : f.logicalInline());
        });
    }
    static Stream<Arguments> kinds() {
        return Stream.of(
            Arguments.of("SmokingStatus", Map.of("Status", "Y"), "SKST", "Yes", null, null, false),
            Arguments.of("SmokingPacks", Map.of("PerDay", "+0.50"), "POSK", "0.5", "pack/day", null, false),
            Arguments.of("Weight", Map.of("Weight", "75.00", "WeightUnit", "kg"), "WT", "75", "kg", null, false),
            Arguments.of("Height", Map.of("Height", "170", "HeightUnit", "cm"), "HT", "170", "cm", null, false),
            Arguments.of("WaistCircumference", Map.of("WaistCircumference", "90", "WaistCircumferenceUnit", "cm"), "WAIS", "90", "cm", null, false),
            Arguments.of("BloodPressure", Map.of("SystolicBP", "+120.00", "DiastolicBP", "080", "BPUnit", "mmHg"), "BP", "120/80", "mmHg", null, false),
            Arguments.of("DiabetesComplicationsScreening", Map.of("ExamCode", "67536-3"), "CDS_EXAM", "Performed", null, "67536-3", false),
            Arguments.of("DiabetesMotivationalCounselling", Map.of("CounsellingPerformed", "Smoking Cessation"), "MCCS", "Yes", null, null, false),
            Arguments.of("DiabetesSelfManagementCollaborative", Map.of("CodeValue", "44943-9", "DocumentedGoals", "Walk daily"), "CDS_GOALS", "Walk daily", null, "44943-9", false),
            Arguments.of("DiabetesSelfManagementChallenges", Map.of("CodeValue", "44941-3", "ChallengesIdentified", "n"), "SMCD", "No", null, "44941-3", false),
            Arguments.of("DiabetesEducationalSelfManagement", Map.of("EducationalTrainingPerformed", "N"), "DMME", "No", null, null, false),
            Arguments.of("HypoglycemicEpisodes", Map.of("NumOfReportedEpisodes", "2"), "HYPE", "2", null, null, false),
            Arguments.of("SelfMonitoringBloodGlucose", Map.of("SelfMonitoring", "y"), "SMBG", "Yes", null, null, false),
            Arguments.of("Weight", Map.of("Weight", "Unable to stand", "WeightUnit", "kg"), "CDS_CARE_VALUE", "Unable to stand", "kg", null, true),
            Arguments.of("HypoglycemicEpisodes", Map.of("NumOfReportedEpisodes", "2.5"), "CDS_CARE_VALUE", "2.5", null, null, true),
            Arguments.of("BloodPressure", Map.of("SystolicBP", "Unobtainable", "DiastolicBP", "80", "BPUnit", "mmHg"), "CDS_CARE_VALUE", "Systolic: Unobtainable; Diastolic: 80", "mmHg", null, true));
    }
    @ParameterizedTest @MethodSource("kinds")
    void shouldBindEveryCoreKind_andKeepNonquantitativeText(String element, Map<String, String> scalars, String type, String value, String unit, String code, boolean uninterpreted) throws Exception {
        add(element, scalars, type, value, unit, code, false, uninterpreted);
        var result = read(); assertThat(result.sourceFields()).hasSize(scalars.size() + 1); assertThat(result.changes()).isEmpty();
        assertThat(result.dates().get(element + "[1]").lexical()).isEqualTo("2020-02-28");
    }
    @Test void shouldShiftEachObservation_usingOriginalOptions() throws Exception {
        shift = 2; weight(); var result = read(); assertThat(result.changes()).hasSize(1);
        assertThat(result.dates().get("Weight[1]").lexical()).isEqualTo("2020-03-01");
        var xml = mock(CdsExportXmlComposer.ContentWriter.class); result.changes().getFirst().content().write(xml); verify(xml).text("2020-03-01");
    }
    @Test void shouldPreserveCompleteOverflowGoal_withoutMaterializingItDuringBinding() throws Exception {
        String goal = "X".repeat(32769); add("DiabetesSelfManagementCollaborative", Map.of("CodeValue", "44943-9", "DocumentedGoals", goal), "CDS_GOALS", CdsCareScalarContract.OVERFLOW_GOALS, null, "44943-9", true, false);
        doThrow(new AssertionError("Overflow goal materialized")).when(values).text(argThat(f -> f != null && f.sourcePath().contains("DocumentedGoals")));
        assertThat(read().sourceFields()).hasSize(3);
    }
    @Test void shouldUseReviewedLogicalGoalLength_andRetainOriginalDigest() throws Exception {
        String original = "X".repeat(40000); add("DiabetesSelfManagementCollaborative", Map.of("CodeValue", "44943-9", "DocumentedGoals", original), "CDS_GOALS", "Reviewed goal", null, "44943-9", false, false);
        replaceGoal("Reviewed goal"); assertThat(read().sourceFields()).hasSize(3);
    }
    @Test void shouldRecognizeReviewedGoalThatExceedsNativeBudget() throws Exception {
        add("DiabetesSelfManagementCollaborative", Map.of("CodeValue", "44943-9", "DocumentedGoals", "Original"), "CDS_GOALS", CdsCareScalarContract.OVERFLOW_GOALS, null, "44943-9", true, false);
        replaceGoal("X".repeat(40000)); assertThat(read().sourceFields()).hasSize(3);
    }
    @Test void shouldRetainAnEmptyGoal_asActualText() throws Exception {
        add("DiabetesSelfManagementCollaborative", Map.of("CodeValue", "44943-9", "DocumentedGoals", ""), "CDS_GOALS", "", null, "44943-9", false, false); assertThat(read().sourceFields()).hasSize(3);
    }
    @Test void shouldRefuseChangedPreparedValue_insteadOfReturningOldXml() {
        weight(); var p = prepared.get("Weight[1]"); prepared.put("Weight[1]", new CdsCareElementDto(p.element(), p.measurementType(), "76", p.unit(), p.code(), p.date(), false, false, null, List.of()));
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_CARE_SOURCE_PREPARATION_MISMATCH");
    }
    @Test void shouldRefuseChangedPreparedDate_orUnrecordedShift() {
        weight(); shift = 1; assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_CARE_SOURCE_PREPARATION_MISMATCH");
    }
    @Test void shouldRefuseMissingChildPreparation() {
        weight(); prepared.clear(); assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }
    @Test void shouldRefuseUnwitnessedExtraPreparation() {
        weight(); prepared.put("Weight[2]", prepared.get("Weight[1]")); assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }
    @Test void shouldRefuseWrongUnit_andUnknownScalar() {
        weight(); replace("Weight[1]/WeightUnit[1]", "lb"); assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }
    @Test void shouldRefuseDuplicateIdentities() {
        weight(); fields.add(fields.getFirst()); assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }
    @Test void shouldRefuseForeignImportRun() {
        weight(); var f = fields.getFirst(); fields.set(0, new CdsExportRetainedFieldDto(f.id(), "22222222-2222-4222-8222-222222222222", f.sourcePatientKey(), f.fieldUnitKey(), f.category(), f.sourcePath(), f.occurrence(), f.namespace(), f.valueType(), f.precision(), f.originalInline(), f.logicalInline(), f.originalPayload()));
        assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }
    @Test void shouldRefuseExpiredStreamScope_beforeReadingPayloads() throws Exception {
        weight(); doThrow(new IOException("EXPIRED")).when(values).requireActive(); assertThatThrownBy(this::read).hasMessage("EXPIRED"); verify(values, never()).text(any());
    }
    @Test void shouldPropagateCancellation_beforeBinding() {
        weight(); assertThatThrownBy(() -> new CdsExportCareRetainedSource().readVerified(fields, options(), prepared, values, () -> { throw new java.util.concurrent.CancellationException("CANCELLED"); })).hasMessage("CANCELLED");
    }
    private void weight() { add("Weight", Map.of("Weight", "75", "WeightUnit", "kg"), "WT", "75", "kg", null, false, false); }
    private void add(String element, Map<String, String> scalars, String type, String value, String unit, String code, boolean overflow, boolean uninterpreted) {
        scalars.forEach((name, lexical) -> addField(element + "[1]/" + name + "[1]", lexical)); addField(element + "[1]/Date[1]", "2020-02-28");
        String goal = scalars.get("DocumentedGoals"), digest = goal == null ? null : sha(goal);
        var date = CdsTemporal.shift(CdsTemporal.parse(null, null, "2020-02-28", null, "Date"), shift, "Date");
        prepared.put(element + "[1]", new CdsCareElementDto(element, type, value, unit, code, date, overflow, uninterpreted, digest, List.of()));
    }
    private void addField(String path, String value) {
        var type = CdsWireSchema.schemaModel().element("cds", "OmdCds").child("cds", "PatientRecord").child("cds", "CareElements"); String namespace = "cds";
        for (String segment : path.split("/")) {
            String name = segment.substring(0, segment.indexOf('[')); var child = type.childNames().stream().filter(candidate -> candidate.local().equals(name)).findFirst().orElseThrow(); namespace = child.namespace(); type = type.child(namespace, name);
        }
        var payload = value.length() <= 32768 ? null : new CdsExportRetainedFieldDto.Payload("/private/captured-goal", sha(value), 0, value.getBytes(StandardCharsets.UTF_8).length);
        fields.add(new CdsExportRetainedFieldDto(CdsImportKeys.hash(path, Integer.toString(fields.size())), RUN, HASH, HASH, CdsCategory.CARE_ELEMENTS, "/OmdCds[1]/PatientRecord[1]/CareElements[1]/" + path, 1, namespace, "{" + type.getTypeNamespace() + "}" + type.getTypeName(), null, payload == null ? value : null, payload == null ? value : null, payload));
    }
    private void replaceGoal(String logical) { for (int i = 0; i < fields.size(); i++) if (fields.get(i).sourcePath().endsWith("/DocumentedGoals[1]")) { var f = fields.get(i); fields.set(i, new CdsExportRetainedFieldDto(f.id(), f.runId(), f.sourcePatientKey(), f.fieldUnitKey(), f.category(), f.sourcePath(), f.occurrence(), f.namespace(), f.valueType(), f.precision(), f.originalInline(), logical, f.originalPayload())); } }
    private void replace(String path, String logical) { for (int i = 0; i < fields.size(); i++) if (fields.get(i).sourcePath().endsWith(path)) { var f = fields.get(i); fields.set(i, new CdsExportRetainedFieldDto(f.id(), f.runId(), f.sourcePatientKey(), f.fieldUnitKey(), f.category(), f.sourcePath(), f.occurrence(), f.namespace(), f.valueType(), f.precision(), f.originalInline(), logical, f.originalPayload())); } }
    private CdsExportRetainedImportOptionsReader.Options options() { return new CdsExportRetainedImportOptionsReader.Options(RUN, HASH, HASH, HASH, "omd-5.2", shift != 0, shift); }
    private CdsExportCareRetainedSource.Result read() throws Exception { return new CdsExportCareRetainedSource().readVerified(fields, options(), prepared, values, () -> {}); }
    private static String sha(String value) { return HexFormat.of().formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8))); }
}
