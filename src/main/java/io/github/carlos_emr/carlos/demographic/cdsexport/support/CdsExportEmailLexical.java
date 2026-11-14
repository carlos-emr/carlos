/*
 * Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.support;

/**
 * Exact ASCII email pattern from the pinned CDS datatype, scanned in linear time.
 * This checks syntax only: callers retain their existing length/presence policy.
 * No trimming, case conversion, RFC expansion or source-value replacement occurs.
 */
public final class CdsExportEmailLexical {
    private CdsExportEmailLexical() { }

    public static boolean matches(String value) {
        if (value == null) return false;
        int separator = value.indexOf('@');
        if (separator < 1) return false;
        for (int i = 0; i < separator; i++) {
            char current = value.charAt(i);
            if (current != '.' && !labelCharacter(current)) return false;
        }
        boolean labelPresent = false;
        boolean dotPresent = false;
        for (int i = separator + 1; i < value.length(); i++) {
            char current = value.charAt(i);
            if (labelCharacter(current)) labelPresent = true;
            else if (current == '.' && labelPresent) {
                dotPresent = true;
                labelPresent = false;
            } else return false;
        }
        return dotPresent && labelPresent;
    }

    private static boolean labelCharacter(char value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9' || value == '_' || value == '-';
    }
}
