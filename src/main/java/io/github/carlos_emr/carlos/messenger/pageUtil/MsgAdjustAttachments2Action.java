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
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Mutation gate for the attachment-adjustment step in the messenger document
 * transfer flow. Replaces the scriptlet previously in AdjustAttachments.jsp.
 * <p>
 * Parses {@code item*} checkbox parameters, decodes the {@code xmlDoc}
 * Base64-encoded XML, filters the attachment list to the user's selection,
 * stages it for forwarding and redirects to the demographic search page.
 * <p>
 * The selection is saved under the forwarding key of the message the attachment
 * window was opened from ({@code attachmentKey}, see
 * {@link MsgAttachmentStaging#forwardKey}); each compose page that the message
 * view's Compose link, Reply, Reply All or Forward opens starts with a copy of the
 * items ({@link MsgAttachmentStaging#composeKey}) and sends them. It used
 * to be kept on the login-wide
 * {@link MsgSessionBean}, so the next message sent from any compose window, for
 * any patient, carried them (issue #4673).
 * <p>
 * POST-only: the action mutates session state, so GET requests are rejected
 * with 405 to prevent CSRF via link/redirect.
 *
 * @since 2026-04-13
 */
public final class MsgAdjustAttachments2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Parses checkbox selections and the Base64-encoded {@code xmlDoc}, stages
     * the selection under the posted message-view key and redirects to the
     * demographic-search gate. A malformed document, or a missing or malformed
     * key, is a 400 and stages nothing.
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
            logger.warn("MsgAdjustAttachments2Action denied: provider={} lacks _msg write",
                    providerNoOf(loggedInInfo));
            throw new SecurityException("missing required sec object (_msg)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            logger.warn("MsgAdjustAttachments2Action method not allowed: provider={} method={}",
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
            logger.warn("MsgAdjustAttachments2Action: missing/invalid msgSessionBean for provider={}; redirecting to DisplayMessages",
                    providerNoOf(loggedInInfo));
            response.sendRedirect(request.getContextPath() + "/messenger/DisplayMessages");
            return NONE;
        }

        // The message view this selection is forwarded from. Without its key the selection could
        // only be staged where every compose window would find it, so it is refused instead.
        String forwardKey = request.getParameter(MsgAttachmentStaging.KEY_PARAMETER);
        if (!MsgAttachmentStaging.isKey(forwardKey)) {
            logger.warn("MsgAdjustAttachments2Action: missing or malformed attachmentKey for provider={}",
                    providerNoOf(loggedInInfo));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No message view to forward from");
            return NONE;
        }

        String xmlDocRaw = request.getParameter("xmlDoc");
        if (xmlDocRaw == null || xmlDocRaw.isEmpty()) {
            logger.warn("MsgAdjustAttachments2Action: missing xmlDoc param for provider={}",
                    providerNoOf(loggedInInfo));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing xmlDoc");
            return NONE;
        }

        // Collect selected item indices from checkbox parameters ("item0", "item1", …).
        // Only accept numeric suffixes; anything else is silently dropped so a
        // crafted "itemfoo=on" cannot reach the XML parser.
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
        // XML returns a clean 400 instead of falling through to a 500 via
        // parseXML()-returns-null -> getDocumentElement() NPE.
        String sXML;
        try {
            String xmlDoc = MsgCommxml.decode64(xmlDocRaw);
            sXML = MsgCommxml.toXML(new MsgSendDocument().parseChecks(xmlDoc, checks.toString()));
        } catch (RuntimeException e) {
            logger.warn("MsgAdjustAttachments2Action: malformed xmlDoc for provider={}; rejecting as 400",
                    providerNoOf(loggedInInfo));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed xmlDoc");
            return NONE;
        }
        // MsgCommxml.toXML() returns empty (never null) if serialization fails,
        // which also covers the case where parseChecks returns a Document that
        // transforms to nothing.
        if (sXML == null || sXML.isEmpty()) {
            logger.warn("MsgAdjustAttachments2Action: xmlDoc serialized empty for provider={}; rejecting as 400",
                    providerNoOf(loggedInInfo));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed xmlDoc");
            return NONE;
        }

        MsgAttachmentStaging.stageItems(session, forwardKey, sXML);

        // DemographicLinkMsg forwards to the relocated msgSearchDemo view;
        // the previous redirect to Transfer/DemographicSearch.jsp 404s because
        // that JSP does not exist in the webapp.
        response.sendRedirect(request.getContextPath() + "/demographic/DemographicLinkMsg");
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
