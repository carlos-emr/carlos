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
package io.github.carlos_emr.carlos.email.action;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.email.core.EmailFooterHtml;
import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.RequestNegotiation;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.dao.ConcurrencyFailureException;

/**
 * POST-only personal saves. Request owner/clinic/restore parameters never gain authority.
 *
 * <p>A form post (My Email Footer) behaves as it always has: a redirect when saved, the page again
 * when refused. The footer editor window opened from Preferences or the email screen saves here by
 * AJAX ({@link RequestNegotiation#isAjax}, the header CSRFGuard and its fetch send) and gets JSON
 * instead: {@code saved} with the new {@code revision}, or a fixed {@code error} code (stale,
 * tooLong, conflict, invalid) and none of the text.</p>
 */
public final class SaveMyEmailFooter2Action extends ActionSupport {
    private final SecurityInfoManager security;
    private final EmailFooterService personal;
    private final ClinicEmailFooterService clinic;

    public SaveMyEmailFooter2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterService.class),
                SpringUtils.getBean(ClinicEmailFooterService.class));
    }

    SaveMyEmailFooter2Action(SecurityInfoManager security, EmailFooterService personal,
            ClinicEmailFooterService clinic) {
        this.security = security;
        this.personal = personal;
        this.clinic = clinic;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: the only redirect is to this application's own context path plus the fixed route /email/myEmailFooter?saved=true; no request value is part of the URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is this application's context path plus a fixed route; no request value is part of the URL")
    @Override
    public String execute() throws IOException {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        var user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!security.hasPrivilege(user, "_email", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(405);
            return NONE;
        }
        boolean ajax = RequestNegotiation.isAjax(request);
        String operation = request.getParameter("footerAction");
        String value;
        if ("save".equals(operation)) {
            value = request.getParameter("myFooter");
            if (value == null || value.length() > 4 * EmailFooterHtml.MAX_HTML_LENGTH) {
                return refuse(response, ajax, value == null ? "invalid" : "tooLong");
            }
        } else if ("clear".equals(operation)) {
            value = "";
        } else {
            return refuse(response, ajax, "invalid");
        }
        String providerNo = user.getLoggedInProviderNo();
        if (ajax) {
            return saveForWindow(request, response, providerNo, value);
        }
        try {
            personal.saveOwnFooter(user.getLoggedInProviderNo(), value, request.getParameter("personalFooterRevision"));
        } catch (EmailFooterService.StaleFooterException failure) {
            ViewMyEmailFooter2Action.expose(request, personal.ownFooter(user.getLoggedInProviderNo()), clinic);
            request.setAttribute("myFooterStale", true);
            request.setAttribute("myFooterAttempted", EmailFooterHtml.toPlainText(EmailFooterHtml.clean(value)));
            return INPUT;
        } catch (EmailFooterService.FooterTooLongException failure) {
            ViewMyEmailFooter2Action.expose(request, EmailFooterHtml.clean(value), clinic);
            request.setAttribute("personalFooterRevision", request.getParameter("personalFooterRevision"));
            request.setAttribute("myFooterTooLong", true);
            return INPUT;
        } catch (ConcurrencyFailureException | jakarta.persistence.PersistenceException failure) {
            ViewMyEmailFooter2Action.expose(request, EmailFooterHtml.clean(value), clinic);
            request.setAttribute("personalFooterRevision", request.getParameter("personalFooterRevision"));
            request.setAttribute("myFooterSaveConflict", true);
            return INPUT;
        }
        LogAction.addLog(user.getLoggedInProviderNo(), LogConst.UPDATE, "emailFooterOwn", "",
                request.getRemoteAddr());
        response.sendRedirect(request.getContextPath() + "/email/myEmailFooter?saved=true");
        return NONE;
    }

    /** The editor window's save: the same checks and audit, answered in JSON. */
    private String saveForWindow(HttpServletRequest request, HttpServletResponse response,
            String providerNo, String value) throws IOException {
        try {
            personal.saveOwnFooter(providerNo, value, request.getParameter("personalFooterRevision"));
        } catch (EmailFooterService.StaleFooterException failure) {
            FooterJson.error(response, HttpServletResponse.SC_CONFLICT, "stale");
            return NONE;
        } catch (EmailFooterService.FooterTooLongException failure) {
            FooterJson.error(response, HttpServletResponse.SC_BAD_REQUEST, "tooLong");
            return NONE;
        } catch (ConcurrencyFailureException | jakarta.persistence.PersistenceException failure) {
            FooterJson.error(response, HttpServletResponse.SC_CONFLICT, "conflict");
            return NONE;
        }
        LogAction.addLog(providerNo, LogConst.UPDATE, "emailFooterOwn", "", request.getRemoteAddr());
        var body = FooterJson.body();
        body.put("saved", true);
        // The saved footer is the cleaned value, and a revision is taken over the cleaned value.
        body.put("revision", EmailFooterService.revisionOf(providerNo, value));
        FooterJson.write(response, HttpServletResponse.SC_OK, body);
        return NONE;
    }

    private static String refuse(HttpServletResponse response, boolean ajax, String code) throws IOException {
        if (ajax) {
            FooterJson.error(response, HttpServletResponse.SC_BAD_REQUEST, code);
        } else {
            response.sendError(400);
        }
        return NONE;
    }
}
