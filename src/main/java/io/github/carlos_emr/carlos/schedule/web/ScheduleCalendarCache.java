/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.schedule.web;

import java.util.Hashtable;
import java.util.List;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import org.springframework.web.util.WebUtils;

import io.github.carlos_emr.HScheduleDate;
import io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * The schedule month view's session copy of one provider's custom ({@code c}) date overrides.
 *
 * <p>schedulecreatedate.jsp loads the copy when it builds a provider's calendar and then draws every
 * month from it, keyed {@code yyyy-MM-dd}. A date with no entry shows the provider's rule schedule.
 * The schedule date popup ({@link ScheduleDateSave2Action}) updates the one day it changed.</p>
 *
 * <p>The copy and the rule schedule beside it are session-wide, so opening another provider's
 * calendar in a second tab replaces both. The copy therefore records whose overrides it holds, and
 * the popup refuses to change a date for any other provider (#5297).</p>
 *
 * <p>Every read and write of the copy holds the session mutex. When the popup cannot read its
 * committed change back, it marks the copy stale instead of failing a save that has already
 * committed. The next month view then reloads the copy from the database, read-only.</p>
 *
 * @since 2026-11-19
 */
public final class ScheduleCalendarCache {

    /** Session attribute of the JSP {@code scheduleDateBean}: {@code yyyy-MM-dd} to {@link HScheduleDate}. */
    static final String DAYS = "scheduleDateBean";
    /** Session attribute naming the provider whose overrides {@link #DAYS} holds. */
    static final String PROVIDER = "scheduleDateBeanProvider";
    /** Session attribute set when {@link #DAYS} may be out of date and must be reloaded before it is shown. */
    static final String STALE = "scheduleDateBeanStale";

    private static final Logger logger = MiscUtils.getLogger();

    private ScheduleCalendarCache() {
    }

    /**
     * Replaces the session copy with the provider's custom overrides and records that it is theirs.
     * The JSP's {@code scheduleDateBean} keeps referring to the same table.
     *
     * @param session the user's session
     * @param providerNo the provider whose calendar is being shown
     * @param customOverrides the provider's active custom overrides
     */
    public static void load(HttpSession session, String providerNo, List<ScheduleDate> customOverrides) {
        synchronized (WebUtils.getSessionMutex(session)) {
            Hashtable<String, Object> days = days(session);
            days.clear();
            for (ScheduleDate override : customOverrides) {
                days.put(ConversionUtils.toDateString(override.getDate()), entry(override));
            }
            session.setAttribute(PROVIDER, providerNo);
            session.removeAttribute(STALE);
        }
    }

    /**
     * Reloads the session copy from the database when a save could not refresh it. Reads only.
     *
     * @param session the user's session
     * @param providerNo the provider whose month is about to be drawn
     * @param scheduleDateDao the source of the provider's custom overrides
     */
    public static void reloadIfStale(HttpSession session, String providerNo, ScheduleDateDao scheduleDateDao) {
        synchronized (WebUtils.getSessionMutex(session)) {
            if (session.getAttribute(STALE) != null) {
                load(session, providerNo, scheduleDateDao.search_scheduledate_c(providerNo));
            }
        }
    }

    /**
     * Whether the session copy holds this provider's overrides.
     *
     * @param session the user's session
     * @param providerNo the provider a request names
     * @return {@code true} only when the copy was loaded for that provider
     */
    public static boolean isFor(HttpSession session, String providerNo) {
        return providerNo.equals(session.getAttribute(PROVIDER));
    }

    /**
     * Sets the copy's entry for the date to the committed override. Call it only after the change has
     * committed.
     *
     * <p>The read and the store happen together under the session mutex. A later change can commit
     * before this request gets here, but the request that refreshes last reads after every commit
     * that preceded it, so the copy ends on the latest committed value. A copy now loaded for another
     * provider is left alone.</p>
     *
     * <p>If the read fails, the change has still committed and only the copy is behind. The copy is
     * marked stale for the month view to reload, and the caller can still acknowledge the save.</p>
     *
     * @param session the user's session
     * @param providerNo the provider whose date changed
     * @param date the changed date
     * @param committedOverride reads the date's committed active override, or {@code null} when none
     */
    static void refreshDay(HttpSession session, String providerNo, java.util.Date date,
                           Supplier<ScheduleDate> committedOverride) {
        String day = ConversionUtils.toDateString(date);
        synchronized (WebUtils.getSessionMutex(session)) {
            if (!isFor(session, providerNo)) {
                return;
            }
            Hashtable<String, Object> days = days(session);
            try {
                ScheduleDate current = committedOverride.get();
                if (current != null && current.getPriority() == 'c') {
                    days.put(day, entry(current));
                } else {
                    days.remove(day);
                }
            } catch (RuntimeException readFailure) {
                session.setAttribute(STALE, Boolean.TRUE);
                // No provider number or date: they would tie the log line to a patient's schedule.
                logger.warn("Could not read a committed schedule date override back ({}); the calendar will reload it",
                        readFailure.getClass().getSimpleName());
            }
        }
    }

    private static HScheduleDate entry(ScheduleDate override) {
        return new HScheduleDate(String.valueOf(override.getAvailable()), String.valueOf(override.getPriority()),
                override.getReason(), override.getHour(), override.getCreator());
    }

    /** The JSP's {@code scheduleDateBean}, created the way {@code jsp:useBean} would when absent. */
    @SuppressWarnings("unchecked")
    private static Hashtable<String, Object> days(HttpSession session) {
        Object days = session.getAttribute(DAYS);
        if (days instanceof Hashtable) {
            return (Hashtable<String, Object>) days;
        }
        Hashtable<String, Object> created = new Hashtable<>();
        session.setAttribute(DAYS, created);
        return created;
    }
}
