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


package io.github.carlos_emr.carlos.messenger.pageUtil;

import org.apache.struts2.ActionSupport;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Struts2 action for writing message content to a patient encounter.
 *
 * <p>This action facilitates the transfer of message content into a patient's clinical
 * encounter record. It creates a bridge between the messaging system and the encounter
 * module, allowing providers to incorporate message content directly into patient charts.
 * This is particularly useful when messages contain clinical information that should be
 * documented as part of the patient's medical record.</p>
 *
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Creates an encounter context with current date and provider</li>
 *   <li>Passes message ID to the encounter module</li>
 *   <li>Preserves demographic context for the encounter</li>
 *   <li>Sets encounter reason as "messenger" to indicate source</li>
 * </ul>
 *
 * <p>The action constructs a redirect URL to the encounter module with all necessary
 * parameters including provider information, demographic number, current date, and
 * the message ID. The encounter module can then retrieve and display the message
 * content for incorporation into the clinical note.</p>
 *
 * <p>Security: Requires "_msg" read privilege. All URL parameters are URI-encoded
 * using OWASP Encoder to prevent malformed URLs or injection. Patient data access
 * is recorded in the audit log.</p>
 *
 * @version 2.0
 * @since 2003
 */
public class MsgWriteToEncounter2Action extends ActionSupport {
    /**
     * HTTP request object for accessing session and parameters.
     */
    HttpServletRequest request = ServletActionContext.getRequest();

    /**
     * HTTP response object used for redirecting to encounter module.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Security manager for privilege checks — required before accessing patient data.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    /**
     * Executes the message-to-encounter transfer workflow.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates the user session and checks "_msg" read privilege</li>
     *   <li>Generates current date for the encounter</li>
     *   <li>Retrieves provider information from session</li>
     *   <li>Constructs redirect URL with URI-encoded encounter parameters</li>
     *   <li>Records patient data access in the audit log</li>
     *   <li>Redirects to the encounter module</li>
     * </ol>
     *
     * <p>The redirect URL includes these parameters:</p>
     * <ul>
     *   <li>providerNo - Current provider number</li>
     *   <li>demographicNo - Patient demographic number</li>
     *   <li>curDate - Today's date for the encounter</li>
     *   <li>reason - Set to "messenger" to indicate source</li>
     *   <li>msgId - Message ID to retrieve content from</li>
     *   <li>encType - Optional encounter type parameter</li>
     * </ul>
     *
     *
     * @return NONE as the method performs a redirect instead of forwarding
     * @throws IOException if there's an error with the redirect
     * @throws ServletException if there's a servlet processing error
     */
    public String execute() throws IOException, ServletException {
        // Validate session and check privilege before any processing
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return NONE;
        }

        // Security check: requires "_msg" read privilege
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "r", null)) {
            throw new SecurityException("missing required sec object: _msg");
        }

        // Generate current date for the encounter
        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        String dateString = curYear + "-" + curMonth + "-" + curDay;

        // Get provider number from session — required to proceed
        String provider = (String) request.getSession().getAttribute("user");
        if (provider == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return NONE;
        }

        // Null-safe retrieval of optional session/request attributes
        String demographicNo = request.getParameter("demographic_no") != null ? request.getParameter("demographic_no") : "";
        String msgId = request.getParameter("msgId") != null ? request.getParameter("msgId") : "";
        String firstName = request.getSession().getAttribute("userfirstname") != null ? (String) request.getSession().getAttribute("userfirstname") : "";
        String lastName = request.getSession().getAttribute("userlastname") != null ? (String) request.getSession().getAttribute("userlastname") : "";

        // Build redirect URL to encounter module with URI-encoded parameters
        // to prevent malformed URLs from special characters in user/session data
        StringBuilder forward = new StringBuilder(request.getContextPath());
        forward.append("/encounter/IncomingEncounter.do");
        forward.append("?providerNo=").append(Encode.forUriComponent(provider));
        forward.append("&appointmentNo=");
        forward.append("&demographicNo=").append(Encode.forUriComponent(demographicNo));
        forward.append("&curProviderNo=").append(Encode.forUriComponent(provider));
        forward.append("&reason=messenger");
        forward.append("&userName=").append(Encode.forUriComponent(firstName + " " + lastName));
        forward.append("&curDate=").append(Encode.forUriComponent(dateString));
        forward.append("&appointmentDate=");
        forward.append("&startTime=");
        forward.append("&status=");
        forward.append("&msgId=").append(Encode.forUriComponent(msgId));

        // Add optional encounter type
        String encType = request.getParameter("encType");
        if (encType != null)
            forward.append("&encType=").append(Encode.forUriComponent(encType));

        // Audit log: record patient data access before redirect
        LogAction.addLogSynchronous(loggedInInfo, "MsgWriteToEncounter2Action", "demographicNo=" + demographicNo);

        // Redirect to encounter module
        response.sendRedirect(forward.toString());
        return NONE;
    }
}
