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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for bulk message operations on the deleted/archived message view.
 *
 * <p>This action handles two operations depending on the button clicked:</p>
 * <ul>
 *   <li><b>Unarchive (btnUnarchive)</b>: Restores deleted messages by setting their
 *       deleted flag to false, returning them to the inbox</li>
 *   <li><b>Default</b>: Marks selected messages as "read" status</li>
 * </ul>
 *
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Validates read permissions for messaging operations</li>
 *   <li>Processes an array of message IDs for the selected operation</li>
 *   <li>Updates message fields in the database for each selected message</li>
 *   <li>Returns to the message display page with updated statuses</li>
 * </ul>
 *
 * <p>Important notes:</p>
 * <ul>
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
     * Executes the bulk message operation for selected messages.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates that the user has read permissions for messaging</li>
     *   <li>Retrieves the message session bean from the HTTP session</li>
     *   <li>Determines operation type: unarchive (btnUnarchive) or mark as read</li>
     *   <li>Iterates through the provided message IDs and applies the operation</li>
     *   <li>Returns success to redisplay the updated message list</li>
     * </ol>
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

        // Determine operation: unarchive (restore) or mark as read
        boolean isUnarchive = request.getParameter("btnUnarchive") != null;

        for (int i = 0; i < messageNo.length; i++) {
            for (MessageList ml : dao.findByProviderNoAndMessageNo(providerNo, Long.valueOf(messageNo[i]))) {
                if (isUnarchive) {
                    ml.setDeleted(false);
                } else {
                    ml.setStatus("read");
                }
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
