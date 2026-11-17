/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.commn.model.Tickler;

/**
 * The status each ticked task showed when a task list was rendered, and the tasks a bulk action
 * left alone because that status had changed.
 *
 * <p>The task lists post only the ticked task numbers. A list loaded before another user
 * completed or deleted a task still shows it as Active, and its Complete used to write over the
 * other user's change (a Deleted task came back as Completed). The lists now also post, in the one
 * field {@value #PARAMETER}, what each ticked row showed ({@code <tickler_no>:<status>} pairs
 * separated by commas), and the bulk actions pass it to
 * {@code TicklerManager.updateStatusIfCurrent}, which compares it with the locked row.</p>
 *
 * <p>Tasks that were refused are remembered in the session for the next rendering of a task list,
 * which names them ({@code ticklerStaleSelection.jspf}); the others in the same action are still
 * applied.</p>
 *
 * @since 2026-11-12
 */
public final class TicklerRenderedStatus {

    /** Hidden field of the task lists' bulk form: {@code <tickler_no>:<status>} for each ticked row. */
    public static final String PARAMETER = "ticklerRendered";

    private static final String REFUSED_ATTRIBUTE = TicklerRenderedStatus.class.getName() + ".refused";
    /** A banner cannot usefully list more; the count is still reported. */
    private static final int MAX_REMEMBERED = 200;

    private TicklerRenderedStatus() { }

    /**
     * Reads the rendered statuses a bulk form posted. Malformed pairs are ignored: the task is
     * then handled as one whose rendered status is unknown.
     *
     * @param request the bulk form post
     * @return the status each ticked row showed, by tickler number; empty when the form posted none
     */
    public static Map<Integer, Tickler.STATUS> read(HttpServletRequest request) {
        Map<Integer, Tickler.STATUS> rendered = new HashMap<>();
        String[] values = request.getParameterValues(PARAMETER);
        if (values == null) {
            return rendered;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String pair : value.split(",")) {
                int separator = pair.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                try {
                    rendered.put(Integer.valueOf(pair.substring(0, separator).trim()),
                            Tickler.STATUS.valueOf(pair.substring(separator + 1).trim()));
                } catch (IllegalArgumentException malformed) {
                    // Not "<number>:<A|C|D>": no rendered status for that task.
                }
            }
        }
        return rendered;
    }

    /**
     * Remembers the tasks a bulk action refused, for the list page the action redirects to.
     *
     * @param session the user's session
     * @param ticklerNos the refused tasks; nothing is stored when empty
     */
    public static void rememberRefused(HttpSession session, List<Integer> ticklerNos) {
        if (ticklerNos == null || ticklerNos.isEmpty()) {
            return;
        }
        session.setAttribute(REFUSED_ATTRIBUTE,
                new ArrayList<>(ticklerNos.subList(0, Math.min(ticklerNos.size(), MAX_REMEMBERED))));
    }

    /**
     * Returns the tasks the last bulk action refused and forgets them, so the notice is shown once.
     *
     * @param session the user's session, or {@code null}
     * @return the refused tickler numbers; empty when there is nothing to report
     */
    public static List<Integer> takeRefused(HttpSession session) {
        if (session == null) {
            return Collections.emptyList();
        }
        Object stored = session.getAttribute(REFUSED_ATTRIBUTE);
        if (stored == null) {
            return Collections.emptyList();
        }
        session.removeAttribute(REFUSED_ATTRIBUTE);
        List<Integer> refused = new ArrayList<>();
        if (stored instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Integer ticklerNo) {
                    refused.add(ticklerNo);
                }
            }
        }
        return refused;
    }
}
