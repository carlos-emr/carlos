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

/** Actual captured projection digest. A partial digest never attests a historical whole-row receipt; unavailable metadata remains explicit. */
public record CdsExportNativeRowReceiptDto(String table, String id, String recordKey, String digest,
        io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState state, boolean partial) {
    public CdsExportNativeRowReceiptDto(String table, String id, String recordKey, String digest) { this(table, id, recordKey, digest, null, false); }
    public CdsExportNativeRowReceiptDto(String table, String id, String recordKey, String digest,
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState state) { this(table, id, recordKey, digest, state, false); }
    public CdsExportNativeRowReceiptDto {
        if (table == null || !table.matches("[A-Za-z0-9_]{1,64}") || id == null || !id.matches("[A-Za-z0-9_-]{1,64}")
                || recordKey == null || recordKey.isEmpty() || recordKey.length() > 2048 || digest != null && !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_RECEIPT_INVALID");
        }
        boolean selectedCust = "demographiccust".equals(table) && state != null && java.util.Set.copyOf(state.columns().stream().map(column -> column.name()).toList())
                .equals(java.util.Set.of("content", "cust1", "cust2", "cust4", "demographic_no"));
        if (partial != selectedCust) {
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_PARTIAL_RECEIPT_INVALID");
        }
        if (state != null && (!table.equals(state.table()) || !id.equals(state.id()) || digest == null || !digest.equals(state.digest()))) {
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_RECEIPT_STATE_INVALID");
        }
    }
    @Override public String toString() { return "CdsExportNativeRowReceiptDto[protected]"; }
}
