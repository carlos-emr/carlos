/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Central date parsing for Ontario billing.
 */
public final class BillingDates {
    private static final DateTimeFormatter SERVICE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter OHIP_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private BillingDates() {
    }

    public static String ohipEffectiveDate(String raw, LocalDate fallbackDate) {
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) {
            return fallbackDate.format(SERVICE_DATE);
        }
        return parseOhipDate(raw, false).format(SERVICE_DATE);
    }

    public static String ohipTerminationDate(String raw) {
        if ("99999999".equals(raw)) {
            return "9999-12-31";
        }
        return parseOhipDate(raw, true).format(SERVICE_DATE);
    }

    public static Date serviceDate(String serviceDate) {
        return java.sql.Date.valueOf(LocalDate.parse(serviceDate, SERVICE_DATE));
    }

    public static String toOhipDate(Date date) {
        if (date == null) {
            return "";
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(OHIP_DATE);
    }

    private static LocalDate parseOhipDate(String raw, boolean normalizeZeroDay) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("\\d{8}")) {
            throw new IllegalArgumentException("Expected OHIP date in yyyyMMdd format: " + raw);
        }

        int year = Integer.parseInt(value.substring(0, 4));
        int month = Integer.parseInt(value.substring(4, 6));
        int day = Integer.parseInt(value.substring(6, 8));
        if (normalizeZeroDay && day == 0) {
            day = 1;
        }
        return LocalDate.of(year, month, day);
    }
}
