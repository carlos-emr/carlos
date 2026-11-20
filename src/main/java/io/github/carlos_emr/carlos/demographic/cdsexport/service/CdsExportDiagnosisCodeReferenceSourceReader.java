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

import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDiagnosisCodeReferenceDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Public code-table descriptions of the codes a patient's current Disease Registry entries and
 * episodes name ({@code icd9}, {@code icd10}, {@code ichppccode} and the MSP
 * {@code diagnosticcode} table, through the fixed allowlist in
 * {@link io.github.carlos_emr.carlos.commn.dao.CdsExportDiagnosisCodeReadSupport}). The code
 * tables keep their REFERENCE ruling; this logical capture holds only the rows matching this
 * patient's codes, read in the same snapshot as the entries, so the description exported is the
 * one the chart showed. A code with no matching row yields no row here; the entry is still
 * exported with its code and an empty standard description.
 */
@Service
public class CdsExportDiagnosisCodeReferenceSourceReader implements CdsExportNativeSourceReader {
    public static final String TABLE = "diagnosis_code_reference";
    public static final Set<String> COLUMNS =
            Set.of(
                    "code",
                    "coding_system",
                    "description",
                    "reference_code",
                    "reference_id",
                    "reference_table");
    private final DxresearchDAO source;

    public CdsExportDiagnosisCodeReferenceSourceReader(DxresearchDAO source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public void authorizeReadScope(int patient, CdsExportRunControl control) {
        control.authorizeCategory(patient, CdsCategory.PROBLEM_LIST, false);
    }

    @Override
    public String table() {
        return TABLE;
    }

    @Override
    public Set<String> columns() {
        return COLUMNS;
    }

    @Override
    public Set<CdsCategory> categories() {
        return Set.of(CdsCategory.PROBLEM_LIST);
    }

    @Override
    public Set<String> requiredTables() {
        return CdsExportProblemRegistrySources.TABLES;
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        var fields =
                COLUMNS.stream()
                        .sorted()
                        .map(
                                name ->
                                        new CdsExportTableSchemaDto.Column(
                                                name,
                                                "reference_text_v1",
                                                name.equals("description") ? "YES" : "NO",
                                                null,
                                                null,
                                                null))
                        .toList();
        return new CdsExportTableSchemaDto(
                TABLE,
                "TRUSTED_PUBLIC_REFERENCE",
                fields,
                List.of("coding_system", "code", "reference_table", "reference_id"));
    }

    @Override
    public long count(int patient) {
        return rows(patient).size();
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        for (var entry : rows(patient).entrySet()) {
            var value = entry.getValue();
            output.row(
                    entry.getKey(),
                    row -> {
                        row.text("code", value.code());
                        row.text("coding_system", value.codingSystem());
                        row.text("description", value.description());
                        row.text("reference_code", value.referenceCode());
                        row.text("reference_id", value.referenceId());
                        row.text("reference_table", value.referenceTable());
                    });
        }
    }

    /** Rows in strictly ascending record-key order, as the capture composer requires. */
    private SortedMap<String, CdsExportDiagnosisCodeReferenceDto> rows(int patient) {
        var result = new TreeMap<String, CdsExportDiagnosisCodeReferenceDto>();
        for (var row : source.readCdsExportDiagnosisCodeReferences(patient))
            if (result.putIfAbsent(key(row), row) != null)
                throw new IllegalStateException("CDS_EXPORT_DIAGNOSIS_REFERENCE_INVALID");
        return result;
    }

    /** One stable key per code-table row and patient lookup key. */
    static String key(CdsExportDiagnosisCodeReferenceDto row) {
        return CdsImportKeys.hash(
                "carlos-diagnosis-code-reference-v1",
                row.codingSystem(),
                row.code(),
                row.referenceTable(),
                row.referenceId());
    }
}
