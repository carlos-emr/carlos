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
 *
 */
package io.github.carlos_emr.carlos.lab.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System-wide settings for the inline preview of PDFs embedded in HL7 lab results.
 *
 * <p>Stored as two {@code SystemPreferences} rows, both optional:</p>
 * <ul>
 *   <li>{@code lab_pdf_inline_preview} &mdash; {@code "false"} turns the preview off; any other
 *       value, or no row, leaves it on.</li>
 *   <li>{@code lab_pdf_max_size} &mdash; the largest PDF previewed inline, in bytes. A {@code K},
 *       {@code M} or {@code G} suffix (optionally followed by {@code B}, binary multiples) is
 *       also accepted, matching the {@code 5MB} form of the open-osp/Open-O preference this is
 *       adapted from. Missing, unparseable or non-positive values use {@link #DEFAULT_MAX_BYTES};
 *       values above {@link #MAX_ALLOWED_BYTES} are clamped to it.</li>
 * </ul>
 *
 * <p>The limit protects the server, which decodes the document in memory, and the browser, which
 * renders it; a larger PDF is still available through the download link.</p>
 *
 * @param inlinePreviewEnabled whether ED PDFs are previewed inline on the lab display
 * @param maxBytes the largest PDF previewed inline, in bytes; always positive
 * @since 2026-09-30
 */
public record LabPdfPreviewSettings(boolean inlinePreviewEnabled, long maxBytes) {

    /** Default preview limit: 10 MiB. */
    public static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;

    /** Largest limit an administrator may set: 100 MiB. */
    public static final long MAX_ALLOWED_BYTES = 100L * 1024 * 1024;

    /** The settings used when no preference row exists. */
    public static final LabPdfPreviewSettings DEFAULTS = new LabPdfPreviewSettings(true, DEFAULT_MAX_BYTES);

    // The optional B belongs to the unit: "5MB" is 5 MiB, but a bare "5B" is not a valid size
    // (it would otherwise set a 5-byte limit and turn the preview off for nearly every PDF).
    private static final Pattern SIZE = Pattern.compile("^(\\d{1,9})(?:\\s*([KkMmGg])[Bb]?)?$");

    public LabPdfPreviewSettings {
        maxBytes = maxBytes <= 0 ? DEFAULT_MAX_BYTES : Math.min(maxBytes, MAX_ALLOWED_BYTES);
    }

    /**
     * Builds the settings from the stored preference values.
     *
     * @param inlinePreview the {@code lab_pdf_inline_preview} value, or {@code null} when unset
     * @param maxSize the {@code lab_pdf_max_size} value, or {@code null} when unset
     * @return the settings; never {@code null}
     */
    public static LabPdfPreviewSettings fromPreferences(String inlinePreview, String maxSize) {
        boolean enabled = inlinePreview == null || !"false".equals(inlinePreview.trim());
        return new LabPdfPreviewSettings(enabled, parseSize(maxSize));
    }

    /**
     * Parses a size such as {@code 10485760}, {@code 512K} or {@code 5MB}.
     *
     * @param value the stored value
     * @return the size in bytes, or {@link #DEFAULT_MAX_BYTES} when missing or unparseable
     */
    static long parseSize(String value) {
        if (value == null) {
            return DEFAULT_MAX_BYTES;
        }
        Matcher matcher = SIZE.matcher(value.trim());
        if (!matcher.matches()) {
            return DEFAULT_MAX_BYTES;
        }
        long number = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2) == null ? "" : matcher.group(2);
        int shift = switch (unit) {
            case "K", "k" -> 10;
            case "M", "m" -> 20;
            case "G", "g" -> 30;
            default -> 0;
        };
        // The pattern caps the number at nine digits, so parseLong cannot overflow and nine digits
        // shifted by at most 30 bits (< 2^60) stays well inside a long before the record clamps it.
        return number << shift;
    }

    /** The limit in whole mebibytes, rounded up, for display. */
    public long maxMegabytes() {
        return (maxBytes + (1024 * 1024) - 1) / (1024 * 1024);
    }
}
