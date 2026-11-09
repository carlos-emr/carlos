/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, session-private definitions for independently rendered CDM forms. */
public final class CdmReportContexts {
    public static final String PARAMETER = "cdmFormContext";
    private static final String KEY = CdmReportContexts.class.getName();
    private static final long TTL_MILLIS = 60 * 60 * 1000L;
    private static final int CAPACITY = 64;
    private record Entry(long created, Map<String, Object> attributes) { }

    private CdmReportContexts() { }

    public static String issue(HttpServletRequest request) {
        return issue(request, System.currentTimeMillis());
    }

    static String issue(HttpServletRequest request, long now) {
        HttpSession session = request.getSession();
        synchronized (session) {
            Map<String, Entry> entries = entries(session);
            entries.values().removeIf(entry -> now - entry.created() >= TTL_MILLIS);
            while (entries.size() >= CAPACITY) entries.remove(entries.keySet().iterator().next());
            Map<String, Object> attributes = new LinkedHashMap<>();
            for (String name : new String[]{"measurementTypes", "CDMGroup", "today", "lastYear"}) {
                Object value = session.getAttribute(name);
                if (value != null) attributes.put(name, value);
            }
            String token = UUID.randomUUID().toString();
            entries.put(token, new Entry(now, Map.copyOf(attributes)));
            restore(request, token, entries.get(token));
            return token;
        }
    }

    public static boolean bind(HttpServletRequest request) {
        return bind(request, System.currentTimeMillis());
    }

    static boolean bind(HttpServletRequest request, long now) {
        HttpSession session = request.getSession(false);
        String token = request.getParameter(PARAMETER);
        if (session == null || token == null || token.length() != 36) return false;
        synchronized (session) {
            Entry entry = entries(session).get(token);
            if (entry == null) return false;
            if (now - entry.created() >= TTL_MILLIS) {
                entries(session).remove(token);
                return false;
            }
            restore(request, token, entry);
            return selectorsMatch(request, entry);
        }
    }

    private static boolean selectorsMatch(HttpServletRequest request, Entry entry) {
        Object stored = entry.attributes().get("measurementTypes");
        if (!(stored instanceof RptMeasurementTypesBeanHandler definitions)) return false;
        Map<String, java.util.Set<String>> allowed = new LinkedHashMap<>();
        for (int row = 0; row < definitions.getMeasurementTypeVector().size(); row++) {
            String type = definitions.getMeasurementTypeVector().get(row).getType();
            for (String suffix : new String[]{"", "C", "D"}) {
                allowed.put("value(measurementType" + suffix + row + ")", java.util.Set.of(type));
                var instructions = definitions.getMeasuringInstrcBeanVector().get(row).getMeasuringInstrcVector();
                for (int column = 0; column < instructions.size(); column++) {
                    String key = "value(mInstrcsCheckbox" + suffix + row + column + ")";
                    allowed.computeIfAbsent(key, ignored -> new java.util.HashSet<>()).add(instructions.get(column).getMeasuringInstrc());
                }
            }
        }
        for (var parameter : request.getParameterMap().entrySet()) {
            String key = parameter.getKey();
            if (!key.startsWith("value(measurementType") && !key.startsWith("value(mInstrcsCheckbox")) continue;
            var values = allowed.get(key);
            if (values == null || parameter.getValue().length != 1 || !values.contains(parameter.getValue()[0])) return false;
        }
        return true;
    }

    private static void restore(HttpServletRequest request, String token, Entry entry) {
        entry.attributes().forEach(request::setAttribute);
        request.setAttribute(PARAMETER, token);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Entry> entries(HttpSession session) {
        Object stored = session.getAttribute(KEY);
        if (stored instanceof Map<?, ?>) return (Map<String, Entry>) stored;
        Map<String, Entry> entries = new LinkedHashMap<>();
        session.setAttribute(KEY, entries);
        return entries;
    }
}
