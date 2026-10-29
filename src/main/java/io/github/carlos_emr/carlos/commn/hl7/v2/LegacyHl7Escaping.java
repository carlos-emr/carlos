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
    private static final char[] CODES = {'F', 'S', 'T', 'R', 'E'};
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
        int length = text.length();
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            boolean replaced = false;
            for (int j = 0; j < characters.length; j++) {
                if (c != characters[j]) {
                    continue;
                }
                if (j == ESCAPE_INDEX && i + 1 < length && text.charAt(i + 1) == '.') {
                    int closing = text.indexOf(characters[j], i + 1);
                    if (closing > 0) {
                        result.append(text, i, closing + 1);
                        i = closing;
                        replaced = true;
                        break;
                    }
                }
                result.append(encodings[j]);
                replaced = true;
                break;
            }
            if (!replaced) {
                result.append(c);
            }
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
        int length = text.length();
        StringBuilder result = new StringBuilder(length + 20);
        int i = 0;
        while (i < length) {
            char c = text.charAt(i);
            if (c != escape) {
                result.append(c);
                i++;
                continue;
            }
            boolean decoded = false;
            for (int j = 0; j < encodings.length; j++) {
                String encoding = encodings[j];
                if (text.startsWith(encoding, i)) {
                    result.append(characters[j]);
                    i += encoding.length();
                    decoded = true;
                    break;
                }
            }
            if (decoded) {
                continue;
            }
            if (i + 1 < length && text.charAt(i + 1) == '.') {
                int closing = text.indexOf(escape, i + 1);
                if (closing > 0) {
                    result.append(text, i, closing + 1);
                    i = closing + 1;
                    continue;
                }
            }
            // 1.0.1 dropped the escape character of any sequence it did not recognise.
            i++;
        }
        return result.toString();
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
        String[] encodings = new String[CODES.length + 1];
        for (int i = 0; i < CODES.length; i++) {
            encodings[i] = "" + escape + CODES[i] + escape;
        }
        // 1.0.1 used a backslash here whatever the message's escape character was.
        encodings[CODES.length] = CARRIAGE_RETURN_ESCAPE;
        return encodings;
    }
}
