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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import ca.uhn.hl7v2.parser.EncodingCharacters;
import ca.uhn.hl7v2.parser.Escaping;

/**
 * HL7 v2 text escaping exactly as HAPI 1.0.1 did it, for parsers and encoders in CARLOS (#5414).
 *
 * <p>Both HAPI releases turn {@code \F\ \S\ \T\ \R\ \E\} into the delimiter characters, turn
 * {@code \X000d\} into a carriage return, and keep {@code \.br\}-style formatting commands intact.
 * They differ on the other formatting escapes. HAPI 2.x keeps {@code \H\}, {@code \N\},
 * {@code \Xhh\}, {@code \Cxxyy\}, {@code \Mxxyyzz\} and {@code \Zxx\} intact, while 1.0.1 dropped
 * only their escape characters, so {@code \H\high\N\} became {@code HhighN}. Lab handlers,
 * results display, PDF export and the measurement import were built against the 1.0.1 output,
 * and the prescription QR code and ADT export were encoded with it. This class keeps that output,
 * so the library upgrade does not change how clinical text reads; rendering these escapes properly
 * is a separate, deliberate change.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class LegacyHl7Escaping implements Escaping {

    /** Escape codes in lookup order; index 4 is the escape character itself. */
    private static final String CODES = "FSTRE";
    private static final int ESCAPE_INDEX = 4;
    private static final String CARRIAGE_RETURN_ESCAPE = "\\X000d\\";

    /**
     * Encodes {@code text} for a pipe-delimited message: each delimiter becomes its escape code,
     * a carriage return becomes {@code \X000d\}, and a {@code \.xx\} formatting command is copied
     * unchanged.
     */
    @Override
    public String escape(String text, EncodingCharacters encodingCharacters) {
        char[] characters = characters(encodingCharacters);
        String[] encodings = encodings(encodingCharacters);
        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            i = escapeAt(text, i, characters, encodings, result);
        }
        return result.toString();
    }

    /**
     * Decodes {@code text} from a pipe-delimited message: escape codes become delimiters,
     * {@code \X000d\} becomes a carriage return, a {@code \.xx\} formatting command is copied
     * unchanged, and the escape character of any other sequence is dropped.
     */
    @Override
    public String unescape(String text, EncodingCharacters encodingCharacters) {
        char escape = encodingCharacters.getEscapeCharacter();
        if (text.indexOf(escape) < 0) {
            return text;
        }
        char[] characters = characters(encodingCharacters);
        String[] encodings = encodings(encodingCharacters);
        StringBuilder result = new StringBuilder(text.length() + 20);
        int i = 0;
        while (i < text.length()) {
            i = unescapeAt(text, i, characters, encodings, result);
        }
        return result.toString();
    }

    /** Appends the encoded form of the character at {@code i}; returns where the next one starts. */
    private static int escapeAt(String text, int i, char[] characters, String[] encodings, StringBuilder result) {
        char c = text.charAt(i);
        int code = indexOf(characters, c);
        if (code < 0) {
            result.append(c);
            return i + 1;
        }
        if (code == ESCAPE_INDEX) {
            int closing = closingOfFormattingCommand(text, i, c);
            if (closing > 0) {
                result.append(text, i, closing + 1);
                return closing + 1;
            }
        }
        result.append(encodings[code]);
        return i + 1;
    }

    /** Appends the decoded form of the text at {@code i}; returns where the next part starts. */
    private static int unescapeAt(String text, int i, char[] characters, String[] encodings, StringBuilder result) {
        char escape = characters[ESCAPE_INDEX];
        char c = text.charAt(i);
        if (c != escape) {
            result.append(c);
            return i + 1;
        }
        for (int code = 0; code < encodings.length; code++) {
            if (text.startsWith(encodings[code], i)) {
                result.append(characters[code]);
                return i + encodings[code].length();
            }
        }
        int closing = closingOfFormattingCommand(text, i, escape);
        if (closing > 0) {
            result.append(text, i, closing + 1);
            return closing + 1;
        }
        // 1.0.1 dropped the escape character of any sequence it did not recognise.
        return i + 1;
    }

    /**
     * Index of the escape character closing a {@code \.xx\} formatting command that opens at
     * {@code i}, or -1 when no such command starts there.
     */
    private static int closingOfFormattingCommand(String text, int i, char escape) {
        if (i + 1 < text.length() && text.charAt(i + 1) == '.') {
            return text.indexOf(escape, i + 1);
        }
        return -1;
    }

    /** The first index of {@code c} in {@code characters}, or -1. */
    private static int indexOf(char[] characters, char c) {
        for (int code = 0; code < characters.length; code++) {
            if (characters[code] == c) {
                return code;
            }
        }
        return -1;
    }

    private static char[] characters(EncodingCharacters encodingCharacters) {
        return new char[] {
                encodingCharacters.getFieldSeparator(),
                encodingCharacters.getComponentSeparator(),
                encodingCharacters.getSubcomponentSeparator(),
                encodingCharacters.getRepetitionSeparator(),
                encodingCharacters.getEscapeCharacter(),
                '\r'};
    }

    private static String[] encodings(EncodingCharacters encodingCharacters) {
        char escape = encodingCharacters.getEscapeCharacter();
        String[] encodings = new String[CODES.length() + 1];
        for (int i = 0; i < CODES.length(); i++) {
            encodings[i] = "" + escape + CODES.charAt(i) + escape;
        }
        // 1.0.1 used a backslash here whatever the message's escape character was.
        encodings[CODES.length()] = CARRIAGE_RETURN_ESCAPE;
        return encodings;
    }
}
