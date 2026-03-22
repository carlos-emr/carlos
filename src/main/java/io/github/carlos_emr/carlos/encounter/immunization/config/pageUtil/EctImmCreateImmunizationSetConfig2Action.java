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


package io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil;

import java.io.IOException;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.encounter.immunization.config.data.EctImmHeading;
import io.github.carlos_emr.carlos.encounter.immunization.config.data.EctImmImmunizationSetWriter;
import io.github.carlos_emr.carlos.encounter.immunization.config.data.EctImmImmunizations;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action that creates a new immunization set configuration from form parameters.
 *
 * @since 2001-01-01
 */
public class EctImmCreateImmunizationSetConfig2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    public String webizeNewLines(String str) {
        String temp = str.replaceAll("\n", "<br>");
        return temp;
    }

    public String execute()
            throws ServletException, IOException {
        EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
        String providerNo = bean.providerNo;
        Enumeration e = request.getParameterNames();
        Vector headingVec = new Vector();
        Vector immunizationVec = new Vector();
        Vector yearAgeVec = new Vector();
        String setName = this.getName();
        String headers = "true";
        String str;
        for (; e.hasMoreElements(); MiscUtils.getLogger().debug("str ".concat(String.valueOf(String.valueOf(str))))) {
            str = (String) e.nextElement();
            if (str.startsWith("heading")) {
                headingVec.add(str);
                continue;
            }
            if (str.startsWith("immunization")) {
                immunizationVec.add(str);
                continue;
            }
            boolean flag1;
            if (str.startsWith("yearAge"))
                yearAgeVec.add(str);
            else
                flag1 = false;
        }

        int flag = 0;
        Vector headingVector = new Vector();
        headingVector.setSize(headingVec.size());
        for (int u = 0; u < headingVec.size(); u++) {
            String headingString = (String) headingVec.elementAt(u);
            if (!headingString.equals(""))
                flag = 1;
            String gett = headingString.substring(9);
            StringTokenizer stringTokenizer = new StringTokenizer(gett, "D");
            String shouldBeCol = (String) stringTokenizer.nextElement();
            String headVal = request.getParameter(headingString);
            headVal = headVal.trim();
            headVal = webizeNewLines(headVal);
            EctImmHeading header = new EctImmHeading();
            header.headVal = headVal;
            int forTheCol = 0;
            try {
                forTheCol = Integer.parseInt(shouldBeCol);
            } catch (Exception colE) {
                MiscUtils.getLogger().debug("Kick'm out");
            }
            header.col = forTheCol;
            headingVector.setElementAt(header, header.col - 1);
        }

        Vector immunizationVector = new Vector();
        immunizationVector.setSize(immunizationVec.size());
        for (int i = 0; i < immunizationVec.size(); i++) {
            EctImmImmunizations immunization = new EctImmImmunizations();
            String colHead = (String) immunizationVec.elementAt(i);
            String immName = (String) immunizationVec.elementAt(i);
            String gett = immName.substring(12);
            StringTokenizer stringTokenizer = new StringTokenizer(gett, "D");
            String shouldBeRow = (String) stringTokenizer.nextElement();
            int forTheRow = 0;
            try {
                forTheRow = Integer.parseInt(shouldBeRow);
            } catch (Exception colE) {
                MiscUtils.getLogger().debug("Kick'm out");
            }
            String immVal = request.getParameter(immName);
            immVal = immVal.trim();
            immunization.immunizationName = immVal;
            immunization.row = forTheRow;
            immunization.indexAge = new Vector();
            immunizationVector.setElementAt(immunization, immunization.row - 1);
        }

        for (int q = 0; q < immunizationVector.size(); q++) {
            EctImmImmunizations immunization = (EctImmImmunizations) immunizationVector.elementAt(q);

        }

        Vector yearAgeVector = new Vector();
        yearAgeVector.setSize(yearAgeVec.size());
        for (int i = 0; i < yearAgeVec.size(); i++) {
            String yearAgeName = (String) yearAgeVec.elementAt(i);
            String subbedString = yearAgeName.substring(7);
            StringTokenizer stringTokenizer = new StringTokenizer(subbedString, "D");
            String rowToBeParsed = (String) stringTokenizer.nextElement();
            String colToBeParsed = (String) stringTokenizer.nextElement();
            int intRow = 0;
            int intCol = 0;
            try {
                intRow = Integer.parseInt(rowToBeParsed);
                intCol = Integer.parseInt(colToBeParsed);
            } catch (Exception rowAndCole) {
                MiscUtils.getLogger().debug("kickum out");
            }
            EctImmImmunizations immunizations = (EctImmImmunizations) immunizationVector.elementAt(intRow - 1);
            immunizations.indexAge.add(colToBeParsed);

        }

        EctImmImmunizationSetWriter immunizationSetWriter = new EctImmImmunizationSetWriter();
        immunizationSetWriter.doWrite(flag, headingVector, immunizationVector, yearAgeVec, setName, providerNo);
        request.setAttribute("cols", "2");
        request.setAttribute("rows", "2");
        return SUCCESS;
    }


    public String getName() {
        if (name == null)
            name = new String();
        return name;
    }

    @StrutsParameter
    public void setName(String str) {
        name = str;
    }

    String name;
}
