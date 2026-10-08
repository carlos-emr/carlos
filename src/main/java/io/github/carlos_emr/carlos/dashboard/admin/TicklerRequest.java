/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.dashboard.admin;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

/** Parses the dashboard form without side effects, before its at-most-once receipt is claimed. */
record TicklerRequest(Tickler tickler, Integer[] patients) {
    static TicklerRequest parse(Map<String, String[]> parameters) {
        String selected = single(parameters, "demographics").trim();
        if (selected.startsWith("[") && selected.endsWith("]")) {
            selected = selected.substring(1, selected.length() - 1);
        }
        var patients = new LinkedHashSet<Integer>();
        for (String token : selected.split(",", -1)) {
            String id = token.trim();
            if (!id.matches("[0-9]+")) throw new IllegalArgumentException("Invalid patient selection");
            int patient = Integer.parseInt(id);
            if (patient <= 0) throw new IllegalArgumentException("Invalid patient selection");
            patients.add(patient);
        }
        var serviceDate = parseServiceDateTime(single(parameters, "serviceDate").trim(),
                single(parameters, "serviceTime").trim());
        int category;
        try {
            category = Integer.parseInt(single(parameters, "ticklerCategoryId"));
        } catch (NumberFormatException e) {
            // Fixed text: the JSON error body must not echo request data.
            throw new IllegalArgumentException("Invalid category");
        }
        if (category < 0) throw new IllegalArgumentException("Invalid category");
        String assignee = single(parameters, "taskAssignedTo");
        if (assignee.isBlank()) throw new IllegalArgumentException("An assignee is required");
        var tickler = new Tickler();
        tickler.setServiceDate(serviceDate);
        tickler.setCategoryId(category);
        try {
            tickler.setPriority(Tickler.PRIORITY.valueOf(single(parameters, "priority")));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Select a priority");
        }
        tickler.setTaskAssignedTo(assignee);
        tickler.setMessage(single(parameters, "message") + " " + single(parameters, "messageAppend"));
        tickler.setStatus(Tickler.STATUS.A);
        return new TicklerRequest(tickler, patients.toArray(Integer[]::new));
    }

    /** Date layouts accepted: the picker's ISO form first, then the legacy {@link Tickler#DATE_FORMAT}. */
    private static final String[] DATE_PATTERNS = {"yyyy-MM-dd", Tickler.DATE_FORMAT};
    /** Time layouts accepted: 12-hour with AM/PM (legacy and picker) and 24-hour. */
    private static final String[] TIME_PATTERNS = {Tickler.TIME_FORMAT, "HH:mm"};

    /**
     * Strictly parses a service date and time, accepting any combination of the supported layouts.
     * The messages name the field and the expected layouts so the dashboard can show them to the user.
     */
    static Date parseServiceDateTime(String date, String time) {
        Date day = parseWhole(date, DATE_PATTERNS);
        if (day == null) {
            throw new IllegalArgumentException("Invalid service date. Use yyyy-MM-dd (for example 2026-10-15)");
        }
        Date clock = parseWhole(time, TIME_PATTERNS);
        if (clock == null) {
            throw new IllegalArgumentException("Invalid service time. Use hh:mm AM/PM or 24-hour HH:mm (for example 10:30 AM or 14:30)");
        }
        var merged = Calendar.getInstance();
        merged.setTime(day);
        var hm = Calendar.getInstance();
        hm.setTime(clock);
        merged.set(Calendar.HOUR_OF_DAY, hm.get(Calendar.HOUR_OF_DAY));
        merged.set(Calendar.MINUTE, hm.get(Calendar.MINUTE));
        return merged.getTime();
    }

    private static Date parseWhole(String text, String[] patterns) {
        for (String pattern : patterns) {
            var format = new SimpleDateFormat(pattern, Locale.ENGLISH);
            format.setLenient(false);
            var position = new ParsePosition(0);
            Date parsed = format.parse(text, position);
            if (parsed != null && position.getIndex() == text.length()) return parsed;
        }
        return null;
    }

    static String single(Map<String, String[]> parameters, String field) {
        String[] values = parameters.get(field);
        if (values == null || values.length != 1 || values[0] == null) {
            throw new IllegalArgumentException("A single value is required for " + field);
        }
        return values[0];
    }
}
