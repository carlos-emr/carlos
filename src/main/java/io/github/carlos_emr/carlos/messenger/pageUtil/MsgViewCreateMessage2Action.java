/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for the message composition form (CreateMessage.jsp).
 * <p>
 * Separate from {@link MsgCreateMessage2Action}, which is the POST submit
 * handler. This action exists so the compose form can be reached via a single
 * privilege-checked URL from other pages (inbox "Compose" button, reply
 * shortcuts, admin popup) without making the JSP publicly reachable.
 * <p>
 * The HTTP method is not enforced here: the gate writes nothing to the
 * database. A message view's Compose link names its message
 * ({@code forwardMessage}), and the gate then stages, in the session, a copy of
 * the chart items saved for that message under the new window's own key
 * ({@link MsgAttachmentStaging#composeKey}). Only {@code _msg} read is required.
 *
 * @since 2026-04-13
 */
public final class MsgViewCreateMessage2Action extends ActionSupport {

    /** The message a view's Compose link opens a new message from. */
    static final String FORWARD_MESSAGE_PARAMETER = "forwardMessage";

    private static final Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Validates the current user's {@code _msg} read privilege before forwarding
     * to the compose-form JSP via the Struts {@code success} result.
     *
     * @return {@link #SUCCESS} when access is authorized
     * @throws Exception never thrown here; declared to satisfy the overridden signature
     * @throws SecurityException if the current user lacks {@code _msg} read privilege
     * @since 2026-04-13
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "r", null)) {
            logger.warn("MsgViewCreateMessage2Action denied: provider={} lacks _msg read",
                    loggedInInfo == null ? "anon" : loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_msg)");
        }

        // A message view's Compose link names its message: this compose window gets a key of its
        // own that starts with the items saved for that message. The document-transfer step
        // redirects here with the new key it staged its items under. Either way this window sends
        // those items and nothing another window attached (issue #4673). Any other visit starts
        // a new message.
        String forwardMessage = request.getParameter(FORWARD_MESSAGE_PARAMETER);
        String attachmentKey = request.getParameter(MsgAttachmentStaging.KEY_PARAMETER);
        if (MsgAttachmentStaging.isKey(attachmentKey)) {
            request.setAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE, attachmentKey);
        } else if (forwardMessage != null && forwardMessage.matches("[1-9][0-9]*")
                && io.github.carlos_emr.carlos.util.ConversionUtils.fromIntString(forwardMessage) > 0) {
            request.setAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE,
                    MsgAttachmentStaging.composeKey(request.getSession(),
                            io.github.carlos_emr.carlos.util.ConversionUtils.fromIntString(forwardMessage)));
        }

        return SUCCESS;
    }
}
