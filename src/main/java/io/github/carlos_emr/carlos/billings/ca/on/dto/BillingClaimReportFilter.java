/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.LogSanitizer;

/** Typed filter for the legacy ON billing claim report query. */
public record BillingClaimReportFilter(
        String billType,
        String statusType,
        String providerNo,
        String startDate,
        String endDate,
        String demoNo,
        String serviceCodes,
        String dx,
        String visitType) {

    public BillingClaimReportFilter {
        LocalDate parsedStartDate = parseOptionalDate(startDate, "startDate");
        LocalDate parsedEndDate = parseOptionalDate(endDate, "endDate");
        if (parsedStartDate != null && parsedEndDate != null && parsedEndDate.isBefore(parsedStartDate)) {
            throw new BillingValidationException(
                    "BillingClaimReportFilter: endDate must be on or after startDate");
        }
    }

    private static LocalDate parseOptionalDate(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BillingValidationException(
                    "BillingClaimReportFilter: malformed " + fieldName + " ["
                            + LogSanitizer.sanitizeForDisplay(raw) + "]",
                    e);
        }
    }
}
