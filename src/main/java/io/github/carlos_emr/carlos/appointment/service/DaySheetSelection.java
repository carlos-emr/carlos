/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.appointment.service;

import io.github.carlos_emr.carlos.commn.dao.AppointmentDaySheetRow;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Intersects the submitted preview versions with the freshly authorized report rows. */
public final class DaySheetSelection {
    private static final int MAX_ROWS = 10000;
    private final Map<Integer, String> versions;

    private DaySheetSelection(Map<Integer, String> versions) {
        this.versions = Map.copyOf(versions);
    }

    /** Missing selections never acknowledge the fresh query's entire result. */
    public static DaySheetSelection fromParameters(String[] parameters, String displayedCount) {
        Map<Integer, String> versions = new HashMap<>();
        if (parameters != null) {
            if (parameters.length > MAX_ROWS) {
                throw new IllegalArgumentException("Too many selected appointments");
            }
            for (String parameter : parameters) {
                if (parameter == null || parameter.length() > 40
                        || !parameter.matches("[1-9][0-9]{0,9}:(?:unversioned|-?[0-9]{1,19})")) {
                    throw new IllegalArgumentException("Invalid appointment selection");
                }
                int delimiter = parameter.indexOf(':');
                Integer id = Integer.valueOf(parameter.substring(0, delimiter));
                String version = parameter.substring(delimiter + 1);
                if (!version.equals("unversioned")) {
                    Long.parseLong(version);
                }
                if (versions.putIfAbsent(id, version) != null) {
                    throw new IllegalArgumentException("Duplicate appointment selection");
                }
            }
        }
        if (!Integer.toString(versions.size()).equals(displayedCount)) {
            throw new IllegalArgumentException("Incomplete appointment selection");
        }
        return new DaySheetSelection(versions);
    }

    /** Preserve the fresh report's ordering and leave final state/version checks to the row lock. */
    public List<AppointmentDaySheetRow> matching(List<AppointmentDaySheetRow> currentRows) {
        return currentRows.stream()
                .filter(row -> versions.containsKey(row.appointmentNo())
                        && Objects.equals(versions.get(row.appointmentNo()), row.editVersion()))
                .toList();
    }

    public int size() {
        return versions.size();
    }
}
