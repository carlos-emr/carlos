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
    void shouldRefuseUnrepresentedDemographicHistory_insteadOfDroppingAnArchive() {
        rows.get("demographicArchive")
                .add(
                        new CdsExportCapturedTextRowDto(
                                "demographicArchive", "archive", Map.of("id", "1")));
        assertThatThrownBy(this::assemble).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseAForeignPatientOrAChangedRowIdentity() {
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
    void shouldVerifyTheSelectedNativeNoteProjectionWithoutBorrowingAWholeRowReceipt()
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
    void shouldRefuseSubstitutedNativeValuesBeforeAnyTypedXmlIsEmitted() {
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
    void shouldAccountForBothPrimaryAndEnrolledUsesOfTheSameCapturedPhysician() throws Exception {
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
}
