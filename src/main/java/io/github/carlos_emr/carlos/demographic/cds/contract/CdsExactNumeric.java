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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Locale;

/** Exact finite IEEE value from a verified server-binary JDBC result. Values require encrypted storage. */
public record CdsExactNumeric(Codec codec, String bits) {
    public enum Codec {
        IEEE754_BINARY32_V1,
        IEEE754_BINARY64_V1
    }

    /** A value-free witness suitable for the import ledger's native baseline. */
    public record Witness(Codec codec, String sha256) {
        public Witness {
            if (codec == null || sha256 == null || !sha256.matches("[0-9a-f]{64}")) invalid();
        }

        @Override
        public String toString() {
            return "CdsExactNumeric.Witness[protected]";
        }
    }

    public CdsExactNumeric {
        if (codec == null
                || bits == null
                || !bits.matches(
                        codec == Codec.IEEE754_BINARY32_V1 ? "[0-9a-f]{8}" : "[0-9a-f]{16}"))
            invalid();
        long value = Long.parseUnsignedLong(bits, 16);
        if (codec == Codec.IEEE754_BINARY32_V1
                ? !Float.isFinite(Float.intBitsToFloat((int) value))
                : !Double.isFinite(Double.longBitsToDouble(value))) invalid();
    }

    public static Codec codec(String sqlType) {
        return switch (sqlType.toUpperCase(Locale.ROOT)) {
            case "FLOAT", "FLOAT UNSIGNED" -> Codec.IEEE754_BINARY32_V1;
            case "DOUBLE", "DOUBLE UNSIGNED", "REAL" -> Codec.IEEE754_BINARY64_V1;
            default -> null;
        };
    }

    /** Caller must obtain this ResultSet from the guarded server-binary statement factory. */
    public static CdsExactNumeric readBinary(ResultSet rows, int column, Codec codec)
            throws SQLException {
        if (codec == null) throw new SQLException("CDS_EXACT_NUMERIC_CODEC_REQUIRED");
        String bits;
        if (codec == Codec.IEEE754_BINARY32_V1) {
            float value = rows.getFloat(column);
            if (rows.wasNull()) return null;
            bits = HexFormat.of().toHexDigits(Float.floatToRawIntBits(value));
        } else {
            double value = rows.getDouble(column);
            if (rows.wasNull()) return null;
            bits = HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));
        }
        return new CdsExactNumeric(codec, bits);
    }

    public Witness witness() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(codec.name().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            return new Witness(
                    codec, HexFormat.of().formatHex(digest.digest(HexFormat.of().parseHex(bits))));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("CDS_EXACT_NUMERIC_SHA256_UNAVAILABLE");
        }
    }

    public String decimal() {
        long value = Long.parseUnsignedLong(bits, 16);
        return codec == Codec.IEEE754_BINARY32_V1
                ? Float.toString(Float.intBitsToFloat((int) value))
                : Double.toString(Double.longBitsToDouble(value));
    }

    private static void invalid() {
        throw new IllegalArgumentException("CDS_EXACT_NUMERIC_INVALID");
    }

    @Override
    public String toString() {
        return "CdsExactNumeric[protected]";
    }
}
