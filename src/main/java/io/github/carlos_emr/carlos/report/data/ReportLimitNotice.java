/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.data;

import jakarta.servlet.ServletRequest;

/**
 * Tells the user which limit stopped a configured (form, antenatal or clinical) report.
 *
 * <p>These reports fail through the shared error page, which shows nothing about a failure except
 * its status and incident reference. A limit refusal is not a fault, and the user can act on it
 * (narrow the filters, or ask an administrator to raise the limit), so the report entry points
 * publish it here and {@code errorpage.jsp} shows the bundle message for it. Only a bundle key, the
 * limit's number and the name of its {@code carlos.properties} setting are published: never the
 * exception, the SQL or any report value.</p>
 */
public final class ReportLimitNotice {
    /** Request attribute: bundle key of the limit's message. */
    public static final String KEY_ATTRIBUTE = "carlosReportLimitKey";
    /** Request attribute: the limit's value, as an Integer in the unit the message names. */
    public static final String VALUE_ATTRIBUTE = "carlosReportLimitValue";
    /** Request attribute: the setting an administrator can raise; absent when the limit is at its maximum. */
    public static final String SETTING_ATTRIBUTE = "carlosReportLimitSetting";

    /** Deep enough for an action's wrapper around a servlet's wrapper around the refusal. */
    private static final int MAX_CAUSE_DEPTH = 16;

    private ReportLimitNotice() { }

    /**
     * Publishes the limit refusal that caused a report failure, if there is one.
     *
     * @param request the request the error page will be rendered for; ignored when null
     * @param failure the failure, which may wrap the refusal as a cause
     * @return true when the failure was a limit refusal and it was published
     */
    public static boolean publish(ServletRequest request, Throwable failure) {
        ConfiguredReportQuery.LimitExceededException refusal = find(failure);
        if (refusal == null) {
            return false;
        }
        return publish(request, refusal.limit(), refusal.value());
    }

    /**
     * Publishes a refusal the caller decided itself, such as a download over its size limit.
     *
     * @param request the request the error page will be rendered for; ignored when null
     * @param limit the limit that was exceeded
     * @param value the limit's value in the unit its message names
     * @return true when the notice was published
     */
    public static boolean publish(ServletRequest request, ConfiguredReportQuery.Limit limit, int value) {
        if (request == null || limit == null) {
            return false;
        }
        request.setAttribute(KEY_ATTRIBUTE, limit.messageKey());
        request.setAttribute(VALUE_ATTRIBUTE, value);
        String setting = limit.raisableSetting(value);
        if (setting != null) {
            request.setAttribute(SETTING_ATTRIBUTE, setting);
        }
        return true;
    }

    /** The limit refusal in a failure's cause chain, or null. */
    public static ConfiguredReportQuery.LimitExceededException find(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof ConfiguredReportQuery.LimitExceededException refusal) {
                return refusal;
            }
            current = current.getCause();
        }
        return null;
    }
}
