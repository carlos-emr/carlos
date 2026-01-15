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


package ca.openosp.openo.messenger.pageUtil;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
 * <p>Known issues:</p>
 * <ul>
 *   <li>URL parameters are not properly encoded (missing ? and &amp; separators)</li>
 *   <li>No URL encoding for special characters in parameters</li>
 *   <li>No security validation before redirecting</li>
 *   <li>encType parameter append is missing = sign</li>
 * </ul>
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
     * Executes the message-to-encounter transfer workflow.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Generates current date for the encounter</li>
     *   <li>Retrieves provider information from session</li>
     *   <li>Constructs redirect URL with encounter parameters</li>
     *   <li>Includes message ID for content retrieval</li>
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
     * <p>BUG: The URL construction is flawed - parameters are concatenated without
     * proper separators (? and &amp;), making the resulting URL invalid. The code should
     * use proper URL building with encoded parameters.</p>
     * 
     * @return NONE as the method performs a redirect instead of forwarding
     * @throws IOException if there's an error with the redirect
     * @throws ServletException if there's a servlet processing error
     */
    public String execute() throws IOException, ServletException {
        // Generate current date for the encounter
        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        String dateString = curYear + "-" + curMonth + "-" + curDay;
        
        // Get provider number from session
        String provider = (String) request.getSession().getAttribute("user");


        // Build redirect URL to encounter module
        // BUG: Missing ? after .do and & between parameters
        // This creates an invalid URL that won't work properly
        StringBuilder forward = new StringBuilder("/oscarEncounter/IncomingEncounter.do");
        forward.append("providerNo=").append(provider);
        forward.append("appointmentNo=").append("");
        forward.append("demographicNo=").append(request.getParameter("demographic_no"));
        forward.append("curProviderNo=").append(provider);
        forward.append("reason=").append("messenger");
        forward.append("userName=").append(request.getSession().getAttribute("userfirstname") + " " + request.getSession().getAttribute("userlastname"));
        forward.append("curDate=").append(dateString);
        forward.append("appointmentDate=").append("");
        forward.append("startTime=").append("");
        forward.append("status=").append("");
        forward.append("msgId=").append(request.getParameter("msgId"));
        
        // Add optional encounter type
        String encType = request.getParameter("encType");
        if (encType != null)
            // BUG: Missing = sign between parameter name and value
            forward.append("encType").append(encType);
            
        // Redirect to encounter module
        response.sendRedirect(forward.toString());
        return NONE;
    }
}
