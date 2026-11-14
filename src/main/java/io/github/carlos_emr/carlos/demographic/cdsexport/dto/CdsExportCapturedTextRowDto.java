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
        if (!(fields instanceof PackedFields)) fields = Collections.unmodifiableMap(new TreeMap<>(fields));
    }

    /**
     * The same row held compactly: its column names as one sorted array and its values as one
     * UTF-8 byte array, in place of a map entry and a string for every field. An in-memory index of
     * many rows uses it; a long imported chart keeps about a million retained fields, and held as
     * map entries and strings they cost three times their text. Every read decodes the value
     * again, so the row reads exactly as the row it was packed from. {@code layout} is a packed
     * row of the same table, or null: when its columns are the same, its column array is shared.
     * A row whose text cannot be encoded exactly is returned as it is.
     */
    public CdsExportCapturedTextRowDto packed(CdsExportCapturedTextRowDto layout) {
        if (fields instanceof PackedFields) return this;
        String[] columns = fields.keySet().toArray(String[]::new);
        if (layout != null && layout.fields instanceof PackedFields shared && java.util.Arrays.equals(shared.columns, columns))
            columns = shared.columns;
        var encoder = java.nio.charset.StandardCharsets.UTF_8.newEncoder();
        var bytes = new java.io.ByteArrayOutputStream();
        int[] ends = new int[columns.length];
        try {
            for (int i = 0; i < columns.length; i++) {
                String value = fields.get(columns[i]);
                if (value != null) {
                    var encoded = encoder.encode(java.nio.CharBuffer.wrap(value));
                    bytes.write(encoded.array(), encoded.arrayOffset() + encoded.position(), encoded.remaining());
                }
                // A SQL null ends where the previous value ended and is marked by the complement.
                ends[i] = value == null ? ~bytes.size() : bytes.size();
            }
        } catch (java.nio.charset.CharacterCodingException inexact) {
            return this;
        }
        return new CdsExportCapturedTextRowDto(table, recordKey, new PackedFields(columns, bytes.toByteArray(), ends, null, null), exactNumbers);
    }

    /**
     * The same row with one column read through {@code view}: the row keeps the stored text,
     * packed as {@link #packed} keeps it, and every read of that column returns {@code view} of it
     * (SQL null stays null). An in-memory index uses it to keep a large derived value, such as an
     * unsealed witness, out of the heap until a reader asks for it. {@code view} must be a pure
     * function of the stored text that was already applied successfully once; it is applied again
     * on every read.
     */
    public CdsExportCapturedTextRowDto withDerived(String column, java.util.function.UnaryOperator<String> view, CdsExportCapturedTextRowDto layout) {
        java.util.Objects.requireNonNull(view);
        if (!fields.containsKey(column) || exactNumbers.containsKey(column)
                || !(packed(layout).fields instanceof PackedFields stored) || stored.derived != null)
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_ROW_INVALID");
        return new CdsExportCapturedTextRowDto(table, recordKey,
                new PackedFields(stored.columns, stored.bytes, stored.ends, column, view), exactNumbers);
    }

    /** A sorted read-only map over packed values; its one derived column is computed on each read. */
    private static final class PackedFields extends java.util.AbstractMap<String, String> {
        private final String[] columns;
        private final byte[] bytes;
        private final int[] ends;
        private final String derived;
        private final java.util.function.UnaryOperator<String> view;

        PackedFields(String[] columns, byte[] bytes, int[] ends, String derived, java.util.function.UnaryOperator<String> view) {
            this.columns = columns;
            this.bytes = bytes;
            this.ends = ends;
            this.derived = derived;
            this.view = view;
        }

        private String read(int index) {
            if (ends[index] < 0) return null;
            int start = index == 0 ? 0 : ends[index - 1] < 0 ? ~ends[index - 1] : ends[index - 1];
            String stored = new String(bytes, start, ends[index] - start, java.nio.charset.StandardCharsets.UTF_8);
            return columns[index].equals(derived) ? view.apply(stored) : stored;
        }

        private int index(Object key) {
            return key instanceof String name ? java.util.Arrays.binarySearch(columns, name) : -1;
        }

        @Override public String get(Object key) { int index = index(key); return index < 0 ? null : read(index); }
        @Override public boolean containsKey(Object key) { return index(key) >= 0; }
        @Override public int size() { return columns.length; }

        @Override
        public java.util.Set<String> keySet() {
            return new java.util.AbstractSet<>() {
                @Override public int size() { return columns.length; }
                @Override public boolean contains(Object key) { return index(key) >= 0; }
                @Override public java.util.Iterator<String> iterator() { return java.util.Arrays.asList(columns).iterator(); }
            };
        }

        @Override
        public java.util.Set<Map.Entry<String, String>> entrySet() {
            return new java.util.AbstractSet<>() {
                @Override public int size() { return columns.length; }

                @Override
                public java.util.Iterator<Map.Entry<String, String>> iterator() {
                    return new java.util.Iterator<>() {
                        private int next;

                        @Override public boolean hasNext() { return next < columns.length; }

                        @Override
                        public Map.Entry<String, String> next() {
                            if (next >= columns.length) throw new java.util.NoSuchElementException();
                            int index = next++;
                            return new java.util.AbstractMap.SimpleImmutableEntry<>(columns[index], read(index));
                        }
                    };
                }
            };
        }
    }
    @Override public String toString() { return "CdsExportCapturedTextRowDto[protected]"; }
}
