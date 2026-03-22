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

package io.github.carlos_emr.carlos.appt.tld;

import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;

/**
 * JSP custom tag that renders the next appointment date for a patient.
 *
 * <p>Looks up the next scheduled appointment for the specified demographic number
 * using {@link AppointmentManager} and writes the formatted date directly to the
 * JSP output.</p>
 *
 * @since 2026-03-17
 */
public class NextApptTag extends TagSupport {

    private String demoNo = null;
    private String date = null;
    private String format = null;

    /**
     * Creates a new instance of NextApptTag
     */
    public NextApptTag() {
    }

    public void setDemographicNo(String demoNo1) {
        demoNo = demoNo1;
    }

    public String getDemographicNo() {
        return demoNo;
    }

    public int doStartTag() throws JspException {
        if (demoNo != null && !demoNo.isEmpty()) {
            try {
                AppointmentManager appointmentManager = SpringUtils.getBean(AppointmentManager.class);
                String nextAppointment = appointmentManager.getNextAppointmentDate(Integer.parseInt(demoNo));
                JspWriter out = super.pageContext.getOut();
                out.print(nextAppointment);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Could not fetch next appointment for demo number " + demoNo, e);
            }
        }
        return (SKIP_BODY);
    }

    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
