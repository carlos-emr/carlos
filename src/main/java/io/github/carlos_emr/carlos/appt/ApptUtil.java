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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.utility.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains Appointment related presentation layer helper methods.
 *
 * @author Eugene Petruhin
 */
public class ApptUtil {

    private static final Logger logger = LoggerFactory.getLogger(ApptUtil.class);
    private static final String SESSION_APPT_BEAN = "apptBean";
    private static final int MAX_FIELD_LEN = 255;
    private static final int MAX_NOTES_LEN = 2000;

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
    private static final Pattern SAFE_REASON_CODE = Pattern.compile("[a-zA-Z0-9]{1,20}");

    private static String validateOptional(String value, Pattern pattern, String fieldName) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!pattern.matcher(value).matches()) {
            logger.warn("Rejected invalid {} at trust boundary: {}", fieldName, LogSafe.sanitize(value));
            return null;
        }
        return value;
    }

    private static String sanitizeText(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String capped = value.length() > maxLen ? value.substring(0, maxLen) : value;
        if (!SAFE_TEXT.matcher(capped).matches()) {
            logger.warn("Rejected text with control characters at trust boundary");
            return null;
        }
        return capped;
    }

    private static String sanitizeMultilineText(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String capped = value.length() > maxLen ? value.substring(0, maxLen) : value;
        if (!SAFE_MULTILINE.matcher(capped).matches()) {
            logger.warn("Rejected multiline text with control characters at trust boundary");
            return null;
        }
        return capped;
    }

    public static void copyAppointmentIntoSession(HttpServletRequest request) {
        // Validate numeric ID fields at trust boundary (CWE-501)
        String demoNoParam = request.getParameter("demographic_no");
        if (demoNoParam != null && !demoNoParam.isEmpty() && !demoNoParam.matches("\\d+")) {
            logger.warn("Invalid non-numeric demographic_no: {}", LogSafe.sanitize(demoNoParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return;
        }
        String chartNo = request.getParameter("chart_no");
        if (chartNo != null && !chartNo.isEmpty() && !chartNo.matches("\\d+")) {
            logger.warn("Invalid non-numeric chart_no: {}", LogSafe.sanitize(chartNo)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return;
        }

        ApptData obj = new ApptData();
        // Structured fields: validated against expected formats
        obj.setAppointment_date(validateOptional(request.getParameter("appointment_date"), SAFE_DATE, "appointment_date"));
        obj.setStart_time(validateOptional(request.getParameter("start_time"), SAFE_TIME, "start_time"));
        obj.setEnd_time(validateOptional(request.getParameter("end_time"), SAFE_TIME, "end_time"));
        obj.setDemographic_no(demoNoParam);
        obj.setChart_no(chartNo);
        obj.setStatus(validateOptional(request.getParameter("status"), SAFE_STATUS, "status"));
        obj.setDuration(validateOptional(request.getParameter("duration"), SAFE_DURATION, "duration"));
        obj.setReasonCode(validateOptional(request.getParameter("reasonCode"), SAFE_REASON_CODE, "reasonCode"));
        // Free-text fields: length-capped and control characters rejected
        obj.setName(sanitizeText(request.getParameter("keyword"), MAX_FIELD_LEN));
        obj.setNotes(sanitizeMultilineText(request.getParameter("notes"), MAX_NOTES_LEN));
        obj.setReason(sanitizeText(request.getParameter("reason"), MAX_FIELD_LEN));
        obj.setLocation(sanitizeText(request.getParameter("location"), MAX_FIELD_LEN));
        obj.setResources(sanitizeText(request.getParameter("resources"), MAX_FIELD_LEN));
        obj.setType(sanitizeText(request.getParameter("type"), MAX_FIELD_LEN));
        obj.setStyle(sanitizeText(request.getParameter("style"), MAX_FIELD_LEN));
        obj.setBilling(sanitizeText(request.getParameter("billing"), MAX_FIELD_LEN));
        obj.setRemarks(sanitizeMultilineText(request.getParameter("remarks"), MAX_FIELD_LEN));
        obj.setUrgency(sanitizeText(request.getParameter("urgency"), MAX_FIELD_LEN));
        // nosemgrep: tainted-session-from-http-request -- numeric IDs validated via regex; status validated against [a-zA-Z]{1,2};
        // date/time validated against format patterns; free-text fields length-capped and control characters rejected via SAFE_TEXT
        request.getSession().setAttribute(SESSION_APPT_BEAN, obj); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): ApptData bean fields regex-validated/sanitized by sanitizeText() before storage
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
