/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

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
}
