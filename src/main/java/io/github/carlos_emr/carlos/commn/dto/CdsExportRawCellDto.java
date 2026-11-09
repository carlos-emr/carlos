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
package io.github.carlos_emr.carlos.commn.dto;

/** Exact bounded source descriptor. Large values are fetched in snapshot-bound byte ranges. */
public record CdsExportRawCellDto(boolean sqlNull, long bytes, String sha256, byte[] inlineBytes,
        io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric exactNumeric) {
    public CdsExportRawCellDto(boolean sqlNull, long bytes, String sha256, byte[] inlineBytes) { this(sqlNull, bytes, sha256, inlineBytes, null); }
    public static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    public CdsExportRawCellDto {
        if (bytes < 0 || bytes > (1L << 40) || sha256 == null || !sha256.matches("[0-9a-f]{64}")
                || inlineBytes != null && (inlineBytes.length != bytes || inlineBytes.length > 16_384)
                || sqlNull && (bytes != 0 || inlineBytes != null || exactNumeric != null || !EMPTY_SHA256.equals(sha256))
                || !sqlNull && bytes == 0 && (inlineBytes == null || !EMPTY_SHA256.equals(sha256))) {
            throw new IllegalArgumentException("CDS_EXPORT_RAW_CELL_INVALID");
        }
        if (inlineBytes != null) inlineBytes = inlineBytes.clone();
    }
    @Override public byte[] inlineBytes() { return inlineBytes == null ? null : inlineBytes.clone(); }
    @Override public boolean equals(Object other) {
        return other instanceof CdsExportRawCellDto cell && sqlNull == cell.sqlNull && bytes == cell.bytes
                && sha256.equals(cell.sha256) && java.util.Objects.equals(exactNumeric, cell.exactNumeric) && java.util.Arrays.equals(inlineBytes, cell.inlineBytes);
    }
    @Override public int hashCode() { return 31 * java.util.Objects.hash(sqlNull, bytes, sha256, exactNumeric) + java.util.Arrays.hashCode(inlineBytes); }
    @Override public String toString() { return "CdsExportRawCellDto[protected]"; }
}
