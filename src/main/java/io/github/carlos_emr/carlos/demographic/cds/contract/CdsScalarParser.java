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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.xml.XMLConstants;
import javax.xml.validation.ValidatorHandler;
import org.xml.sax.SAXException;

/**
 * One bounded scalar's wire lexical state, shared by import and export validation. Only
 * schema-proven unbounded values use witnesses; constrained strings reach the real validator.
 * The caller owns decoded output and supplies its directional safe-exception factory.
 */
public class CdsScalarParser {
    private static final String NUMERIC_LEXICAL_INVALID = "CDS_XML_NUMERIC_LEXICAL_INVALID";
    private static final String POSITIVE_INTEGER = "positiveInteger";
    public enum Mode { COMPLEX, BOUNDED, TEXT, LENGTH_TEXT, BINARY, INTEGER, DECIMAL }
    private final Function<String, IOException> failures;
    private CdsSchemaModel.Type type;
    private boolean identity;
    private Mode mode = Mode.COMPLEX;
    private String whitespace = "preserve";
    private long validatedCharacters;
    private boolean started;
    private boolean pendingSpace;
    private int base64Count;
    private final int[] quartet = new int[4];
    private boolean padded;
    private long binaryCount;
    private byte[] binaryBuffer;
    private int binaryBuffered;
    private boolean numberSign;
    private boolean numberNegative;
    private boolean numberDigits;
    private boolean numberNonzero;
    private boolean numberDot;
    private boolean numberTrailingWhitespace;


    public CdsScalarParser() { this(IOException::new); }

    public CdsScalarParser(Function<String, IOException> failures) {
        this.failures = Objects.requireNonNull(failures);
    }

    public Mode mode() { return mode; }
    public CdsSchemaModel.Type type() { return type; }
    public boolean identity() { return identity; }
    public long decodedBytes() { return binaryCount; }

    /** Normalized Unicode length for bounded string modes; unbounded streaming modes do not count it. */
    public long normalizedCharacters() { return validatedCharacters; }

    public void configure(CdsSchemaModel.Type info) {
        type = info; whitespace = whitespace(info);
        identity = CdsSchemaTypeCursor.identity(info);
        if (info == null || !info.simple()) { mode = Mode.COMPLEX; return; }
        mode = Mode.BOUNDED;
        if (info.atomic() && !info.lengthFacet() && !info.otherFacet()) {
            if (info.plainString()) mode = Mode.TEXT;
            if (info.builtIn().equals("base64Binary")) { mode = Mode.BINARY; binaryBuffer = new byte[8192]; }
            if (info.builtIn().equals("decimal")) mode = Mode.DECIMAL;
        }
        if (info.atomic() && info.plainString() && info.lengthFacet() && !info.otherFacet()) {
            // Feed a BMP witness of the exact normalized Unicode-character length.
            // Pattern/enumeration restrictions always receive their actual bounded value.
            mode = Mode.LENGTH_TEXT;
        }
        if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(info.getTypeNamespace())
                && Set.of("integer", "nonNegativeInteger", POSITIVE_INTEGER).contains(info.getTypeName())) {
            mode = Mode.INTEGER;
        }
    }

    public static String whitespace(CdsSchemaModel.Type info) {
        return info == null || !info.simple() ? "preserve" : info.whitespace();
    }

    public void validate(char[] chars, int offset, int length, ValidatorHandler validator, int maximum)
            throws SAXException, IOException {
        if (mode == Mode.TEXT) return;
        if (mode == Mode.INTEGER || mode == Mode.DECIMAL) {
            for (int i = 0; i < length; i++) number(chars[offset + i]);
            return;
        }
        if (mode == Mode.COMPLEX) { validator.characters(chars, offset, length); return; }
        char[] normalized = new char[length + 1];
        int count = 0;
        for (int i = 0; i < length; i++) {
            char c = chars[offset + i];
            boolean space = xmlSpace(c);
            if (whitespace.equals("collapse") && space) { pendingSpace = started; continue; }
            if (pendingSpace) { normalized[count++] = ' '; validatedCharacters++; pendingSpace = false; }
            if (whitespace.equals("replace") && space) c = ' ';
            normalized[count++] = c;
            if (!Character.isLowSurrogate(c)) validatedCharacters++;
            started = true;
            if (validatedCharacters > maximum) throw failures.apply("CDS_XML_SCALAR_RESOURCE_LIMIT");
        }
        if (count > 0 && mode != Mode.LENGTH_TEXT) validator.characters(normalized, 0, count);
    }

    private void number(char c) throws IOException {
        if (xmlSpace(c)) { if (numberDigits || numberSign || numberDot) numberTrailingWhitespace = true; return; }
        if (numberTrailingWhitespace) throw failures.apply(NUMERIC_LEXICAL_INVALID);
        if ((c == '+' || c == '-') && !numberSign && !numberDigits && !numberDot) {
            numberSign = true; numberNegative = c == '-'; return;
        }
        if (c >= '0' && c <= '9') { numberDigits = true; numberNonzero |= c != '0'; return; }
        if (mode == Mode.DECIMAL && c == '.' && !numberDot) { numberDot = true; return; }
        throw failures.apply(NUMERIC_LEXICAL_INVALID);
    }

    public void base64(char[] chars, int offset, int length, OutputStream target, long maximum) throws IOException {
        for (int i = 0; i < length; i++) {
            char c = chars[offset + i];
            if (xmlSpace(c)) continue;
            if (padded) throw failures.apply("CDS_BASE64_TRAILING_DATA");
            int value = c == '=' ? -2 : decode(c);
            if (value == -1) throw failures.apply("CDS_BASE64_LEXICAL_INVALID");
            quartet[base64Count++] = value;
            if (base64Count == 4) {
                int a = quartet[0], b = quartet[1], third = quartet[2], fourth = quartet[3];
                if (a < 0 || b < 0 || (third == -2 && fourth != -2)) throw failures.apply("CDS_BASE64_PADDING_INVALID");
                binaryByte((a << 2) | (b >> 4), target); binaryCount++;
                if (third == -2) {
                    if ((b & 15) != 0) throw failures.apply("CDS_BASE64_PAD_BITS_INVALID");
                    padded = true;
                } else {
                    binaryByte((b << 4) | (third >> 2), target); binaryCount++;
                    if (fourth == -2) {
                        if ((third & 3) != 0) throw failures.apply("CDS_BASE64_PAD_BITS_INVALID");
                        padded = true;
                    } else { binaryByte((third << 6) | fourth, target); binaryCount++; }
                }
                if (binaryCount > maximum) throw failures.apply("CDS_BINARY_BYTE_LIMIT");
                base64Count = 0;
            }
        }
        if (binaryBuffered > 0) { target.write(binaryBuffer, 0, binaryBuffered); binaryBuffered = 0; }
    }

    private void binaryByte(int value, OutputStream target) throws IOException {
        binaryBuffer[binaryBuffered++] = (byte) value;
        if (binaryBuffered == binaryBuffer.length) {
            target.write(binaryBuffer, 0, binaryBuffered); binaryBuffered = 0;
        }
    }

    public void finishScalar(ValidatorHandler validator) throws SAXException, IOException {
        if (mode == Mode.BINARY && base64Count != 0) throw failures.apply("CDS_BASE64_TRUNCATED");
        if (mode == Mode.LENGTH_TEXT) {
            char[] lengthWitness = new char[Math.toIntExact(validatedCharacters)];
            java.util.Arrays.fill(lengthWitness, 'x');
            if (lengthWitness.length > 0) validator.characters(lengthWitness, 0, lengthWitness.length);
        }
        if (mode == Mode.INTEGER || mode == Mode.DECIMAL) {
            if (!numberDigits) throw failures.apply(NUMERIC_LEXICAL_INVALID);
            if (mode == Mode.INTEGER && type != null) {
                if (type.getTypeName().equals(POSITIVE_INTEGER) && (numberNegative || !numberNonzero)) {
                    throw failures.apply("CDS_XML_NUMERIC_RANGE_INVALID");
                }
                if (type.getTypeName().equals("nonNegativeInteger") && numberNegative && numberNonzero) {
                    throw failures.apply("CDS_XML_NUMERIC_RANGE_INVALID");
                }
            }
            char[] value = (mode == Mode.INTEGER && type.getTypeName().equals(POSITIVE_INTEGER) ? "1" : "0").toCharArray();
            validator.characters(value, 0, value.length);
        }
    }

    public static boolean xmlSpace(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r'; }
    private static int decode(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        return c == '+' ? 62 : c == '/' ? 63 : -1;
    }
}
