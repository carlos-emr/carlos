/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.data;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.CarlosProperties;

/**
 * Reads one report limit from {@code carlos.properties} (or the operator's override file).
 *
 * <p>Every report limit is a whole number from 1 to a documented maximum. There is deliberately no
 * value that switches a limit off: the limits bound the heap one report may hold and the time it may
 * keep a database connection, so an unlimited report could take the whole clinic's server down. A
 * missing or blank setting keeps the default. A value that is not a whole number of at least 1 also
 * keeps the default, and a value above the maximum is lowered to the maximum; each of those is
 * reported with one warning per setting and value for the life of the JVM, never once per report.</p>
 *
 * <p>The value is read each time a report runs, so an edited properties file takes effect at the
 * next restart (which is when {@link CarlosProperties} re-reads its files) and needs no further
 * cache to be cleared.</p>
 */
public final class ReportLimitSetting {
    private static final Logger LOGGER = LogManager.getLogger(ReportLimitSetting.class);
    /** Setting and value pairs already warned about, so a misconfigured value is not logged on every report run. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ReportLimitSetting() { }

    /**
     * Reads a limit from the running server's properties.
     *
     * @param key the property name, for example {@code report_by_template.max_rows}
     * @param fallback the default used when the setting is missing, blank or not allowed
     * @param ceiling the largest value an operator may set
     * @return the effective limit, from 1 to {@code ceiling}
     */
    public static int read(String key, int fallback, int ceiling) {
        return read(ReportLimitSetting::configured, key, fallback, ceiling);
    }

    /**
     * Reads a limit from the given settings; tests pass a map instead of the server's properties.
     *
     * @param settings returns the raw text of a setting, or null when it is not set
     * @param key the property name
     * @param fallback the default used when the setting is missing, blank or not allowed
     * @param ceiling the largest value an operator may set
     * @return the effective limit, from 1 to {@code ceiling}
     */
    public static int read(UnaryOperator<String> settings, String key, int fallback, int ceiling) {
        String raw = settings.apply(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String text = raw.trim();
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            // Digits too many for a long are still a number above the maximum.
            value = text.matches("\\+?[0-9]+") ? Long.MAX_VALUE : 0;
        }
        if (value < 1) {
            warnOnce(key, text, "{} must be a whole number of at least 1; using the default {}", fallback);
            return fallback;
        }
        if (value > ceiling) {
            warnOnce(key, text, "{} is above its maximum of {}; using {}", ceiling, ceiling);
            return ceiling;
        }
        return (int) value;
    }

    /**
     * The raw text of a setting in the running server's properties, or null when it is not set. The
     * optional settings are normally absent, so this asks whether the key exists first:
     * {@link CarlosProperties#getProperty(String)} warns about every key it cannot find.
     *
     * @param key the property name
     * @return the configured text, or null
     */
    public static String configured(String key) {
        CarlosProperties properties = CarlosProperties.getInstance();
        return properties.containsKey(key) ? properties.getProperty(key) : null;
    }

    /** Logs the setting's name, never its raw value, and only the first time this value is seen. */
    private static void warnOnce(String key, String value, String message, Object... numbers) {
        if (WARNED.add(key + '\n' + value)) {
            Object[] arguments = new Object[numbers.length + 1];
            arguments[0] = key;
            System.arraycopy(numbers, 0, arguments, 1, numbers.length);
            LOGGER.warn(message, arguments);
        }
    }
}
