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
 */

package io.github.carlos_emr.carlos.form.pageUtil.tagLib;

import org.apache.commons.text.StringSubstitutor;
import io.github.carlos_emr.carlos.form.model.FormRourke2017Constants;

import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class FormRourkeDiscussionRadioSelect extends TagSupport {

    private String sectionName;
    private Properties formProperties;
    private Boolean showNoOption = null;
    private Boolean showNotDiscussedOption = null;

    private static final String TEMPLATE_OK = "<td valign=\"top\"><input type=\"radio\" id=\"${sectionName}Ok\" name=\"${sectionName}Ok\" onclick=\"onCheck(this,'${sectionName}')\" ${sectionValueOk}></td>";
    private static final String TEMPLATE_OK_CONCERNS = "<td valign=\"top\"><input type=\"radio\" id=\"${sectionName}OkConcerns\" name=\"${sectionName}OkConcerns\" onclick=\"onCheck(this,'${sectionName}')\" ${sectionValueOkConcerns}></td>";
    private static final String TEMPLATE_NO = "<td valign=\"top\"><input type=\"radio\" id=\"${sectionName}No\" name=\"${sectionName}No\" onclick=\"onCheck(this,'${sectionName}')\" ${sectionValueNo}></td>";
    private static final String TEMPLATE_NOT_DISCUSSED = "<td valign=\"top\"><input type=\"radio\" id=\"${sectionName}NotDiscussed\" name=\"${sectionName}NotDiscussed\" onclick=\"onCheck(this,'${sectionName}')\" ${sectionValueNotDiscussed}></td>";

    public int doStartTag() {
        JspWriter out = pageContext.getOut();

        Map<String, String> valuesMap = new HashMap<String, String>();
        valuesMap.put("sectionName", sectionName);
        for (String suffix : FormRourke2017Constants.BOOLEAN_FIELD_SUFFIXES) {
            valuesMap.put("sectionValue" + suffix, formProperties.getProperty(sectionName + suffix, ""));
        }

        StringSubstitutor sub = new StringSubstitutor(valuesMap);
        try {
            out.println(sub.replace(TEMPLATE_OK));
            out.println(sub.replace(TEMPLATE_OK_CONCERNS));
            if (showNoOption == null) {
                out.print("<td>&nbsp;</td>");
            } else if (showNoOption) {
                out.println(sub.replace(TEMPLATE_NO));
            }
            if (showNotDiscussedOption == null) {
                out.println("<td>&nbsp;</td>");
            } else if (showNotDiscussedOption) {
                out.println(sub.replace(TEMPLATE_NOT_DISCUSSED));
            }
        } catch (Exception e) {

        }
        return EVAL_PAGE;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public void setFormProperties(Properties formProperties) {
        this.formProperties = formProperties;
    }

    public void setShowNoOption(Boolean showNoOption) {
        this.showNoOption = showNoOption;
    }

    public void setShowNotDiscussedOption(Boolean showNotDiscussedOption) {
        this.showNotDiscussedOption = showNotDiscussedOption;
    }
}