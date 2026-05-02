/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
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
    public BillingClaimReportRow {
        id = empty(id);
        payProgram = empty(payProgram);
        demographicNo = empty(demographicNo);
        demographicName = empty(demographicName);
        billingDate = empty(billingDate);
        billingTime = empty(billingTime);
        status = empty(status);
        providerNo = empty(providerNo);
        providerOhipNo = empty(providerOhipNo);
        updateDatetime = empty(updateDatetime);
        total = empty(total);
        paid = empty(paid);
        clinic = empty(clinic);
        serviceCount = empty(serviceCount);
        billingOnItemId = empty(billingOnItemId);
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
