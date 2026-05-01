/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

/** Row returned by the ON new-report billed-claim query. */
public record BillingOnNewReportBilledRow(
        String id,
        String billingDate,
        String billingTime,
        String demographicName,
        String status,
        String apptProviderNo,
        String providerNo,
        String clinic) {
}
