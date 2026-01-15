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

import ca.openosp.openo.commn.dao.MessageListDao;
import ca.openosp.openo.commn.model.MessageList;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for marking multiple messages as read and redisplaying the message list.
 * 
 * <p>This action handles the bulk operation of marking selected messages as "read" status.
 * It's typically invoked when users want to mark multiple unread messages as read without
 * opening each one individually. The action updates the status in the database and then
 * redisplays the message list to reflect the changes.</p>
 * 
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Validates read permissions for messaging operations</li>
 *   <li>Processes an array of message IDs to mark as read</li>
 *   <li>Updates the status field in the messagelisttbl for each message</li>
 *   <li>Returns to the message display page with updated statuses</li>
 * </ul>
 * 
 * <p>Important notes:</p>
 * <ul>
 *   <li>Despite the misleading comment in the code, messages are marked as "read" not "del" (deleted)</li>
 *   <li>The action requires an active session with a MsgSessionBean</li>
 *   <li>Each message is individually updated in the database (not batch processed)</li>
 * </ul>
 * 
 * @version 2.0
 * @since 2003
 * @see MessageListDao
 * @see MsgSessionBean
 * @see MsgDisplayMessages2Action
 */
public class MsgReDisplayMessages2Action extends ActionSupport {
    /**
     * HTTP request object for accessing session and parameters.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object, maintained for consistency but not actively used.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Data access object for message list operations.
     */
    private MessageListDao dao = SpringUtils.getBean(MessageListDao.class);
    
    /**
     * Security manager for enforcing read permissions on messaging operations.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the mark-as-read operation for selected messages.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates that the user has read permissions for messaging</li>
     *   <li>Retrieves the message session bean from the HTTP session</li>
     *   <li>Iterates through the provided message IDs</li>
     *   <li>Updates each message's status to "read" in the database</li>
     *   <li>Returns success to redisplay the updated message list</li>
     * </ol>
     * 
     * <p>The method handles multiple messages efficiently by processing them
     * in a loop, though each update is a separate database operation rather
     * than a batch update.</p>
     * 
     * @return SUCCESS constant to redisplay the message list
     * @throws IOException if there's an I/O error
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks read permissions for messaging
     */
    public String execute() throws IOException, ServletException {

        // Verify user has read permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Retrieve session bean for provider context
        MsgSessionBean bean = null;
        bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");

        if (bean == null) {
            // No active session - should redirect to login or error page
            return SUCCESS;
        }

        String providerNo = bean.getProviderNo();
        
        // Check if there are messages to process
        // NOTE: The original comment is incorrect - messages are marked as "read" not deleted
        if (messageNo == null || messageNo.length == 0) {
            return SUCCESS;
        }

        // Mark each selected message as read
        for (int i = 0; i < messageNo.length; i++) {
            // Find all instances of this message for the provider
            for (MessageList ml : dao.findByProviderNoAndMessageNo(providerNo, Long.valueOf(messageNo[i]))) {
                // Update status to "read"
                ml.setStatus("read");
                // Persist the change to database
                dao.merge(ml);
            }
        }

        return SUCCESS;
    }

    /**
     * Array of message IDs to be marked as read.
     */
    String[] messageNo;

    /**
     * Gets the array of message IDs to be marked as read.
     * 
     * <p>Note: The original comment incorrectly states these will be deleted,
     * but the implementation actually marks them as read.</p>
     *
     * @return String[] array of message IDs, never null
     */
    public String[] getMessageNo() {
        if (messageNo == null) {
            messageNo = new String[]{};
        }
        return messageNo;
    }

    /**
     * Sets the array of message IDs to be marked as read.
     * 
     * <p>Note: The original comment incorrectly states these will be deleted,
     * but the implementation actually marks them as read.</p>
     *
     * @param mess String[] array of message IDs to mark as read
     */
    public void setMessageNo(String[] mess) {
        this.messageNo = mess;
    }
}
