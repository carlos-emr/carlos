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
package io.github.carlos_emr.carlos.documentManager.gate;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * View gate for document-manager JSPs that render add/edit/upload forms.
 * Requires {@code _edoc w}. GET-renderable (the JSPs themselves are
 * interactive forms); the underlying save endpoints
 * ({@code addEditDocument}, {@code addEditHtml}, {@code documentUpload}) are
 * already separate {@code .do} actions with their own writes.
 */
public final class ViewDocumentWrite2Action extends ActionSupport {

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        SecurityInfoManager sim = SpringUtils.getBean(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !sim.hasPrivilege(loggedInInfo, "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc w)");
        }
        return SUCCESS;
    }
}
