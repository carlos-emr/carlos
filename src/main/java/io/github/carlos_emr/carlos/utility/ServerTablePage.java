// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.dao.QueryPage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Publishes table navigation independently of the surrounding edit/search forms. */
public final class ServerTablePage {
    private ServerTablePage() { }

    public static int requested(HttpServletRequest request) {
        String value = request.getParameter("tablePage");
        if (value == null) return 0;
        if (!value.matches("[0-9]{1,9}")) throw new IllegalArgumentException("Invalid table page");
        return Integer.parseInt(value);
    }

    public static void publish(HttpServletRequest request, QueryPage<?> page, String action, String method,
            Map<String, String[]> parameters) {
        request.setAttribute("serverTablePage", page);
        request.setAttribute("serverTableAction", request.getContextPath() + action);
        request.setAttribute("serverTableMethod", method);
        request.setAttribute("serverTableParameters", new LinkedHashMap<>(parameters));
    }
}
