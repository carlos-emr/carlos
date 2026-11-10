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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportAllergyFieldReconcilerUnitTest {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            UNIT = "a".repeat(64),
            ADDRESS = RUN + ":" + UNIT,
            SOURCE = "b".repeat(64);
    @TempDir Path directory;

    @Test
    void
            shouldKeepTheProvenOriginalCodeAuthorityLifeThreateningSeverityNotesAndClock_whileUsingAnEditedReaction()
                    throws Exception {
        var fields = baseline();
        fields.put("reaction", "Current café 😀 reaction");
        fields.put("lastUpdateDate", "2026-11-06 01:02:03");
        var fixture = fixture(fields, true, source());
        var result = reconcile(fixture, List.of());
        assertThat(result.mapping().nativeRowRepresentable()).isTrue();
        assertThat(result.mapping().value().reaction()).isEqualTo("Current café 😀 reaction");
        assertThat(result.mapping().value().severity()).isEqualTo("LT");
        assertThat(result.mapping().value().codeType()).isEqualTo("DIN");
        assertThat(result.mapping().value().notes()).isEqualTo("Source-only provider note");
        assertThat(result.currentPaths()).containsExactly("Reaction");
        assertThat(result.preparedPaths()).contains("Code", "Severity", "RecordedDate", "Notes");
        var received = roundTrip(result.mapping().value());
        assertThat(received.code()).isEqualTo("00123456");
        assertThat(received.codeSystem()).isEqualTo("DIN");
        assertThat(received.severity()).isEqualTo("LT");
        assertThat(received.recorded().original()).isEqualTo("2024-02-29T12:34:56.1200-05:00");
        assertThat(received.reaction()).isEqualTo("Current café 😀 reaction");
        assertThat(received.notes()).isEqualTo("Source-only provider note");
    }

    @Test
    void shouldKeepAnExplicitlyClearedNativeReactionCleared_withoutResurrectingTheRetainedValue()
            throws Exception {
        var fields = baseline();
        fields.put("reaction", null);
        var result = reconcile(fixture(fields, true, source()), List.of());
        assertThat(result.mapping().nativeRowRepresentable()).isTrue();
        assertThat(result.mapping().value().reaction()).isNull();
        assertThat(result.currentPaths()).contains("Reaction");
    }

    @Test
    void shouldRefuseAnUnprovenChangedSeverity_andAChangedDateWhoseOriginalClockCannotBeReconciled()
            throws Exception {
        var fields = baseline();
        fields.put("severity_of_reaction", "3");
        var old = source();
        var moderate =
                new CdsAllergyDto(
                        old.description(),
                        old.property(),
                        old.code(),
                        old.codeSystem(),
                        old.reactionType(),
                        old.reaction(),
                        "MO",
                        old.lifeStage(),
                        old.notes(),
                        old.start(),
                        old.recorded());
        // The sealed preparation contradicts the original native projection; matching current
        // severity cannot qualify it.
        assertThat(
                        reconcile(fixture(fields, true, moderate), List.of())
                                .mapping()
                                .nativeRowRepresentable())
                .isFalse();
        var previous = baseline();
        previous.put("severity_of_reaction", "2");
        var changedSeverity = reconcile(fixture(fields, true, moderate, previous), List.of());
        assertThat(changedSeverity.mapping().nativeRowRepresentable()).isFalse();
        assertThat(changedSeverity.mapping().fields().get(key("severity_of_reaction")).rule())
                .isEqualTo("SEVERE_IS_NOT_EVIDENCE_OF_LIFE_THREATENING");
        fields = baseline();
        fields.put("entry_date", "2024-03-01");
        var result = reconcile(fixture(fields, true, source()), List.of());
        assertThat(result.mapping().nativeRowRepresentable()).isFalse();
        assertThat(result.mapping().fields().get(key("entry_date")).rule())
                .isEqualTo("UNPROJECTED_CLOCK_OR_OFFSET_REQUIRES_RECONCILIATION");
    }

    @Test
    void shouldUseAnExactUnchangedLegacyReceipt_withoutPersistingOrInventingAChangedFieldBaseline()
            throws Exception {
        var unchanged = fixture(baseline(), false, source());
        assertThat(reconcile(unchanged, List.of()).mapping().nativeRowRepresentable()).isTrue();
        assertThat(
                        unchanged
                                .index
                                .tables()
                                .get("cds_import_unit")
                                .values()
                                .iterator()
                                .next()
                                .fields()
                                .get("native_baseline"))
                .isNull();
        var fields = baseline();
        fields.put("reaction", "Changed after legacy import");
        assertThatThrownBy(() -> reconcile(fixture(fields, false, source()), List.of()))
                .hasMessage("CDS_EXPORT_ALLERGY_FIELD_BASELINE_REQUIRED");
    }

    @Test
    void
            shouldRequireReviewForNewPrecisionRows_insteadOfRestoringTheOriginalPrecisionOrInventingAFullDate()
                    throws Exception {
        var precision =
                new CdsExportCapturedTextRowDto(
                        "partial_date",
                        CdsExportPatientTableSourceReader.key(11),
                        Map.of(
                                "id",
                                "11",
                                "table_name",
                                "1",
                                "table_id",
                                "51",
                                "field_name",
                                "1",
                                "format",
                                "YYYY"));
        var result = reconcile(fixture(baseline(), true, source()), List.of(precision));
        assertThat(result.mapping().nativeRowRepresentable()).isFalse();
        assertThat(result.mapping().fields().get(key("start_date")).rule())
                .isEqualTo("CHANGED_PRECISION_DEPENDENCY_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseDeletedRowsAndAliases_beforeReturningARepresentation() throws Exception {
        var f = fixture(baseline(), true, source());
        var ref = f.checked.units().get(ADDRESS).getFirst();
        var deleted =
                new CdsExportRetainedReceiptVerificationDto(
                        SOURCE,
                        Map.of(
                                ADDRESS,
                                List.of(
                                        new CheckedReference(
                                                ref.expected(), null, Reason.ROW_NOT_IN_CAPTURE))),
                        Set.of(),
                        Set.of(ADDRESS));
        assertThatThrownBy(
                        () ->
                                new CdsExportAllergyFieldReconciler()
                                        .reconcile(
                                                27, f.index, ADDRESS, deleted, null, null,
                                                List.of()))
                .hasMessage("CDS_EXPORT_ALLERGY_DELETION_REQUIRES_REVIEW");
        String alias = RUN + ":" + "f".repeat(64);
        var refs = new LinkedHashMap<>(f.index.references());
        refs.put(alias, refs.get(ADDRESS));
        var index =
                new CdsExportRetainedIndexDto(
                        SOURCE, 3, 4096, f.index.tables(), Set.of(ADDRESS, alias), refs, Map.of());
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        SOURCE,
                        Map.of(ADDRESS, List.of(ref), alias, List.of(ref)),
                        Set.of(ADDRESS, alias),
                        Set.of());
        assertThatThrownBy(
                        () ->
                                new CdsExportAllergyFieldReconciler()
                                        .reconcile(
                                                27, index, ADDRESS, checked, f.row, f.witness,
                                                List.of()))
                .hasMessage("CDS_EXPORT_ALLERGY_ALIAS_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseSubstitutedRowsOrMismatchedCapturedGenerations_beforeMakingFieldDecisions()
            throws Exception {
        var f = fixture(baseline(), true, source());
        var values = new LinkedHashMap<>(f.row.fields());
        values.put("reaction", "Substituted");
        assertThatThrownBy(
                        () ->
                                new CdsExportAllergyFieldReconciler()
                                        .reconcile(
                                                27,
                                                f.index,
                                                ADDRESS,
                                                f.checked,
                                                new CdsExportCapturedTextRowDto(
                                                        "allergies", f.row.recordKey(), values),
                                                f.witness,
                                                List.of()))
                .hasMessage("CDS_EXPORT_ALLERGY_RECONCILIATION_SCOPE_INVALID");
        var other =
                new CdsExportRetainedReceiptVerificationDto(
                        "f".repeat(64),
                        f.checked.units(),
                        f.checked.unchangedUnits(),
                        f.checked.reviewUnits());
        assertThatThrownBy(
                        () ->
                                new CdsExportAllergyFieldReconciler()
                                        .reconcile(
                                                27, f.index, ADDRESS, other, f.row, f.witness,
                                                List.of()))
                .hasMessage("CDS_EXPORT_ALLERGY_RECONCILIATION_SCOPE_INVALID");
    }

    record Fixture(
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportCapturedTextRowDto row,
            CdsExportNativeRowReceiptDto witness) {}

    @Test
    void shouldVerifyBothAtcProjectionVersions_fromTheirOriginalSealedPreparation()
            throws Exception {
        var original = source();
        for (boolean marker : List.of(false, true)) {
            var atcSource =
                    new CdsAllergyDto(
                            original.description(),
                            original.property(),
                            "B01AB01",
                            "ATC",
                            original.reactionType(),
                            original.reaction(),
                            original.severity(),
                            original.lifeStage(),
                            original.notes(),
                            original.start(),
                            original.recorded(),
                            false,
                            null,
                            null,
                            marker);
            var fields = baseline();
            fields.put("regional_identifier", marker ? null : "B01AB01");
            fields.put("atc", marker ? "B01AB01" : null);
            fields.put("TYPECODE", marker ? "8" : "0");
            var result = reconcile(fixture(fields, true, atcSource, fields), List.of());
            assertThat(result.mapping().nativeRowRepresentable()).isTrue();
            assertThat(result.mapping().value().codeType()).isEqualTo("ATC");
            var received = roundTrip(result.mapping().value());
            assertThat(received.code()).isEqualTo("B01AB01");
            assertThat(received.atcResidual()).isTrue();
        }
    }

    private CdsExportAllergyFieldReconciler.Result reconcile(
            Fixture f, List<CdsExportCapturedTextRowDto> precision) throws Exception {
        return new CdsExportAllergyFieldReconciler()
                .reconcile(27, f.index, ADDRESS, f.checked, f.row, f.witness, precision);
    }

    Fixture fixture(Map<String, String> current, boolean baseline, CdsAllergyDto source)
            throws Exception {
        return fixture(current, baseline, source, baseline());
    }

    Fixture fixture(
            Map<String, String> current,
            boolean baseline,
            CdsAllergyDto source,
            Map<String, String> previous)
            throws Exception {
        var old = state(previous);
        var now = state(current);
        String id = CdsImportKeys.hash(RUN, UNIT),
                sourceDigest = "c".repeat(64),
                options = "d".repeat(64),
                ruleset = "e".repeat(64);
        var ref = new CdsExportRetainedIndexDto.Reference("allergies", "51", old.digest());
        String references = JSON.writeValueAsString(Map.of("protocol", 2, "refs", List.of(ref))),
                target = sha(references),
                payload = JSON.writeValueAsString(source);
        var unit =
                new LinkedHashMap<String, String>(
                        Map.of(
                                "id",
                                id,
                                "run_id",
                                RUN,
                                "unit_key",
                                UNIT,
                                "category",
                                "AllergiesAndAdverseReactions",
                                "state",
                                "COMMITTED",
                                "source_digest",
                                sourceDigest,
                                "target_references",
                                references,
                                "target_digest",
                                target));
        unit.put(
                "native_baseline",
                baseline ? CdsNativeBaselineCodec.encode(references, target, List.of(old)) : null);
        var prep =
                Map.of(
                        "id",
                        id,
                        "run_id",
                        RUN,
                        "unit_key",
                        UNIT,
                        "source_digest",
                        sourceDigest,
                        "options_digest",
                        options,
                        "ruleset_digest",
                        ruleset,
                        "payload_type",
                        CdsAllergyDto.class.getName(),
                        "payload",
                        payload,
                        "digest",
                        CdsPreparationSeal.digest(
                                CdsAllergyDto.class.getName(),
                                sourceDigest,
                                options,
                                ruleset,
                                payload));
        var tables =
                Map.of(
                        "cds_import_unit",
                        Map.of(id, new CdsExportCapturedTextRowDto("cds_import_unit", id, unit)),
                        "cds_import_preparation",
                        Map.of(
                                id,
                                new CdsExportCapturedTextRowDto(
                                        "cds_import_preparation", id, prep)),
                        "cds_import_run",
                        Map.of(
                                RUN,
                                new CdsExportCapturedTextRowDto(
                                        "cds_import_run",
                                        RUN,
                                        Map.of(
                                                "id",
                                                RUN,
                                                "options_digest",
                                                options,
                                                "ruleset_digest",
                                                ruleset))));
        var index =
                new CdsExportRetainedIndexDto(
                        SOURCE,
                        3,
                        4096,
                        tables,
                        Set.of(ADDRESS),
                        Map.of(ADDRESS, List.of(ref)),
                        Map.of());
        boolean same = old.digest().equals(now.digest());
        var checkedRef =
                new CheckedReference(
                        ref,
                        now.digest(),
                        same ? Reason.UNCHANGED_ROW : Reason.CHANGED_ROW,
                        CdsNativeRowComparison.compare(old, now));
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        SOURCE,
                        Map.of(ADDRESS, List.of(checkedRef)),
                        same ? Set.of(ADDRESS) : Set.of(),
                        same ? Set.of() : Set.of(ADDRESS));
        var row =
                new CdsExportCapturedTextRowDto(
                        "allergies", CdsExportPatientTableSourceReader.key(51), current);
        return new Fixture(
                index,
                checked,
                row,
                new CdsExportNativeRowReceiptDto(
                        "allergies", "51", row.recordKey(), now.digest(), now));
    }

    static Map<String, String> baseline() {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.ALLERGIES.columns().forEach(c -> fields.put(c, null));
        fields.putAll(
                Map.of(
                        "allergyid",
                        "51",
                        "demographic_no",
                        "27",
                        "DESCRIPTION",
                        "Original agent",
                        "nonDrug",
                        "0",
                        "TYPECODE",
                        "0",
                        "regional_identifier",
                        "00123456",
                        "severity_of_reaction",
                        "3",
                        "reaction_type",
                        "AL",
                        "reaction",
                        "Original reaction",
                        "life_stage",
                        "A"));
        fields.putAll(
                Map.of(
                        "archived",
                        "0",
                        "start_date",
                        "2020-02-29",
                        "entry_date",
                        "2024-02-29",
                        "position",
                        "0",
                        "lastUpdateDate",
                        "2024-02-29 01:02:03"));
        return fields;
    }

    static CdsAllergyDto source() {
        return new CdsAllergyDto(
                "Original agent",
                "DR",
                "00123456",
                "DIN",
                "AL",
                "Original reaction",
                "LT",
                "A",
                "Source-only provider note",
                CdsTemporal.parse(null, null, "2020-02-29", null, "Date"),
                CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.1200-05:00", "Date"));
    }

    private static CdsNativeRowState state(Map<String, String> values) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var column : CdsExportPatientTableReadSupport.Plan.ALLERGIES.columns()) {
            columns.add(new CdsNativeRowDigest.Column(column, "VARCHAR", 128, 0));
            String value = values.get(column);
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    value.getBytes(StandardCharsets.UTF_8).length,
                                    sha(value)));
        }
        return new CdsNativeRowState("allergies", "51", columns, cells);
    }

    private static CdsExportNativeFieldKeyDto key(String field) {
        return new CdsExportNativeFieldKeyDto(
                "allergies", CdsExportPatientTableSourceReader.key(51), field);
    }

    private static String sha(String value) {
        return HexFormat.of()
                .formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private CdsAllergyDto roundTrip(CdsExportAllergyDto value) throws Exception {
        var patient = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(c -> patient.put(c, null));
        patient.putAll(
                Map.of(
                        "first_name",
                        "Synthetic",
                        "last_name",
                        "Fixture",
                        "year_of_birth",
                        "1980",
                        "month_of_birth",
                        "01",
                        "date_of_birth",
                        "01",
                        "patient_status",
                        "AC",
                        "sex",
                        "U"));
        var demographics =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var selected =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        var sections =
                List.of(
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                                xml ->
                                        new CdsExportDemographicXmlWriter()
                                                .write(demographics, xml)),
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64),
                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                        "c".repeat(64)),
                                xml -> new CdsExportAllergyXmlWriter().write(value, xml)));
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "d".repeat(64),
                        "a".repeat(64),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        sections.iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var file = Files.write(directory.resolve("patient.xml"), output.toByteArray());
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        file,
                        java.nio.file.Files.createTempDirectory(directory, "receiving-")
                                .resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(records.get(1));
    }
}
