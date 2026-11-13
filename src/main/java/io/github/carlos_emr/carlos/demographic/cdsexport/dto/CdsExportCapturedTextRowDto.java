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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** One bounded authenticated native text row; SQL null and empty text remain distinct. */
public record CdsExportCapturedTextRowDto(String table, String recordKey, Map<String, String> fields,
        Map<String, io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric> exactNumbers) {
    private static final java.util.regex.Pattern TABLE = java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final java.util.regex.Pattern COLUMN = java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,128}");
    public CdsExportCapturedTextRowDto(String table, String recordKey, Map<String, String> fields) { this(table, recordKey, fields, Map.of()); }
    public CdsExportCapturedTextRowDto {
        if (table == null || !TABLE.matcher(table).matches() || recordKey == null || recordKey.isEmpty() || recordKey.length() > 2048
                || !CdsExportCapturedFieldDto.safeKey(recordKey)
                || fields == null || fields.isEmpty() || fields.size() > 4096
                || fields.keySet().stream().anyMatch(column -> column == null || !COLUMN.matcher(column).matches())) {
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_INVALID");
        }
        // Sorted, like fields: both are serialized into residual JSON that must not change between runs.
        exactNumbers = Collections.unmodifiableSortedMap(new TreeMap<>(Map.copyOf(exactNumbers)));
        if (!fields.keySet().containsAll(exactNumbers.keySet()))
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_NUMERIC_SCOPE_INVALID");
        for (String column : exactNumbers.keySet()) if (fields.get(column) == null) throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_NUMERIC_SCOPE_INVALID");
        fields = Collections.unmodifiableMap(new TreeMap<>(fields));
    }
    @Override public String toString() { return "CdsExportCapturedTextRowDto[protected]"; }
}
