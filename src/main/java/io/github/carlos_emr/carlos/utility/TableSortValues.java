/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility;

/** Supplies typed sort keys without changing a cell's displayed text or date formatting. */
public final class TableSortValues {
    private TableSortValues() { }
    public static String type(Object value) {
        if (value instanceof Number) return "number";
        if (value instanceof java.util.Date) return "date";
        return "text";
    }
    public static String value(Object value) {
        if (value == null) return "";
        if (value instanceof java.util.Date date) return Long.toString(date.getTime());
        return value.toString();
    }
}
