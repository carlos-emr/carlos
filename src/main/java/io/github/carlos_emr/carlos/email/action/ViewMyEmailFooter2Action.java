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
import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Only the logged-in sender's personal default is editable; clinic content is read-only. */
public final class ViewMyEmailFooter2Action extends ActionSupport {
    private final SecurityInfoManager security;
    private final EmailFooterService personal;
    private final ClinicEmailFooterService clinic;

    public ViewMyEmailFooter2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterService.class),
                SpringUtils.getBean(ClinicEmailFooterService.class));
    }

    ViewMyEmailFooter2Action(SecurityInfoManager security, EmailFooterService personal,
            ClinicEmailFooterService clinic) {
        this.security = security; this.personal = personal; this.clinic = clinic;
    }

    @Override
    public String execute() {
        var request = ServletActionContext.getRequest();
        var user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!security.hasPrivilege(user, "_email", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
        expose(request, personal.ownFooter(user.getLoggedInProviderNo()), clinic);
        return SUCCESS;
    }

    static void expose(HttpServletRequest request, String footer, ClinicEmailFooterService clinic) {
        request.setAttribute("myFooter", footer);
        ClinicEmailFooterService.expose(request, clinic.snapshot());
    }
}
