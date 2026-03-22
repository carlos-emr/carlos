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


package io.github.carlos_emr.carlos.demographic.tld;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * JSP custom tag that renders a patient's name, age, and sex as formatted text.
 *
 * <p>Outputs a string in the format "LastName, FirstName Sex Age" for the specified
 * demographic number. Used in JSP pages to display patient identification inline
 * without requiring scriptlet code.</p>
 *
 * <p><b>Tag Usage:</b></p>
 * <pre>{@code
 * <demographic:nameAge demographicNo="12345"/>
 * }</pre>
 *
 * @see io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString
 * @since 2026-03-17
 */
public class DemographicNameAgeTag extends TagSupport {

    /**
     * Constructs a new DemographicNameAgeTag instance.
     */
    public DemographicNameAgeTag() {
    }

    /**
     * Sets the demographic number for the patient to display.
     *
     * @param demoNo1 String the patient demographic number
     */
    public void setDemographicNo(String demoNo1) {
        demoNo = demoNo1;
    }

    /**
     * Returns the demographic number set for this tag.
     *
     * @return String the patient demographic number
     */
    public String getDemographicNo() {
        return demoNo;
    }

    /**
     * Renders the patient's name, age, and sex string to the JSP output.
     *
     * <p>Looks up the demographic data using the current session's LoggedInInfo
     * and writes the formatted string directly to the page output.</p>
     *
     * @return int SKIP_BODY to skip any tag body content
     * @throws JspException if a JSP processing error occurs
     */
    public int doStartTag() throws JspException {
        DemographicNameAgeString demoNameAge = DemographicNameAgeString.getInstance();
        Integer intDemoNo = ConversionUtils.fromIntString(demoNo);
        if (intDemoNo == 0) {
            MiscUtils.getLogger().error("Unable to parse demo no: " + demoNo);
            return SKIP_BODY;
        }
        String nameage = demoNameAge.getNameAgeString(LoggedInInfo.getLoggedInInfoFromSession(this.pageContext.getSession()), intDemoNo);
        try {
            JspWriter out = super.pageContext.getOut();
            out.print(nameage);
        } catch (Exception p) {
            MiscUtils.getLogger().error("Error", p);
        }
        return (SKIP_BODY);
    }

    /**
     * Completes tag processing and continues page evaluation.
     *
     * @return int EVAL_PAGE to continue processing the rest of the JSP page
     * @throws JspException if a JSP processing error occurs
     */
    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

    private String demoNo;
}
