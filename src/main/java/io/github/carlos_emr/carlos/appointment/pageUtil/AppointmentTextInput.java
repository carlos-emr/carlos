/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Text exactly as it will be stored, with browser line endings normalized before length validation. */
record AppointmentTextInput(String name, String reason, String notes, String resources) {
    static AppointmentTextInput from(HttpServletRequest request, String name) {
        return new AppointmentTextInput(name, normalizeLines(request.getParameter("reason")),
                normalizeLines(request.getParameter("notes")), request.getParameter("resources"));
    }

    boolean rejectIfTooLong(HttpServletRequest request, HttpServletResponse response) {
        List<String> errors = new ArrayList<>();
        check(errors, "Name", name, 50);
        check(errors, "Reason", reason, 80);
        check(errors, "Notes", notes, 255);
        check(errors, "Resources", resources, 255);
        if (errors.isEmpty()) return false;
        request.setAttribute("appointmentValidationErrors", errors);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return true;
    }

    private static void check(List<String> errors, String field, String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum) {
            errors.add(field + " exceeds the maximum length of " + maximum + " characters.");
        }
    }

    private static String normalizeLines(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
