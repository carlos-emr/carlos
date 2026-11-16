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
        // Every captured field of every read of a capture is validated here, a million times over
        // for a long chart, so the three patterns are checked character by character.
        if (table == null || !name(table, 64) || recordKey == null || recordKey.isEmpty()
                || recordKey.length() > 2048 || !safeKey(recordKey)
                || column == null || !name(column, 128) || kind == null
                || exactNumeric != null && kind != Kind.UTF8 || offset < 0 || bytes < 0 || bytes > Long.MAX_VALUE - offset || kind == Kind.SQL_NULL && bytes != 0
                || sha256 == null || !digest(sha256)) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURED_FIELD_INVALID");
        }
    }

    private static final boolean[] NAME = new boolean[128];
    private static final boolean[] HEX = new boolean[128];

    static {
        for (char c = '0'; c <= '9'; c++) NAME[c] = HEX[c] = true;
        for (char c = 'a'; c <= 'z'; c++) NAME[c] = true;
        for (char c = 'A'; c <= 'Z'; c++) NAME[c] = true;
        for (char c = 'a'; c <= 'f'; c++) HEX[c] = true;
        NAME['_'] = true;
    }

    /** {@code [A-Za-z0-9_]{1,maximum}}. */
    private static boolean name(String value, int maximum) {
        int length = value.length();
        if (length < 1 || length > maximum) return false;
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c >= 128 || !NAME[c]) return false;
        }
        return true;
    }

    /** {@code [0-9a-f]{64}}. */
    private static boolean digest(String value) {
        if (value.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = value.charAt(i);
            if (c >= 128 || !HEX[c]) return false;
        }
        return true;
    }

    /** No ISO control character and no surrogate code point (an unpaired surrogate included). */
    static boolean safeKey(String key) {
        int length = key.length();
        int i = 0;
        // Printable ASCII, which is nearly every key, needs no code point decoding.
        while (i < length && key.charAt(i) >= 0x20 && key.charAt(i) < 0x7f) i++;
        while (i < length) {
            int cp = key.codePointAt(i);
            if (Character.isISOControl(cp) || cp >= 0xd800 && cp <= 0xdfff) return false;
            i += Character.charCount(cp);
        }
        return true;
    }
    @Override public String toString() { return "CdsExportCapturedFieldDto[protected]"; }
}
