/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.model.PartialDate;
import java.text.SimpleDateFormat;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/** Explanations come only from explicit precision, never from a placeholder day or time. */
public final class PartialDateExplanation {
    private PartialDateExplanation() { }

    public static String recordedValue(Date date, String format) {
        if (date == null) return "";
        if (PartialDate.YEARONLY.equals(format)) return new SimpleDateFormat("yyyy").format(date);
        if (PartialDate.YEARMONTH.equals(format)) return new SimpleDateFormat("yyyy-MM").format(date);
        return "";
    }

    public static String explain(Date date, String format) {
        return explain(recordedValue(date, format));
    }

    /** Call with the date rendered at its saved precision, not an inferred truncated date. */
    public static String explain(String recordedValue) {
        if (recordedValue == null) return "";
        try {
            if (recordedValue.matches("[0-9]{4}")) {
                Year year = Year.parse(recordedValue);
                return "Recorded year only: " + year
                        + ". January 1 is a placeholder; the exact date is unknown.";
            }
            if (recordedValue.matches("[0-9]{4}-[0-9]{2}")) {
                YearMonth month = YearMonth.parse(recordedValue);
                return "Recorded year and month only: "
                        + month.format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH))
                        + ". The first day is a placeholder; the exact day is unknown.";
            }
        } catch (java.time.DateTimeException invalidDate) {
            return "";
        }
        return "";
    }

    /** A date picker must not promote an unchanged storage anchor to an exact clinical date. */
    public static String forSave(String submitted, Date original, String format, boolean confirmedExact) {
        String partial = recordedValue(original, format);
        if (!confirmedExact && !partial.isEmpty() && submitted != null
                && (submitted.equals(new SimpleDateFormat("yyyy-MM-dd").format(original))
                    || submitted.equals(new SimpleDateFormat("yyyy-MM-dd").format(original) + " 00:00"))) {
            return partial;
        }
        return submitted;
    }
}
