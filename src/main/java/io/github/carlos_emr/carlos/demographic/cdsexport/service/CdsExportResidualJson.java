/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import java.util.Locale;

/**
 * Compact JSON as the Content of a CDS residual DataElement.
 *
 * <p>Content is an xs:token in an XML 1.0 document, so two things outside our choice apply: a
 * schema-aware reader collapses XML whitespace, and XML 1.0 has no way to write U+FFFE, U+FFFF,
 * an unpaired surrogate or a control character other than tab, CR and LF. Neither is a reason to
 * hold a patient or to lose a character: in compact JSON all of them can only occur inside a
 * string, where a {@code \}{@code uXXXX} escape means exactly the same character. The decoded
 * JSON is unchanged, so a reader (the CARLOS importer included) gets the stored value back
 * exactly, and carriers keep their names.
 */
final class CdsExportResidualJson {
    private CdsExportResidualJson() {}

    /**
     * The same JSON with every space and every character XML 1.0 cannot carry written as a JSON
     * escape.
     *
     * @param json compact JSON (no whitespace outside strings), as Jackson writes it
     * @return JSON that decodes to the same values and is legal, whitespace-stable XML text
     */
    static String token(String json) {
        return escaped(json, true);
    }

    /**
     * The same JSON for an element whose text keeps its whitespace (Notes, TextContent): only
     * the characters XML 1.0 cannot carry are written as JSON escapes.
     *
     * @param json compact JSON, as Jackson writes it
     * @return JSON that decodes to the same values and is legal XML text
     */
    static String text(String json) {
        return escaped(json, false);
    }

    private static String escaped(String json, boolean spaces) {
        StringBuilder safe = null;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            boolean paired =
                    Character.isHighSurrogate(c)
                                    && i + 1 < json.length()
                                    && Character.isLowSurrogate(json.charAt(i + 1))
                            || Character.isLowSurrogate(c)
                                    && i > 0
                                    && Character.isHighSurrogate(json.charAt(i - 1));
            boolean escape =
                    c < 0x20
                            || c == ' ' && spaces
                            || c == 0xfffe
                            || c == 0xffff
                            || Character.isSurrogate(c) && !paired;
            if (escape && safe == null)
                safe = new StringBuilder(json.length() + 64).append(json, 0, i);
            if (safe == null) continue;
            if (!escape) safe.append(c);
            else if (c == ' ') safe.append("\\u0020");
            else safe.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
        }
        return safe == null ? json : safe.toString();
    }
}
