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

/*
 * programExclusiveViewTag.java
 *
 * Created on May 24, 2007, 12:03 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.www.tld;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.dao.ProviderDefaultProgramDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * JSP custom tag that conditionally includes its body based on the provider's
 * exclusive view setting.
 *
 * <p>Checks whether the provider's default program has an exclusive view configured
 * (e.g., "appointment" or "case-management"). If the configured exclusive view matches
 * the tag's {@code value} attribute, the tag body is included; otherwise it is skipped.
 * When no exclusive view is set, the body is skipped (user can switch between views).
 *
 * @since 2007-05-24
 */
public class programExclusiveViewTag extends TagSupport {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance of programExclusiveViewTag
     */
    public programExclusiveViewTag() {
        exclusiveView = "no";
    }

    /**
     * Sets the provider number to look up the exclusive view setting for.
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
     * Sets the view type value to match against the exclusive view setting.
     *
     * @param value1 String the view type (e.g., "appointment", "case-management")
     */
    public void setValue(String value1) {
        value = value1;
    }

    /**
     * Returns the view type value to match against.
     *
     * @return String the view type value
     */
    public String getValue() {
        return value;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Evaluates the provider's exclusive view setting and includes the tag body
     * only if it matches the configured value attribute.
     *
     * @return int {@code EVAL_BODY_INCLUDE} if the view matches, {@code SKIP_BODY} otherwise
     * @throws JspException if an error occurs during tag processing
     */
    public int doStartTag() throws JspException {
        ProviderDefaultProgramDao dao = SpringUtils.getBean(ProviderDefaultProgramDao.class);
        for (Program p : dao.findProgramsByProvider(providerNo)) {
            exclusiveView = p.getExclusiveView();
            if (exclusiveView.equals("")) {
                exclusiveView = "no";
            }
        }

        /* For the time being, only the Appointment/Oscar view can be set exclusive.
         * If necessary, modify the following code and relating .jsp to enable other view(s) exclusive.
         *    exclusiveView = "no" -> no exclusive view set, user can switch between views
         *    exclusiveView = "appointment" -> Appointment/Oscar view exclusive
         *    exclusiveView = "case-management" -> Case-management view exclusive
         */
        if (exclusiveView.equalsIgnoreCase(value)) {
            return (EVAL_BODY_INCLUDE);
        } else {
            return (SKIP_BODY);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return int always returns {@code EVAL_PAGE} to continue page evaluation
     * @throws JspException if an error occurs during tag processing
     */
    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

    private String providerNo;
    private String value;
    private String exclusiveView;
}
