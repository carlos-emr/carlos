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
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.RemoteAttachmentsDao;
import ca.openosp.openo.commn.model.RemoteAttachments;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.util.ConversionUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for handling remote attachment associations with demographics.
 * 
 * <p>This action manages the process of linking remote attachments to patient demographics
 * in the messaging system. It checks if an attachment is already linked to a demographic
 * and either confirms the existing link or creates a new one. This is typically used
 * when processing messages with attachments that need to be associated with specific
 * patient records.</p>
 * 
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Validates write permissions for messaging operations</li>
 *   <li>Checks for existing remote attachment associations</li>
 *   <li>Creates new remote attachment records when needed</li>
 *   <li>Clears session attachments after processing</li>
 * </ul>
 * 
 * <p>The action sets a confirmation message attribute indicating whether:</p>
 * <ul>
 *   <li>"1" - Attachment already exists for this demographic/message combination</li>
 *   <li>"2" - New attachment association was successfully created</li>
 * </ul>
 * 
 * <p>This action is part of the attachment workflow where remote documents or files
 * need to be permanently associated with patient records for medical documentation
 * purposes.</p>
 * 
 * @version 2.0
 * @since 2003
 * @see RemoteAttachments
 * @see RemoteAttachmentsDao
 * @see MsgSessionBean
 */
public class MsgProceed2Action extends ActionSupport {
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
     * Executes the remote attachment association workflow.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates that the user has write permissions for messaging</li>
     *   <li>Retrieves the message session bean from the HTTP session</li>
     *   <li>Checks if a remote attachment already exists for the demographic/message combination</li>
     *   <li>If no existing attachment, creates a new RemoteAttachments record</li>
     *   <li>Sets a confirmation message indicating the result</li>
     *   <li>Clears any temporary attachments from the session</li>
     * </ol>
     * 
     * <p>The method uses the demoId (demographic ID) and id (message ID) properties
     * to identify which patient and message the attachment should be associated with.</p>
     * 
     * @return SUCCESS constant to indicate successful processing
     * @throws IOException if there's an I/O error
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks write permissions for messaging
     */
    public String execute() throws IOException, ServletException {

        // Verify user has write permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "w", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Retrieve session bean for user context
        MsgSessionBean bean;
        bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");

        // Check for existing remote attachment association
        RemoteAttachmentsDao dao = SpringUtils.getBean(RemoteAttachmentsDao.class);
        List<RemoteAttachments> rs = dao.findByDemoNoAndMessageId(ConversionUtils.fromIntString(demoId), ConversionUtils.fromIntString(id));
        
        if (rs.size() > 0) {
            // Attachment already exists - set confirmation message "1"
            request.setAttribute("confMessage", "1");
        } else {
            // Create new remote attachment association
            RemoteAttachments ra = new RemoteAttachments();
            ra.setDemographicNo(Integer.parseInt(demoId));
            ra.setMessageId(Integer.parseInt(id));
            ra.setSavedBy(bean.getUserName());
            ra.setDate(new Date());
            ra.setTime(new Date());
            dao.persist(ra);
            // Set confirmation message "2" for successful creation
            request.setAttribute("confMessage", "2");
        }

        // Clear any temporary attachments from the session
        bean.nullAttachment();

        return SUCCESS;
    }

    /**
     * Demographic ID for the patient to associate with the attachment.
     */
    String demoId = null;
    
    /**
     * Message ID for the message containing the attachment.
     */
    String id = null;

    /**
     * Gets the demographic ID.
     * 
     * @return String the demographic ID, empty string if null
     */
    public String getDemoId() {
        if (this.demoId == null) {
            this.demoId = new String();
        }
        return this.demoId;
    }

    /**
     * Sets the demographic ID.
     * 
     * @param str String the demographic ID to set
     */
    public void setDemoId(String str) {
        this.demoId = str;
    }

    /**
     * Gets the message ID.
     * 
     * @return String the message ID, empty string if null
     */
    public String getId() {
        if (this.id == null) {
            this.id = new String();
        }
        return this.id;
    }

    /**
     * Sets the message ID.
     * 
     * @param str String the message ID to set
     */
    public void setId(String str) {
        this.id = str;
    }
}
