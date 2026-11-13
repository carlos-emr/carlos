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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/** Exact finite source-read contracts; unknown columns/types/engines require a reviewed adapter. */
public final class CdsExportSourceSchemaValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> INSTALLED =
            Set.of(
                    "clinical_source_catalogue",
                    CdsExportListedSourceReader.TABLE,
                    "HRMDocumentToDemographic",
                    "HRMDocument",
                    "HRMDocumentComment",
                    "HRMDocumentSubClass",
                    "HRMDocumentToProvider",
                    CdsExportHospitalReportSources.FILES,
                    "native_document_files",
                    CdsExportEformRenderingSourceReader.TABLE,
                    "document",
                    "ctl_document",
                    "measurements",
                    "measurementsExt",
                    "measurementsDeleted",
                    "measurementType",
                    "cds_laboratory_result",
                    "hl7TextMessage",
                    "hl7TextInfo",
                    "patientLabRouting",
                    "providerLabRouting",
                    CdsExportDocumentRouteSourceReader.TABLE,
                    "document_review",
                    "DocumentExtraReviewer",
                    "casemgmt_issue",
                    "issue",
                    "casemgmt_issue_notes",
                    "casemgmt_note_ext",
                    "casemgmt_note_link",
                    "casemgmt_note",
                    CdsExportProblemRegistrySources.REGISTRY,
                    CdsExportProblemRegistrySources.PRECISION,
                    CdsExportProblemRegistrySources.EPISODES,
                    CdsExportProblemRegistrySources.REFERENCES,
                    "retained_import_options",
                    "Contact",
                    "DemographicContact",
                    "demographicPharmacy",
                    "pharmacyInfo",
                    "clinical_timezone",
                    "preventionsExt",
                    "prevention_precision",
                    "prevention_configuration",
                    "preventions",
                    "preventions_scope",
                    CdsExportPreventionSources.SCREENINGS,
                    CdsExportPreventionSources.SCREENING_EXTENSIONS,
                    CdsExportPreventionSources.SCREENING_PRECISION,
                    "CVCImmunization",
                    "CVCMapping",
                    "demographic_merged",
                    "demographicArchive",
                    "drugs",
                    "drugReason",
                    "prescription",
                    "medication_reference",
                    "appointmentArchive",
                    "provider",
                    "appointment_status",
                    "LookupListItem",
                    "appointment",
                    "demographic",
                    "demographicExt",
                    "demographiccust",
                    "allergies",
                    "partial_date",
                    "cds_import_file",
                    "cds_import_file_reference",
                    "cds_import_field",
                    "cds_import_unit",
                    "cds_import_patient",
                    "cds_import_preparation",
                    "cds_import_dependency",
                    "cds_import_run");

    public String verify(CdsExportTableSchemaDto actual, Set<String> columns) throws IOException {
        return verify(actual == null ? null : actual.table(), actual, columns);
    }

    public String verify(String scope, CdsExportTableSchemaDto actual, Set<String> columns)
            throws IOException {
        if (dataSummary(scope)) return verifyDataSummary(scope, actual, columns);
        if (scope == null
                || actual == null
                || !INSTALLED.contains(scope)
                || !physical(scope).equals(actual.table())) invalid();
        byte[] bytes;
        try (var input =
                getClass().getResourceAsStream("/cds-export/source-schema/" + scope + ".json")) {
            if (input == null) throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING");
            bytes = input.readNBytes(1_048_577);
            if (bytes.length > 1_048_576) invalid();
        }
        var expected = JSON.readValue(bytes, CdsExportTableSchemaDto.class);
        var allColumns =
                Set.copyOf(
                        actual.columns().stream()
                                .map(CdsExportTableSchemaDto.Column::name)
                                .toList());
        var demographicCust = new java.util.HashSet<>(allColumns);
        demographicCust.remove("cust3");
        boolean qualifiedColumns =
                scope.equals("preventions_scope")
                        ? CdsExportPreventionScopePlanner.COLUMNS.equals(columns)
                        : actual.table().equals("provider")
                                ? io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto
                                        .COLUMNS
                                        .equals(columns)
                                : allColumns.equals(columns)
                                        || actual.table().equals("demographiccust")
                                                && demographicCust.equals(columns);
        if (!actual.equals(expected)
                || !(scope.equals("clinical_source_catalogue")
                                ? "TRUSTED_SOURCE_CATALOGUE"
                                : scope.equals("native_document_files")
                                                || scope.equals(
                                                        CdsExportHospitalReportSources.FILES)
                                        ? "TRUSTED_NATIVE_DOCUMENT_STORAGE"
                                        : scope.equals(CdsExportListedSourceReader.TABLE)
                                        ? "TRUSTED_LISTED_SOURCE_COUNTS"
                                        : scope.equals(CdsExportEformRenderingSourceReader.TABLE)
                                        ? "TRUSTED_EFORM_RENDERINGS"
                                        : scope.equals("medication_reference")
                                                        || scope.equals(
                                                                CdsExportProblemRegistrySources
                                                                        .REFERENCES)
                                                ? "TRUSTED_PUBLIC_REFERENCE"
                                                : scope.equals("retained_import_options")
                                                        ? "TRUSTED_IMPORT_PROVENANCE"
                                                        : Set.of(
                                                                                "prevention_configuration",
                                                                                "clinical_timezone")
                                                                        .contains(scope)
                                                                ? "TRUSTED_CONFIGURATION"
                                                                : "InnoDB")
                        .equals(actual.engine())
                || !qualifiedColumns) invalid();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Every schema digest a capture of this scope may legitimately carry. A data-summary table has
     * one per pinned profile that has it (the legacy formRourke2009 differs only by engine) and
     * one for its absence from a profile that lacks it; any other scope has exactly one.
     */
    public Set<String> acceptedSha256(String table, Set<String> columns) throws IOException {
        if (!dataSummary(table)) return Set.of(expectedSha256(table, columns));
        var accepted = new java.util.TreeSet<String>();
        for (var candidate : dataSummaryCandidates(table))
            accepted.add(verifyDataSummary(table, candidate, columns));
        return Set.copyOf(accepted);
    }

    /** Mapping/recovery verifies the captured schema contract without rereading mutable clinical tables. */
    public String expectedSha256(String table, Set<String> columns) throws IOException {
        if (dataSummary(table)) {
            var accepted = acceptedSha256(table, columns);
            if (accepted.size() != 1) invalid();
            return accepted.iterator().next();
        }
        if (!INSTALLED.contains(table)) invalid();
        try (var input =
                getClass().getResourceAsStream("/cds-export/source-schema/" + table + ".json")) {
            if (input == null) throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING");
            byte[] bytes = input.readNBytes(1_048_577);
            if (bytes.length > 1_048_576) invalid();
            return verify(table, JSON.readValue(bytes, CdsExportTableSchemaDto.class), columns);
        }
    }

    private static boolean dataSummary(String scope) {
        return scope != null
                && io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue
                                .current()
                                .tables()
                                .get(scope)
                        == io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportSourceCatalogue.Disposition.DOCUMENT;
    }

    /**
     * A data-summary table is bound to the pinned physical contract itself: the actual structure
     * must equal one profile's table exactly, or be absent where a pinned profile has no such
     * table. All columns are captured.
     */
    private static java.util.List<CdsExportTableSchemaDto> dataSummaryCandidates(String table) {
        var result = new java.util.ArrayList<CdsExportTableSchemaDto>();
        boolean absent = false;
        for (var profile :
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue
                        .current()
                        .profiles()) {
            var schema = profile.tables().get(table);
            if (schema == null) absent = true;
            else if (!result.contains(schema)) result.add(schema);
        }
        if (absent)
            result.add(
                    new CdsExportTableSchemaDto(
                            table,
                            io.github.carlos_emr.carlos.commn.dao.CdsExportDataSummaryReadDao.ABSENT,
                            java.util.List.of(),
                            java.util.List.of()));
        return java.util.List.copyOf(result);
    }

    private static String verifyDataSummary(
            String table, CdsExportTableSchemaDto actual, Set<String> columns) throws IOException {
        var candidates = dataSummaryCandidates(table);
        var physical =
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue
                        .current()
                        .physical(table)
                        .orElse(null);
        if (actual == null
                || !candidates.contains(actual)
                || physical == null
                || !columns.equals(
                        Set.copyOf(
                                physical.columns().stream()
                                        .map(CdsExportTableSchemaDto.Column::name)
                                        .toList()))) invalid();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(JSON.writeValueAsBytes(actual)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String physical(String scope) {
        if (scope.equals(CdsExportDocumentRouteSourceReader.TABLE)) return "providerLabRouting";
        return scope.equals("preventions_scope")
                ? "preventions"
                : CdsExportProblemRegistrySources.PRECISION.equals(scope)
                        ? CdsExportProblemRegistrySources.schemaTable(scope)
                        : CdsExportPreventionSources.schemaTable(scope);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");
    }
}
