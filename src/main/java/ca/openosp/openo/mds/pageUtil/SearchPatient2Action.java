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


package ca.openosp.openo.mds.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.lab.ca.on.CommonLabResultData;

import org.owasp.encoder.Encode;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action that handles the E-Chart button functionality in lab display pages.
 *
 * <p>This action determines the appropriate page to display when clicking the E-Chart button:
 * <ul>
 *   <li>If the lab is already linked to a patient, redirects directly to the patient's E-Chart</li>
 *   <li>If the lab is not linked, redirects to the patient search page to allow linking</li>
 * </ul>
 *
 * <p>The action receives lab information (segmentID, labType, patient name) and checks if
 * the lab result is already associated with a demographic record. Based on this, it constructs
 * the appropriate redirect URL including all necessary parameters for the target page.
 *
 * @since 2004-02-04
 */
public class SearchPatient2Action extends ActionSupport {
    private static final String PATIENT_SEARCH_URL = "/oscarMDS/PatientSearch.jsp?search_mode=search_name&limit1=0&limit2=10";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public SearchPatient2Action() {
    }

    /**
     * Executes the search patient action to determine the appropriate redirect target.
     *
     * <p>This method checks if the specified lab result is already linked to a patient:
     * <ul>
     *   <li>If linked: redirects to OpenEChart.jsp with the patient's demographic number</li>
     *   <li>If not linked: redirects to PatientSearch.jsp to allow manual patient matching</li>
     *   <li>On error: redirects to PatientSearch.jsp as a fallback</li>
     * </ul>
     *
     * <p>All redirects include the lab information (labNo, labType, keyword) as query parameters
     * to maintain context for the target page. All URL parameter values are encoded using
     * OWASP Encoder to prevent injection attacks.
     *
     * <p>Request Parameters:
     * <ul>
     *   <li>segmentID (String): The lab result identifier</li>
     *   <li>labType (String): The type of lab result (e.g., "HL7")</li>
     *   <li>name (String): The patient name from the lab result</li>
     * </ul>
     *
     * <p>Security: All user-provided parameters are encoded using {@code Encode.forUriComponent()}
     * before being included in redirect URLs to prevent XSS and injection attacks.
     *
     * @return String constant NONE indicating a redirect response (no view rendering)
     * @throws ServletException if a servlet-specific error occurs during processing
     * @throws IOException if an I/O error occurs during redirect
     * @throws SecurityException if the user lacks required "_lab" read privilege
     */
    public String execute()
            throws ServletException, IOException {
        
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "r", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String labNo = request.getParameter("segmentID");
        String name = request.getParameter("name");
        String labType = request.getParameter("labType");
        String contextPath = request.getContextPath();

        // Validate required parameters (name is optional, only used for keyword search)
        if (labNo == null || labType == null) {
            MiscUtils.getLogger().error("Missing required parameters in SearchPatient2Action: labNo=" + labNo +
                    ", labType=" + labType);
            response.sendRedirect(contextPath + PATIENT_SEARCH_URL);
            return NONE;
        }

        String newURL = "";
        try {
            String demographicNo = CommonLabResultData.searchPatient(labNo, labType);
            if (demographicNo != null && !demographicNo.equals("0")) {
                // Lab is linked to a patient - open e-chart directly
                newURL = contextPath + "/oscarMDS/OpenEChart.jsp";
                newURL = newURL + "?demographicNo=" + Encode.forUriComponent(demographicNo);
            } else {
                // Lab is not linked or demographicNo is null - show patient search
                newURL = contextPath + PATIENT_SEARCH_URL;
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("exception in SearchPatient2Action:" + e);
            // On error, show patient search to allow manual linking
            newURL = contextPath + PATIENT_SEARCH_URL;
        }

        if (newURL.indexOf("?") == -1) {
            newURL = newURL + "?";
        } else {
            newURL = newURL + "&";
        }
        newURL = newURL + "labNo=" + Encode.forUriComponent(labNo)
                + "&labType=" + Encode.forUriComponent(labType)
                + "&keyword=" + Encode.forUriComponent(name != null ? name : "");

        response.sendRedirect(newURL);
        return NONE;
    }
}
