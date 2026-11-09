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

/** One protected captured source field; payload bytes live in the authenticated values stream. */
public record CdsExportCapturedFieldDto(String table, String recordKey, String column, Kind kind,
        long offset, long bytes, String sha256,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric exactNumeric) {
    public CdsExportCapturedFieldDto(String table, String recordKey, String column, Kind kind, long offset, long bytes, String sha256) {
        this(table, recordKey, column, kind, offset, bytes, sha256, null);
    }
    public enum Kind { SQL_NULL, UTF8, BINARY }
    public CdsExportCapturedFieldDto {
        if (table == null || !table.matches("[A-Za-z0-9_]{1,64}") || recordKey == null || recordKey.isEmpty()
                || recordKey.length() > 2048 || recordKey.codePoints().anyMatch(cp -> Character.isISOControl(cp) || cp >= 0xd800 && cp <= 0xdfff)
                || column == null || !column.matches("[A-Za-z0-9_]{1,128}") || kind == null
                || exactNumeric != null && kind != Kind.UTF8 || offset < 0 || bytes < 0 || bytes > Long.MAX_VALUE - offset || kind == Kind.SQL_NULL && bytes != 0
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURED_FIELD_INVALID");
        }
    }
    @Override public String toString() { return "CdsExportCapturedFieldDto[protected]"; }
}
