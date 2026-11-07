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

import java.util.Map;
import java.util.Set;

/** Completed authenticated scan counts. Partial projections are separate from available whole-row receipts and unavailable metadata. */
public record CdsExportNativeRowReceiptsDto(String sourceManifestSha256, Map<String, Long> rows,
        long available, long unavailable, long partialRows, Set<String> unavailableTables) {
    public CdsExportNativeRowReceiptsDto(String sourceManifestSha256, Map<String, Long> rows,
            long available, long unavailable, Set<String> unavailableTables) { this(sourceManifestSha256, rows, available, unavailable, 0, unavailableTables); }
    public CdsExportNativeRowReceiptsDto {
        if (sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}") || available < 0 || unavailable < 0 || partialRows < 0) {
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_RECEIPTS_INVALID");
        }
        rows = Map.copyOf(rows); unavailableTables = Set.copyOf(unavailableTables); long total = 0;
        for (long count : rows.values()) { if (count < 0) throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_RECEIPTS_INVALID"); total = Math.addExact(total, count); }
        if (Math.addExact(Math.addExact(available, unavailable), partialRows) != total || !rows.keySet().containsAll(unavailableTables)) {
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_RECEIPTS_INVALID");
        }
    }
    @Override public String toString() { return "CdsExportNativeRowReceiptsDto[protected]"; }
}
