/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.util.NativeQueryValues;
import java.util.Date;

/** Display scalars for a day-sheet appointment, including nullable left-joined patient data. */
public record AppointmentDaySheetRow(String dob, String familyDoctor, String date, String providerNo,
        String startTime, String endTime, String reason, String name, String bookingSource,
        String lastName, String firstName, String sex, String hin, String ver, String doctorNo,
        String phone, String rosterStatus, String doctorLastName, String doctorFirstName, String chartNo,
        Integer appointmentNo, String editVersion) {
    static AppointmentDaySheetRow from(Object[] row) {
        String[] values = new String[row.length];
        for (int i = 0; i < row.length; i++) values[i] = row[i] == null ? null : row[i].toString();
        Date updated = NativeQueryValues.asDate(row[21]);
        String version = updated == null ? "unversioned" : Long.toString(updated.getTime());
        return new AppointmentDaySheetRow(values[0], values[1], values[2], values[3], values[4], values[5],
                values[6], values[7], values[8], values[9], values[10], values[11], values[12], values[13],
                values[14], values[15], values[16], values[17], values[18], values[19], ((Number) row[20]).intValue(), version);
    }
}
