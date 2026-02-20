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
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for bulk message operations on the deleted/archived message view.
 *
 * <p>This action restores selected archived messages to the inbox by setting their
 * status to {@link MessageList#STATUS_READ "read"}.</p>
 *
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Validates write permissions for messaging operations</li>
 *   <li>Processes an array of message IDs for the restore operation</li>
 *   <li>Updates message status to "read" in the database for each selected message</li>
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
     * HTTP response object used for redirect on session timeout.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Data access object for message list operations.
     */
    private MessageListDao dao = SpringUtils.getBean(MessageListDao.class);
    
    /**
     * Security manager for enforcing write permissions on messaging operations.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the bulk message operation for selected messages.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates that the user has write permissions for messaging</li>
     *   <li>Retrieves the message session bean from the HTTP session</li>
     *   <li>Iterates through the provided message IDs and sets status to read</li>
     *   <li>Returns success to redisplay the updated message list</li>
     * </ol>
     * 
     * @return String SUCCESS constant to redisplay the message list, or {@code null}
     *         if the response was redirected (e.g., session timeout redirect to index.jsp)
     * @throws IOException if there's an I/O error during redirect
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks "_msg" write permissions, or if the session
     *         bean's provider does not match the logged-in user (IDOR protection)
     */
    public String execute() throws IOException, ServletException {

        // Validate session and permissions
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
            throw new SecurityException("No valid session found");
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "w", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Retrieve session bean for provider context
        MsgSessionBean bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");

        if (bean == null) {
            MiscUtils.getLogger().warn("MsgSessionBean is null in MsgReDisplayMessages2Action; redirecting to login.");
            response.sendRedirect(request.getContextPath() + "/index.jsp");
            return null;
        }

        // IDOR protection: verify the session bean's provider matches the logged-in user
        if (!loggedInInfo.getLoggedInProviderNo().equals(bean.getProviderNo())) {
            throw new SecurityException("Cannot access another provider's messages");
        }

        String providerNo = bean.getProviderNo();

        // Check if there are messages to process
        if (messageNo == null || messageNo.length == 0) {
            return SUCCESS;
        }

        // Process each selected message individually; failures are logged and skipped
        int failureCount = 0;
        for (int i = 0; i < messageNo.length; i++) {
            try {
                // A single message ID may map to multiple MessageList rows (one per recipient)
                for (MessageList ml : dao.findByProviderNoAndMessageNo(providerNo, ConversionUtils.fromLongString(messageNo[i]))) {
                    ml.setStatus(MessageList.STATUS_READ);
                    dao.merge(ml);
                }
            } catch (RuntimeException e) {
                failureCount++;
                MiscUtils.getLogger().error("Failed to update message", e);
            }
        }
        // Surface partial failures to the UI via request attribute
        if (failureCount > 0) {
            request.setAttribute("updateFailureCount", failureCount);
            MiscUtils.getLogger().warn("Some messages failed to update");
        }

        return SUCCESS;
    }

    /**
     * Array of message IDs selected for bulk operations (mark as read or unarchive).
     */
    String[] messageNo;

    /**
     * Gets the array of message IDs selected for processing.
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
     * Sets the array of message IDs for bulk operations.
     *
     * @param mess String[] array of message IDs selected for processing
     */
    public void setMessageNo(String[] mess) {
        this.messageNo = mess;
    }
}
