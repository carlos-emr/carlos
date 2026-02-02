//CHECKSTYLE:OFF
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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;

public class EctConTitlebar {

    public EctConTitlebar() {
        ResourceBundle oscarR = ResourceBundle.getBundle("oscarResources");
        init(oscarR);
    }

    public EctConTitlebar(HttpServletRequest request) {
        ResourceBundle oscarR = ResourceBundle.getBundle("oscarResources", request.getLocale());
        init(oscarR);
    }

    private void init(ResourceBundle oscarR) {
        jspVect = new ArrayList<String>();
        displayNameVect = new ArrayList<String>();
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/EnableRequestResponse.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnEnableRequestResponse"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/AddSpecialist.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnAddSpecialist"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/AddService.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnAddService"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/EditSpecialists.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnEditSpecialists"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/ShowAllServices.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnShowAllServices"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/DeleteServices.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnDeleteServices"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/AddInstitution.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnAddInstitution"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/EditInstitutions.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnEditInstitutions"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/AddDepartment.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnAddDepartment"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/EditDepartments.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnEditDepartments"));
        jspVect.add("oscarEncounter/oscarConsultationRequest/config/ShowAllInstitutions.jsp");
        displayNameVect.add(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.btnShowAllInstitutions"));
    }


    public String estBar(HttpServletRequest request) {
        StringBuilder strBuf = new StringBuilder();
        strBuf.append("<table bgcolor=\"#ffffff\" cellspacing=\"2\">\n");
        strBuf.append("   <tr>\n");
        String uri = request.getRequestURI();
        int ind = uri.lastIndexOf("/");
        uri = uri.substring(ind + 1);

        for (int i = 0; i < jspVect.size(); i++) {
            if (uri.equals(jspVect.get(i)) && request.getAttribute("upd") == null) {
                strBuf.append("      <td bgcolor=\"#ccccff\">\n");
                strBuf.append("         <a href=" + jspVect.get(i) + " class=\"consultButtonsDormant\">" + displayNameVect.get(i) + "</a>\n");
                strBuf.append("      </td>\n");
            } else {
                strBuf.append("      <td bgcolor=\"#9999ff\">\n");
                strBuf.append(String.valueOf(String.valueOf((new StringBuilder("         <a href=")).append(jspVect.get(i)).append(" class=\"consultButtonsActive\">").append(displayNameVect.get(i)).append("</a>\n"))));
                strBuf.append("      </td>\n");
            }
            strBuf.append("   </tr>\n");
        }
        strBuf.append("</table>\n");
        return strBuf.toString();
    }

    List<String> jspVect;
    List<String> displayNameVect;
}
