/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

/** Row returned by the legacy ON billing claim report query. */
public record BillingClaimReportRow(
        String id,
        String payProgram,
        String demographicNo,
        String demographicName,
        String billingDate,
        String billingTime,
        String status,
        String providerNo,
        String providerOhipNo,
        String updateDatetime,
        String total,
        String paid,
        String clinic,
        String serviceCount,
        String billingOnItemId) {
}
