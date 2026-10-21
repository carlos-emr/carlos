/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import org.apache.struts2.ActionSupport;

/** The four native booking text fields and their existing utf8mb4 column capacities. */
final class AppointmentTextBounds {
    static final String RETRY = "appointmentTextRetry";

    record Values(String name, String reason, String notes, String resources) { }
    record Violation(String labelKey, int maximum, boolean invalidUnicode) { }

    private AppointmentTextBounds() { }

    /** HTML textarea submissions encode one logical line break as CR LF. */
    static String logicalLines(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String preserveUnchanged(String submitted, String stored) {
        return stored != null && Objects.equals(logicalLines(submitted), logicalLines(stored))
                ? stored : logicalLines(submitted);
    }

    static Values submitted(HttpServletRequest request, String name, Appointment previous) {
        return new Values(name,
                preserveUnchanged(request.getParameter("reason"), previous == null ? null : previous.getReason()),
                preserveUnchanged(request.getParameter("notes"), previous == null ? null : previous.getNotes()),
                request.getParameter("resources"));
    }

    static Violation validate(Values values, Appointment previous) {
        Violation failure = check(values.name(), previous == null ? null : previous.getName(), 255, "Appointment.formName", false);
        if (failure == null) failure = check(values.reason(), previous == null ? null : previous.getReason(), 80, "Appointment.formReason", true);
        if (failure == null) failure = check(values.notes(), previous == null ? null : previous.getNotes(), 255, "Appointment.formNotes", true);
        if (failure == null) failure = check(values.resources(), previous == null ? null : previous.getResources(), 255, "Appointment.formResources", false);
        return failure;
    }

    private static Violation check(String value, String stored, int maximum, String labelKey, boolean textarea) {
        if (value == null) return null;
        for (int i = 0; i < value.length(); i++) {
            char unit = value.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    return new Violation(labelKey, maximum, true);
                }
            } else if (Character.isLowSurrogate(unit)) {
                return new Violation(labelKey, maximum, true);
            }
        }
        String logical = textarea ? logicalLines(value) : value;
        // Reading or saving a legacy value unchanged must not rewrite or shorten it.
        if (stored != null && Objects.equals(logical, textarea ? logicalLines(stored) : stored)) return null;
        return logical.codePointCount(0, logical.length()) > maximum ? new Violation(labelKey, maximum, false) : null;
    }

    static String refuse(HttpServletRequest request, HttpServletResponse response, Violation violation) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        request.setAttribute(RETRY, Boolean.TRUE);
        request.setAttribute("appointmentTextLabelKey", violation.labelKey());
        request.setAttribute("appointmentTextMaximum", violation.maximum());
        request.setAttribute("appointmentTextMessageKey", violation.invalidUnicode()
                ? "Appointment.textInvalidUnicode" : "Appointment.textTooLong");
        // Native forms carry these fields. Malformed API posts get an explicit refusal
        // without forwarding their invalid date/status through a parsing JSP.
        if (!canRedisplay(request)) {
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/plain");
            response.getWriter().write(violation.invalidUnicode()
                    ? "Appointment text contains an invalid Unicode character. Nothing was saved."
                    : "Appointment text exceeds the maximum length of " + violation.maximum() + " characters. Nothing was saved.");
            return ActionSupport.NONE;
        }
        return ActionSupport.INPUT;
    }

    private static boolean canRedisplay(HttpServletRequest request) {
        String date = request.getParameter("appointment_date");
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try { if (!LocalDate.parse(date).toString().equals(date)) return false; }
        catch (DateTimeParseException invalidDate) { return false; }
        String timePattern = "(?:[01]\\d|2[0-3]):[0-5]\\d";
        String start = request.getParameter("start_time"), end = request.getParameter("end_time");
        String demographic = request.getParameter("demographic_no");
        String duration = request.getParameter("duration");
        if (duration != null && !duration.isEmpty() && !duration.equals(" ") && !duration.equals("null")) {
            try { Integer.parseInt(duration); }
            catch (NumberFormatException invalidDuration) { return false; }
        }
        if (demographic != null && !demographic.isEmpty()) {
            if (!demographic.matches("\\d{1,10}")) return false;
            try { if (Long.parseLong(demographic) > Integer.MAX_VALUE) return false; }
            catch (NumberFormatException invalidIdentity) { return false; }
        }
        return start != null && start.matches(timePattern) && end != null && end.matches(timePattern)
                && request.getParameter("status") != null;
    }
}
