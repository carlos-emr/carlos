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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeDemographicAssemblerUnitTest {
    private static final String HASH = "a".repeat(64);
    private Map<String, List<CdsExportCapturedTextRowDto>> rows;

    @BeforeEach
    void setup() {
        rows = new LinkedHashMap<>();
        for (String table :
                List.of(
                        "demographic",
                        "demographicExt",
                        "demographiccust",
                        "demographicArchive",
                        "provider",
                        "Contact",
                        "DemographicContact",
                        "pharmacyInfo",
                        "demographicPharmacy")) rows.put(table, new ArrayList<>());
        var values = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> values.put(column, null));
        values.putAll(
                Map.of(
                        "demographic_no",
                        "27",
                        "first_name",
                        "Élodie😀",
                        "last_name",
                        "D’Arcy",
                        "year_of_birth",
                        "2000",
                        "month_of_birth",
                        "2",
                        "date_of_birth",
                        "29",
                        "sex",
                        "F",
                        "patient_status",
                        "AC"));
        rows.get("demographic")
                .add(new CdsExportCapturedTextRowDto("demographic", "0000000027", values));
    }

    private CdsExportNativeDemographicAssembler.Result assemble(Set<CdsCategory> categories)
            throws IOException {
        return new CdsExportNativeDemographicAssembler()
                .assemble(27, categories, HASH, rows, 100, 1L << 20, () -> {});
    }

    private CdsExportNativeDemographicAssembler.Result assemble() throws IOException {
        return assemble(Set.of(CdsCategory.DEMOGRAPHICS));
    }

    private void custom(String content, String alert, boolean selected) {
        var values = new LinkedHashMap<String, String>();
        (selected ? Plan.DEMOGRAPHIC_CUST : Plan.DEMOGRAPHIC_CUST_DEMOGRAPHICS)
                .columns()
                .forEach(column -> values.put(column, null));
        values.put("demographic_no", "27");
        values.put("content", content);
        if (selected) values.put("cust3", alert);
        rows.get("demographiccust")
                .add(
                        new CdsExportCapturedTextRowDto(
                                "demographiccust",
                                CdsExportPatientTableSourceReader.key(27),
                                values));
    }

    @Test
    void shouldAssembleANativeChartWithoutInventingAnImportedParent_andCloseEveryBaseField()
            throws Exception {
        var result = assemble();
        assertThat(result.fields()).hasSize(CdsExportDemographicRowDto.COLUMNS.size() + 1);
        assertThat(result.fields().values())
                .noneMatch(
                        field ->
                                field.outcome()
                                        == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
        var bytes = new ByteArrayOutputStream();
        var identity = new CdsExportIdentityDto(HASH, CdsCategory.DEMOGRAPHICS, "b".repeat(64));
        new CdsExportXmlComposer()
                .compose(
                        bytes,
                        HASH,
                        HASH,
                        "2026.09.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        List.of(
                                        new CdsExportXmlComposer.Section(
                                                identity,
                                                xml ->
                                                        new CdsExportDemographicXmlWriter()
                                                                .writeWithRelated(
                                                                        result.demographic(),
                                                                        result.pharmacy(),
                                                                        result.contacts(),
                                                                        xml)))
                                .iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        var verified =
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(bytes.toByteArray()),
                                "27",
                                Set.of(CdsCategory.DEMOGRAPHICS),
                                "2026.09.0-test",
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                CdsExportXmlLimitsDto.defaults(),
                                ignored -> {},
                                () -> {});
        assertThat(verified.occurrences()).containsEntry(CdsCategory.DEMOGRAPHICS, 1L);
        assertThat(bytes.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("Élodie😀", "D’Arcy", "2000-02-29");
    }

    @Test
    void shouldPreserveTheCompleteNativeNote_andExplicitlyAccountForAnAbsentSelectedAlert()
            throws Exception {
        custom("<unotes>Exact café 😀 </unotes> literal\nsecond line</unotes>", null, true);
        var result =
                assemble(Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALERTS_AND_SPECIAL_NEEDS));
        assertThat(result.demographic().note().text())
                .isEqualTo("Exact café 😀 </unotes> literal\nsecond line");
        assertThat(
                        result.fields().keySet().stream()
                                .filter(key -> key.table().equals("demographiccust")))
                .hasSize(6);
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "demographiccust",
                                                CdsExportPatientTableSourceReader.key(27),
                                                "cust3"))
                                .outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.ABSENT);
    }

    @Test
    void shouldRefusePopulatedAncillaryClinicalData_insteadOfPublishingOnlyTheSimpleBaseFields() {
        var row = rows.get("demographic").getFirst();
        var values = new LinkedHashMap<>(row.fields());
        values.put("children", "Unrepresented family information");
        rows.get("demographic")
                .set(0, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), values));
        assertThatThrownBy(this::assemble).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefusePopulatedLegacyAlerts_evenIfTheCurrentNativeNoteIsRepresentable() {
        custom("Native note", "Legacy alert", true);
        assertThatThrownBy(
                        () ->
                                assemble(
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.ALERTS_AND_SPECIAL_NEEDS)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseMalformedDemographicHistory_beforeAccountingForItsFields() {
        rows.get("demographicArchive")
                .add(
                        new CdsExportCapturedTextRowDto(
                                "demographicArchive", "archive", Map.of("id", "1")));
        assertThatThrownBy(this::assemble).hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_SCOPE_INVALID");
    }

    @Test
    void shouldUseTheCurrentSnapshotAndPreserveRosterHistory_afterOrdinaryDemographicEdits()
            throws Exception {
        var old = new LinkedHashMap<String, String>();
        Plan.DEMOGRAPHIC_ARCHIVE.columns().forEach(column -> old.put(column, null));
        old.putAll(
                Map.of(
                        "id",
                        "1",
                        "demographic_no",
                        "27",
                        "first_name",
                        "Previous spelling",
                        "roster_status",
                        "TE",
                        "roster_date",
                        "2020-01-02",
                        "roster_termination_date",
                        "2021-01-02"));
        rows.get("demographicArchive")
                .add(
                        new CdsExportCapturedTextRowDto(
                                "demographicArchive",
                                CdsExportPatientTableSourceReader.key(1),
                                old));
        var current = rows.get("demographic").getFirst();
        var fields = new LinkedHashMap<>(current.fields());
        fields.putAll(
                Map.of(
                        "roster_status",
                        "RO",
                        "roster_date",
                        "2022-01-02",
                        "family_doctor",
                        "<rdohip></rdohip><rd></rd>"));
        rows.get("demographic")
                .set(
                        0,
                        new CdsExportCapturedTextRowDto(
                                current.table(), current.recordKey(), fields));
        var result = assemble();
        assertThat(result.demographic().events()).hasSize(2);
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "demographicArchive",
                                                CdsExportPatientTableSourceReader.key(1),
                                                "first_name"))
                                .rule())
                .isEqualTo("SUPERSEDED_DEMOGRAPHIC_SNAPSHOT_IN_APPROVED_AUDIT");
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "demographicArchive",
                                                CdsExportPatientTableSourceReader.key(1),
                                                "roster_date"))
                                .outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
        assertThat(result.fields().values())
                .noneMatch(
                        field ->
                                field.outcome()
                                        == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldRefuseAForeignPatientOrAChangedRowIdentity_forNativeDemographicAssemblerContract() {
        var row = rows.get("demographic").getFirst();
        rows.get("demographic")
                .set(0, new CdsExportCapturedTextRowDto(row.table(), "0000000028", row.fields()));
        assertThatThrownBy(this::assemble)
                .hasMessage("CDS_EXPORT_NATIVE_DEMOGRAPHIC_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseAnUnaccountedCapturedProvider_insteadOfHidingTheMissingFieldDecisions() {
        var values = new LinkedHashMap<String, String>();
        io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS.forEach(
                column -> values.put(column, null));
        values.putAll(
                Map.of(
                        "provider_no",
                        "99",
                        "first_name",
                        "Other",
                        "last_name",
                        "Clinician",
                        "ohip_no",
                        "654321"));
        rows.get("provider")
                .add(
                        new CdsExportCapturedTextRowDto(
                                "provider", CdsExportPublicProviderSourceReader.key("99"), values));
        assertThatThrownBy(this::assemble).isInstanceOf(IOException.class);
    }

    private Map<String, List<CdsExportNativeRowReceiptDto>> witnessesWithoutProviderReceipts() {
        var providerRows = rows.get("provider");
        rows.put("provider", List.of());
        try {
            return witnesses();
        } finally {
            rows.put("provider", providerRows);
        }
    }

    private Map<String, List<CdsExportNativeRowReceiptDto>> witnesses() {
        var result = new LinkedHashMap<String, List<CdsExportNativeRowReceiptDto>>();
        for (var table : rows.entrySet()) {
            var found = new ArrayList<CdsExportNativeRowReceiptDto>();
            result.put(table.getKey(), found);
            for (var row : table.getValue()) {
                var columns = new ArrayList<CdsNativeRowDigest.Column>();
                var cells = new ArrayList<CdsNativeRowState.Cell>();
                for (var entry : row.fields().entrySet()) {
                    columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
                    String value = entry.getValue();
                    byte[] bytes =
                            value == null
                                    ? null
                                    : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    cells.add(
                            bytes == null
                                    ? new CdsNativeRowState.Cell(true, 0, null)
                                    : new CdsNativeRowState.Cell(
                                            false,
                                            bytes.length,
                                            HexFormat.of()
                                                    .formatHex(
                                                            io.github.carlos_emr.carlos.utility
                                                                    .CdsImportKeys.digest()
                                                                    .digest(bytes))));
                }
                String id =
                        row.fields()
                                .get(
                                        row.table().equals("demographic")
                                                        || row.table().equals("demographiccust")
                                                ? "demographic_no"
                                                : "id");
                var state = new CdsNativeRowState(row.table(), id, columns, cells);
                found.add(
                        new CdsExportNativeRowReceiptDto(
                                row.table(),
                                id,
                                row.recordKey(),
                                state.digest(),
                                state,
                                row.table().equals("demographiccust")
                                        && !row.fields().containsKey("cust3")));
            }
        }
        return result;
    }

    @Test
    void shouldVerifyTheSelectedNativeNoteProjection_withoutBorrowingAWholeRowReceipt()
            throws Exception {
        custom("Complete native note", null, false);
        var proof = witnesses();
        CdsExportDemographicOccurrenceService.requireNativeWitnesses(
                rows, proof, Set.of(CdsCategory.DEMOGRAPHICS), () -> {});
        assertThatThrownBy(
                        () ->
                                CdsExportDemographicOccurrenceService.requireNativeWitnesses(
                                        rows,
                                        proof,
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.ALERTS_AND_SPECIAL_NEEDS),
                                        () -> {}))
                .hasMessage("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseSubstitutedNativeValues_beforeAnyTypedXmlIsEmitted() {
        var proof = witnesses();
        var row = rows.get("demographic").getFirst();
        var changed = new LinkedHashMap<>(row.fields());
        changed.put("first_name", "Substituted");
        rows.get("demographic")
                .set(0, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), changed));
        assertThatThrownBy(
                        () ->
                                CdsExportDemographicOccurrenceService.requireNativeWitnesses(
                                        rows, proof, Set.of(CdsCategory.DEMOGRAPHICS), () -> {}))
                .hasMessage("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID");
    }

    @Test
    void shouldRejectAMissingIndependentWitness_insteadOfQualifyingMatchingMapperTotals() {
        var proof = witnesses();
        proof.put("demographic", List.of());
        assertThatThrownBy(
                        () ->
                                CdsExportDemographicOccurrenceService.requireNativeWitnesses(
                                        rows, proof, Set.of(CdsCategory.DEMOGRAPHICS), () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldSerializeTheRegisteredPatientIdentity_andRefuseAnotherPatientsIdentity()
            throws Exception {
        var result = assemble();
        for (String id : List.of("C27", "C28")) {
            var bytes = new ByteArrayOutputStream();
            var identity = new CdsExportIdentityDto(HASH, CdsCategory.DEMOGRAPHICS, "b".repeat(64));
            org.assertj.core.api.ThrowableAssert.ThrowingCallable write =
                    () ->
                            new CdsExportXmlComposer()
                                    .compose(
                                            bytes,
                                            HASH,
                                            HASH,
                                            "2026.09.0-test",
                                            CdsLogicalProfile.ONTARIO_MD_5_2,
                                            Set.of(CdsCategory.DEMOGRAPHICS),
                                            List.of(
                                                            new CdsExportXmlComposer.Section(
                                                                    identity,
                                                                    xml ->
                                                                            new CdsExportDemographicXmlWriter()
                                                                                    .writeWithRelated(
                                                                                            result
                                                                                                    .demographic(),
                                                                                            result
                                                                                                    .pharmacy(),
                                                                                            result
                                                                                                    .contacts(),
                                                                                            id,
                                                                                            xml)))
                                                    .iterator(),
                                            ignored -> {},
                                            1L << 20,
                                            () -> {});
            if (id.equals("C28"))
                assertThatThrownBy(write).hasMessage("CDS_EXPORT_NATIVE_PATIENT_IDENTITY_INVALID");
            else {
                assertThatCode(write).doesNotThrowAnyException();
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(bytes.toByteArray()),
                                "C27",
                                Set.of(CdsCategory.DEMOGRAPHICS),
                                "2026.09.0-test",
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                CdsExportXmlLimitsDto.defaults(),
                                ignored -> {},
                                () -> {});
            }
        }
    }

    @Test
    void shouldAccount_forBothPrimaryAndEnrolledUsesOfTheSameCapturedPhysician() throws Exception {
        var row = rows.get("demographic").getFirst();
        var values = new LinkedHashMap<>(row.fields());
        values.putAll(
                Map.of(
                        "provider_no",
                        "99",
                        "roster_enrolled_to",
                        "99",
                        "roster_status",
                        "RO",
                        "roster_date",
                        "2024-02-29"));
        rows.get("demographic")
                .set(0, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), values));
        var provider = new LinkedHashMap<String, String>();
        io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS.forEach(
                column -> provider.put(column, null));
        provider.putAll(
                Map.of(
                        "provider_no",
                        "99",
                        "first_name",
                        "Shared",
                        "last_name",
                        "Physician",
                        "ohip_no",
                        "654321"));
        rows.get("provider")
                .add(
                        new CdsExportCapturedTextRowDto(
                                "provider",
                                CdsExportPublicProviderSourceReader.key("99"),
                                provider));
        var proof = witnessesWithoutProviderReceipts();
        CdsExportDemographicOccurrenceService.requireNativeWitnesses(
                rows, proof, Set.of(CdsCategory.DEMOGRAPHICS), () -> {});
        var result = assemble();
        assertThat(result.demographic().demographic().primaryPhysician()).isNotNull();
        assertThat(result.demographic().events()).hasSize(1);
        assertThat(result.fields().keySet().stream().filter(key -> key.table().equals("provider")))
                .hasSize(6);
        assertThat(result.fields().values())
                .noneMatch(
                        value ->
                                value.outcome()
                                        == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
    }

    @Test
    void
            shouldRefuseAFabricatedWholeProviderReceipt_insteadOfTreatingPublicFieldsAsAPrivateAccountRow() {
        var proof = witnesses();
        proof.put(
                "provider",
                List.of(
                        new CdsExportNativeRowReceiptDto(
                                "provider",
                                "99",
                                CdsExportPublicProviderSourceReader.key("99"),
                                HASH)));
        assertThatThrownBy(
                        () ->
                                CdsExportDemographicOccurrenceService.requireNativeWitnesses(
                                        rows, proof, Set.of(CdsCategory.DEMOGRAPHICS), () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldKeepClinicalDomainProvidersOutOfTheDemographicSlice_whileRejectingRawUnownedRows()
            throws Exception {
        var current = rows.get("demographic").getFirst();
        var values = new LinkedHashMap<>(current.fields());
        values.put("provider_no", "99");
        rows.get("demographic")
                .set(
                        0,
                        new CdsExportCapturedTextRowDto(
                                current.table(), current.recordKey(), values));
        for (String id : List.of("99", "17")) {
            var provider = new LinkedHashMap<String, String>();
            io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS.forEach(
                    c -> provider.put(c, null));
            provider.putAll(Map.of("provider_no", id, "first_name", "Public", "last_name", id));
            rows.get("provider")
                    .add(
                            new CdsExportCapturedTextRowDto(
                                    "provider",
                                    CdsExportPublicProviderSourceReader.key(id),
                                    provider));
        }
        // The assembler itself still refuses a provider row it is handed but cannot account for.
        assertThatThrownBy(this::assemble).hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_SCOPE_INVALID");
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.MEDICATIONS_AND_TREATMENTS);
        var own = CdsExportDemographicOccurrenceService.demographicRows(rows);
        assertThat(own.get("provider")).hasSize(1);
        var result =
                new CdsExportNativeDemographicAssembler()
                        .assemble(27, selected, HASH, own, 100, 1L << 20, () -> {});
        assertThat(result.demographic().demographic().primaryPhysician().lastName())
                .isEqualTo("99");
        // Updated for D1: the demographic slice no longer depends on which other categories are
        // selected. A Demographics-only job used to hand the assembler the unreferenced row and
        // fail the whole job; the slice now keeps only rows a demographic column names exactly.
        var demographicsOnly = Set.of(CdsCategory.DEMOGRAPHICS);
        assertThat(own.get("provider")).hasSize(1);
        assertThat(
                        new CdsExportNativeDemographicAssembler()
                                .assemble(27, demographicsOnly, HASH, own, 100, 1L << 20, () -> {})
                                .demographic()
                                .demographic()
                                .primaryPhysician())
                .isNotNull();
    }

    @Test
    void shouldAccountForAFormerMostResponsiblePhysician_whenOnlyAnArchiveSnapshotNamesThem()
            throws Exception {
        // DemographicUpdate2Action archives the patient row on every save; after reception
        // changes the physician from 17 to 99, older archive rows still name 17.
        var current = rows.get("demographic").getFirst();
        var values = new LinkedHashMap<>(current.fields());
        values.put("provider_no", "99");
        rows.get("demographic")
                .set(0, new CdsExportCapturedTextRowDto(current.table(), current.recordKey(), values));
        var old = new LinkedHashMap<String, String>();
        Plan.DEMOGRAPHIC_ARCHIVE.columns().forEach(column -> old.put(column, null));
        old.putAll(Map.of("id", "1", "demographic_no", "27", "provider_no", "17"));
        rows.get("demographicArchive")
                .add(
                        new CdsExportCapturedTextRowDto(
                                "demographicArchive", CdsExportPatientTableSourceReader.key(1), old));
        for (String id : List.of("99", "17")) {
            var provider = new LinkedHashMap<String, String>();
            io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS.forEach(
                    c -> provider.put(c, null));
            provider.putAll(
                    Map.of("provider_no", id, "first_name", "Public", "last_name", "Dr" + id));
            rows.get("provider")
                    .add(
                            new CdsExportCapturedTextRowDto(
                                    "provider",
                                    CdsExportPublicProviderSourceReader.key(id),
                                    provider));
        }
        var own = CdsExportDemographicOccurrenceService.demographicRows(rows);
        assertThat(own.get("provider")).hasSize(2);
        var result =
                new CdsExportNativeDemographicAssembler()
                        .assemble(
                                27,
                                Set.of(CdsCategory.DEMOGRAPHICS),
                                HASH,
                                own,
                                100,
                                1L << 20,
                                () -> {});
        var former =
                result.fields()
                        .get(
                                new CdsExportNativeFieldKeyDto(
                                        "provider",
                                        CdsExportPublicProviderSourceReader.key("17"),
                                        "last_name"));
        assertThat(former.outcome()).isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
        assertThat(former.rule()).isEqualTo("SUPERSEDED_PRIMARY_PHYSICIAN_IN_APPROVED_AUDIT");
        assertThat(result.fields().keySet().stream().filter(key -> key.table().equals("provider")))
                .hasSize(12);
        assertThat(result.fields().values())
                .noneMatch(
                        field ->
                                field.outcome()
                                        == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
        String xml = validatedXml(result);
        assertThat(xml).contains("<cdsd:LastName>Dr99</cdsd:LastName>").doesNotContain("Dr17");
    }

    @Test
    void shouldIntegrateNativeExternalFamilyPhysicians_withEveryContactFieldAccounted()
            throws Exception {
        rows.get("Contact").add(CdsExportProfessionalContactMapperUnitTest.contact(9));
        rows.get("DemographicContact")
                .add(CdsExportProfessionalContactMapperUnitTest.association(1, 9, "Family Doctor"));
        var result = assemble();
        assertThat(result.professional().physicians()).hasSize(1);
        var bytes = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        bytes,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        List.of(
                                        new CdsExportXmlComposer.Section(
                                                new CdsExportIdentityDto(
                                                        HASH, CdsCategory.DEMOGRAPHICS, HASH),
                                                xml ->
                                                        new CdsExportDemographicXmlWriter()
                                                                .writeWithRelated(
                                                                        result.demographic(),
                                                                        result.pharmacy(),
                                                                        result.contacts(),
                                                                        result.professional(),
                                                                        "C27",
                                                                        xml)))
                                .iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(bytes.toByteArray()),
                        "C27",
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        assertThat(bytes.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("<FamilyPhysician>", "Élodie😀 &amp; Bea");
        assertThat(
                        result.fields().keySet().stream()
                                .filter(
                                        k ->
                                                k.table().equals("Contact")
                                                        || k.table().equals("DemographicContact")))
                .hasSize(40);
    }

    @Test
    void shouldCloseAnImportedChartAgainstItsOwnProviderSlice_whenAnotherCategoryCapturedItsAuthor()
            throws Exception {
        // D2: an allergy recorded by Dr 17 puts provider 17 in the capture. The retained path
        // used to validate closure against every captured row, so 17's unassessed fields
        // failed the whole job; it must use the same owned slice as its reconcilers.
        var current = rows.get("demographic").getFirst();
        var values = new LinkedHashMap<>(current.fields());
        values.put("provider_no", "99");
        rows.get("demographic")
                .set(0, new CdsExportCapturedTextRowDto(current.table(), current.recordKey(), values));
        for (String id : List.of("99", "17")) {
            var provider = new LinkedHashMap<String, String>();
            io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS.forEach(
                    c -> provider.put(c, null));
            provider.putAll(
                    Map.of("provider_no", id, "first_name", "Public", "last_name", "Dr" + id));
            rows.get("provider")
                    .add(
                            new CdsExportCapturedTextRowDto(
                                    "provider",
                                    CdsExportPublicProviderSourceReader.key(id),
                                    provider));
        }
        var selected =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        var owned = CdsExportDemographicOccurrenceService.demographicRows(rows);
        var assembled =
                new CdsExportNativeDemographicAssembler()
                        .assemble(27, selected, HASH, owned, 100, 1L << 20, () -> {});
        String source = "c".repeat(64);
        var result =
                new CdsExportDemographicRetainedCoreReconciler.Result(
                        HASH,
                        "11111111-1111-4111-8111-111111111111:" + "b".repeat(64),
                        Set.of(source),
                        Set.of(),
                        assembled.fields(),
                        Set.of(),
                        Set.of(),
                        List.of());
        var validator =
                new io.github.carlos_emr.carlos.demographic.cdsexport.validator
                        .CdsExportDemographicClosureValidator();
        var closure = CdsExportDemographicOccurrenceService.closureRows(owned);
        assertThat(closure)
                .noneMatch(
                        row ->
                                row.table().equals("provider")
                                        && "17".equals(row.fields().get("provider_no")));
        assertThat(
                        validator
                                .validate(HASH, closure, Set.of(source), result, 1_000_000, () -> {})
                                .nativeFields())
                .isEqualTo(assembled.fields().size());
        var everyCapturedRow = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.values().forEach(everyCapturedRow::addAll);
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        HASH,
                                        everyCapturedRow,
                                        Set.of(source),
                                        result,
                                        1_000_000,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_SCOPE_INVALID");
    }

    /**
     * The row shapes that CARLOS's own forms write. add-form-personal.jsp preselects newsletter
     * "Unknown", country of origin "-1", an empty title and spoken language, and English;
     * DemographicAddRecord2Action stores the empty referring-physician wrapper. A later Master
     * Record save (DemographicUpdate2Action) writes one demographicExt row per listed key, empty
     * for blank fields, with rxInteractionWarningLevel "0". contact.jsp links a contact with
     * consent "1"; DemographicPharmacyDaoImpl links a pharmacy with the model's consent TRUE.
     */
    private void masterRecordPatient() {
        var current = rows.get("demographic").getFirst();
        var values = new LinkedHashMap<>(current.fields());
        values.put("newsletter", "Unknown");
        values.put("country_of_origin", "-1");
        values.put("title", "");
        values.put("spoken_lang", "");
        values.put("official_lang", "English");
        values.put("family_doctor", "<rdohip></rdohip><rd></rd>");
        values.put("hin", "1234567890");
        values.put("ver", "");
        values.put("hc_type", "ON");
        values.put("address", "12 Main St");
        values.put("city", "Ottawa");
        values.put("province", "ON");
        values.put("postal", "K1A 0B1");
        values.put("chart_no", "");
        values.put("sin", "");
        values.put("email", "");
        values.put("alias", "");
        values.put("pref_name", "");
        values.put("gender", "");
        values.put("pronoun", "");
        rows.get("demographic")
                .set(0, new CdsExportCapturedTextRowDto(current.table(), current.recordKey(), values));
        long id = 0;
        for (String key : List.of("aboriginal", "ethnicity", "area", "usSigned", "EmploymentStatus"))
            rows.get("demographicExt")
                    .add(CdsExportDemographicPhonesMapperUnitTest.property(++id, key, null));
        for (String key : List.of("cytolNum", "phoneComment", "demo_cell", "hPhoneExt", "PHU"))
            rows.get("demographicExt")
                    .add(CdsExportDemographicPhonesMapperUnitTest.property(++id, key, ""));
        rows.get("demographicExt")
                .add(
                        CdsExportDemographicPhonesMapperUnitTest.property(
                                ++id, "rxInteractionWarningLevel", "0"));
        var link =
                CdsExportPersonalContactMapperUnitTest.changed(
                        CdsExportPersonalContactMapperUnitTest.changed(
                                CdsExportPersonalContactMapperUnitTest.changed(
                                        CdsExportPersonalContactMapperUnitTest.association(-1, 9),
                                        "role",
                                        "Parent"),
                                "ec",
                                "true"),
                        "sdm",
                        "");
        rows.get("DemographicContact")
                .add(CdsExportPersonalContactMapperUnitTest.changed(link, "consentToContact", "1"));
        rows.get("Contact").add(CdsExportPersonalContactMapperUnitTest.contact(9));
        rows.get("demographicPharmacy")
                .add(
                        CdsExportPersonalContactMapperUnitTest.changed(
                                CdsExportPharmacyMapperUnitTest.link(-1, 7),
                                "consentToContact",
                                "1"));
        rows.get("pharmacyInfo").add(CdsExportPharmacyMapperUnitTest.pharmacy(7));
    }

    @Test
    void shouldExportAPatientCreatedAndEditedThroughTheMasterRecord_withoutHoldingForFormDefaults()
            throws Exception {
        masterRecordPatient();
        var result = assemble();
        assertThat(result.fields().values())
                .noneMatch(
                        field ->
                                field.outcome()
                                        == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
        String xml = validatedXml(result);
        assertThat(xml)
                .contains("<cdsd:PurposeAsPlainText>Parent</cdsd:PurposeAsPlainText>", "<PreferredPharmacy>")
                .doesNotContain(">Unknown<", ">-1<");
    }

    /** Renders through the real composer and pinned-schema validator, as the job does. */
    private static String validatedXml(CdsExportNativeDemographicAssembler.Result result)
            throws IOException {
        var bytes = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        bytes,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        List.of(
                                        new CdsExportXmlComposer.Section(
                                                new CdsExportIdentityDto(
                                                        HASH, CdsCategory.DEMOGRAPHICS, HASH),
                                                xml ->
                                                        new CdsExportDemographicXmlWriter()
                                                                .writeWithRelated(
                                                                        result.demographic(),
                                                                        result.pharmacy(),
                                                                        result.contacts(),
                                                                        result.professional(),
                                                                        "C27",
                                                                        xml)))
                                .iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(bytes.toByteArray()),
                        "C27",
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
