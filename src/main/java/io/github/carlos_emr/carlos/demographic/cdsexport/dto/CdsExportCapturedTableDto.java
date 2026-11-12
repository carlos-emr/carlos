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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.Set;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column;

/** Exact captured table scope and source-count receipt; semantic completeness needs the independent clinical inventory. */
public record CdsExportCapturedTableDto(
        Set<String> columns,
        long rows,
        String schemaSha256,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Column> receiptColumns,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CdsExportNoteAccessDto> noteAccess) {
    /** Existing source tables keep their original manifest shape when they have no note access dependencies. */
    public CdsExportCapturedTableDto(
            Set<String> columns, long rows, String schemaSha256, List<Column> receiptColumns) {
        this(columns, rows, schemaSha256, receiptColumns, List.of());
    }

    /** Previously captured manifests retain their missing-metadata meaning; no current metadata is substituted. */
    public CdsExportCapturedTableDto(Set<String> columns, long rows, String schemaSha256) {
        this(columns, rows, schemaSha256, List.of());
    }

    public CdsExportCapturedTableDto {
        columns = Set.copyOf(columns);
        receiptColumns = receiptColumns == null ? List.of() : List.copyOf(receiptColumns);
        noteAccess = noteAccess == null ? List.of() : List.copyOf(noteAccess);
        if (noteAccess.size() > 8192
                || !noteAccess.isEmpty()
                        && (noteAccess.size() != rows
                                || !columns.containsAll(
                                        Set.of(
                                                "note_id",
                                                "demographic_no",
                                                "program_no",
                                                "reporter_caisi_role",
                                                "locked"))
                                || noteAccess.stream()
                                                .map(CdsExportNoteAccessDto::noteId)
                                                .distinct()
                                                .count()
                                        != noteAccess.size())) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_NOTE_ACCESS_INVALID");
        }
        if (!receiptColumns.isEmpty()
                && (receiptColumns.size() > 512
                        || receiptColumns.size()
                                != Set.copyOf(receiptColumns.stream().map(Column::name).toList())
                                        .size()
                        || !columns.containsAll(
                                receiptColumns.stream().map(Column::name).toList()))) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_RECEIPT_RECIPE_INVALID");
        }
        if (columns.isEmpty()
                || columns.size() > 4096
                || rows < 0
                || columns.stream().anyMatch(column -> !column.matches("[A-Za-z0-9_]{1,128}"))
                || schemaSha256 == null
                || !schemaSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_PLAN_INVALID");
        }
    }
}
