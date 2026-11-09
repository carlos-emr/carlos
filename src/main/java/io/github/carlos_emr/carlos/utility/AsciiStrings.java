/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility;

/** ASCII protocol identifiers must not accept additional Unicode case-fold equivalents. */
public final class AsciiStrings {
    private AsciiStrings() {}

    public static boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) return false;
        for (int index = 0; index < left.length(); index++) {
            char a = left.charAt(index);
            char b = right.charAt(index);
            if (a > 127 || b > 127) return false;
            if (a >= 'A' && a <= 'Z') a += 'a' - 'A';
            if (b >= 'A' && b <= 'Z') b += 'a' - 'A';
            if (a != b) return false;
        }
        return true;
    }
}
