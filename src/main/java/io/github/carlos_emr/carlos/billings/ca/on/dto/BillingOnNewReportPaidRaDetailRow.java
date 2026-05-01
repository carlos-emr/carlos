/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

/** RA detail row returned for the ON new-report paid mode. */
public record BillingOnNewReportPaidRaDetailRow(
        String billingNo,
        String amountClaim,
        String amountPay,
        String hin,
        String serviceDate) {
}
