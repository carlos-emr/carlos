/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.dashboard.admin;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

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
        String dateTime = single(parameters, "serviceDate") + " " + single(parameters, "serviceTime");
        var format = new SimpleDateFormat(Tickler.DATE_FORMAT + " " + Tickler.TIME_FORMAT);
        format.setLenient(false);
        var position = new ParsePosition(0);
        var serviceDate = format.parse(dateTime, position);
        if (serviceDate == null || position.getIndex() != dateTime.length()) {
            throw new IllegalArgumentException("Invalid service date or time");
        }
        int category = Integer.parseInt(single(parameters, "ticklerCategoryId"));
        if (category < 0) throw new IllegalArgumentException("Invalid category");
        String assignee = single(parameters, "taskAssignedTo");
        if (assignee.isBlank()) throw new IllegalArgumentException("An assignee is required");
        var tickler = new Tickler();
        tickler.setServiceDate(serviceDate);
        tickler.setCategoryId(category);
        tickler.setPriority(Tickler.PRIORITY.valueOf(single(parameters, "priority")));
        tickler.setTaskAssignedTo(assignee);
        tickler.setMessage(single(parameters, "message") + " " + single(parameters, "messageAppend"));
        tickler.setStatus(Tickler.STATUS.A);
        return new TicklerRequest(tickler, patients.toArray(Integer[]::new));
    }

    // A record compares and prints an array component by identity; use the contents of the
    // patient numbers instead. toString reports only how many were selected, so a logged request
    // never carries demographic numbers.
    @Override
    public boolean equals(Object other) {
        return other instanceof TicklerRequest that
                && Objects.equals(tickler, that.tickler) && Arrays.equals(patients, that.patients);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(tickler) + Arrays.hashCode(patients);
    }

    @Override
    public String toString() {
        return "TicklerRequest[tickler=" + tickler + ", patients="
                + (patients == null ? "null" : patients.length + " selected") + "]";
    }

    static String single(Map<String, String[]> parameters, String field) {
        String[] values = parameters.get(field);
        if (values == null || values.length != 1 || values[0] == null) {
            throw new IllegalArgumentException("A single value is required for " + field);
        }
        return values[0];
    }
}
