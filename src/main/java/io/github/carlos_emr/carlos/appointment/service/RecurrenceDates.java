/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.appointment.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validates the entire inclusive recurrence range before any appointment is written. */
public final class RecurrenceDates {
    private RecurrenceDates() { }

    public static List<LocalDate> between(LocalDate start, LocalDate end, int interval, String unit) {
        validateRange(start, end, interval, unit);
        List<LocalDate> dates = new ArrayList<>();
        // Calculate from the original date: January 31 -> February 28 -> March 31.
        for (int n = 0; ; n++) {
            long distance = (long) interval * n;
            LocalDate date = switch (unit) {
                case "day" -> start.plusDays(distance);
                case "week" -> start.plusWeeks(distance);
                case "month" -> start.plusMonths(distance);
                case "year" -> start.plusYears(distance);
                default -> throw new IllegalArgumentException("Choose a valid repeat unit.");
            };
            if (date.isAfter(end)) return List.copyOf(dates);
            if (dates.size() == 366) {
                throw new IllegalArgumentException("This range exceeds 366 appointments. Choose an earlier end date.");
            }
            dates.add(date);
        }
    }

    static void validateRange(LocalDate start, LocalDate end, int interval, String unit) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("Choose an end date on or after the appointment date.");
        }
        if (interval < 1 || interval > 11 || unit == null || !Set.of("day", "week", "month", "year").contains(unit)) {
            throw new IllegalArgumentException("Choose an interval from 1 to 11 and a valid repeat unit.");
        }
        if (end.isAfter(start.plusYears(10))) {
            throw new IllegalArgumentException("Choose an end date within ten years of the appointment.");
        }
    }
}
