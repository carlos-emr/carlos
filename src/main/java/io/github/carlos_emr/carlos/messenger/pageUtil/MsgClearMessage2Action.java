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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;



import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * Struts2 action for clearing message attachments from the session.
 * 
 * <p>This action provides a simple mechanism to clear all attachments that have been
 * accumulated in the message session bean during message composition. It is typically
 * invoked when a user wants to remove all attachments from a draft message or reset
 * the attachment state before composing a new message.</p>
 * 
 * <p>The action operates by retrieving the MsgSessionBean from the HTTP session and
 * calling its nullAttachment() method, which removes all stored attachment data
 * including Base64-encoded PDF documents and file attachments.</p>
 * 
 * <p>Usage scenarios:</p>
 * <ul>
 *   <li>User clicks "Clear Attachments" button in message composition</li>
 *   <li>Automatic cleanup when starting a new message</li>
 *   <li>Error recovery when attachment processing fails</li>
 * </ul>
 * 
 * <p>This class is marked as final to prevent inheritance, ensuring the simple
 * attachment clearing behavior cannot be modified through subclassing.</p>
 * 
 * @version 2.0
 * @since 2003
 * @see MsgSessionBean
 * @see MsgAttachPDF2Action
 */
public final class MsgClearMessage2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * HTTP request object used to access the session containing the message bean.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * HTTP response object used to redirect when the message session bean is unavailable.
     */
    HttpServletResponse response = ServletActionContext.getResponse();



    /**
     * Clears all attachments from the message session bean.
     *
     * <p>This method retrieves the MsgSessionBean from the HTTP session and
     * invokes its nullAttachment() method to remove all stored attachments.
     * The attachments are permanently cleared from the session, requiring
     * users to re-attach any documents they wish to include in their message.</p>
     *
     * <p>If the session bean is absent (expired session, direct URL hit, or race
     * condition), the user is redirected to the message inbox.</p>
     *
     * @return SUCCESS if the attachments were cleared successfully, or NONE if the
     *         session bean was absent and the request was redirected to the inbox
     * @throws IOException if there's an I/O error (declared but not typically thrown)
     * @throws ServletException if there's a servlet processing error (declared but not typically thrown)
     */
    public String execute() throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "w", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Retrieve the message session bean from the HTTP session
        MsgSessionBean bean;
        bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");
        if (bean == null) {
            response.sendRedirect(response.encodeRedirectURL(
                    request.getContextPath() + "/messenger/DisplayMessages"));
            return NONE;
        }

        // Clear all attachments from the bean
        bean.nullAttachment();
        return SUCCESS;
    }
}
