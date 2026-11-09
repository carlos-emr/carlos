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
package io.github.carlos_emr.carlos.demographic.cdsexport.web;

import io.github.carlos_emr.carlos.demographic.cdsexport.assembler.CdsExportViewModelAssembler;
import java.util.Set;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** GET page composition never performs export source work. */
@Component("viewCdsExport2Action")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ViewCdsExport2Action extends ActionSupport {
    private final transient SecurityInfoManager securityInfoManager;
    private final transient CdsExportWebService web;
    private final transient CdsExportViewModelAssembler assembler;

    public ViewCdsExport2Action(
            CdsExportWebService web,
            CdsExportViewModelAssembler assembler,
            SecurityInfoManager securityInfoManager) {
        this.web = web;
        this.assembler = assembler;
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        if (!Set.of("GET", "HEAD").contains(request.getMethod())) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(405);
            return NONE;
        }
        var session = request.getSession(false);
        var viewer = session == null ? null : LoggedInInfo.getLoggedInInfoFromSession(session);
        if (!securityInfoManager.hasPrivilege(viewer, "_demographic", "r", (String) null)
                || !(securityInfoManager.hasPrivilege(viewer, "_admin", "w", (String) null)
                        || securityInfoManager.hasPrivilege(
                                viewer, "_cds_export", "w", (String) null))) {
            throw new SecurityException("CDS_EXPORT_ACCESS_DENIED");
        }
        var actor = web.authorize(request);
        web.parameters(request, Set.of("runId"), true);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        request.setAttribute("model", assembler.assemble(actor));
        return SUCCESS;
    }
}
