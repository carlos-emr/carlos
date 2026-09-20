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
import io.github.carlos_emr.carlos.utility.SafeEncode;

import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Renders the formatted patient label {@code "last, first sex age"} for a demographic,
 * registered as {@code <oscar:nameage>} in {@code oscar-tag.tld}.
 *
 * <p>Patient names are attacker-influenced stored data, so the tag encodes its output at
 * the render boundary instead of trusting call sites to wrap it. The default
 * {@code html} context suits the HTML body and {@code <title>} call sites that make up
 * the current usage; a page that renders the label into a JavaScript string, an HTML
 * attribute, or a URL must select the matching context, because HTML body encoding
 * leaves quotes intact and would let a crafted name break out of those sinks:
 *
 * <pre>
 * &lt;oscar:nameage demographicNo="${demographicNo}"/&gt;                          &lt;!-- HTML body --&gt;
 * &lt;oscar:nameage demographicNo="${demographicNo}" context="javaScript"/&gt;      &lt;!-- inside a JS string --&gt;
 * </pre>
 *
 * @see SafeEncode#forContext(java.io.Writer, String, String)
 * @since 2004-02-24
 */
public class DemographicNameAgeTag extends TagSupport {

    public DemographicNameAgeTag() {
    }

    /**
     * Select the output context for the rendered label. Optional; defaults to
     * {@code html} (HTML body content). Accepts every context name supported by
     * {@code <carlos:encode>}.
     */
    public void setContext(String context1) {
        context = context1;
    }

    public String getContext() {
        return context;
    }

    public void setDemographicNo(String demoNo1) {
        demoNo = demoNo1;
    }

    public String getDemographicNo() {
        return demoNo;
    }

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
            SafeEncode.forContext(out, context, nameage);
        } catch (IllegalArgumentException p) {
            // A bad context attribute is a page bug: fail the render rather than emit unencoded PHI.
            throw new JspException("oscar:nameage: " + p.getMessage(), p);
        } catch (Exception p) {
            MiscUtils.getLogger().error("Error", p);
        }
        return (SKIP_BODY);
    }

    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

    @Override
    public void release() {
        demoNo = null;
        context = null;
        super.release();
    }

    private String demoNo;

    private String context;
}
