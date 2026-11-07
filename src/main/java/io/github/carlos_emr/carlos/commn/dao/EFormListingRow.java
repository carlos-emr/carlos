/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/** Legacy eForm list text, with database date/time formatting preserved by the projection. */
public record EFormListingRow(String dataId, String formId, String name, String subject,
        String fileName, String date, String time, String roleType) {
    static EFormListingRow from(Object[] row) {
        return new EFormListingRow(text(row[0]), text(row[1]), text(row[2]), text(row[3]),
                text(row[4]), text(row[5]), text(row[6]), text(row[7]));
    }

    private static String text(Object value) {
        return StringUtils.trimToEmpty(value == null ? null : value.toString());
    }

    static String requireSort(String sort) {
        if (!Set.of("form_name", "subject", "form_date DESC, form_time DESC", "file_name", "form_provider").contains(sort)) {
            throw new IllegalArgumentException("Invalid eForm list sort");
        }
        return sort;
    }

    public HashMap<String, String> toLegacyMap(boolean patient) {
        var result = new HashMap<String, String>();
        if (patient) result.put("fdid", dataId);
        result.putAll(Map.of("fid", formId, "formName", name, "formSubject", subject,
                "formDate", date, "formTime", time, "roleType", roleType));
        if (!patient) result.put("formFileName", fileName);
        return result;
    }
}
