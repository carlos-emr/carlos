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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.nio.file.*;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** Multi-row projection/alias/deletion/mutation counterexamples; actual driver receipts are covered by the installed proof. */
class CdsExportImmunizationFieldReconcilerUnitTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    private static final String SOURCE = "a".repeat(64),
            RUN = "123e4567-e89b-12d3-a456-426614174000",
            UNIT = "f".repeat(64),
            ADDRESS = RUN + ":" + UNIT;
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(
                            com.fasterxml.jackson.databind.SerializationFeature
                                    .WRITE_DATES_AS_TIMESTAMPS);
    private final CdsExportImmunizationFieldReconciler reconciler =
            new CdsExportImmunizationFieldReconciler();
    private CdsImmunizationDto source;
    private CdsExportPreventionClassifier definitions;
    private Map<String, String> oldRoot, currentRoot;
    private final Map<Integer, Map<String, String>> oldExtras = new LinkedHashMap<>(),
            currentExtras = new LinkedHashMap<>(),
            oldDates = new LinkedHashMap<>(),
            currentDates = new LinkedHashMap<>();
    private CdsExportRetainedIndexDto index;
    private CdsExportRetainedReceiptVerificationDto checked;
    private List<CdsExportNativeRowReceiptDto> witnesses;
    private boolean withBaseline = true;

    @BeforeEach
    void prepare() throws Exception {
        prepare(CdsTemporal.parse(null, null, "2024-02-29", null, "Date"));
    }

    private void prepare(CdsTemporalDto date) throws Exception {
        source =
                new CdsImmunizationDto(
                        "Original café vaccine",
                        "Td",
                        "Original maker",
                        null,
                        "IM",
                        "Left arm",
                        "0.5 mL",
                        "00123456",
                        "DIN",
                        false,
                        "Original instructions",
                        "Original notes",
                        date);
        definitions =
                CdsExportPreventionClassifier.fromCaptured(
                        SOURCE,
                        "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='HB' layout='injection' healthCanadaType='HB'/></preventions>"
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
        oldRoot = new LinkedHashMap<>();
        CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL
                .columns()
                .forEach(column -> oldRoot.put(column, null));
        oldRoot.putAll(
                Map.of(
                        "id",
                        "17",
                        "demographic_no",
                        "27",
                        "prevention_type",
                        "Td",
                        "provider_no",
                        "",
                        "deleted",
                        "0",
                        "never",
                        "0",
                        "refused",
                        "0",
                        "creator",
                        "AUDIT_ACTOR"));
        var expected = CdsExportImmunizationDateProjection.expected(date);
        oldRoot.put(
                "prevention_date",
                expected.kind() == CdsExportImmunizationDateProjection.Kind.HISTORIC_ZONE_REQUIRED
                        ? "2024-02-29 12:34:56"
                        : expected.nativeAnchor());
        currentRoot = new LinkedHashMap<>(oldRoot);
        oldExtras.clear();
        currentExtras.clear();
        oldDates.clear();
        currentDates.clear();
        int id = 30;
        for (var entry :
                CdsExportImmunizationProjectionEvidence.expectedExtensions(source).entrySet())
            if (entry.getValue() != null) {
                var row = extra(id++, entry.getKey(), entry.getValue());
                oldExtras.put(Integer.parseInt(row.get("id")), row);
                currentExtras.put(Integer.parseInt(row.get("id")), new LinkedHashMap<>(row));
            }
        if (date.legacyPartialFormat() != null) {
            var row =
                    new LinkedHashMap<String, String>(
                            Map.of(
                                    "id",
                                    "70",
                                    "table_id",
                                    "17",
                                    "table_name",
                                    "4",
                                    "field_name",
                                    "7",
                                    "format",
                                    date.legacyPartialFormat()));
            oldDates.put(70, row);
            currentDates.put(70, new LinkedHashMap<>(row));
        }
    }

    private CdsExportImmunizationFieldReconciler.Result reconcile() throws Exception {
        build();
        return invoke();
    }

    private CdsExportImmunizationFieldReconciler.Result invoke() throws Exception {
        return reconciler.reconcile(
                27,
                index,
                ADDRESS,
                checked,
                row("preventions", currentRoot),
                currentExtras.values().stream().map(value -> row("preventionsExt", value)).toList(),
                currentDates.values().stream()
                        .map(value -> row("prevention_precision", value))
                        .toList(),
                witnesses,
                definitions);
    }

    @Test
    void unchangedVerifiedProjectionUsesPreparedMeaningAndAccountsForEveryNativeField()
            throws Exception {
        var result = reconcile();
        assertThat(result.mapping().nativeRowRepresentable()).isTrue();
        assertThat(result.mapping().value())
                .isEqualTo(new CdsExportPreparedImmunizationMapper().map(source));
        assertThat(result.mapping().fields()).hasSize(16 + 4 * oldExtras.size());
        assertThat(result.preparedPaths())
                .contains("ImmunizationName", "Date", "Notes", "ImmunizationCode");
    }

    @Test
    void anEditedCommentChangesOnlyNotesAndKeepsOtherProvenOriginalFields() throws Exception {
        edit("comments").put("val", "Current café 😀\r\nnotes");
        var result = reconcile();
        assertThat(result.mapping().value().notes()).isEqualTo("Current café 😀\r\nnotes");
        assertThat(result.mapping().value().name()).isEqualTo(source.name());
        assertThat(result.mapping().value().date().lexical()).isEqualTo("2024-02-29");
        assertThat(result.currentPaths()).containsExactly("Notes");
    }

    @Test
    void clearedAndEmptyCommentsDoNotResurrectOldNotes() throws Exception {
        for (String text : Arrays.asList(null, "")) {
            edit("comments").put("val", text);
            var result = reconcile();
            assertThat(result.mapping().value().notes()).isEqualTo(text);
            assertThat(result.currentPaths()).contains("Notes");
        }
    }

    @Test
    void changedNativeNameNeverReplaysTheOldImportedName() throws Exception {
        edit("cdsName").put("val", "Current brand");
        assertThat(reconcile().mapping().value().name()).isEqualTo("Current brand");
    }

    @Test
    void changingRefusalDoesNotChangeUnrelatedPreparedText() throws Exception {
        currentRoot.put("refused", "1");
        var result = reconcile();
        assertThat(result.mapping().value().refused()).isTrue();
        assertThat(result.mapping().value().notes()).isEqualTo(source.notes());
        assertThat(result.currentPaths()).contains("RefusedFlag");
    }

    @Test
    void anUnchangedDateShiftIsNotUndoneByTheOriginalDateHint() throws Exception {
        prepare(
                CdsTemporal.shift(
                        CdsTemporal.parse(null, null, "2024-02-29", null, "Date"), 1, "Date"));
        var result = reconcile();
        assertThat(result.mapping().value().date().lexical()).isEqualTo("2024-03-01");
        var hint = edit("cdsDateOriginal");
        var field =
                new CdsExportNativeFieldKeyDto(
                        "preventionsExt",
                        CdsExportPatientTableSourceReader.key(Integer.parseInt(hint.get("id"))),
                        "val");
        assertThat(result.mapping().fields().get(field).outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
    }

    @Test
    void anEditedUnrelatedCommentKeepsAProvenOriginalFractionalClock() throws Exception {
        prepare(CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.1200", "Date"));
        edit("comments").put("val", "Current note");
        var result = reconcile();
        assertThat(result.mapping().value().date().lexical()).isEqualTo("2024-02-29T12:34:56.1200");
        assertThat(result.mapping().value().notes()).isEqualTo("Current note");
    }

    @Test
    void editedClockCannotSilentlyDiscardAnOriginalFraction() throws Exception {
        prepare(CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.1200", "Date"));
        currentRoot.put("prevention_date", "2024-03-01 12:34:57");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void historicOffsetCannotBeReconstructedFromTheCurrentJvmZone() throws Exception {
        prepare(CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.1200-05:00", "Date"));
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void yearAndMonthStayPartialInsteadOfReplayingAnchorDayAndClock() throws Exception {
        for (CdsTemporalDto date :
                List.of(
                        CdsTemporal.parse("2024", null, null, null, "Date"),
                        CdsTemporal.parse(null, "2024-02", null, null, "Date"))) {
            prepare(date);
            var result = reconcile();
            assertThat(result.mapping().value().date())
                    .isEqualTo(new CdsExportPreparedTemporalMapper().map(date, true));
            assertThat(result.mapping().fields()).hasSize(16 + 4 * oldExtras.size() + 5);
        }
    }

    @Test
    void changedPrecisionRemainsReviewInsteadOfChoosingTheAnchor() throws Exception {
        prepare(CdsTemporal.parse("2024", null, null, null, "Date"));
        currentDates.get(70).put("format", "YYYY-MM");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void deletingAnOriginalNativeChildRequiresReviewRatherThanAnEmptySuccess() {
        currentExtras.remove(Integer.parseInt(edit("comments").get("id")));
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_DELETION_REQUIRES_REVIEW");
    }

    @Test
    void changingAnExtensionKeyCannotMoveAnOldCommentIntoAnotherStandardField() throws Exception {
        edit("comments").put("keyval", "instructions");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void duplicateCurrentKeysRetainReviewInsteadOfFirstLastOrConcatenation() throws Exception {
        currentExtras.put(90, extra(90, "comments", "A second comment"));
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void aNewExplicitDinMustNotBeOverwrittenWithAnAbsentOldCode() throws Exception {
        source =
                new CdsImmunizationDto(
                        source.name(),
                        source.type(),
                        source.manufacturer(),
                        source.lot(),
                        source.route(),
                        source.site(),
                        source.dose(),
                        null,
                        null,
                        source.refused(),
                        source.instructions(),
                        source.notes(),
                        source.date());
        for (String key : List.of("cdsCode", "cdsCodeSystem")) {
            int id = Integer.parseInt(edit(key).get("id"));
            oldExtras.remove(id);
            currentExtras.remove(id);
        }
        currentExtras.put(90, extra(90, "din", "00987654"));
        assertThat(reconcile().mapping().value().code()).isEqualTo("00987654");
    }

    @Test
    void anAdditionalDinCannotSilentlyReplaceOrDisappearBehindAnExistingImportedDin()
            throws Exception {
        currentExtras.put(90, extra(90, "din", "00987654"));
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void aChangedClinicalProviderOrUnsupportedStatusCannotBeClearedByUnchangedPreparedFields()
            throws Exception {
        currentRoot.put("provider_name", "Recorded administering clinician");
        assertThat(reconcile().mapping().value()).isNull();
        currentRoot.put("provider_name", null);
        currentRoot.put("refused", "3");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void aChangedNativeTypeCannotKeepAnOldContradictoryPreparedType() throws Exception {
        currentRoot.put("prevention_type", "HB");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void missingFieldBaselinesAllowOnlyActuallyUnchangedQualifiedLegacyReceipts() throws Exception {
        withBaseline = false;
        assertThat(reconcile().mapping().value()).isNotNull();
        edit("comments").put("val", "Changed");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_FIELD_BASELINE_REQUIRED");
    }

    @Test
    void aPreparedValueThatNeverProjectedIntoItsClaimedBaselineIsReview() throws Exception {
        oldExtras
                .get(Integer.parseInt(edit("comments").get("id")))
                .put("val", "Different baseline text");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void unknownAddedClinicalExtensionsRemainExplicitlyUnmapped() throws Exception {
        currentExtras.put(90, extra(90, "customClinicalField", "Exact value"));
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void aForeignCatalogueGenerationCannotSupplyOtherwiseIdenticalTypeMeaning() throws Exception {
        build();
        definitions =
                CdsExportPreventionClassifier.fromCaptured(
                        "b".repeat(64),
                        "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/></preventions>"
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_RECONCILIATION_SCOPE_INVALID");
    }

    @Test
    void aForeignReceiptGenerationCannotBeRelabelledAsTheCurrentCapture() throws Exception {
        build();
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        "b".repeat(64),
                        checked.units(),
                        checked.unchangedUnits(),
                        checked.reviewUnits());
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_RECONCILIATION_SCOPE_INVALID");
    }

    @Test
    void newOrdinaryOptionalFieldsMustNotDisappearBehindAbsentPreparedValues() throws Exception {
        source =
                new CdsImmunizationDto(
                        source.name(),
                        source.type(),
                        null,
                        source.lot(),
                        source.route(),
                        source.site(),
                        source.dose(),
                        source.code(),
                        source.codeSystem(),
                        source.refused(),
                        source.instructions(),
                        source.notes(),
                        source.date());
        int id = Integer.parseInt(edit("manufacture").get("id"));
        oldExtras.remove(id);
        currentExtras.remove(id);
        currentExtras.put(90, extra(90, "manufacture", "New recorded maker"));
        assertThat(reconcile().mapping().value().manufacturer()).isEqualTo("New recorded maker");
    }

    @Test
    void unknownDateDoesNotBecomeTheNativeCreationOrModificationClock() throws Exception {
        prepare(CdsTemporalDto.unknown());
        oldRoot.put("creation_date", "2020-01-01 12:34:56");
        oldRoot.put("lastUpdateDate", "2026-01-01 12:34:56");
        currentRoot = new LinkedHashMap<>(oldRoot);
        assertThat(reconcile().mapping().value().date().precision())
                .isEqualTo(CdsExportDateDto.Precision.ABSENT);
    }

    @Test
    void numericTimestampTransportCannotPretendItPreservedAnOriginalOffset() throws Exception {
        prepare(CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.1200-05:00", "Date"));
        build();
        String id = CdsImportKeys.hash(RUN, UNIT);
        var tables = new TreeMap<String, Map<String, CdsExportCapturedTextRowDto>>(index.tables());
        var prior = tables.get("cds_import_preparation").get(id);
        var fields = new TreeMap<>(prior.fields());
        String numeric =
                new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(source);
        fields.put("payload", numeric);
        fields.put(
                "digest",
                CdsPreparationSeal.digest(
                        CdsImmunizationDto.class.getName(),
                        fields.get("source_digest"),
                        fields.get("options_digest"),
                        fields.get("ruleset_digest"),
                        numeric));
        tables.put(
                "cds_import_preparation",
                Map.of(id, new CdsExportCapturedTextRowDto("cds_import_preparation", id, fields)));
        index =
                new CdsExportRetainedIndexDto(
                        index.sourceManifestSha256(),
                        index.metadataRows(),
                        index.metadataBytes(),
                        tables,
                        index.appliedUnits(),
                        index.references(),
                        index.nativeParents());
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_PREPARED_TEMPORAL_REQUIRES_REVIEW");
    }

    @Test
    void reconciledCurrentNotesAndOriginalFractionReachTheActualReceivingParser() throws Exception {
        prepare(CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.1200", "Date"));
        edit("comments").put("val", "Current café 😀\r\nnotes");
        var value = reconcile().mapping().value();
        for (var profile :
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile.values()) {
            var received = roundTrip(value, profile);
            assertThat(received.notes()).isEqualTo("Current café 😀\r\nnotes");
            assertThat(received.date().original()).isEqualTo("2024-02-29T12:34:56.1200");
            assertThat(received.name()).isEqualTo(source.name());
            assertThat(received.code()).isEqualTo(source.code());
        }
    }

    @Test
    void reconciledDateShiftIsAppliedExactlyOnceAcrossExportAndReceivingNormalization()
            throws Exception {
        prepare(
                CdsTemporal.shift(
                        CdsTemporal.parse(null, null, "2024-02-29", null, "Date"), 1, "Date"));
        edit("comments").put("val", "");
        var received =
                roundTrip(
                        reconcile().mapping().value(),
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile
                                .ONTARIO_MD_5_2);
        assertThat(received.date().original()).isEqualTo("2024-03-01");
        assertThat(received.date().earliest()).isEqualTo(source.date().earliest());
        assertThat(received.date().appliedShiftDays()).isZero();
        assertThat(received.notes()).isEmpty();
    }

    @Test
    void aNewDinSurvivesReconciliationSerializationAndReceivingImport() throws Exception {
        source =
                new CdsImmunizationDto(
                        source.name(),
                        source.type(),
                        source.manufacturer(),
                        source.lot(),
                        source.route(),
                        source.site(),
                        source.dose(),
                        null,
                        null,
                        source.refused(),
                        source.instructions(),
                        source.notes(),
                        source.date());
        for (String key : List.of("cdsCode", "cdsCodeSystem")) {
            int id = Integer.parseInt(edit(key).get("id"));
            oldExtras.remove(id);
            currentExtras.remove(id);
        }
        currentExtras.put(90, extra(90, "din", "00987654"));
        var received =
                roundTrip(
                        reconcile().mapping().value(),
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile
                                .ONTARIO_MD_5_2);
        assertThat(received.code()).isEqualTo("00987654");
        assertThat(received.codeSystem()).isEqualTo("DIN");
    }

    @Test
    void rowsEditedAfterReceiptVerificationCannotBorrowTheEarlierWitnesses() throws Exception {
        build();
        edit("comments").put("val", "Edited after receipt verification");
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
    }

    @Test
    void aSecondAppliedUnitCannotClaimTheSameNativeParentWithoutAliasReview() throws Exception {
        build();
        String other = RUN + ":" + "e".repeat(64);
        var references = new TreeMap<>(index.references());
        references.put(other, references.get(ADDRESS));
        var units = new HashSet<>(index.appliedUnits());
        units.add(other);
        index =
                new CdsExportRetainedIndexDto(
                        index.sourceManifestSha256(),
                        index.metadataRows(),
                        index.metadataBytes(),
                        index.tables(),
                        units,
                        references,
                        index.nativeParents());
        var checkedUnits = new TreeMap<>(checked.units());
        checkedUnits.put(other, checkedUnits.get(ADDRESS));
        var unchanged = new HashSet<>(checked.unchangedUnits());
        unchanged.add(other);
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        checked.sourceManifestSha256(),
                        checkedUnits,
                        unchanged,
                        checked.reviewUnits());
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_ALIAS_REQUIRES_REVIEW");
    }

    private CdsImmunizationDto roundTrip(
            CdsExportImmunizationDto value,
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile profile)
            throws Exception {
        var patient = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
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
        var demographic =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var selected =
                Set.of(
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                .DEMOGRAPHICS,
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                .IMMUNIZATIONS);
        var sections =
                List.of(
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64),
                                        io.github.carlos_emr.carlos.demographic.cds.contract
                                                .CdsCategory.DEMOGRAPHICS,
                                        "b".repeat(64)),
                                xml -> new CdsExportDemographicXmlWriter().write(demographic, xml)),
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64),
                                        io.github.carlos_emr.carlos.demographic.cds.contract
                                                .CdsCategory.IMMUNIZATIONS,
                                        "c".repeat(64)),
                                xml -> new CdsExportImmunizationXmlWriter().write(value, xml)));
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "d".repeat(64),
                        "a".repeat(64),
                        "2026.08.0-test",
                        profile,
                        selected,
                        sections.iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        profile,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var input =
                Files.write(directory.resolve(UUID.randomUUID() + ".xml"), output.toByteArray());
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        input,
                        directory.resolve(UUID.randomUUID().toString()),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return new CdsClinicalNormalizationService(new CdsFieldParser())
                .immunization(records.get(1));
    }

    record Fixture(
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportImmunizationFieldReconciler.Result decision,
            CdsImmunizationDto source,
            CdsExportCapturedTextRowDto parent,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> precision,
            List<CdsExportNativeRowReceiptDto> witnesses,
            CdsExportPreventionClassifier definitions) {}

    Fixture fixture(CdsTemporalDto date, String currentNotes) throws Exception {
        prepare(date);
        edit("comments").put("val", currentNotes);
        var result = reconcile();
        return new Fixture(
                index,
                checked,
                result,
                source,
                row("preventions", currentRoot),
                currentExtras.values().stream().map(value -> row("preventionsExt", value)).toList(),
                currentDates.values().stream()
                        .map(value -> row("prevention_precision", value))
                        .toList(),
                witnesses,
                definitions);
    }

    private Map<String, String> edit(String key) {
        return currentExtras.values().stream()
                .filter(row -> key.equals(row.get("keyval")))
                .findFirst()
                .orElseThrow();
    }

    private void build() throws Exception {
        var oldStates = new ArrayList<CdsNativeRowState>();
        oldStates.add(state("preventions", oldRoot));
        oldExtras.values().forEach(value -> oldStates.add(state("preventionsExt", value)));
        oldDates.values().forEach(value -> oldStates.add(state("partial_date", value)));
        var current = new LinkedHashMap<String, CdsNativeRowState>();
        var states = new ArrayList<CdsNativeRowState>();
        states.add(state("preventions", currentRoot));
        currentExtras.values().forEach(value -> states.add(state("preventionsExt", value)));
        currentDates.values().forEach(value -> states.add(state("partial_date", value)));
        states.forEach(value -> current.put(value.table() + "/" + value.id(), value));
        var refs =
                oldStates.stream()
                        .map(
                                value ->
                                        new CdsExportRetainedIndexDto.Reference(
                                                value.table(), value.id(), value.digest()))
                        .toList();
        String references = JSON.writeValueAsString(Map.of("protocol", 2, "refs", refs)),
                target = sha(references),
                payload = JSON.writeValueAsString(source),
                sourceDigest = "c".repeat(64),
                options = "d".repeat(64),
                ruleset = "e".repeat(64),
                id = CdsImportKeys.hash(RUN, UNIT);
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
                                "Immunizations",
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
                withBaseline ? CdsNativeBaselineCodec.encode(references, target, oldStates) : null);
        var preparation =
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
                        CdsImmunizationDto.class.getName(),
                        "payload",
                        payload,
                        "digest",
                        CdsPreparationSeal.digest(
                                CdsImmunizationDto.class.getName(),
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
                                        "cds_import_preparation", id, preparation)),
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
        index =
                new CdsExportRetainedIndexDto(
                        SOURCE, 3, 4096, tables, Set.of(ADDRESS), Map.of(ADDRESS, refs), Map.of());
        var comparisons = new ArrayList<CheckedReference>();
        boolean same = true;
        for (int i = 0; i < refs.size(); i++) {
            var ref = refs.get(i);
            var now = current.get(ref.table() + "/" + ref.id());
            if (now == null) {
                comparisons.add(new CheckedReference(ref, null, Reason.ROW_NOT_IN_CAPTURE));
                same = false;
            } else {
                boolean equal = ref.digest().equals(now.digest());
                same &= equal;
                comparisons.add(
                        new CheckedReference(
                                ref,
                                now.digest(),
                                equal ? Reason.UNCHANGED_ROW : Reason.CHANGED_ROW,
                                CdsNativeRowComparison.compare(oldStates.get(i), now)));
            }
        }
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        SOURCE,
                        Map.of(ADDRESS, comparisons),
                        same ? Set.of(ADDRESS) : Set.of(),
                        same ? Set.of() : Set.of(ADDRESS));
        witnesses =
                states.stream()
                        .map(
                                value ->
                                        new CdsExportNativeRowReceiptDto(
                                                value.table(),
                                                value.id(),
                                                CdsExportPatientTableSourceReader.key(
                                                        Integer.parseInt(value.id())),
                                                value.digest(),
                                                value))
                        .toList();
    }

    private static Map<String, String> extra(int id, String key, String value) {
        var row = new LinkedHashMap<String, String>();
        row.put("id", Integer.toString(id));
        row.put("prevention_id", "17");
        row.put("keyval", key);
        row.put("val", value);
        return row;
    }

    private static CdsExportCapturedTextRowDto row(String table, Map<String, String> value) {
        return new CdsExportCapturedTextRowDto(
                table,
                CdsExportPatientTableSourceReader.key(Integer.parseInt(value.get("id"))),
                value);
    }

    private static CdsNativeRowState state(String table, Map<String, String> value) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : new TreeMap<>(value).entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
            String text = entry.getValue();
            cells.add(
                    new CdsNativeRowState.Cell(
                            text == null,
                            text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length,
                            text == null ? null : sha(text)));
        }
        return new CdsNativeRowState(table, value.get("id"), columns, cells);
    }

    private static String sha(String value) {
        return HexFormat.of()
                .formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
