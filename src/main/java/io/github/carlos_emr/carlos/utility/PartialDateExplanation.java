/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.model.PartialDate;
import java.text.SimpleDateFormat;
import java.text.MessageFormat;
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
        if (PartialDate.YEARONLY.equals(format)) return new SimpleDateFormat("yyyy", Locale.ROOT).format(date);
        if (PartialDate.YEARMONTH.equals(format)) return new SimpleDateFormat("yyyy-MM", Locale.ROOT).format(date);
        return "";
    }

    public static String explain(Date date, String format) {
        return explain(date, format, Locale.ENGLISH);
    }

    public static String explain(Date date, String format, Locale locale) {
        return explain(recordedValue(date, format), locale);
    }

    /** Call with the date rendered at its saved precision, not an inferred truncated date. */
    public static String explain(String recordedValue) {
        return explain(recordedValue, Locale.ENGLISH);
    }

    public static String explain(String recordedValue, Locale locale) {
        if (recordedValue == null) return "";
        try {
            if (recordedValue.matches("[0-9]{4}")) {
                Year year = Year.parse(recordedValue);
                return new MessageFormat(LocaleUtils.getMessage(locale, "global.partialDate.msgYearOnly"), locale)
                        .format(new Object[] { year.toString() });
            }
            if (recordedValue.matches("[0-9]{4}-[0-9]{2}")) {
                YearMonth month = YearMonth.parse(recordedValue);
                return new MessageFormat(LocaleUtils.getMessage(locale, "global.partialDate.msgYearMonth"), locale)
                        .format(new Object[] { month.format(DateTimeFormatter.ofPattern("MMMM uuuu", locale)) });
            }
        } catch (java.time.DateTimeException invalidDate) {
            return "";
        }
        return "";
    }

    /** Converts an explicitly partial display value to the normal date picker's storage anchor. */
    public static String pickerValue(String recordedValue) {
        if (recordedValue == null) return null;
        if (recordedValue.matches("[0-9]{4}")) return Year.parse(recordedValue).atDay(1).toString();
        if (recordedValue.matches("[0-9]{4}-[0-9]{2}")) return YearMonth.parse(recordedValue).atDay(1).toString();
        return recordedValue;
    }

    /** A date picker must not promote an unchanged storage anchor to an exact clinical date. */
    public static String forSave(String submitted, Date original, String format, boolean confirmedExact) {
        String partial = recordedValue(original, format);
        if (!confirmedExact && !partial.isEmpty() && submitted != null
                && (submitted.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(original))
                    || submitted.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(original) + " 00:00"))) {
            return partial;
        }
        return submitted;
    }
}
