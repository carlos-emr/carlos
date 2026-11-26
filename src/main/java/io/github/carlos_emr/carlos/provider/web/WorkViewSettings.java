/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.provider.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The saved "work view" settings a provider may store, and the values each may hold (#2778).
 *
 * <p>{@code /saveWorkView} keeps a provider's last-used list filters in the {@code view} table, keyed by
 * a view name and a setting name, so the next visit opens with the same filters. It used to store every
 * request parameter under whatever view name the request gave, which let a request create any number of
 * rows with any name and any text for the signed-in provider. This class is the allowlist: a view name
 * is accepted only if CARLOS has a page that reads it, a setting only if that page reads it, and a value
 * only if it is one of the values that page can produce. The role and provider number the rows belong to
 * are never taken from the request; the action reads them from the session.</p>
 *
 * <p>Only the {@code tickler} view exists today. Its four filters are written by the Tickler page's
 * "Save view" button and read back by {@code ticklerMain.jsp} and {@code TicklerTag}:</p>
 * <ul>
 *   <li>{@code ticklerview}: the status filter, {@code A} (active), {@code C} (completed) or
 *       {@code D} (deleted);</li>
 *   <li>{@code providerview}, {@code assignedTo}, {@code mrpview}: {@code all}, or a provider number. A
 *       provider number is at most six characters ({@code provider.provider_no}) of letters, digits,
 *       {@code .}, {@code _} or {@code -}. An empty value is kept, because the Tickler page sends one
 *       while its assigned-to list is still empty.</li>
 * </ul>
 *
 * <p>Parameters that are not settings of the view (the {@code method} and {@code view_name} routing
 * values, and the {@code userrole} and {@code providerno} copies the Tickler page also posts) are ignored,
 * not rejected, so existing pages keep working. A value that is not valid for its setting rejects the
 * whole request, so a save is never applied in part.</p>
 *
 * <p>Add a view here only together with the page that reads it.</p>
 */
public final class WorkViewSettings {

    /** The Tickler page's saved filters. */
    public static final String TICKLER_VIEW = "tickler";

    private static final Pattern TICKLER_STATUS = Pattern.compile("[ACD]");
    private static final Pattern PROVIDER_FILTER = Pattern.compile("all|[A-Za-z0-9._-]{0,6}");

    private static final Map<String, Map<String, Pattern>> SETTINGS = Map.of(
            TICKLER_VIEW, Map.of(
                    "ticklerview", TICKLER_STATUS,
                    "providerview", PROVIDER_FILTER,
                    "assignedTo", PROVIDER_FILTER,
                    "mrpview", PROVIDER_FILTER));

    private WorkViewSettings() {
    }

    /**
     * Reads the settings of one view out of a request.
     *
     * @param viewName   the {@code view_name} request parameter; may be {@code null}
     * @param parameters the request parameters, as {@code HttpServletRequest#getParameterMap()} returns them;
     *                   the first value of each is used
     * @return the submitted settings of that view, by setting name, in a stable order; never empty and never
     *         modifiable
     * @throws Rejected if the view is unknown, a submitted setting has a value it cannot hold, or none of the
     *                  view's settings was submitted. The message names the setting but never repeats a
     *                  submitted value.
     */
    public static Map<String, String> accept(String viewName, Map<String, String[]> parameters) {
        Map<String, Pattern> allowed = viewName == null ? null : SETTINGS.get(viewName);
        if (allowed == null) {
            throw new Rejected("unknown work view");
        }
        if (parameters == null) {
            throw new Rejected("no work view settings submitted");
        }

        Map<String, String> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, Pattern> setting : new TreeMap<>(allowed).entrySet()) {
            String[] submitted = parameters.get(setting.getKey());
            if (submitted == null || submitted.length == 0) {
                continue;
            }
            String value = submitted[0];
            if (value == null || !setting.getValue().matcher(value).matches()) {
                throw new Rejected("invalid value for work view setting " + setting.getKey());
            }
            accepted.put(setting.getKey(), value);
        }
        if (accepted.isEmpty()) {
            throw new Rejected("no work view settings submitted");
        }
        return Collections.unmodifiableMap(accepted);
    }

    /** A save request the allowlist does not accept. Unchecked: the action turns it into HTTP 400. */
    public static final class Rejected extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        Rejected(String message) {
            super(message);
        }
    }
}
