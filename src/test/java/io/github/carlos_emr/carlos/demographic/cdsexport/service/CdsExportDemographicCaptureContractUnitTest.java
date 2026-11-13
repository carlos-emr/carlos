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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent installed-schema column coverage; a new physical field cannot silently evade raw capture. */
class CdsExportDemographicCaptureContractUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"on", "bc"})
    void shouldAccountForEveryPhysicalDemographicColumn_inBothFreshContracts(String province)
            throws Exception {
        var schema =
                new ObjectMapper()
                        .readTree(
                                Path.of("database/mysql/schema-contract/" + province + ".json")
                                        .toFile())
                        .path("schema");
        var actual = new java.util.HashSet<String>();
        for (var column : schema.path("columns")) {
            if (!column.get(0).asText().equals("demographic")) continue;
            actual.add(column.get(1).asText());
            if (!column.get(6).isNull())
                assertThat(column.get(6).asText()).isIn("utf8mb4", "ascii");
        }
        var captured = new java.util.HashSet<>(CdsExportDemographicRowDto.COLUMNS);
        captured.add("demographic_no");
        assertThat(captured).isEqualTo(actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"on", "bc"})
    void shouldPinTheWholeReviewedReadSchema_inBothIndependentFreshContracts(String province)
            throws Exception {
        var json = new ObjectMapper();
        var schema =
                json.readTree(
                                Path.of("database/mysql/schema-contract/" + province + ".json")
                                        .toFile())
                        .path("schema");
        var actual =
                new java.util.ArrayList<
                        io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto.Column>();
        for (var column : schema.path("columns")) {
            if (!column.get(0).asText().equals("demographic")) continue;
            actual.add(
                    new io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto.Column(
                            column.get(1).asText(),
                            column.get(2).asText(),
                            column.get(3).asText(),
                            column.get(6).isNull() ? null : column.get(6).asText(),
                            column.get(7).isNull() ? null : column.get(7).asText(),
                            column.get(8).isNull() ? null : column.get(8).asText()));
        }
        var pinned =
                new io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto(
                        "demographic", "InnoDB", actual, java.util.List.of("demographic_no"));
        var columns = new java.util.HashSet<>(CdsExportDemographicRowDto.COLUMNS);
        columns.add("demographic_no");
        assertThat(new CdsExportSourceSchemaValidator().verify(pinned, columns))
                .matches("[0-9a-f]{64}");
        var keys = new java.util.ArrayList<String>();
        for (var key : schema.path("keys")) {
            if (key.get(0).asText().equals("demographic") && key.get(1).asText().equals("PRIMARY"))
                keys.add(key.get(3).asText());
        }
        assertThat(keys).containsExactly("demographic_no");
        assertThatThrownBy(
                        () ->
                                new CdsExportSourceSchemaValidator()
                                        .verify(
                                                new io.github.carlos_emr.carlos.commn.dto
                                                        .CdsExportTableSchemaDto(
                                                        "demographic",
                                                        "InnoDB",
                                                        actual,
                                                        java.util.List.of()),
                                                columns))
                .hasMessage("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");
        assertThatThrownBy(
                        () ->
                                new CdsExportSourceSchemaValidator()
                                        .verify(
                                                new io.github.carlos_emr.carlos.commn.dto
                                                        .CdsExportTableSchemaDto(
                                                        "demographic",
                                                        "InnoDB",
                                                        actual,
                                                        java.util.List.of(
                                                                "demographic_no", "first_name")),
                                                columns))
                .hasMessage("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");

        actual.removeFirst();
        assertThatThrownBy(
                        () ->
                                new CdsExportSourceSchemaValidator()
                                        .verify(
                                                new io.github.carlos_emr.carlos.commn.dto
                                                        .CdsExportTableSchemaDto(
                                                        "demographic",
                                                        "InnoDB",
                                                        actual,
                                                        java.util.List.of("demographic_no")),
                                                columns))
                .hasMessage("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");
    }

    @ParameterizedTest
    @ValueSource(strings = {"on", "bc"})
    void shouldCoverEveryAdjunctColumnAndPrimaryKey_inBothIndependentRegionalContracts(
            String province) throws Exception {
        var schema =
                new ObjectMapper()
                        .readTree(
                                Path.of("database/mysql/schema-contract/" + province + ".json")
                                        .toFile())
                        .path("schema");
        for (var plan :
                io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                        .values()) {
            if (plan
                    == io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                            .DOCUMENT_FILES) {
                // The file-pointer projection CdsExportDocumentFileSourceReader pages: it is
                // never a captured table (capture reads and schema-verifies DOCUMENTS), so it
                // only has to stay a projection of that verified table.
                var documents =
                        io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport
                                .Plan.DOCUMENTS;
                assertThat(plan.table()).isEqualTo(documents.table());
                assertThat(documents.columns()).containsAll(plan.columns());
                continue;
            }
            if (plan
                    == io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                            .HRM_REPORT_FILES) {
                // The stored-message pointer CdsExportHospitalReportFileSourceReader pages: a
                // projection of the verified HRM_REPORTS table, never a captured table itself.
                var reports =
                        io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport
                                .Plan.HRM_REPORTS;
                assertThat(plan.table()).isEqualTo(reports.table());
                assertThat(reports.columns()).containsAll(plan.columns());
                continue;
            }
            var columns =
                    new java.util.ArrayList<
                            io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto.Column>();
            var keys = new java.util.ArrayList<String>();
            for (var column : schema.path("columns"))
                if (column.get(0).asText().equals(plan.table())) {
                    columns.add(
                            new io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto
                                    .Column(
                                    column.get(1).asText(),
                                    column.get(2).asText(),
                                    column.get(3).asText(),
                                    column.get(6).isNull() ? null : column.get(6).asText(),
                                    column.get(7).isNull() ? null : column.get(7).asText(),
                                    column.get(8).isNull() ? null : column.get(8).asText()));
                }
            for (var key : schema.path("keys"))
                if (key.get(0).asText().equals(plan.table())
                        && key.get(1).asText().equals("PRIMARY")) keys.add(key.get(3).asText());
            var actual =
                    new io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto(
                            plan.table(), "InnoDB", columns, keys);
            boolean preventionMetadata =
                    plan
                            == io.github.carlos_emr.carlos.commn.dao
                                    .CdsExportPatientTableReadSupport.Plan.PREVENTION_SCOPE;
            String scope = preventionMetadata ? "preventions_scope" : plan.table();
            assertThat(
                            new CdsExportSourceSchemaValidator()
                                    .verify(scope, actual, java.util.Set.copyOf(plan.columns())))
                    .matches("[0-9a-f]{64}");
            var expectedProjection =
                    columns.stream()
                            .map(
                                    io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto
                                                    .Column
                                            ::name)
                            .filter(
                                    name ->
                                            !preventionMetadata
                                                    || java.util.Set.of(
                                                                    "demographic_no",
                                                                    "id",
                                                                    "prevention_type",
                                                                    "programNo",
                                                                    "restrictToProgram",
                                                                    "snomedId")
                                                            .contains(name))
                            .filter(
                                    name ->
                                            plan
                                                            != io.github.carlos_emr.carlos.commn.dao
                                                                    .CdsExportPatientTableReadSupport
                                                                    .Plan
                                                                    .DEMOGRAPHIC_CUST_DEMOGRAPHICS
                                                    || !name.equals("cust3"))
                            .filter(
                                    name ->
                                            plan
                                                            != io.github.carlos_emr.carlos.commn.dao
                                                                    .CdsExportPatientTableReadSupport
                                                                    .Plan.PUBLIC_PROVIDERS
                                                    || java.util.Set.of(
                                                                    "first_name",
                                                                    "last_name",
                                                                    "ohip_no",
                                                                    "practitionerNo",
                                                                    "practitionerNoType",
                                                                    "provider_no")
                                                            .contains(name))
                            .toList();
            assertThat(plan.columns()).containsExactlyElementsOf(expectedProjection);
            if (preventionMetadata) {
                var full =
                        new java.util.HashSet<>(
                                columns.stream()
                                        .map(
                                                io.github.carlos_emr.carlos.commn.dto
                                                                .CdsExportTableSchemaDto.Column
                                                        ::name)
                                        .toList());
                assertThat(full).hasSize(16);
                full.removeAll(plan.columns());
                assertThat(full)
                        .containsExactlyInAnyOrder(
                                "creation_date",
                                "creator",
                                "deleted",
                                "lastUpdateDate",
                                "never",
                                "next_date",
                                "prevention_date",
                                "provider_name",
                                "provider_no",
                                "refused");
                assertThatThrownBy(
                                () ->
                                        new CdsExportSourceSchemaValidator()
                                                .verify(
                                                        actual,
                                                        java.util.Set.copyOf(plan.columns())))
                        .hasMessage("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");
                assertThatThrownBy(
                                () ->
                                        new CdsExportSourceSchemaValidator()
                                                .verify(
                                                        scope,
                                                        actual,
                                                        java.util.Set.copyOf(
                                                                columns.stream()
                                                                        .map(
                                                                                io.github.carlos_emr
                                                                                                .carlos
                                                                                                .commn
                                                                                                .dto
                                                                                                .CdsExportTableSchemaDto
                                                                                                .Column
                                                                                        ::name)
                                                                        .toList())))
                        .hasMessage("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");
            }
            if (plan
                    == io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                            .PUBLIC_PROVIDERS) {
                var allColumns =
                        java.util.Set.copyOf(
                                columns.stream()
                                        .map(
                                                io.github.carlos_emr.carlos.commn.dto
                                                                .CdsExportTableSchemaDto.Column
                                                        ::name)
                                        .toList());
                assertThatThrownBy(
                                () ->
                                        new CdsExportSourceSchemaValidator()
                                                .verify(actual, allColumns))
                        .hasMessage("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");
            }
        }
    }

    @Test
    void shouldFreezeNullEmptyAndFalseAsDifferentRawValues_inDeterministicColumnOrder() {
        var original = values();
        original.put("first_name", "FAKE-CDS");
        original.put("email", "");
        original.put("consentToUseEmailForCare", "0");
        var captured = new CdsExportDemographicRowDto(27, original);
        original.put("first_name", "changed");
        assertThat(captured.fields())
                .containsEntry("first_name", "FAKE-CDS")
                .containsEntry("email", "")
                .containsEntry("month_of_birth", null)
                .containsEntry("consentToUseEmailForCare", "0");
        assertThat(captured.fields().keySet())
                .containsExactlyElementsOf(CdsExportDemographicRowDto.COLUMNS);
        assertThatThrownBy(() -> captured.fields().put("email", "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(captured.toString()).doesNotContain("FAKE-CDS", "first_name");
    }

    @Test
    void shouldRejectMissingOrUnexpectedFields_ratherThanInventOrDropTheirValues() {
        var missing = values();
        missing.remove("phone");
        assertThatThrownBy(() -> new CdsExportDemographicRowDto(27, missing))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
        var added = values();
        added.put("future_clinical_fact", "available");
        assertThatThrownBy(() -> new CdsExportDemographicRowDto(27, added))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
    }

    private LinkedHashMap<String, String> values() {
        var fields = new LinkedHashMap<String, String>();
        for (String column : CdsExportDemographicRowDto.COLUMNS.reversed())
            fields.put(column, null);
        return fields;
    }
}
