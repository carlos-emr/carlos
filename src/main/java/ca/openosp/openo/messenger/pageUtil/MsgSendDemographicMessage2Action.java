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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.providers.data.ProviderData;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for initiating a new message composition for a specific patient demographic.
 * 
 * <p>This action sets up the messaging session for composing a new message that will be
 * associated with a specific patient's demographic record. It's typically invoked from
 * a patient's chart or demographic view when a provider wants to send a message related
 * to that patient's care.</p>
 * 
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Creates a new message session bean with provider context</li>
 *   <li>Validates write permissions for messaging operations</li>
 *   <li>Sets up the provider's name and number in the session</li>
 *   <li>Preserves the demographic number for message association</li>
 * </ul>
 * 
 * <p>This action serves as the entry point for demographic-specific messaging, ensuring
 * that messages can be properly linked to patient records for medical documentation
 * and care coordination purposes.</p>
 * 
 * @version 2.0
 * @since 2006
 * @see MsgSessionBean
 * @see ProviderData
 * @see MsgCreateMessage2Action
 */
public class MsgSendDemographicMessage2Action extends ActionSupport {
    /**
     * HTTP request object for accessing session and parameters.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object, maintained for consistency but not actively used.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Security manager for enforcing write permissions on messaging operations.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Default constructor for MsgSendDemographicMessage2Action.
     * 
     * <p>Creates a new instance of the action. No initialization is required
     * as all dependencies are injected via Spring.</p>
     */
    public MsgSendDemographicMessage2Action() {
    }

    /**
     * Executes the demographic message initialization workflow.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Retrieves the current provider number from the session</li>
     *   <li>Validates that the user has write permissions for messaging</li>
     *   <li>Creates a new message session bean</li>
     *   <li>Sets the provider number and name in the session bean</li>
     *   <li>Stores the session bean for use in message composition</li>
     *   <li>Preserves the demographic number for message association</li>
     * </ol>
     * 
     * <p>After successful execution, the user is forwarded to the message
     * composition page with the session properly initialized and the
     * demographic context preserved.</p>
     * 
     * @return SUCCESS constant to forward to message composition page
     * @throws IOException if there's an I/O error
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks write permissions for messaging
     */
    public String execute() throws IOException, ServletException {
        // Get current provider number from session
        String provNo = (String) request.getSession().getAttribute("user");

        // Verify user has write permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "w", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Create and initialize new message session bean
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(provNo);
        
        // Set provider name using utility class
        // Note: Creating ProviderData instance is unnecessary here
        ProviderData pd = new ProviderData();
        bean.setUserName(ProviderData.getProviderName(provNo));

        // Store session bean and demographic context
        request.getSession().setAttribute("msgSessionBean", bean);
        request.setAttribute("demographic_no", request.getParameter("demographic_no"));
        
        return SUCCESS;
    }
}
