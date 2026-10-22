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
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.provider.web.DocumentDescriptionTemplateAccess;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Security gate for the document description template page.
 *
 * <p>The page shows one of two lists. With {@code setDefault=true} it is the clinic-wide list,
 * which needs {@code _admin} or {@code _admin.document} read; without it, it is the signed-in
 * provider's own list, which needs {@code _pref} write like the other Preferences editors, so a
 * provider who is not an administrator can open their own templates (issue #5057). The rules live
 * in {@link DocumentDescriptionTemplateAccess}; the page and its endpoint apply the same ones.</p>
 *
 * @since 2026-04-05
 */
public class DisplayDocumentDescriptionTemplate2Action extends ActionSupport {

    // ActionSupport is Serializable; the privilege service is not, and a per-request gate never is.
    private final transient SecurityInfoManager securityInfoManager;

    public DisplayDocumentDescriptionTemplate2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    DisplayDocumentDescriptionTemplate2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        DocumentDescriptionTemplateAccess.requireOpenPage(securityInfoManager, loggedInInfo,
                DocumentDescriptionTemplateAccess.isClinicListRequest(request));

        return SUCCESS;
    }
}
