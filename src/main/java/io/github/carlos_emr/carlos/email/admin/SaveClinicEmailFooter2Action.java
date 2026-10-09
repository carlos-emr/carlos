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
package io.github.carlos_emr.carlos.email.admin;

import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService.SaveResult;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.log.LogAction;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Only administrators may edit the mandatory clinic footer. Personal footers never alter it. */
public final class SaveClinicEmailFooter2Action extends ActionSupport {
    private final SecurityInfoManager security;
    private final ClinicEmailFooterService footers;

    public SaveClinicEmailFooter2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(ClinicEmailFooterService.class));
    }

    SaveClinicEmailFooter2Action(SecurityInfoManager security, ClinicEmailFooterService footers) {
        this.security = security;
        this.footers = footers;
    }

    @Override
    public String execute() throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        var user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!security.hasPrivilege(user, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(405);
            return NONE;
        }
        String html = request.getParameter("clinicFooter");
        String shown = request.getParameter("clinicFooterFingerprint");
        if (html == null || shown == null || !shown.matches("[0-9a-f]{64}")) {
            response.sendError(400);
            return NONE;
        }
        String outcome;
        try {
            SaveResult result = footers.save(html, shown);
            outcome = result == SaveResult.STALE ? "stale" : result == SaveResult.SAVED ? "saved" : "unchanged";
            if (result == SaveResult.SAVED) {
                LogAction.addLog(user.getLoggedInProviderNo(), "update", "emailFooterClinicDefault", "",
                        request.getRemoteAddr());
            }
        } catch (IllegalArgumentException e) {
            outcome = "invalid";
        } catch (org.springframework.dao.ConcurrencyFailureException | jakarta.persistence.PersistenceException e) {
            outcome = "conflict";
        }
        // Fixed application route and fixed outcome constants; request text never reaches this URL.
        response.sendRedirect(request.getContextPath() + "/admin/ViewConfigureEmail?clinicFooterOutcome=" + outcome);
        return NONE;
    }
}
