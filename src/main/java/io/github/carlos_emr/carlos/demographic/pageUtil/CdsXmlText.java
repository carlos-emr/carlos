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
package io.github.carlos_emr.carlos.demographic.pageUtil;

/**
 * Text normalisation for values written into an OntarioMD CDS export.
 *
 * <p>XML 1.0 (section 2.2, production {@code Char}) forbids most C0 control characters, lone
 * UTF-16 surrogates and U+FFFE/U+FFFF anywhere in a document, and no escaping makes them legal:
 * {@code &#x1;} is itself a well-formedness error. HL7 lab values routinely carry such characters
 * (vertical tab and form feed from report formatters, NUL padding, stray escape bytes). XMLBeans
 * 3.1 does not reject them: it silently rewrites each C0 control to {@code '?'} (so a result of
 * {@code "5.2<VT>"} is exported as {@code "5.2?"}) and passes lone surrogates through to the
 * writer, which either substitutes {@code '?'} again or, with a non-substituting writer, produces
 * a file the receiving EMR cannot parse. Values are therefore filtered before they reach the
 * document, not escaped, and length limits are applied to the filtered text.</p>
 *
 * <p>Unlike the regular expression used by open-osp/Open-O e20d26e0ef and 21d706a271
 * ({@code [^\x09\x0A\x0D\x20-퟿-�]}), this filter works on code points, so
 * characters outside the Basic Multilingual Plane (valid XML) survive while unpaired surrogates
 * (invalid XML) are removed.</p>
 *
 * @since 2026-09-26
 */
public final class CdsXmlText {

    private CdsXmlText() {
    }

    /**
     * Whether a code point may appear in an XML 1.0 document.
     *
     * @param codePoint the Unicode code point
     * @return {@code true} for {@code #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]}
     */
    public static boolean isXmlCharacter(int codePoint) {
        return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    /**
     * Removes every character XML 1.0 does not allow.
     *
     * @param value the text to filter; may be {@code null}
     * @return {@code value} itself when it is already clean (the common case allocates nothing),
     *         otherwise a copy without the illegal characters; {@code null} for {@code null}
     */
    public static String stripInvalidXmlCharacters(String value) {
        if (value == null) {
            return null;
        }
        int length = value.length();
        int index = 0;
        while (index < length) {
            int codePoint = value.codePointAt(index);
            if (!isXmlCharacter(codePoint)) {
                break;
            }
            index += Character.charCount(codePoint);
        }
        if (index == length) {
            return value;
        }
        StringBuilder clean = new StringBuilder(length);
        clean.append(value, 0, index);
        while (index < length) {
            // codePointAt returns a lone surrogate as itself, which isXmlCharacter rejects.
            int codePoint = value.codePointAt(index);
            if (isXmlCharacter(codePoint)) {
                clean.appendCodePoint(codePoint);
            }
            index += Character.charCount(codePoint);
        }
        return clean.toString();
    }

    /**
     * Shortens text to a schema {@code maxLength}, never splitting a surrogate pair.
     *
     * <p>XML Schema counts {@code maxLength} in characters (code points). Cutting at a UTF-16
     * index could leave half a pair, which {@link #stripInvalidXmlCharacters(String)} would then
     * have to drop, so the cut is made on code points.</p>
     *
     * @param value the text; may be {@code null}
     * @param maxCodePoints the schema limit
     * @return {@code value} when it already fits, otherwise its first {@code maxCodePoints} code points
     */
    public static String truncate(String value, int maxCodePoints) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }
}
