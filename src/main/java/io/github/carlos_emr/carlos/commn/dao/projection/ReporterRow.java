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
package io.github.carlos_emr.carlos.commn.dao.projection;

/**
 * One billing-report-eligible provider — joined from
 * {@code (ReportProvider r, Provider p)} by
 * {@code ReportProviderDao.search_reportprovider}, projected to the only
 * three fields the three consumers ({@code BillingReportControlViewModelAssembler},
 * {@code BillingOnNewReportViewModelAssembler}) actually read. The
 * {@code ReportProvider} half of the join was returned but never read by the
 * legacy scriptlets — dropped here.
 *
 * @since 2026-05-01
 */
public record ReporterRow(String providerNo, String firstName, String lastName) {
    public ReporterRow {
        providerNo = providerNo == null ? "" : providerNo;
        firstName = firstName == null ? "" : firstName;
        lastName = lastName == null ? "" : lastName;
    }
}
