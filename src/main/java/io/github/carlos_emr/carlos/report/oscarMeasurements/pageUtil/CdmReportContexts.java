/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, session-private definitions for independently rendered CDM forms.
 * @since 2026-11-09
 */
public final class CdmReportContexts {
    public static final String PARAMETER = "cdmFormContext";
    static final String MEASUREMENT_TYPES = "measurementTypes";
    static final String GROUP = "CDMGroup";
    static final String TODAY = "today";
    static final String LAST_YEAR = "lastYear";
    private static final String KEY = CdmReportContexts.class.getName();
    private static final long TTL_MILLIS = 60 * 60 * 1000L;
    private static final int CAPACITY = 64;
    private record Entry(long created, LinkedHashMap<String, Serializable> attributes) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    private CdmReportContexts() { }

    public static String issue(HttpServletRequest request, Map<String, Object> attributes) {
        return issue(request, attributes, System.currentTimeMillis());
    }

    static String issue(HttpServletRequest request, Map<String, Object> attributes, long now) {
        HttpSession session = request.getSession();
        synchronized (session) {
            LinkedHashMap<String, Entry> entries = entries(session);
            entries.values().removeIf(entry -> now - entry.created() >= TTL_MILLIS);
            while (entries.size() >= CAPACITY) entries.remove(entries.keySet().iterator().next());
            LinkedHashMap<String, Serializable> snapshot = new LinkedHashMap<>();
            attributes.forEach((name, value) -> {
                Object captured = value;
                if (MEASUREMENT_TYPES.equals(name) && value instanceof RptMeasurementTypesBeanHandler definitions) {
                    captured = RptMeasurementTypesBeanHandler.snapshot(definitions);
                }
                if (!(captured instanceof Serializable serializable)) {
                    throw new IllegalArgumentException("CDM form attributes must be serializable: " + name);
                }
                snapshot.put(java.util.Objects.requireNonNull(name), serializable);
            });
            String token = UUID.randomUUID().toString();
            entries.put(token, new Entry(now, snapshot));
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

    public static void refuse(jakarta.servlet.http.HttpServletResponse response, org.apache.struts2.ActionSupport action)
            throws java.io.IOException {
        response.sendError(jakarta.servlet.http.HttpServletResponse.SC_CONFLICT,
                action.getText("oscarReport.CDMReport.msgStaleForm"));
    }

    private static boolean selectorsMatch(HttpServletRequest request, Entry entry) {
        Object stored = entry.attributes().get(MEASUREMENT_TYPES);
        if (!(stored instanceof RptMeasurementTypesBeanHandler definitions)) return false;
        Map<String, java.util.Set<String>> allowed = new LinkedHashMap<>();
        for (int row = 0; row < definitions.getMeasurementTypeVector().size(); row++) {
            String type = definitions.getMeasurementTypeVector().get(row).getType();
            for (String suffix : new String[]{"", "C", "D"}) {
                allowed.put("value(measurementType" + suffix + row + ")", java.util.Set.of(type));
                var instructions = definitions.getMeasuringInstrcBeanVector().get(row).getMeasuringInstrcVector();
                var choices = new java.util.HashSet<String>();
                for (var instruction : instructions) choices.add(instruction.getMeasuringInstrc());
                for (int column = 0; column < instructions.size(); column++) {
                    String key = "value(mInstrcsCheckbox" + suffix + row + column + ")";
                    allowed.computeIfAbsent(key, ignored -> new java.util.HashSet<>()).addAll(choices);
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
    private static LinkedHashMap<String, Entry> entries(HttpSession session) {
        Object stored = session.getAttribute(KEY);
        if (stored instanceof LinkedHashMap<?, ?>) return (LinkedHashMap<String, Entry>) stored;
        LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
        session.setAttribute(KEY, entries);
        return entries;
    }
}
