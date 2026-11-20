/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.app;

import java.util.Map;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFileWriteException;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

/**
 * Converts mapped failures to fixed public guidance without exposing clinical diagnostics.
 * Original exception messages, filenames, context, causes and code locations stay off error pages.
 *
 * @since 2026-09-27
 */
public final class PublicExceptionDetails {
    private static final String VALIDATION =
            "Billing information could not be validated. Reload the chart and review the selected records, form values and existing entries before retrying.";
    private static final String DATA_LOAD =
            "Billing data could not be loaded. The displayed results may be incomplete; contact your billing administrator before proceeding.";
    private static final String GENERAL =
            "The request could not be completed. Contact your administrator and quote the incident reference.";

    private PublicExceptionDetails() { }

    /**
     * Returns only fixed messages, including when a JSP receives a raw container error.
     *
     * @param failure a mapped, forwarded or absent failure
     * @return public guidance without interpolated diagnostic values
     */
    public static String message(Throwable failure) {
        if (failure instanceof BillingFileWriteException fileFailure) {
            return fileFailure.reason().publicMessage();
        }
        if (failure instanceof BillingDataLoadException) return DATA_LOAD;
        if (failure instanceof BillingValidationException) return VALIDATION;
        return GENERAL;
    }

    /**
     * Retains known billing types and a safe phase while removing every raw diagnostic field.
     *
     * @param failure the internal failure, never published to the view
     * @return a new exception with a fixed message, no cause/context/filename and no stack trace
     */
    public static Exception forView(Exception failure) {
        Exception visible;
        if (failure instanceof BillingFileWriteException fileFailure) {
            visible = BillingFileWriteException.forReason(fileFailure.reason(), null);
        } else if (failure instanceof BillingDataLoadException dataFailure) {
            visible = new BillingDataLoadException(message(failure), dataFailure.phase(), Map.of());
        } else if (failure instanceof BillingValidationException) {
            visible = new BillingValidationException(message(failure));
        } else {
            visible = new Exception(message(failure));
        }
        visible.setStackTrace(new StackTraceElement[0]);
        return visible;
    }
}
