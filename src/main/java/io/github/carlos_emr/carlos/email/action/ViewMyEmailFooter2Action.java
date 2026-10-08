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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Shows the logged-in user's email footer page ({@code email/myEmailFooter.jsp}): their own footer
 * or the clinic default they follow, and a notice after a clinic footer change they have not answered.
 * Read only; {@link SaveMyEmailFooter2Action} saves. It needs {@code _email} write, as saving does:
 * a user who cannot send email has no footer to manage, and would only meet Save buttons that fail.
 *
 * @since 2026-10-07
 */
public final class ViewMyEmailFooter2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final EmailFooterService emailFooterService;

    public ViewMyEmailFooter2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterService.class));
    }

    // Package-private so tests can supply the collaborators.
    ViewMyEmailFooter2Action(SecurityInfoManager securityInfoManager, EmailFooterService emailFooterService) {
        this.securityInfoManager = securityInfoManager;
        this.emailFooterService = emailFooterService;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", "w", null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
        exposeSettings(request, emailFooterService.settingsFor(loggedInInfo.getLoggedInProviderNo()));
        return SUCCESS;
    }

    /** The page's request attributes; also used when a save is refused and the page is shown again. */
    static void exposeSettings(HttpServletRequest request, EmailFooterService.UserFooterSettings settings) {
        boolean followsClinicDefault = settings.ownFooter() == null;
        request.setAttribute("followsClinicDefault", followsClinicDefault);
        request.setAttribute("myFooter", followsClinicDefault ? settings.clinicDefault() : settings.ownFooter());
        request.setAttribute("clinicFooter", settings.clinicDefault());
        request.setAttribute("clinicChangeNotice", settings.clinicChangeNotice());
        // Sent back with a save, so a clinic change made after the page opened is not overwritten.
        request.setAttribute("clinicFooterShownFingerprint", EmailFooterService.fingerprint(settings.clinicDefault()));
        request.setAttribute("clinicChangeKeptOwnFooter", settings.keptOwnFooter());
    }
}
