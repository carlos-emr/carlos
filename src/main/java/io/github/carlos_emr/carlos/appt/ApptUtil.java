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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
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

    private static String cap(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    public static void copyAppointmentIntoSession(HttpServletRequest request) {
        // Validate numeric ID fields at trust boundary (CWE-501)
        String demoNoParam = request.getParameter("demographic_no");
        if (demoNoParam != null && !demoNoParam.isEmpty() && !demoNoParam.matches("\\d{1,9}")) {
            logger.warn("Invalid non-numeric demographic_no: {}", LogSanitizer.sanitize(demoNoParam));
            return;
        }
        String chartNo = request.getParameter("chart_no");
        if (chartNo != null && !chartNo.isEmpty() && !chartNo.matches("\\d{1,9}")) {
            logger.warn("Invalid non-numeric chart_no: {}", LogSanitizer.sanitize(chartNo));
            return;
        }

        ApptData obj = new ApptData();
        obj.setAppointment_date(cap(request.getParameter("appointment_date"), MAX_FIELD_LEN));
        obj.setStart_time(cap(request.getParameter("start_time"), MAX_FIELD_LEN));
        obj.setEnd_time(cap(request.getParameter("end_time"), MAX_FIELD_LEN));
        obj.setName(cap(request.getParameter("keyword"), MAX_FIELD_LEN));
        obj.setDemographic_no(demoNoParam);
        obj.setNotes(cap(request.getParameter("notes"), MAX_NOTES_LEN));
        obj.setReason(cap(request.getParameter("reason"), MAX_FIELD_LEN));
        obj.setLocation(cap(request.getParameter("location"), MAX_FIELD_LEN));
        obj.setResources(cap(request.getParameter("resources"), MAX_FIELD_LEN));
        obj.setType(cap(request.getParameter("type"), MAX_FIELD_LEN));
        obj.setStyle(cap(request.getParameter("style"), MAX_FIELD_LEN));
        obj.setBilling(cap(request.getParameter("billing"), MAX_FIELD_LEN));
        obj.setStatus(cap(request.getParameter("status"), MAX_FIELD_LEN));
        obj.setRemarks(cap(request.getParameter("remarks"), MAX_FIELD_LEN));
        obj.setDuration(cap(request.getParameter("duration"), MAX_FIELD_LEN));
        obj.setChart_no(chartNo);
        obj.setUrgency(cap(request.getParameter("urgency"), MAX_FIELD_LEN));
        obj.setReasonCode(cap(request.getParameter("reasonCode"), MAX_FIELD_LEN));
        // numeric ID fields validated above; display strings length-capped to prevent oversized session storage
        request.getSession().setAttribute(SESSION_APPT_BEAN, obj); // nosemgrep: tainted-session-from-http-request
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
