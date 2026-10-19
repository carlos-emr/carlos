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
 * Struts2 action behind the compose page's "Clear New Message" button: drops what that compose
 * window has attached and shows a fresh compose page.
 * 
 * <p>The compose page's link names its own window ({@code attachmentKey}); only that window's
 * staged attachments ({@link MsgAttachmentStaging}) are dropped, and the fresh page gets a new
 * key. This used to empty the attachments of the login-wide {@link MsgSessionBean}, which also
 * discarded what another compose window, for another patient, had attached (issue #4673).</p>
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
     * Drops the attachments of the compose window named by {@code attachmentKey}; the user must
     * attach again for the new message. Other compose windows keep theirs.
     *
     * <p>If the session bean is absent (expired session, direct URL hit, or race
     * condition), the user is redirected to the message inbox.</p>
     *
     * @return SUCCESS to show a fresh compose page, or NONE if the
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
            // A relative, constant target stays within the current /messenger/ path without
            // accepting request-controlled redirect input or invoking URL-based session tracking.
            response.sendRedirect("DisplayMessages");
            return NONE;
        }

        // Drop this compose window's attachments only; a missing or malformed key drops nothing.
        MsgAttachmentStaging.discard(request.getSession(), request.getParameter(MsgAttachmentStaging.KEY_PARAMETER));
        return SUCCESS;
    }
}
