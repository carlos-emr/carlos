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
package io.github.carlos_emr.carlos.form.gate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;

import io.github.carlos_emr.carlos.form.data.FrmData;

/**
 * Resolves a legacy form shortcut name to the extensionless Struts form action
 * that can be forwarded to directly.
 */
public final class FormShortcutRouteResolver {

    private static final String LATEST_FORM_ID = "latest";
    private static final String[] TRAILING_EMPTY_DEMOGRAPHIC_PARAMETERS = {
            "?demographic_no=",
            "?demographicNo=",
            "&demographic_no=",
            "&demographicNo="
    };

    private FormShortcutRouteResolver() {
    }

    public static String resolve(
            String demographicNo,
            String formName,
            String formId,
            String appointmentNo,
            String provNo) throws SQLException {
        validateDemographicNo(demographicNo);
        String[] shortcutValues = new FrmData().getShortcutFormValue(demographicNo, formName);
        return resolve(shortcutValues, demographicNo, formId, appointmentNo, provNo);
    }

    static String resolve(
            String[] shortcutValues,
            String demographicNo,
            String formId,
            String appointmentNo,
            String provNo) {
        validateDemographicNo(demographicNo);
        String actionPath = resolveActionPath(shortcutValues);
        String requestedFormId = requestedFormId(formId);
        int latestFormId = latestFormId(shortcutValues);
        int requestedFormNumber = 0;

        StringBuilder path = new StringBuilder(actionPath);
        appendQueryParameter(path, "demographic_no", demographicNo);

        if (requestedFormId != null) {
            requestedFormNumber = parseFormId(requestedFormId);
            appendQueryParameter(path, "formId", requestedFormId);
        } else if (latestFormId > 0) {
            appendQueryParameter(path, "formId", String.valueOf(latestFormId));
        }

        if (requestedFormNumber > 0 && latestFormId > 0 && requestedFormNumber < latestFormId) {
            appendQueryParameter(path, "warning", "history");
        }
        if (appointmentNo != null && !appointmentNo.isBlank()) {
            appendQueryParameter(path, "appointmentNo", appointmentNo);
        }
        if (provNo != null && !provNo.isBlank()) {
            appendQueryParameter(path, "provNo", provNo);
        }

        return path.toString();
    }

    private static String resolveActionPath(String[] shortcutValues) {
        if (shortcutValues == null || shortcutValues.length == 0) {
            throw new IllegalArgumentException("Invalid form path");
        }

        String legacyPath = stripTrailingEmptyDemographicParameter(shortcutValues[0]);
        String actionPath = FormViewRoutes.resolveActionPath(legacyPath);
        if (actionPath == null) {
            throw new IllegalArgumentException("Invalid form path");
        }
        return actionPath;
    }

    private static String stripTrailingEmptyDemographicParameter(String legacyPath) {
        if (legacyPath == null) {
            return null;
        }
        String trimmed = legacyPath.trim();
        for (String suffix : TRAILING_EMPTY_DEMOGRAPHIC_PARAMETERS) {
            if (trimmed.endsWith(suffix)) {
                return trimmed.substring(0, trimmed.length() - suffix.length());
            }
        }
        return trimmed;
    }

    private static void validateDemographicNo(String demographicNo) {
        if (demographicNo == null || demographicNo.isBlank()) {
            throw new IllegalArgumentException("Invalid demographic number");
        }
    }

    private static String requestedFormId(String formId) {
        if (formId == null || formId.isBlank()) {
            return null;
        }
        String trimmed = formId.trim();
        if (LATEST_FORM_ID.equals(trimmed.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return trimmed;
    }

    private static int latestFormId(String[] shortcutValues) {
        if (shortcutValues.length <= 1 || shortcutValues[1] == null || shortcutValues[1].isBlank()) {
            return 0;
        }
        return parseFormId(shortcutValues[1].trim());
    }

    private static int parseFormId(String formId) {
        try {
            return Integer.parseInt(formId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid form id", e);
        }
    }

    private static void appendQueryParameter(StringBuilder path, String name, String value) {
        path.append(path.indexOf("?") >= 0 ? "&" : "?")
                .append(name)
                .append("=")
                .append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
    }
}
