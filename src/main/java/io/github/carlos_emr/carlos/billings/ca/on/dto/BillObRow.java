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

import java.util.Date;

/**
 * One row from the OB (obstetric) billing report query — a {@code Billing}
 * row whose {@code BillingDetail} matches one of the OB service codes
 * (P006A, P011A, P009A, P020A, P022A, P028A, P023A, P007A, P008B, P018B,
 * E502A, C989A, E409A, E410A, E411A, H001A).
 *
 * <p>Replaces the {@code List<Object[]>} return of
 * {@code BillingDao.search_billob} via JPA constructor projection
 * ({@code SELECT NEW ...}). Eliminates the per-iteration
 * {@code (Integer) row[0] / (String) row[1]} positional cast in
 * {@code BillingReportFragmentViewModelAssembler}.</p>
 *
 * @since 2026-05-01
 */
public record BillObRow(
        Integer id,
        String total,
        String status,
        Date billingDate,
        String demographicName) {
    public BillObRow {
        total = total == null ? "" : total;
        status = status == null ? "" : status;
        demographicName = demographicName == null ? "" : demographicName;
    }
}
