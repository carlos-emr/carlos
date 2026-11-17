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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicProjectionEvidenceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            PATIENT = CdsImportKeys.hash("fixture", "old27", "default"),
            UNIT =
                    CdsImportKeys.hash(
                            PATIENT, "/OmdCds[1]/PatientRecord[1]/Demographics[1]", "NATIVE", "0"),
            ADDRESS = RUN + ":" + UNIT,
            HASH = "a".repeat(64);
    private final Map<String, String> old = new LinkedHashMap<>(), current = new LinkedHashMap<>();
    private CdsDemographicNativeDto source;
    private CdsExportRetainedIndexDto index;
    private CdsExportRetainedReceiptVerificationDto checked;
    private boolean baseline = true, newSchema;

    @BeforeEach
    void setup() {
        var scalars =
                Map.of(
                        CdsDemographicNativeDto.MIDDLE_NAMES_SCALAR,
                        "Source Middle",
                        "HealthCard/Version",
                        "AB",
                        "ChartNumber",
                        "C27",
                        "Email",
                        "old@example.test",
                        "Names/NamePrefix",
                        "Dr",
                        "SIN",
                        "123456789",
                        "PreferredOfficialLanguage",
                        "ENG");
        var details =
                new CdsDemographicDetailsDto(
                        "French",
                        "2024-01-02",
                        "2028-01-02",
                        null,
                        new CdsDemographicDetailsDto.Enrollment(
                                "RO", "2020-01-02", null, null, "12"),
                        2,
                        List.of());
        source =
                new CdsDemographicNativeDto(
                        "old27",
                        "Ada",
                        "Family",
                        "1980-01-02",
                        "0123456789",
                        "ON",
                        "F",
                        "AC",
                        scalars,
                        new CdsDemographicNativeDto.Address("123 Source", "Town", "on", "K1A0B1"),
                        null,
                        new CdsDemographicNativeDto.Phone("6135550000", "0012"),
                        null,
                        null,
                        "12",
                        details,
                        CdsDemographicRelationsDto.empty(),
                        List.of());
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> old.put(column, null));
        old.put("demographic_no", "27");
        old.putAll(
                Map.of(
                        "first_name",
                        "Ada",
                        "last_name",
                        "Family",
                        "middleNames",
                        "Source Middle",
                        "year_of_birth",
                        "1980",
                        "month_of_birth",
                        "01",
                        "date_of_birth",
                        "02",
                        "hin",
                        "0123456789",
                        "hc_type",
                        "ON",
                        "ver",
                        "AB",
                        "hc_renew_date",
                        "2028-01-02"));
        old.putAll(
                Map.of(
                        "sex",
                        "F",
                        "patient_status",
                        "AC",
                        "patient_status_date",
                        "2024-01-02",
                        "chart_no",
                        "C27",
                        "email",
                        "old@example.test",
                        "title",
                        "Dr",
                        "sin",
                        "123456789",
                        "official_lang",
                        "English",
                        "spoken_lang",
                        "French",
                        "provider_no",
                        "12"));
        old.putAll(
                Map.of(
                        "address",
                        "123 Source",
                        "city",
                        "Town",
                        "province",
                        "ON",
                        "postal",
                        "K1A0B1",
                        "phone",
                        "6135550000",
                        "phone2",
                        "",
                        "roster_status",
                        "RO",
                        "roster_date",
                        "2020-01-02",
                        "roster_enrolled_to",
                        "12"));
        old.put("date_joined", "2026-11-06");
        old.put("lastUpdateDate", "2026-11-06 01:02:03");
        old.put("pref_name", "");
        current.putAll(old);
    }

    @Test
    void
            shouldVerifySourceProjectionGroupsCanBeProven_withoutClaimingThatGeneratedOrOtherNativeFieldsAreClinicalOutput()
                    throws Exception {
        var result = invoke();
        assertThat(result.groups()).hasSize(19);
        assertThat(result.groups().values())
                .allMatch(CdsExportDemographicProjectionEvidence.Projection::unchanged);
        assertThat(result.deferredColumns())
                .contains("date_joined", "pref_name", "family_doctor", "alias", "gender");
        assertThat(result.unqualifiedOperationalColumns()).containsExactly("lastUpdateDate");
        assertThat(result.toString()).doesNotContain("Ada", "0123456789", "1980", RUN);
        assertThat(
                        result.groups()
                                .get(CdsExportDemographicProjectionEvidence.Group.HOME_PHONE)
                                .columns())
                .containsExactly("phone");
    }

    @Test
    void shouldVerifyOneCurrentFieldEditDoesNotInvalidateIndependentUnchangedSourceDependencies_forDemographicProjectionEvidenceContract()
            throws Exception {
        current.put("email", "current@example.test");
        var result = invoke();
        var email = result.groups().get(CdsExportDemographicProjectionEvidence.Group.EMAIL);
        assertThat(email.preparedProjectionProven()).isTrue();
        assertThat(email.unchanged()).isFalse();
        assertThat(email.changedColumns()).containsExactly("email");
        assertThat(
                        result.groups()
                                .get(CdsExportDemographicProjectionEvidence.Group.BIRTH)
                                .unchanged())
                .isTrue();
    }

    @Test
    void shouldVerifyAMatchedExistingChartCannotBeAssumed_toHaveReceivedThePreparedSourceName()
            throws Exception {
        old.put("first_name", "Existing given");
        current.put("first_name", "Existing given");
        var result = invoke();
        assertThat(
                        result.groups()
                                .get(CdsExportDemographicProjectionEvidence.Group.GIVEN)
                                .preparedProjectionProven())
                .isFalse();
        assertThat(
                        result.groups()
                                .get(CdsExportDemographicProjectionEvidence.Group.FAMILY)
                                .unchanged())
                .isTrue();
    }

    @Test
    void shouldVerifyAShiftedPreparedBirthDateIsTheProjectionAnchor_forLaterSourceReconciliation()
            throws Exception {
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        "2000-01-02",
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        source.details(),
                        source.relations(),
                        source.diagnostics());
        old.put("year_of_birth", "2000");
        current.put("year_of_birth", "2000");
        var result = invoke();
        assertThat(result.preparation().birthDate()).isEqualTo("2000-01-02");
        assertThat(
                        result.groups()
                                .get(CdsExportDemographicProjectionEvidence.Group.BIRTH)
                                .unchanged())
                .isTrue();
    }

    @Test
    void shouldVerifyNullAndEmptyAreDifferentHistoricalProjectionFacts_forDemographicProjectionEvidenceContract() throws Exception {
        old.put("phone2", null);
        current.put("phone2", null);
        assertThat(
                        invoke().groups()
                                .get(CdsExportDemographicProjectionEvidence.Group.WORK_PHONE)
                                .preparedProjectionProven())
                .isFalse();
    }

    @Test
    void shouldVerifyAnUnknownLegacyFieldBaselineIsNotBackfilled_fromTodaysRows() {
        baseline = false;
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_FIELD_BASELINE_REQUIRED");
    }

    @Test
    void shouldVerifyChangedSqlMetadataRequiresReview_beforeAnyGroupCanBeQualified() {
        newSchema = true;
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_FIELD_CODEC_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyRowValuesCannotBeSubstituted_afterTheirFrozenWitnessWasVerified() throws Exception {
        build();
        var substituted = new LinkedHashMap<>(current);
        substituted.put("first_name", "Substituted");
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicProjectionEvidence()
                                        .verify(
                                                27,
                                                ADDRESS,
                                                index,
                                                checked,
                                                row(substituted),
                                                witness(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PROJECTION_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyCancellationAndWrongTargetIdentityAreRefused_forDemographicProjectionEvidenceContract() throws Exception {
        build();
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicProjectionEvidence()
                                        .verify(
                                                27,
                                                ADDRESS,
                                                index,
                                                checked,
                                                row(current),
                                                witness(),
                                                () -> {
                                                    throw new IllegalStateException("CANCELLED");
                                                }))
                .hasMessage("CANCELLED");
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicProjectionEvidence()
                                        .verify(
                                                28,
                                                ADDRESS,
                                                index,
                                                checked,
                                                row(current),
                                                witness(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PROJECTION_SCOPE_INVALID");
    }

    private CdsExportDemographicProjectionEvidence.Result invoke() throws Exception {
        build();
        return new CdsExportDemographicProjectionEvidence()
                .verify(27, ADDRESS, index, checked, row(current), witness(), () -> {});
    }

    private void build() throws Exception {
        var oldState = state(old, false);
        var currentState = state(current, newSchema);
        var reference =
                new CdsExportRetainedIndexDto.Reference("demographic", "27", oldState.digest());
        String
                refs =
                        new ObjectMapper()
                                .writeValueAsString(
                                        Map.of("protocol", 2, "refs", List.of(reference))),
                target = sha(refs),
                sourceDigest = "b".repeat(64),
                options = "c".repeat(64),
                rules = "d".repeat(64),
                payload = new ObjectMapper().writeValueAsString(source),
                type = CdsDemographicNativeDto.class.getName();
        var tables = new LinkedHashMap<String, Map<String, CdsExportCapturedTextRowDto>>();
        String key = CdsImportKeys.hash(RUN, UNIT);
        var unit =
                new LinkedHashMap<String, String>(
                        Map.of(
                                "id",
                                key,
                                "run_id",
                                RUN,
                                "unit_key",
                                UNIT,
                                "patient_key",
                                PATIENT,
                                "lineage_key",
                                PATIENT,
                                "ordinal",
                                "1",
                                "category",
                                "Demographics",
                                "state",
                                "COMMITTED",
                                "source_digest",
                                sourceDigest));
        unit.put("target_references", refs);
        unit.put("target_digest", target);
        unit.put(
                "native_baseline",
                baseline ? CdsNativeBaselineCodec.encode(refs, target, List.of(oldState)) : null);
        put(tables, Plan.IMPORT_UNITS, key, unit);
        put(
                tables,
                Plan.IMPORT_RUNS,
                RUN,
                Map.of(
                        "id",
                        RUN,
                        "source_namespace",
                        "fixture",
                        "options_digest",
                        options,
                        "ruleset_digest",
                        rules));
        put(
                tables,
                Plan.IMPORT_PATIENTS,
                CdsImportKeys.hash(RUN, PATIENT),
                Map.of(
                        "run_id",
                        RUN,
                        "patient_key",
                        PATIENT,
                        "lineage_key",
                        PATIENT,
                        "source_patient_id",
                        "old27",
                        "copy_key",
                        "default",
                        "target_demographic_no",
                        "27",
                        "held",
                        "0"));
        put(
                tables,
                Plan.IMPORT_PREPARATIONS,
                key,
                Map.of(
                        "id",
                        key,
                        "run_id",
                        RUN,
                        "unit_key",
                        UNIT,
                        "source_digest",
                        sourceDigest,
                        "options_digest",
                        options,
                        "ruleset_digest",
                        rules,
                        "payload_type",
                        type,
                        "payload",
                        payload,
                        "digest",
                        CdsPreparationSeal.digest(type, sourceDigest, options, rules, payload)));
        index =
                new CdsExportRetainedIndexDto(
                        HASH,
                        4,
                        16384,
                        tables,
                        Set.of(ADDRESS),
                        Map.of(ADDRESS, List.of(reference)),
                        Map.of());
        var checkedRef =
                new CheckedReference(
                        reference,
                        currentState.digest(),
                        Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED,
                        CdsNativeRowComparison.compare(oldState, currentState));
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        HASH, Map.of(ADDRESS, List.of(checkedRef)), Set.of(), Set.of(ADDRESS));
    }

    private static void put(
            Map<String, Map<String, CdsExportCapturedTextRowDto>> tables,
            Plan plan,
            String key,
            Map<String, String> values) {
        var fields = new LinkedHashMap<String, String>();
        plan.columns().forEach(column -> fields.put(column, null));
        fields.putAll(values);
        fields.put("id", key);
        tables.computeIfAbsent(plan.table(), ignored -> new LinkedHashMap<>())
                .put(key, new CdsExportCapturedTextRowDto(plan.table(), key, fields));
    }

    private static CdsExportCapturedTextRowDto row(Map<String, String> values) {
        return new CdsExportCapturedTextRowDto("demographic", "0000000027", values);
    }

    private CdsExportNativeRowReceiptDto witness() {
        var state = state(current, newSchema);
        return new CdsExportNativeRowReceiptDto(
                "demographic", "27", "0000000027", state.digest(), state);
    }

    private static CdsNativeRowState state(Map<String, String> values, boolean changedSchema) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (String column : values.keySet()) {
            columns.add(
                    new CdsNativeRowDigest.Column(
                            column,
                            column.equals("lastUpdateDate")
                                    ? "TIMESTAMP"
                                    : changedSchema && column.equals("first_name")
                                            ? "TEXT"
                                            : "VARCHAR",
                            255,
                            0));
            String value = values.get(column);
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    value.getBytes(StandardCharsets.UTF_8).length,
                                    sha(value)));
        }
        return new CdsNativeRowState("demographic", "27", columns, cells);
    }

    private static String sha(String value) {
        return HexFormat.of()
                .formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    record Graph(
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> fields,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness) {}

    void preparedBirth(String date) {
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        date,
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        source.details(),
                        source.relations(),
                        source.diagnostics());
        String[] parts = date.split("-");
        for (var row : List.of(old, current)) {
            row.put("year_of_birth", parts[0]);
            row.put("month_of_birth", parts[1]);
            row.put("date_of_birth", parts[2]);
        }
    }

    void currentBirth(String year, String month, String day) {
        current.put("year_of_birth", year);
        current.put("month_of_birth", month);
        current.put("date_of_birth", day);
    }

    void oldBirth(String year, String month, String day) {
        old.put("year_of_birth", year);
        old.put("month_of_birth", month);
        old.put("date_of_birth", day);
        currentBirth(year, month, day);
    }

    void currentField(String column, String value) {
        current.put(column, value);
    }

    void oldField(String column, String value) {
        old.put(column, value);
        current.put(column, value);
    }

    void preparedScalar(String key, String value) {
        var scalars = new HashMap<>(source.scalars());
        if (value == null) scalars.remove(key);
        else scalars.put(key, value);
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        scalars,
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        source.details(),
                        source.relations(),
                        source.diagnostics());
    }

    void preparedOptionalDate(String column, String value) {
        var details = source.details();
        details =
                new CdsDemographicDetailsDto(
                        details.spokenLanguage(),
                        column.equals("patient_status_date") ? value : details.statusDate(),
                        column.equals("hc_renew_date") ? value : details.healthCardExpiry(),
                        details.patientNote(),
                        details.roster(),
                        details.enrollmentHistoryCount(),
                        details.diagnostics());
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        details,
                        source.relations(),
                        source.diagnostics());
        oldField(column, value);
    }

    void preparedHealth(String number, String issuer, String version, String expiry) {
        preparedScalar("HealthCard/Version", version);
        preparedOptionalDate("hc_renew_date", expiry);
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        number,
                        issuer,
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        source.details(),
                        source.relations(),
                        source.diagnostics());
        oldField("hin", number == null ? "" : number);
        oldField("hc_type", issuer == null ? "" : issuer);
        oldField("ver", version);
    }

    void preparedPhones(
            CdsDemographicNativeDto.Phone home,
            CdsDemographicNativeDto.Phone work,
            CdsDemographicNativeDto.Phone cell) {
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        home,
                        work,
                        cell,
                        source.primaryProvider(),
                        source.details(),
                        source.relations(),
                        source.diagnostics());
        oldField("phone", home == null ? "" : home.number());
        oldField("phone2", work == null ? "" : work.number());
    }

    void preparedRelations(
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto.Person
                    family,
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto.Person
                    referring) {
        var relations =
                new io.github.carlos_emr.carlos.demographic.cdsimport.dto
                        .CdsDemographicRelationsDto(family, referring, null, 0, List.of());
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        source.details(),
                        relations,
                        source.diagnostics());
    }

    void preparedPrimaryProvider(String provider) {
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        provider,
                        source.details(),
                        source.relations(),
                        source.diagnostics());
        oldField("provider_no", provider == null ? "" : provider);
    }

    void preparedPharmacy(CdsDemographicRelationsDto.Pharmacy pharmacy) {
        var relations =
                new CdsDemographicRelationsDto(
                        source.relations().familyPhysician(),
                        source.relations().referringPhysician(),
                        pharmacy,
                        0,
                        List.of());
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        source.details(),
                        relations,
                        source.diagnostics());
    }

    void preparedRoster(CdsDemographicDetailsDto.Enrollment roster, long count) {
        var d = source.details();
        var details =
                new CdsDemographicDetailsDto(
                        d.spokenLanguage(),
                        d.statusDate(),
                        d.healthCardExpiry(),
                        d.patientNote(),
                        roster,
                        count,
                        d.diagnostics());
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        details,
                        source.relations(),
                        source.diagnostics());
        oldField("roster_status", roster == null ? "" : roster.status());
        oldField("roster_date", roster == null ? null : roster.date());
        oldField("roster_termination_date", roster == null ? null : roster.terminationDate());
        oldField("roster_termination_reason", roster == null ? null : roster.terminationReason());
        oldField("roster_enrolled_to", roster == null ? null : roster.enrolledProvider());
    }

    void preparedNote(String note, boolean sourceOnly) {
        var details = source.details();
        details =
                new CdsDemographicDetailsDto(
                        details.spokenLanguage(),
                        details.statusDate(),
                        details.healthCardExpiry(),
                        note,
                        details.roster(),
                        details.enrollmentHistoryCount(),
                        sourceOnly
                                ? List.of(
                                        new CdsClinicalDiagnosticDto(
                                                "CDS_PATIENT_NOTE_COMPLETE_TEXT_SOURCE_VIEW",
                                                "NoteAboutPatient"))
                                : List.of());
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        source.mailing(),
                        source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        details,
                        source.relations(),
                        source.diagnostics());
    }

    void preparedAddress(boolean residential, CdsDemographicNativeDto.Address value) {
        source =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        source.scalars(),
                        residential ? source.mailing() : value,
                        residential ? value : source.residential(),
                        source.home(),
                        source.work(),
                        source.cell(),
                        source.primaryProvider(),
                        source.details(),
                        source.relations(),
                        source.diagnostics());
        oldField(
                residential ? "residentialAddress" : "address",
                value == null ? null : value.address());
        oldField(residential ? "residentialCity" : "city", value == null ? null : value.city());
        String province = value == null ? null : value.province();
        if (!residential && province != null) province = province.strip().toUpperCase(Locale.ROOT);
        oldField(residential ? "residentialProvince" : "province", province);
        oldField(
                residential ? "residentialPostal" : "postal",
                value == null ? null : value.postal());
    }

    Graph withBirthSource(String lexical) throws Exception {
        return withSource(Map.of("DateOfBirth[1]", lexical));
    }

    Graph withSource(Map<String, String> sourceFields) throws Exception {
        build();
        String root = "/OmdCds[1]/PatientRecord[1]/Demographics[1]",
                page = CdsImportKeys.hash(PATIENT, root, "FIELDS", "1");
        var tables = new LinkedHashMap<String, Map<String, CdsExportCapturedTextRowDto>>();
        index.tables().forEach((name, rows) -> tables.put(name, new LinkedHashMap<>(rows)));
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        var refsList = new ArrayList<CdsExportRetainedIndexDto.Reference>();
        var parents = new HashMap<String, String>();
        for (var entry : sourceFields.entrySet()) {
            String path = root + "/" + entry.getKey(), namespace = "cds", valueType = null;
            var type =
                    CdsWireSchema.schemaModel()
                            .element("cds", "OmdCds")
                            .child("cds", "PatientRecord")
                            .child("cds", "Demographics");
            for (String segment : entry.getKey().split("/")) {
                if (segment.startsWith("@")) {
                    namespace = "";
                    type = type.attribute(namespace, segment.substring(1));
                    break;
                }
                String name = segment.substring(0, segment.indexOf('['));
                var expanded =
                        type.childNames().stream()
                                .filter(candidate -> candidate.local().equals(name))
                                .findFirst()
                                .orElseThrow();
                namespace = expanded.namespace();
                type = type.child(namespace, name);
            }
            if (valueType == null)
                valueType = "{" + type.getTypeNamespace() + "}" + type.getTypeName();
            String id = CdsImportKeys.hash(page, namespace, path, "1");
            var raw = new HashMap<String, String>();
            raw.putAll(
                    Map.of(
                            "run_id",
                            RUN,
                            "patient_key",
                            PATIENT,
                            "unit_key",
                            page,
                            "demographic_no",
                            "27",
                            "category",
                            "Demographics",
                            "source_path",
                            path,
                            "occurrence",
                            "1",
                            "source_namespace",
                            namespace,
                            "value_type",
                            valueType));
            raw.put("original_value", entry.getValue());
            raw.put("normalized_value", entry.getValue());
            raw.put("payload_offset", "0");
            raw.put("payload_length", "0");
            put(tables, Plan.IMPORT_FIELDS, id, raw);
            fields.add(
                    new CdsExportRetainedFieldMapper()
                            .map(
                                    27,
                                    Set.of(CdsCategory.DEMOGRAPHICS),
                                    tables.get("cds_import_field").get(id)));
            refsList.add(new CdsExportRetainedIndexDto.Reference("cds_import_field", id, HASH));
            parents.put(id, ADDRESS);
        }
        String refs =
                new ObjectMapper().writeValueAsString(Map.of("protocol", 2, "refs", refsList));
        var unit =
                new LinkedHashMap<String, String>(
                        Map.of(
                                "run_id",
                                RUN,
                                "unit_key",
                                page,
                                "patient_key",
                                PATIENT,
                                "lineage_key",
                                PATIENT,
                                "ordinal",
                                "1",
                                "category",
                                "Demographics_FIELDS",
                                "state",
                                "COMMITTED",
                                "source_digest",
                                "b".repeat(64)));
        unit.put("target_references", refs);
        unit.put("target_digest", sha(refs));
        put(tables, Plan.IMPORT_UNITS, CdsImportKeys.hash(RUN, page), unit);
        String address = RUN + ":" + page;
        var references = new LinkedHashMap<>(index.references());
        references.put(address, refsList);
        var slots = new LinkedHashMap<>(checked.units());
        slots.put(
                address,
                refsList.stream()
                        .map(ref -> new CheckedReference(ref, HASH, Reason.UNCHANGED_ROW))
                        .toList());
        var parent =
                new CdsExportRetainedIndexDto(
                        HASH, 6, 32768, tables, references.keySet(), references, parents);
        var verification =
                new CdsExportRetainedReceiptVerificationDto(
                        HASH, slots, Set.of(address), Set.of(ADDRESS));
        return new Graph(parent, verification, fields, row(current), witness());
    }
}
