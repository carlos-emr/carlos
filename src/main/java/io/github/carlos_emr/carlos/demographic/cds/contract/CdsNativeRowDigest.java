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

import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Exact existing protocol-2 native-row receipt algorithm. Column order and actual JDBC
 * type/precision/scale participate in the hash. This contract never upgrades an old receipt,
 * supplies a missing baseline or proves field-level equivalence after a mismatch.
 */
public final class CdsNativeRowDigest {
    public record Column(String name, String type, int precision, int scale) {
        public Column {
            if (name == null
                    || name.isEmpty()
                    || name.length() > 128
                    || type == null
                    || type.isEmpty()
                    || type.length() > 128
                    || precision < 0
                    || scale < 0)
                throw new IllegalArgumentException("CDS_NATIVE_ROW_COLUMN_INVALID");
        }
    }

    private CdsNativeRowDigest() {}

    /**
     * {@code [0-9a-fA-F]{64}} without a regular expression: every captured cell of every receipt
     * is checked, many times per exported chart.
     */
    static boolean hex64(String value) {
        if (value.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = value.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F')) return false;
        }
        return true;
    }

    public static Hasher hasher(List<Column> columns) {
        return new Hasher(columns);
    }

    /** Bounded one-row accumulator. Any failure or completed digest permanently closes this instance. */
    public static final class Hasher {
        private final List<Column> columns;

        // Per-operation incremental digest, owned by this stream/row accumulator; never shared
        // singleton state.
        @SuppressWarnings("java:S9428")
        private final MessageDigest digest = CdsImportKeys.digest();

        private int position;
        private boolean closed;

        private Hasher(List<Column> columns) {
            this.columns = List.copyOf(columns);
            var names = new HashSet<String>();
            if (columns.isEmpty()
                    || columns.size() > 512
                    || columns.stream().anyMatch(column -> !names.add(column.name()))) {
                throw new IllegalArgumentException("CDS_NATIVE_ROW_COLUMNS_INVALID");
            }
            CdsImportKeys.add(digest, "carlos-native-row-v2");
        }

        public void append(boolean sqlNull, long bytes, String sha256) {
            try {
                if (closed
                        || position >= columns.size()
                        || bytes < 0
                        || !sqlNull && (sha256 == null || !hex64(sha256)))
                    invalid();
                if (sqlNull
                        && (bytes != 0
                                || sha256 != null
                                        && !sha256.equals(
                                                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")))
                    invalid();
                var column = columns.get(position++);
                CdsImportKeys.add(digest, column.name());
                CdsImportKeys.add(digest, column.type());
                CdsImportKeys.add(digest, Integer.toString(column.precision()));
                CdsImportKeys.add(digest, Integer.toString(column.scale()));
                digest.update(sqlNull ? (byte) 0 : (byte) 1);
                if (!sqlNull) {
                    CdsImportKeys.add(digest, Long.toString(bytes));
                    CdsImportKeys.add(digest, sha256.toLowerCase(Locale.ROOT));
                }
            } catch (RuntimeException | Error failure) {
                closed = true;
                throw failure;
            }
        }

        public String finish() {
            try {
                if (closed || position != columns.size()) invalid();
                closed = true;
                return HexFormat.of().formatHex(digest.digest());
            } catch (RuntimeException | Error failure) {
                closed = true;
                throw failure;
            }
        }

        private static void invalid() {
            throw new IllegalStateException("CDS_NATIVE_ROW_DIGEST_INVALID");
        }
    }
}
