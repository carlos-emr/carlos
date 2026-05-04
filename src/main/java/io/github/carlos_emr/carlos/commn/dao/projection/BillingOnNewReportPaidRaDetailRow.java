/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.commn.dao.projection;

/** RA detail row returned for the ON new-report paid mode. */
public record BillingOnNewReportPaidRaDetailRow(
        String billingNo,
        String amountClaim,
        String amountPay,
        String hin,
        String serviceDate) {
}
