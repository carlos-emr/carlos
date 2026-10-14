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

import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.email.core.EmailFooterHtml;
import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.IOException;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.dao.ConcurrencyFailureException;

/** POST-only personal saves. Request owner/clinic/restore parameters never gain authority. */
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
        this.security = security; this.personal = personal; this.clinic = clinic;
    }

    @Override
    public String execute() throws IOException {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        var user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!security.hasPrivilege(user, "_email", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST"); response.sendError(405); return NONE;
        }
        String operation = request.getParameter("footerAction");
        String value;
        if ("save".equals(operation)) {
            value = request.getParameter("myFooter");
            if (value == null || value.length() > 4 * EmailFooterHtml.MAX_HTML_LENGTH) {
                response.sendError(400); return NONE;
            }
        } else if ("clear".equals(operation)) {
            value = "";
        } else { response.sendError(400); return NONE; }
        try {
            personal.saveOwnFooter(user.getLoggedInProviderNo(), value);
        } catch (EmailFooterService.FooterTooLongException failure) {
            ViewMyEmailFooter2Action.expose(request, EmailFooterHtml.clean(value), clinic);
            request.setAttribute("myFooterTooLong", true); return INPUT;
        } catch (ConcurrencyFailureException | jakarta.persistence.PersistenceException failure) {
            ViewMyEmailFooter2Action.expose(request, EmailFooterHtml.clean(value), clinic);
            request.setAttribute("myFooterSaveConflict", true); return INPUT;
        }
        LogAction.addLog(user.getLoggedInProviderNo(), LogConst.UPDATE, "emailFooterOwn", "",
                request.getRemoteAddr());
        response.sendRedirect(request.getContextPath() + "/email/myEmailFooter?saved=true");
        return NONE;
    }
}
