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


package io.github.carlos_emr.carlos.scratch.tld;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * JSP custom tag that outputs the appropriate scratch pad (notepad) image path based on
 * whether the provider has any scratch pad content.
 *
 * <p>Renders the path to either a filled notepad icon ({@code notepad.gif}) or an empty
 * notepad icon ({@code notepad_blank.gif}) during the tag's start processing. Currently
 * always renders the filled icon as the efficiency check was removed.</p>
 *
 * @since 2026-03-17
 */
public class ScratchTag extends TagSupport {

    public ScratchTag() {
        scratchFilled = false;
    }

    /**
     * Sets the provider number used to determine the scratch pad state.
     *
     * @param providerNo1 String the provider number
     */
    public void setProviderNo(String providerNo1) {
        providerNo = providerNo1;
    }

    /**
     * Returns the provider number.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Outputs the scratch pad image path when the tag is encountered.
     *
     * @return int {@link #EVAL_BODY_INCLUDE} to continue processing the tag body
     * @throws JspException if an error occurs during tag processing
     */
    public int doStartTag() throws JspException {

        if (providerNo != null) {
            //isScratchFilled is inefficient, removed until a more efficient method is available. by default show the filled graphic
            scratchFilled = true; //spm.isScratchFilled(providerNo);
        }

        try {
            JspWriter out = super.pageContext.getOut();
            if (scratchFilled)
                out.print("../images/notepad.gif");
            else
                out.print("../images/notepad_blank.gif");
        } catch (Exception p) {
            MiscUtils.getLogger().error("Error", p);
        }
        return (EVAL_BODY_INCLUDE);
    }

    /**
     * Completes the tag processing and continues page evaluation.
     *
     * @return int {@link #EVAL_PAGE} to continue evaluating the rest of the page
     * @throws JspException if an error occurs during tag processing
     */
    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

    private String providerNo;
    private boolean scratchFilled;
}
