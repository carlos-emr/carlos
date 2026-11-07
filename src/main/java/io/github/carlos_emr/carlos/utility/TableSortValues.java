/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility;

/**
 * Sort keys for the JSTL tables that replaced displaytag (#5472), exposed to JSPs as the
 * {@code carlos:tableSortValue} and {@code carlos:tableSortType} EL functions.
 *
 * <p>displaytag sorted a column by the bean property's own value: numbers and dates through
 * {@link Comparable}, strings through a collator. A JSP cell renders that value as text (a date in
 * its display format), so the cell carries the raw key in {@code data-sort-value} and its kind in
 * {@code data-sort-type}, and {@code carlos-tables.js} compares them the same way without
 * changing what the cell shows. Strings stay {@code text} even when they look numeric, so codes
 * such as {@code 00123} keep displaytag's textual order.
 *
 * @since 2026-11-02
 */
public final class TableSortValues {
    private TableSortValues() { }

    /**
     * Returns the browser comparison kind for a property value.
     *
     * @param value the bean property shown in the cell, possibly {@code null}
     * @return {@code number} for a {@link Number}, {@code date} for a {@link java.util.Date}
     *     (including SQL dates and timestamps), otherwise {@code text}
     */
    public static String type(Object value) {
        if (value instanceof Number) return "number";
        if (value instanceof java.util.Date) return "date";
        return "text";
    }

    /**
     * Returns the sort key for a property value.
     *
     * @param value the bean property shown in the cell, possibly {@code null}
     * @return an empty string for {@code null} (sorted first, as displaytag sorted nulls), epoch
     *     milliseconds for a date, otherwise {@link Object#toString()}
     */
    public static String value(Object value) {
        if (value == null) return "";
        if (value instanceof java.util.Date date) return Long.toString(date.getTime());
        return value.toString();
    }
}
