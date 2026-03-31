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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;

import org.owasp.encoder.Encode;

/**
 * Renders the Bootstrap 5 navigation sidebar for the Consultation Request
 * configuration pages. Highlights the currently active page link based on the
 * incoming request URI.
 *
 * @since 2026-02-11
 */
public class EctConTitlebar {

    /**
     * Constructs an EctConTitlebar using the JVM default locale for resource
     * bundle resolution. Suitable for contexts where no HTTP request is available.
     */
    public EctConTitlebar() {
        ResourceBundle oscarR = ResourceBundle.getBundle("oscarResources");
        init(oscarR);
    }

    /**
     * Constructs an EctConTitlebar using the locale derived from the given
     * HTTP servlet request, ensuring navigation labels are rendered in the
     * user's preferred language.
     *
     * @param request the current HTTP servlet request; must not be {@code null}
     */
    public EctConTitlebar(HttpServletRequest request) {
        ResourceBundle oscarR = ResourceBundle.getBundle("oscarResources", request.getLocale());
        init(oscarR);
    }

    private void init(ResourceBundle oscarR) {
        jspVect.add("encounter/oscarConsultationRequest/config/EnableRequestResponse.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnEnableRequestResponse"));
        jspVect.add("encounter/oscarConsultationRequest/config/AddSpecialist.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnAddSpecialist"));
        jspVect.add("encounter/oscarConsultationRequest/config/AddService.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnAddService"));
        jspVect.add("encounter/oscarConsultationRequest/config/EditSpecialists.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnEditSpecialists"));
        jspVect.add("encounter/oscarConsultationRequest/config/ShowAllServices.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnShowAllServices"));
        jspVect.add("encounter/oscarConsultationRequest/config/DeleteServices.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnDeleteServices"));
        jspVect.add("encounter/oscarConsultationRequest/config/AddInstitution.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnAddInstitution"));
        jspVect.add("encounter/oscarConsultationRequest/config/EditInstitutions.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnEditInstitutions"));
        jspVect.add("encounter/oscarConsultationRequest/config/AddDepartment.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnAddDepartment"));
        jspVect.add("encounter/oscarConsultationRequest/config/EditDepartments.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnEditDepartments"));
        jspVect.add("encounter/oscarConsultationRequest/config/ShowAllInstitutions.jsp");
        displayNameVect.add(oscarR.getString("encounter.oscarConsultationRequest.config.btnShowAllInstitutions"));
    }


    /**
     * Builds and returns the HTML {@code <nav>} element string for the
     * consultation configuration sidebar. The link matching the current
     * request URI is marked as active with the Bootstrap {@code active} class
     * and {@code aria-current="page"} for screen-reader accessibility.
     *
     * @param request the current HTTP servlet request used to derive the
     *                context path and determine the active page; must not be
     *                {@code null}
     * @return an HTML string containing the Bootstrap {@code <nav>} element
     *         with navigation links for all configuration pages
     */
    public String estBar(HttpServletRequest request) {
        StringBuilder strBuf = new StringBuilder();
        strBuf.append("<nav class=\"nav nav-pills flex-column\">\n");
        String contextPath = Encode.forHtmlAttribute(request.getContextPath());
        String uri = request.getRequestURI();
        int ind = uri.lastIndexOf("/");
        String filename = uri.substring(ind + 1);

        for (int i = 0; i < jspVect.size(); i++) {
            String jspPath = jspVect.get(i);
            String jspFilename = jspPath.substring(jspPath.lastIndexOf("/") + 1);
            boolean isActive = jspFilename.equals(filename) && request.getAttribute("upd") == null;
            String activeClass = isActive ? " active" : "";
            String ariaCurrent = isActive ? " aria-current=\"page\"" : "";
            strBuf.append("  <a href=\"").append(contextPath).append("/").append(jspPath)
                  .append("\" class=\"nav-link").append(activeClass).append("\"")
                  .append(ariaCurrent).append(">")
                  .append(Encode.forHtml(displayNameVect.get(i))).append("</a>\n");
        }
        strBuf.append("</nav>\n");
        return strBuf.toString();
    }

    private final List<String> jspVect = new ArrayList<String>();
    private final List<String> displayNameVect = new ArrayList<String>();
}
