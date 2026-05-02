/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.commn.dao.projection;

/** Row returned by the ON new-report unbilled appointment query. */
public record BillingOnNewReportUnbilledRow(
        String appointmentNo,
        String providerNo,
        String appointmentDate,
        String startTime,
        String demographicNo,
        String name,
        String reason,
        String location) {
}
