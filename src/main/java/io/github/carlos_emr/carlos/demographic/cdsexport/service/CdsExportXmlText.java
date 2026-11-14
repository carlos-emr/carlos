/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

/**
 * Stored text as the text of a CDS element.
 *
 * <p>XML 1.0 has no way to write U+FFFE, U+FFFF, an unpaired surrogate or a control character
 * other than tab, CR and LF, not even as a character reference. A database column can hold all of
 * them, and a user can paste them into a CARLOS form. That is no reason to hold a patient, and
 * dropping the character silently would hide that the text was changed. One rule applies wherever
 * such text becomes an element: each character XML cannot carry is written as U+FFFD REPLACEMENT
 * CHARACTER, one for one, so the element keeps its length and shows where something stood; and the
 * exact stored text travels in the record's CARLOS carrier as JSON, where {@link
 * CdsExportResidualJson} writes the same character as its {@code \}{@code uXXXX} escape.
 */
final class CdsExportXmlText {
    /** What stands in an element for a character XML 1.0 cannot carry. */
    static final char REPLACEMENT = '\ufffd';

    private CdsExportXmlText() {}

    /** Whether every character of {@code value} can be written in an XML 1.0 document. */
    static boolean carried(String value) {
        // A replaced character is never equal to the one it replaced.
        return value == null || element(value).equals(value);
    }

    /**
     * {@code value} with each character XML 1.0 cannot carry replaced by U+FFFD.
     *
     * @return the same instance when nothing was replaced
     */
    static String element(String value) {
        StringBuilder safe = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean paired =
                    Character.isHighSurrogate(c)
                                    && i + 1 < value.length()
                                    && Character.isLowSurrogate(value.charAt(i + 1))
                            || Character.isLowSurrogate(c)
                                    && i > 0
                                    && Character.isHighSurrogate(value.charAt(i - 1));
            boolean illegal =
                    c < 0x20 && c != '\t' && c != '\r' && c != '\n'
                            || c == 0xfffe
                            || c == 0xffff
                            || Character.isSurrogate(c) && !paired;
            if (illegal && safe == null) safe = new StringBuilder(value.length()).append(value, 0, i);
            if (safe != null) safe.append(illegal ? REPLACEMENT : c);
        }
        return safe == null ? value : safe.toString();
    }
}
