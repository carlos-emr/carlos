/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.utility;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Look-ups for a patient's next scheduled appointment, rendered for display.
 *
 * <p>Callers pass the demographic number as the raw string they hold (a request
 * parameter, a JSP attribute), so every parse and range decision lives here
 * rather than being repeated (and skipped) at each call site.</p>
 *
 * @since 2026-02-04
 */
public class AppointmentUtil {

    /** Displayed when there is no next appointment to show, including for input this class rejects. */
    private static final String NONE = "(none)";

    private AppointmentUtil() {
    }

    /**
     * Returns the date of the patient's next appointment, formatted for display.
     *
     * @param demographicNo demographic number as held by the caller; surrounding whitespace is
     *                      ignored, and anything that is not a positive {@code int} (null, blank,
     *                      the literal {@code "null"}, non-numeric text, a value too large for
     *                      {@code int}) is treated as no patient rather than as demographic 0
     * @return the next appointment date as {@code yyyy-MM-dd}, or {@code "(none)"} when the input
     *         identifies no patient, the patient has no next appointment, or that appointment
     *         carries no date
     */
    public static String getNextAppointment(String demographicNo) {
        Integer demographicId = parseDemographicNo(demographicNo);
        if (demographicId == null) {
            return NONE;
        }
        return getNextAppointments(Set.of(demographicId)).get(demographicId);
    }

    /**
     * Returns the next appointment date of each of many patients, formatted for display.
     *
     * <p>One query for the whole set. A caller rendering a list -- the patient search returns up to
     * 100 rows per keystroke -- must use this rather than calling
     * {@link #getNextAppointment(String)} per row.</p>
     *
     * @param demographicIds patients to look up; null entries are ignored, and an id that
     *                       identifies no patient (zero or negative) is answered with the sentinel
     *                       rather than sent to the database, as {@link #getNextAppointment(String)}
     *                       does for the same values
     * @return a map holding an entry for every non-null id passed: the next appointment date as
     *         {@code yyyy-MM-dd}, or {@code "(none)"} for an id that identifies no patient, a
     *         patient with no next appointment, or one whose next appointment carries no date
     */
    public static Map<Integer, String> getNextAppointments(Collection<Integer> demographicIds) {
        Map<Integer, String> nextAppointments = new HashMap<>();
        if (demographicIds == null || demographicIds.isEmpty()) {
            return nextAppointments;
        }
        // Answered without a lookup, so a malformed row in the caller's list cannot put
        // demographic 0 (or a negative id) into the IN list of a query about real patients.
        Set<Integer> wanted = new LinkedHashSet<>();
        for (Integer demographicId : demographicIds) {
            if (demographicId == null) {
                continue;
            }
            if (demographicId > 0) {
                wanted.add(demographicId);
            } else {
                nextAppointments.put(demographicId, NONE);
            }
        }
        if (wanted.isEmpty()) {
            return nextAppointments;
        }

        OscarAppointmentDao dao = SpringUtils.getBean(OscarAppointmentDao.class);
        Map<Integer, Date> dates = dao.findNextAppointmentDates(wanted);
        for (Integer demographicId : wanted) {
            Date nextAppointmentDate = dates.get(demographicId);
            nextAppointments.put(demographicId,
                    nextAppointmentDate == null ? NONE : ConversionUtils.toDateString(nextAppointmentDate));
        }
        return nextAppointments;
    }

    /**
     * Parses a demographic number, returning {@code null} for anything that does not identify a
     * patient. Parsing here rather than through {@code ConversionUtils.fromIntString} is
     * deliberate: that helper maps a parse failure (an overflowing number included) to 0, which
     * would send a lookup for demographic 0 to the database instead of failing closed.
     */
    private static Integer parseDemographicNo(String demographicNo) {
        if (demographicNo == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(demographicNo.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
