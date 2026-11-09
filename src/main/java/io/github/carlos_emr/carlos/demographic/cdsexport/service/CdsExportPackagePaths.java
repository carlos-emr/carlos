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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Bounded path/collision accounting, using component order so a file and its descendants are adjacent. */
public final class CdsExportPackagePaths {
    private List<String> previous;

    public byte[] accept(String relative) throws IOException {
        List<String> key = canonicalParts(relative);
        if (previous != null) {
            int common = 0;
            while (common < Math.min(previous.size(), key.size())
                    && previous.get(common).equals(key.get(common))) common++;
            if (common == previous.size()
                    || common == key.size()
                    || previous.get(common).compareTo(key.get(common)) >= 0) {
                throw new IOException("CDS_EXPORT_PACKAGE_PATH_COLLISION_OR_ORDER");
            }
        }
        previous = key;
        return relative.getBytes(StandardCharsets.UTF_8);
    }

    /** Same component collation for encrypted external sorting and final collision/order checks. */
    static List<String> canonicalParts(String relative) throws IOException {
        if (relative == null
                || relative.isEmpty()
                || relative.length() > 8192
                || relative.startsWith("/")
                || relative.endsWith("/")
                || relative.indexOf('\\') >= 0
                || relative.codePoints()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || c >= 0xd800 && c <= 0xdfff
                                                || c == 0xfffe
                                                || c == 0xffff
                                                || "<>:\"|?*".indexOf(c) >= 0)) {
            throw new IOException("CDS_EXPORT_PACKAGE_PATH_INVALID");
        }
        List<String> parts = Arrays.asList(relative.split("/", -1));
        if (parts.size() > 128
                || parts.stream()
                        .anyMatch(
                                p ->
                                        p.isEmpty()
                                                || p.equals(".")
                                                || p.equals("..")
                                                || p.endsWith(".")
                                                || !p.equals(p.strip())
                                                || p.matches(
                                                        "(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))) {
            throw new IOException("CDS_EXPORT_PACKAGE_PATH_INVALID");
        }
        if (relative.getBytes(StandardCharsets.UTF_8).length > 65535)
            throw new IOException("CDS_EXPORT_PACKAGE_PATH_INVALID");
        return parts.stream()
                .map(p -> Normalizer.normalize(p, Normalizer.Form.NFC).toLowerCase(Locale.ROOT))
                .toList();
    }
}
