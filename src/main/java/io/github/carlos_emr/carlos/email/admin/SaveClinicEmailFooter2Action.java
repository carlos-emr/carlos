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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService.Mode;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService.SaveResult;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.log.LogAction;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Saves the clinic email footer, whether it is required or off, and whether emails carry a logo:
 * Configure Email's Edit footer window sends all three with one Save footer. Only administrators may;
 * personal footers never alter it. Each change is audited.
 */
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

    // FindSecBugs UNVALIDATED_REDIRECT: the only redirect is to this application's own context path plus the fixed route /admin/ViewConfigureEmail and one of the fixed outcome constants; no request value is part of the URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is this application's context path plus a fixed route and a fixed outcome constant; no request value is part of the URL")
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
        String requestedMode = request.getParameter("clinicFooterMode");
        Mode mode = requestedMode == null ? null : Mode.parse(requestedMode);
        if (requestedMode != null && mode == null) {
            response.sendError(400);
            return NONE;
        }
        // The switch posts a hidden "false" and, when on, a checked "true": absent means not posted.
        String[] shownValues = request.getParameterValues("logoShown");
        Boolean logoShown = shownValues == null ? null : java.util.Arrays.asList(shownValues).contains("true");
        String outcome;
        try {
            ClinicEmailFooterService.Saved saved = footers.save(html, shown, mode, logoShown);
            SaveResult result = saved.footer();
            boolean modeChanged = saved.modeChanged();
            if (result == SaveResult.SAVED) {
                LogAction.addLog(user.getLoggedInProviderNo(), "update", "emailFooterClinicDefault", "",
                        request.getRemoteAddr());
            }
            if (modeChanged) {
                LogAction.addLog(user.getLoggedInProviderNo(), "update", "emailFooterClinicMode", mode.value(),
                        request.getRemoteAddr());
            }
            if (saved.logoChanged()) {
                LogAction.addLog(user.getLoggedInProviderNo(), "update", "emailFooterLogoShown",
                        String.valueOf(logoShown), request.getRemoteAddr());
            }
            outcome = result == SaveResult.STALE ? "stale" : result == SaveResult.SAVED ? "saved"
                    : modeChanged || saved.logoChanged() ? "settingsSaved" : "unchanged";
        } catch (IllegalArgumentException e) {
            outcome = "invalid";
        } catch (org.springframework.dao.ConcurrencyFailureException | jakarta.persistence.PersistenceException e) {
            outcome = "conflict";
        }
        // A refused save must not lose what the administrator typed: the page puts it back.
        if ("invalid".equals(outcome) || "conflict".equals(outcome) || "stale".equals(outcome)) {
            ClinicFooterDraft.keep(request, html, shown, "stale".equals(outcome), mode, logoShown);
        }
        // Fixed application route and fixed outcome constants; request text never reaches this URL.
        response.sendRedirect(request.getContextPath() + "/admin/ViewConfigureEmail?clinicFooterOutcome=" + outcome);
        return NONE;
    }
}
