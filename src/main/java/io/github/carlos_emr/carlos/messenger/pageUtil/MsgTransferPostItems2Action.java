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

import java.util.ArrayList;
import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.docxfer.send.MsgSendDocument;
import io.github.carlos_emr.carlos.messenger.docxfer.util.MsgCommxml;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Mutation gate for the final document-transfer POST step. Replaces the
 * scriptlet previously in Transfer/PostItems.jsp.
 * <p>
 * Parses {@code item*} checkbox parameters and the Base64-encoded
 * {@code xmlDoc}, builds the attachment XML, writes it onto
 * {@link MsgSessionBean}, and redirects to the compose-message view gate.
 * <p>
 * POST-only: mutates session state.
 *
 * @since 2026-04-13
 */
public final class MsgTransferPostItems2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Parses checkbox selections and the Base64-encoded {@code xmlDoc},
     * builds the attachment XML, writes it onto the messenger session bean,
     * and redirects to the compose-message view gate.
     *
     * @return {@link #NONE} — the action always redirects or sends an HTTP status code
     * @throws Exception if {@code MsgCommxml} / {@code MsgSendDocument} parsing
     *         fails after the request has passed validation
     * @throws SecurityException if the current user lacks {@code _msg} write privilege
     * @since 2026-04-13
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "UNVALIDATED_REDIRECT"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "w", null)) {
            logger.warn("MsgTransferPostItems2Action denied: provider={} lacks _msg write",
                    providerNoOf(loggedInInfo));
            throw new SecurityException("missing required sec object (_msg)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            logger.warn("MsgTransferPostItems2Action method not allowed: provider={} method={}",
                    providerNoOf(loggedInInfo), request.getMethod());
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        HttpSession session = request.getSession(false);
        MsgSessionBean bean = session == null
                ? null
                : (MsgSessionBean) session.getAttribute("msgSessionBean");
        if (bean == null || !bean.isValid()) {
            logger.warn("MsgTransferPostItems2Action: missing/invalid msgSessionBean for provider={}; redirecting to DisplayMessages",
                    providerNoOf(loggedInInfo));
            response.sendRedirect(request.getContextPath() + "/messenger/DisplayMessages");
            return NONE;
        }

        String xmlDocRaw = request.getParameter("xmlDoc");
        if (xmlDocRaw == null || xmlDocRaw.isEmpty()) {
            logger.warn("MsgTransferPostItems2Action: missing xmlDoc param for provider={}",
                    providerNoOf(loggedInInfo));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing xmlDoc");
            return NONE;
        }

        // Only accept numeric suffixes on item* parameters; anything else is
        // silently dropped so a crafted "itemfoo=on" cannot reach the parser.
        StringBuilder checks = new StringBuilder();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.startsWith("item") && "on".equalsIgnoreCase(request.getParameter(name))) {
                String itemId = name.substring(4);
                if (isDigits(itemId)) {
                    checks.append(itemId).append(',');
                }
            }
        }

        // Decode + parse inside a single try so malformed Base64 or malformed
        // XML returns a clean 400 instead of falling through to a 500.
        String sXML;
        try {
            String xmlDoc = MsgCommxml.decode64(xmlDocRaw);
            @SuppressWarnings("rawtypes")
            ArrayList aList = new ArrayList();
            sXML = MsgCommxml.toXML(
                    new MsgSendDocument().parseChecks2(xmlDoc, checks.toString(), aList));
        } catch (RuntimeException e) {
            logger.warn("MsgTransferPostItems2Action: malformed xmlDoc for provider={}; rejecting as 400",
                    providerNoOf(loggedInInfo));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed xmlDoc");
            return NONE;
        }
        // MsgCommxml.toXML() returns empty (never null) on serialization failure.
        if (sXML == null || sXML.isEmpty()) {
            logger.warn("MsgTransferPostItems2Action: xmlDoc serialized empty for provider={}; rejecting as 400",
                    providerNoOf(loggedInInfo));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed xmlDoc");
            return NONE;
        }

        bean.setAttachment(sXML);

        response.sendRedirect(request.getContextPath() + "/messenger/ViewCreateMessage");
        return NONE;
    }

    private static String providerNoOf(LoggedInInfo info) {
        return info == null ? "anon" : info.getLoggedInProviderNo();
    }

    private static boolean isDigits(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
