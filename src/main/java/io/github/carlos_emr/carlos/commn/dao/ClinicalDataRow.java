/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/** Materialized scalar values for the approved, schema-dependent eForm AP templates. */
public record ClinicalDataRow(Map<String, String> values) {
    public ClinicalDataRow {
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (!copy.containsKey(normalized)) copy.put(normalized, value);
        });
        values = Collections.unmodifiableMap(copy);
    }

    public String raw(String column) {
        String key = column.toLowerCase(Locale.ROOT);
        if (!values.containsKey(key)) throw new IllegalArgumentException("Unknown AP result column");
        return values.get(key);
    }

    public String text(String column) {
        return StringUtils.trimToEmpty(raw(column));
    }
}
