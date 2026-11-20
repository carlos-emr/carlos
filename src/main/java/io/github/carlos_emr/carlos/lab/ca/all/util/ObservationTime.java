/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.util;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Parses the observation times HL7 message handlers hand back.
 *
 * <p>Kept free of Spring and DAO dependencies so display code (grids, comparators) can read a
 * handler time without loading the legacy result readers.
 *
 * @since 2026-11-10
 */
public final class ObservationTime {
    private ObservationTime() { }

    /**
     * Observation-time layouts the HL7 message parsers hand back. HL7 OBR-7 may carry 14 digits (with
     * seconds), 12 (no seconds), 10 (hour only) or 8 (date only), and the parsers (e.g.
     * {@code CMLHandler.formatDateTime}, {@code DefaultGenericHandler}) render them as these shapes.
     */
    private static final String[] PATTERNS = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH", "yyyy-MM-dd"};

    /**
     * Parses a handler-formatted observation time, accepting a missing seconds, minutes or time
     * component so a result observed in the past is never re-dated to the moment it was filed
     * (issue #4619). Each layout must match the whole value and be a real calendar time, so a short
     * layout can never silently swallow the date prefix of a longer or malformed value.
     *
     * @param observationDate the handler's formatted timestamp, may be null
     * @return the parsed instant, or {@code null} when no supported layout matches
     */
    public static Date parse(String observationDate) {
        if (observationDate == null) {
            return null;
        }
        String trimmed = observationDate.trim();
        for (String pattern : PATTERNS) {
            // SimpleDateFormat is not thread-safe, so build one per call.
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(trimmed, position);
            if (parsed != null && position.getIndex() == trimmed.length()) {
                return parsed;
            }
        }
        return null;
    }
}
