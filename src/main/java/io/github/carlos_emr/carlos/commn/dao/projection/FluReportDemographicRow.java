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
 * Demographic details required by the Flu Billing Report.
 *
 * <p>The typed projection keeps the native-query column contract inside the
 * DAO and prevents report consumers from depending on positional
 * {@code Object[]} indexes.</p>
 */
public record FluReportDemographicRow(
        String demographicNo,
        String patientName,
        String phone,
        String rosterStatus,
        String patientStatus,
        String dateOfBirth,
        String age) {

    public FluReportDemographicRow {
        demographicNo = demographicNo == null ? "" : demographicNo;
        patientName = patientName == null ? "" : patientName;
        phone = phone == null ? "" : phone;
        rosterStatus = rosterStatus == null ? "" : rosterStatus;
        patientStatus = patientStatus == null ? "" : patientStatus;
        dateOfBirth = dateOfBirth == null ? "" : dateOfBirth;
        age = age == null ? "" : age;
    }
}
