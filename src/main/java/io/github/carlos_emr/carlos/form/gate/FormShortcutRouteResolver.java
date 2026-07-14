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

import io.github.carlos_emr.carlos.form.data.FrmData;

/**
 * Resolves a legacy form shortcut name to the extensionless Struts form action
 * that can be forwarded to directly.
 *
 * @since 2026-07-14
 */
public final class FormShortcutRouteResolver {

    private static final String LATEST_FORM_ID = "latest";
    private static final int ASCII_CASE_OFFSET = 'a' - 'A';
    private static final String[] TRAILING_EMPTY_DEMOGRAPHIC_PARAMETERS = {
            "?demographic_no=",
            "?demographicNo=",
            "&demographic_no=",
            "&demographicNo="
    };

    private FormShortcutRouteResolver() {
    }

    /**
     * Resolves a stored form shortcut name into the internal extensionless form action route.
     *
     * @param demographicNo positive patient demographic number used by legacy form lookups
     * @param formName shortcut name from the encounter form configuration
     * @param formId requested form record id, {@code latest}, {@code 0} for a new form, or blank for latest
     * @param appointmentNo optional appointment number to preserve on browser form routes
     * @param provNo optional provider number to preserve on browser form routes
     * @return an application-relative route such as {@code /form/formannual?demographic_no=3&formId=9}
     * @throws SQLException when the legacy shortcut lookup fails
     * @throws IllegalArgumentException when the route, demographic number, form name, or form id is invalid
     */
    public static String resolve(
            String demographicNo,
            String formName,
            String formId,
            String appointmentNo,
            String provNo) throws SQLException {
        String resolvedDemographicNo = validateDemographicNo(demographicNo);
        String resolvedFormName = validateFormName(formName);
        String[] shortcutValues = new FrmData().getShortcutFormValue(resolvedDemographicNo, resolvedFormName);
        return resolve(shortcutValues, resolvedDemographicNo, formId, appointmentNo, provNo);
    }

    static String resolve(
            String[] shortcutValues,
            String demographicNo,
            String formId,
            String appointmentNo,
            String provNo) {
        String resolvedDemographicNo = validateDemographicNo(demographicNo);
        String actionPath = resolveActionPath(shortcutValues);
        String requestedFormId = requestedFormId(formId);
        int latestFormId = latestFormId(shortcutValues);
        int requestedFormNumber = 0;

        StringBuilder path = new StringBuilder(actionPath);
        appendQueryParameter(path, "demographic_no", resolvedDemographicNo);

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
        if (shortcutValues == null || shortcutValues.length == 0
                || shortcutValues[0] == null || shortcutValues[0].isBlank()) {
            throw new IllegalArgumentException("Invalid form path");
        }

        String legacyPath = stripTrailingEmptyDemographicParameter(shortcutValues[0]);
        if (legacyPath == null || legacyPath.isBlank()) {
            throw new IllegalArgumentException("Invalid form path");
        }
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

    private static String validateDemographicNo(String demographicNo) {
        if (demographicNo == null || demographicNo.isBlank()) {
            throw new IllegalArgumentException("Invalid demographic number");
        }
        String trimmed = demographicNo.trim();
        if (!isAsciiDigits(trimmed)) {
            throw new IllegalArgumentException("Invalid demographic number");
        }
        try {
            if (Integer.parseInt(trimmed) <= 0) {
                throw new IllegalArgumentException("Invalid demographic number");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid demographic number", e);
        }
        return trimmed;
    }

    private static String validateFormName(String formName) {
        if (formName == null || formName.isBlank()) {
            throw new IllegalArgumentException("Invalid form name");
        }
        return formName.trim();
    }

    private static String requestedFormId(String formId) {
        if (formId == null || formId.isBlank()) {
            return null;
        }
        String trimmed = formId.trim();
        if (isLatestFormId(trimmed)) {
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
        if (!isAsciiDigits(formId)) {
            throw new IllegalArgumentException("Invalid form id");
        }
        try {
            return Integer.parseInt(formId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid form id", e);
        }
    }

    private static boolean isLatestFormId(String value) {
        if (value.length() != LATEST_FORM_ID.length()) {
            return false;
        }
        for (int index = 0; index < LATEST_FORM_ID.length(); index++) {
            if (!equalsAsciiIgnoreCase(value.charAt(index), LATEST_FORM_ID.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean equalsAsciiIgnoreCase(char actual, char expectedLowercase) {
        return actual == expectedLowercase || actual == expectedLowercase - ASCII_CASE_OFFSET;
    }

    private static boolean isAsciiDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < '0' || current > '9') {
                return false;
            }
        }
        return true;
    }

    private static void appendQueryParameter(StringBuilder path, String name, String value) {
        path.append(path.indexOf("?") >= 0 ? "&" : "?")
                .append(name)
                .append("=")
                .append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
    }
}
