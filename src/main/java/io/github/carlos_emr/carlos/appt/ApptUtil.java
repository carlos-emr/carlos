/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.appt;

import java.util.List;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.appt.location.AppointmentLocationList;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationResolver;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * This class contains Appointment related presentation layer helper methods.
 *
 * @author Eugene Petruhin
 */
public class ApptUtil {

    private static final String SESSION_APPT_BEAN = "apptBean";
    private static final int MAX_FIELD_LEN = 255;
    private static final int MAX_REASON_LEN = 80;
    private static final String APPOINTMENT_DATE = "appointment_date";
    private static final String START_TIME = "start_time";
    private static final String END_TIME = "end_time";
    private static final String DURATION = "duration";
    private static final String STATUS = "status";
    // Every Copy/Cut edit form posts these controls, including empty text. An absent field is
    // an incomplete request, not permission to discard part of the appointment during a move.
    private static final List<String> CLIPBOARD_FIELDS = List.of(
            APPOINTMENT_DATE, START_TIME, END_TIME, DURATION, STATUS, "demographic_no",
            "keyword", "chart_no", "reason", "notes", "location", "resources", "type", "remarks");
    private static final List<String> NONEMPTY_CLIPBOARD_FIELDS = List.of(
            APPOINTMENT_DATE, START_TIME, END_TIME, DURATION, STATUS);

    // CWE-501 trust boundary validation: appointment status is 1-2 alpha chars (e.g. t,T,H,P,E,N,C,B + optional S/V modifier)
    private static final Pattern SAFE_STATUS = Pattern.compile("[a-zA-Z]{1,2}");
    // Date format: YYYY-MM-DD or similar date strings used by appointment UI
    private static final Pattern SAFE_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    // Time format: HH:MM or HH:MM:SS
    private static final Pattern SAFE_TIME = Pattern.compile("[0-9]{1,2}:[0-9]{2}(:[0-9]{2})?");
    // Safe single-line text: any character except control chars (allows Unicode for bilingual Canadian EMR)
    private static final Pattern SAFE_TEXT = Pattern.compile("[^\\p{Cntrl}]*");
    // Safe multiline text: allows \n \r \t (for textarea-backed fields like notes/remarks) but rejects null bytes and other control chars
    private static final Pattern SAFE_MULTILINE = Pattern.compile("[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*", Pattern.DOTALL);
    // Numeric patterns for structured fields
    private static final Pattern SAFE_DURATION = Pattern.compile("\\d{1,4}");

    private static void validateOptional(String value, Pattern pattern) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!pattern.matcher(value).matches()) {
            throw new AppointmentClipboardValidationException();
        }
    }

    private static String sanitizeText(String value, int maxLen) {
        return validateText(value, maxLen, false);
    }

    private static String validateDate(String value) {
        validateOptional(value, SAFE_DATE);
        if (value != null && !value.isEmpty()) {
            try {
                LocalDate.parse(value);
            } catch (DateTimeParseException _) {
                throw new AppointmentClipboardValidationException();
            }
        }
        return value;
    }

    private static String validateTime(String value) {
        validateOptional(value, SAFE_TIME);
        if (value != null && !value.isEmpty()) {
            try {
                LocalTime.parse(value.charAt(1) == ':' ? "0" + value : value);
            } catch (DateTimeParseException _) {
                throw new AppointmentClipboardValidationException();
            }
        }
        return value;
    }

    private static String sanitizeMultilineText(String value, int maxLen) {
        return validateText(value, maxLen, true);
    }

    private static String validateText(String value, int maxLen, boolean multiline) {
        if (value == null) {
            return null;
        }
        // A supplementary Unicode character occupies one database character, not two UTF-16
        // units. Browser textarea CR LF likewise becomes one logical line break on booking.
        String logical = multiline ? value.replace("\r\n", "\n").replace('\r', '\n') : value;
        if (logical.codePointCount(0, logical.length()) > maxLen
                || logical.codePoints().anyMatch(codePoint -> codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)
                || !(multiline ? SAFE_MULTILINE : SAFE_TEXT).matcher(value).matches()) {
            throw new AppointmentClipboardValidationException();
        }
        return value;
    }

    private static void validateOptionalNonNegativeInteger(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            if (!value.matches("\\d+") || Integer.parseInt(value) < 0) {
                throw new AppointmentClipboardValidationException();
            }
        } catch (NumberFormatException _) {
            // Never attach the parser exception: its message includes the submitted value.
            throw new AppointmentClipboardValidationException();
        }
    }

    public static void copyAppointmentIntoSession(HttpServletRequest request) {
        Integer sourceId = AppointmentLocationList.parseCode(request.getParameter("appointment_no"));
        Appointment source = sourceId == null ? null : SpringUtils.getBean(OscarAppointmentDao.class).find(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("The source appointment is no longer available. Nothing was copied or cut.");
        }
        validateCompleteClipboard(request);
        // The coded dropdown's hidden text is empty. Resolve against the saved source before
        // copying so the clipboard retains its historical label even if the list changes before
        // Paste. Resolution uses a temporary appointment and never mutates the source row.
        AppointmentLocationResolver.ResolvedLocation location = AppointmentLocationResolver.resolvePostedLocation(source, request);

        String demoNoParam = request.getParameter("demographic_no");
        validateOptionalNonNegativeInteger(demoNoParam);
        // Chart numbers are clinic-defined text (Demographic.chartNo), not patient database ids.
        String chartNo = sanitizeText(request.getParameter("chart_no"), MAX_FIELD_LEN);

        ApptData obj = new ApptData();
        // Structured fields: validated against expected formats
        obj.setAppointment_date(validateDate(request.getParameter(APPOINTMENT_DATE)));
        obj.setStart_time(validateTime(request.getParameter(START_TIME)));
        obj.setEnd_time(validateTime(request.getParameter(END_TIME)));
        obj.setDemographic_no(demoNoParam);
        obj.setChart_no(chartNo);
        String status = request.getParameter(STATUS);
        validateOptional(status, SAFE_STATUS);
        obj.setStatus(status);
        String duration = request.getParameter(DURATION);
        validateOptional(duration, SAFE_DURATION);
        obj.setDuration(duration);
        String reasonCode = request.getParameter("reasonCode");
        // The booking form uses -1 for an unspecified coded reason; other codes are Integer ids.
        if (!"-1".equals(reasonCode)) {
            validateOptionalNonNegativeInteger(reasonCode);
        }
        obj.setReasonCode(reasonCode);
        obj.setLocationCode(location.code() == null ? null : String.valueOf(location.code()));
        // Refuse invalid text before replacing the clipboard; never silently shorten a cut booking.
        obj.setName(sanitizeText(request.getParameter("keyword"), MAX_FIELD_LEN));
        obj.setNotes(sanitizeMultilineText(request.getParameter("notes"), MAX_FIELD_LEN));
        obj.setReason(sanitizeMultilineText(request.getParameter("reason"), MAX_REASON_LEN));
        obj.setLocation(sanitizeText(location.text(), MAX_FIELD_LEN));
        obj.setResources(sanitizeText(request.getParameter("resources"), MAX_FIELD_LEN));
        obj.setType(sanitizeText(request.getParameter("type"), MAX_FIELD_LEN));
        obj.setStyle(sanitizeText(request.getParameter("style"), MAX_FIELD_LEN));
        obj.setBilling(sanitizeText(request.getParameter("billing"), MAX_FIELD_LEN));
        obj.setRemarks(sanitizeMultilineText(request.getParameter("remarks"), MAX_FIELD_LEN));
        obj.setUrgency(sanitizeText(request.getParameter("urgency"), MAX_FIELD_LEN));
        // nosemgrep: tainted-session-from-http-request -- numeric IDs validated via regex; status validated against [a-zA-Z]{1,2};
        // date/time validated against format patterns; chart and free-text fields validated for length, Unicode and control characters
        request.getSession().setAttribute(SESSION_APPT_BEAN, obj); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): ApptData bean fields validated before storage; failure leaves the previous clipboard intact
    }

    private static void validateCompleteClipboard(HttpServletRequest request) {
        for (String field : CLIPBOARD_FIELDS) {
            if (request.getParameter(field) == null) {
                throw new AppointmentClipboardValidationException();
            }
        }
        for (String field : NONEMPTY_CLIPBOARD_FIELDS) {
            if (request.getParameter(field).isEmpty()) {
                throw new AppointmentClipboardValidationException();
            }
        }
    }

    public static ApptData getAppointmentFromSession(HttpServletRequest request) {
        return (ApptData) request.getSession().getAttribute(SESSION_APPT_BEAN);
    }

    public static String getColorFromLocation(String site, String colo, String loca) {
        String ret = "white";
        String[] s = site.split("\\|");
        String[] c = colo.split("\\|");
        for (int i = 0; i < s.length; i++) {
            if (s[i].startsWith(loca)) {
                ret = c[i];
                break;
            }
        }
        return ret;
    }

    public static String getColorFromLocation(List<Site> sites, String siteName) {
        for (Site s : sites) {
            if (s.getName().equals(siteName))
                return s.getBgColor();
        }
        return "white";
    }

    public static String getShortNameFromLocation(List<Site> sites, String siteName) {
        for (Site s : sites) {
            if (s.getName().equals(siteName))
                return s.getShortName();
        }
        return "";
    }

    public static Site getSiteFromName(List<Site> sites, String siteName) {
        for (Site s : sites) {
            if (s.getName().equals(siteName))
                return s;
        }
        return null;
    }
}
