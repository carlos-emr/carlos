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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import java.util.List;
import java.util.Locale;

/**
 * Field witnesses from the same SQL projections as a protocol-2 native receipt. These
 * witnesses prove which SQL projections changed; they do not assert a clinical mapping.
 * Optional IEEE witnesses are separate from the unchanged SQL-display receipt and contain
 * only a codec-bound hash, never a dose or its raw bits.
 *
 * <p>A cell holds no value text, but its unsalted SHA-256 and byte length are value
 * equivalents for low-entropy columns (sex, birth date parts, health card or SIN number,
 * postal code, phone): enumeration recovers them. Treat a row state as PHI. It is stored only
 * sealed under a per-installation key ({@link CdsNativeBaselineCodec#seal}) and must never be
 * logged, rendered or written anywhere else in plaintext.
 */
public record CdsNativeRowState(
        String table, String id, List<CdsNativeRowDigest.Column> columns, List<Cell> cells) {
    private static final java.util.regex.Pattern TABLE =
            java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final java.util.regex.Pattern ID =
            java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,64}");
    public record Cell(
            boolean sqlNull, long bytes, String sha256, CdsExactNumeric.Witness exactNumeric) {
        public Cell(boolean sqlNull, long bytes, String sha256) {
            this(sqlNull, bytes, sha256, null);
        }

        public Cell {
            if (bytes < 0
                    || sqlNull && (bytes != 0 || sha256 != null || exactNumeric != null)
                    || !sqlNull && (sha256 == null || !CdsNativeRowDigest.hex64(sha256)))
                invalid();
            if (sha256 != null) sha256 = sha256.toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return "CdsNativeRowState.Cell[protected]";
        }
    }

    public CdsNativeRowState {
        if (table == null
                || !TABLE.matcher(table).matches()
                || id == null
                || !ID.matcher(id).matches()) invalid();
        columns = List.copyOf(columns);
        cells = List.copyOf(cells);
        if (columns.isEmpty() || columns.size() > 512 || columns.size() != cells.size()) invalid();
        CdsNativeRowDigest.hasher(columns);
        for (int i = 0; i < columns.size(); i++) {
            var witness = cells.get(i).exactNumeric();
            if (witness != null && witness.codec() != CdsExactNumeric.codec(columns.get(i).type()))
                invalid();
        }
    }

    public String digest() {
        var hash = CdsNativeRowDigest.hasher(columns);
        for (var cell : cells) hash.append(cell.sqlNull(), cell.bytes(), cell.sha256());
        return hash.finish();
    }

    private static void invalid() {
        throw new IllegalArgumentException("CDS_NATIVE_ROW_STATE_INVALID");
    }

    @Override
    public String toString() {
        return "CdsNativeRowState[protected]";
    }
}
