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
    private static final Set<String> INSTALLED = Set.of("cds_laboratory_result", "hl7TextMessage", "hl7TextInfo", "patientLabRouting", "casemgmt_issue", "issue", "casemgmt_issue_notes", "casemgmt_note_ext", "casemgmt_note", "retained_import_options", "Contact", "DemographicContact", "demographicPharmacy", "pharmacyInfo", "clinical_timezone", "preventionsExt", "prevention_precision", "prevention_configuration", "preventions", "preventions_scope", "CVCImmunization", "CVCMapping", "demographic_merged", "demographicArchive", "drugs", "appointmentArchive", "provider", "appointment_status", "appointment", "demographic", "demographicExt", "demographiccust", "allergies", "partial_date",
            "cds_import_file", "cds_import_file_reference", "cds_import_field", "cds_import_unit", "cds_import_patient", "cds_import_preparation", "cds_import_dependency", "cds_import_run");
    public String verify(CdsExportTableSchemaDto actual, Set<String> columns) throws IOException {
        return verify(actual == null ? null : actual.table(), actual, columns);
    }
    public String verify(String scope, CdsExportTableSchemaDto actual, Set<String> columns) throws IOException {
        if (scope == null || actual == null || !INSTALLED.contains(scope)
                || !physical(scope).equals(actual.table())) invalid();
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/cds-export/source-schema/" + scope + ".json")) {
            if (input == null) throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING");
            bytes = input.readNBytes(1_048_577); if (bytes.length > 1_048_576) invalid();
        }
        var expected = JSON.readValue(bytes, CdsExportTableSchemaDto.class);
        var allColumns = Set.copyOf(actual.columns().stream().map(CdsExportTableSchemaDto.Column::name).toList());
        var demographicCust = new java.util.HashSet<>(allColumns); demographicCust.remove("cust3");
        boolean qualifiedColumns = scope.equals("preventions_scope") ? CdsExportPreventionScopePlanner.COLUMNS.equals(columns)
                : actual.table().equals("provider") ? io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS.equals(columns)
                : allColumns.equals(columns) || actual.table().equals("demographiccust") && demographicCust.equals(columns);
        if (!actual.equals(expected) || !(scope.equals("retained_import_options")?"TRUSTED_IMPORT_PROVENANCE":Set.of("prevention_configuration", "clinical_timezone").contains(scope)?"TRUSTED_CONFIGURATION":"InnoDB").equals(actual.engine()) || !qualifiedColumns) invalid();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    /** Mapping/recovery verifies the captured schema contract without rereading mutable clinical tables. */
    public String expectedSha256(String table, Set<String> columns) throws IOException {
        if (!INSTALLED.contains(table)) invalid();
        try (var input = getClass().getResourceAsStream("/cds-export/source-schema/" + table + ".json")) {
            if (input == null) throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING");
            byte[] bytes = input.readNBytes(1_048_577); if (bytes.length > 1_048_576) invalid();
            return verify(table, JSON.readValue(bytes, CdsExportTableSchemaDto.class), columns);
        }
    }
    private static String physical(String scope){return switch (scope){case "preventions_scope"-> "preventions"; case "prevention_precision"-> "partial_date"; default-> scope; }; }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW"); }
}
