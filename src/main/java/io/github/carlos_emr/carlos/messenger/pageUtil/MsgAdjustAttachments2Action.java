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

import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.docxfer.send.MsgSendDocument;
import io.github.carlos_emr.carlos.messenger.docxfer.util.MsgCommxml;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for the attachment-adjustment step in the messenger document
 * transfer flow. Replaces the scriptlet previously in AdjustAttachments.jsp.
 * <p>
 * Parses {@code item*} checkbox parameters, decodes the {@code xmlDoc}
 * Base64-encoded XML, filters the attachment list to the user's selection,
 * writes the result onto {@link MsgSessionBean}, and redirects to the
 * demographic search page where the user picks recipients.
 * <p>
 * POST-only: the action mutates session state, so GET requests are rejected
 * with 405 to prevent CSRF via link/redirect.
 *
 * @since 2026-04-13
 */
public final class MsgAdjustAttachments2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "w", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        HttpSession session = request.getSession(false);
        MsgSessionBean bean = session == null
                ? null
                : (MsgSessionBean) session.getAttribute("msgSessionBean");
        if (bean == null || !bean.isValid()) {
            response.sendRedirect(request.getContextPath() + "/messenger/index.jsp");
            return NONE;
        }

        // Collect selected item indices from checkbox parameters ("item0", "item1", …).
        StringBuilder checks = new StringBuilder();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.startsWith("item") && "on".equalsIgnoreCase(request.getParameter(name))) {
                checks.append(name.substring(4)).append(',');
            }
        }

        String xmlDoc = MsgCommxml.decode64(request.getParameter("xmlDoc"));
        String idEnc = request.getParameter("id");
        String sXML = MsgCommxml.toXML(new MsgSendDocument().parseChecks(xmlDoc, checks.toString()));

        bean.setAttachment(sXML);
        bean.setMessageId(idEnc);

        response.sendRedirect(request.getContextPath() + "/messenger/Transfer/DemographicSearch.jsp");
        return NONE;
    }
}
