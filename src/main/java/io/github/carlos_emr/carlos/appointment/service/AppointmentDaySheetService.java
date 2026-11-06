/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.appointment.service;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.AppointmentDaySheetRow;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies report visibility before atomically archiving and acknowledging new appointments. */
@Service
public class AppointmentDaySheetService {
    private final OscarAppointmentDao appointments;
    private final AppointmentArchiveDao archives;

    public AppointmentDaySheetService(OscarAppointmentDao appointments, AppointmentArchiveDao archives) {
        this.appointments = appointments;
        this.archives = archives;
    }

    /**
     * Returns rows in report order. Null provider sets mean unrestricted; empty sets admit no rows.
     * The new-only view acknowledges precisely the returned appointments, retaining each prior
     * version in the same transaction. Rows edited or acknowledged since selection are omitted.
     */
    @Transactional
    public List<AppointmentDaySheetRow> prepare(List<AppointmentDaySheetRow> rows, boolean newOnly,
            Set<String> groupProviders, Set<String> permittedProviders, boolean nonRosteredOnly, String updatedBy) {
        List<AppointmentDaySheetRow> visible = rows.stream()
                .filter(row -> !nonRosteredOnly || !"RO".equalsIgnoreCase(row.rosterStatus()))
                .filter(row -> groupProviders == null || groupProviders.contains(row.providerNo()))
                .filter(row -> permittedProviders == null || permittedProviders.contains(row.providerNo()))
                .toList();
        if (!newOnly) return visible;

        Set<Integer> acknowledged = new HashSet<>();
        // A stable lock order avoids inverted locks when two users select different report sorts.
        for (AppointmentDaySheetRow row : visible.stream()
                .sorted(Comparator.comparing(AppointmentDaySheetRow::appointmentNo)).toList()) {
            Appointment appointment = appointments.findForUpdate(row.appointmentNo());
            if (appointment == null || !"t".equals(appointment.getStatus())
                    || !Objects.equals(row.providerNo(), appointment.getProviderNo())
                    || !Objects.equals(row.editVersion(), appointment.getEditVersion())
                    || !Objects.equals(row.date(), ConversionUtils.toDateString(appointment.getAppointmentDate()))) {
                continue;
            }
            archives.archiveAppointment(appointment);
            appointment.setStatus("T");
            appointment.setLastUpdateUser(updatedBy);
            appointment.setUpdateDateTime(new Date());
            appointments.merge(appointment);
            acknowledged.add(row.appointmentNo());
        }
        return visible.stream().filter(row -> acknowledged.contains(row.appointmentNo())).toList();
    }
}
