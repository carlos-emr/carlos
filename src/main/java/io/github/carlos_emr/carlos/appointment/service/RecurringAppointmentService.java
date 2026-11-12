/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.appointment.service;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.AppointmentUpdateException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Atomic recurring-booking operations using the existing series identity (no schema change). */
@Service
public class RecurringAppointmentService {
    /** The edit form's hidden field naming the version it was rendered from (AppointmentEditConflict.PARAMETER). */
    static final String APPOINTMENT_VERSION = "appointmentVersion";

    private final OscarAppointmentDao appointments;
    private final AppointmentArchiveDao archives;
    private final SecurityInfoManager security;

    public RecurringAppointmentService(OscarAppointmentDao appointments, AppointmentArchiveDao archives,
                                       SecurityInfoManager security) {
        this.appointments = appointments;
        this.archives = archives;
        this.security = security;
    }

    /**
     * All validation and series discovery precede writes; a failure rolls back the entire operation.
     *
     * @throws AppointmentUpdateException when the form names the version it was rendered from and the
     *         appointment has changed since (nothing is written)
     */
    @Transactional
    public int apply(LoggedInInfo user, Map<String, String> values, int programId) {
        String requestedId = values.get("appointment_no");
        boolean editsExisting = !"Add Group Appointment".equals(values.get("groupappt"))
                || (requestedId != null && !requestedId.isBlank());
        if (user == null || !security.hasPrivilege(user, "_appointment", "w", null)
                || (editsExisting && !security.hasPrivilege(user, "_appointment", "u", null))) {
            throw new SecurityException("missing required appointment privileges");
        }
        String operation = values.getOrDefault("groupappt", "");
        if (!Set.of("Add Group Appointment", "Group Update", "Group Cancel", "Group Delete").contains(operation)) {
            throw new IllegalArgumentException("Choose a recurring appointment action.");
        }
        Appointment template = template(values, programId, user.getLoggedInProviderNo());
        LocalDate start = Date.valueOf(values.get("appointment_date")).toLocalDate();
        LocalDate end;
        try {
            end = LocalDate.parse(values.getOrDefault("endDate", ""),
                    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Choose a valid end date (dd/mm/yyyy).");
        }
        boolean adding = operation.equals("Add Group Appointment");
        int interval = number(values.get("everyNum"), "repeat interval");
        String unit = values.getOrDefault("everyUnit", "");
        RecurrenceDates.validateRange(start, end, interval, unit);
        List<LocalDate> dates = adding ? RecurrenceDates.between(start, end, interval, unit) : List.of();
        String id = values.get("appointment_no");
        Appointment anchor = id == null || id.isBlank() ? null
                : appointments.findForUpdate(number(id, "appointment number"));
        if ((id != null && !id.isBlank() && anchor == null) || (!adding && anchor == null)) {
            throw new IllegalArgumentException("This appointment no longer exists. Reopen the schedule.");
        }
        // The repeat page is opened from the Edit Appointment form and carries its fields and the
        // version they were rendered from. A form another user's change has overtaken must not
        // write those stale fields over the series; the anchor row is locked, so the comparison
        // and the writes below see the same appointment.
        String renderedVersion = values.get(APPOINTMENT_VERSION);
        if (anchor != null && renderedVersion != null && !renderedVersion.isBlank()
                && !renderedVersion.trim().equals(anchor.getEditVersion())) {
            throw new AppointmentUpdateException(AppointmentUpdateException.Reason.STALE_VERSION);
        }
        if (anchor != null && !Objects.equals(anchor.getAppointmentDate(), template.getAppointmentDate())) {
            throw new IllegalArgumentException("Save the appointment date before changing its repeats.");
        }
        // These fields have no controls in the standard edit form. Absence is
        // not a request to erase existing scheduling/billing metadata.
        if (anchor != null) {
            if (!values.containsKey("style")) template.setStyle(anchor.getStyle());
            if (!values.containsKey("billing")) template.setBilling(anchor.getBilling());
        }
        if (adding && anchor != null && !sameDetails(anchor, template)) {
            throw new IllegalArgumentException("Save changes to the appointment before creating repeats.");
        }
        Appointment identity = anchor == null ? template : anchor;
        // Manage the saved series, including old month-end dates that drifted
        // under GregorianCalendar.add(). Recalculating a new pattern here would
        // silently miss those existing bookings.
        List<Appointment> existing = anchor == null ? List.of()
                : appointments.findRecurringSeries(anchor, Date.valueOf(end));
        if (existing.size() > 366) {
            throw new IllegalArgumentException("This range exceeds 366 appointments. Choose an earlier end date.");
        }
        Set<LocalDate> existingDates = existing.stream()
                .map(a -> new Date(a.getAppointmentDate().getTime()).toLocalDate())
                .collect(java.util.stream.Collectors.toSet());
        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate date : dates) {
            if (!existingDates.contains(date)) missing.add(date);
        }
        List<AuditRow> audit = new ArrayList<>();
        if (adding) {
            rejectBlockedDates(identity, missing);
            for (LocalDate date : missing) {
                Appointment occurrence = copy(identity);
                occurrence.setAppointmentDate(Date.valueOf(date));
                appointments.persist(occurrence);
                audit.add(new AuditRow(LogConst.ADD, occurrence, null));
            }
            auditAfterCommit(user, audit);
            return missing.size();
        }
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("No matching appointments were found. Reopen the schedule.");
        }
        java.util.Date updated = new java.util.Date();
        // Snapshot all matches first: updating the anchor must not change later series lookups.
        for (Appointment occurrence : existing) {
            archives.archiveAppointment(occurrence);
            if (operation.equals("Group Delete")) {
                audit.add(new AuditRow(LogConst.DELETE, occurrence, null));
                appointments.remove(occurrence.getId());
            } else {
                boolean cancel = operation.equals("Group Cancel");
                if (cancel) occurrence.setStatus("C");
                else applyDetails(occurrence, template, values.containsKey("style"), values.containsKey("billing"));
                occurrence.setUpdateDateTime(updated);
                occurrence.setLastUpdateUser(user.getLoggedInProviderNo());
                appointments.merge(occurrence);
                audit.add(new AuditRow(LogConst.UPDATE, occurrence, cancel ? "status=C" : null));
            }
        }
        auditAfterCommit(user, audit);
        return existing.size();
    }

    /** One appointment of the series as its audit row will name it: by id and patient, never by its text. */
    private record AuditRow(String action, Integer appointmentId, int demographicNo, String data) {
        AuditRow(String action, Appointment appointment, String data) {
            this(action, appointment.getId(), appointment.getDemographicNo(), data);
        }
    }

    /**
     * Audits each appointment the series action booked, changed, cancelled or removed, under the
     * appointment and its patient, as the single-appointment popup does. The rows are written
     * once the surrounding transaction has committed, so a series action that rolls back leaves
     * none; without a transaction they are written at once.
     */
    private static void auditAfterCommit(LoggedInInfo user, List<AuditRow> rows) {
        if (rows.isEmpty()) return;
        Runnable write = () -> {
            for (AuditRow row : rows) {
                LogAction.addLog(user, row.action(), LogConst.CON_APPT, String.valueOf(row.appointmentId()),
                        row.demographicNo() > 0 ? String.valueOf(row.demographicNo()) : null, row.data());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write.run();
                }
            });
        } else {
            write.run();
        }
    }

    /** Preflights every new occurrence before writing any member of the series. */
    private void rejectBlockedDates(Appointment template, List<LocalDate> dates) {
        if (dates.isEmpty()) return;
        List<java.util.Date> requestedDates = dates.stream().map(date -> (java.util.Date) Date.valueOf(date)).toList();
        if (!appointments.findDoNotBookDates(template, requestedDates).isEmpty()) {
            throw new IllegalArgumentException("A recurring appointment overlaps a Do Not Book slot. Choose another time.");
        }
    }

    private static Appointment template(Map<String, String> v, int programId, String creator) {
        Appointment a = new Appointment();
        try {
            a.setAppointmentDate(Date.valueOf(LocalDate.parse(v.getOrDefault("appointment_date", ""))));
            a.setStartTime(Time.valueOf(LocalTime.parse(v.getOrDefault("start_time", ""))));
            a.setEndTime(Time.valueOf(LocalTime.parse(v.getOrDefault("end_time", ""))));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Choose a valid appointment date and start/end time.");
        }
        if (a.getEndTime().before(a.getStartTime())) {
            throw new IllegalArgumentException("The appointment must end on the same day, after it starts.");
        }
        String provider = v.getOrDefault("provider_no", "");
        if (provider.isBlank()) throw new IllegalArgumentException("Choose an appointment provider.");
        a.setProviderNo(provider);
        String demographic = v.getOrDefault("demographic_no", "");
        a.setDemographicNo(demographic.isBlank() ? 0 : number(demographic, "patient number"));
        a.setProgramId(programId);
        a.setName(v.getOrDefault("keyword", ""));
        a.setNotes(v.getOrDefault("notes", ""));
        a.setReason(v.getOrDefault("reason", ""));
        a.setLocation(v.getOrDefault("location", ""));
        a.setResources(v.getOrDefault("resources", ""));
        a.setType(v.getOrDefault("type", ""));
        a.setStyle(v.getOrDefault("style", ""));
        a.setBilling(v.getOrDefault("billing", ""));
        a.setStatus(v.getOrDefault("status", ""));
        a.setRemarks(v.getOrDefault("remarks", ""));
        a.setUrgency(v.getOrDefault("urgency", ""));
        String reasonCode = v.get("reasonCode");
        a.setReasonCode(reasonCode == null || reasonCode.isBlank() ? null
                : "-1".equals(reasonCode) ? -1 : number(reasonCode, "reason code"));
        // Whole seconds match the legacy database precision, shared by every new occurrence.
        a.setCreateDateTime(new java.util.Date(System.currentTimeMillis() / 1000 * 1000));
        a.setCreator(creator);
        return a;
    }

    private static int number(String value, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Choose a valid " + label + ".");
        }
    }

    private static boolean equalText(String first, String second) {
        return Objects.equals(first == null ? "" : first, second == null ? "" : second);
    }

    private static boolean sameDetails(Appointment a, Appointment b) {
        return equalText(a.getProviderNo(), b.getProviderNo()) && a.getDemographicNo() == b.getDemographicNo()
                && Objects.equals(a.getStartTime(), b.getStartTime()) && Objects.equals(a.getEndTime(), b.getEndTime())
                && equalText(a.getName(), b.getName())
                && equalText(Appointment.normalizeLineEndings(a.getNotes()), Appointment.normalizeLineEndings(b.getNotes()))
                && equalText(Appointment.normalizeLineEndings(a.getReason()), Appointment.normalizeLineEndings(b.getReason()))
                && equalText(a.getLocation(), b.getLocation())
                && equalText(a.getResources(), b.getResources()) && equalText(a.getType(), b.getType())
                && equalText(a.getStyle(), b.getStyle()) && equalText(a.getBilling(), b.getBilling())
                && equalText(a.getStatus(), b.getStatus()) && equalText(a.getRemarks(), b.getRemarks())
                && equalText(a.getUrgency(), b.getUrgency()) && Objects.equals(a.getReasonCode(), b.getReasonCode());
    }

    private static void applyDetails(Appointment target, Appointment source, boolean updateStyle, boolean updateBilling) {
        target.setProviderNo(source.getProviderNo());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setName(source.getName());
        target.setDemographicNo(source.getDemographicNo());
        target.setNotes(source.getNotes());
        target.setReason(source.getReason());
        target.setLocation(source.getLocation());
        target.setResources(source.getResources());
        target.setType(source.getType());
        if (updateStyle) target.setStyle(source.getStyle());
        if (updateBilling) target.setBilling(source.getBilling());
        target.setStatus(source.getStatus());
        target.setRemarks(source.getRemarks());
        target.setUrgency(source.getUrgency());
        target.setReasonCode(source.getReasonCode());
    }

    private static Appointment copy(Appointment source) {
        Appointment target = new Appointment();
        applyDetails(target, source, true, true);
        target.setProgramId(source.getProgramId());
        target.setCreateDateTime(source.getCreateDateTime());
        target.setCreator(source.getCreator());
        target.setCreatorSecurityId(source.getCreatorSecurityId());
        target.setBookingSource(source.getBookingSource());
        return target;
    }
}
