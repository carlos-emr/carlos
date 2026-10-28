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
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import io.github.carlos_emr.carlos.demographic.cdsimport.assembler.CdsImportViewModelAssembler;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Preserves the existing bookmark/menu route; stale synchronous clients never reach the old writer. */
@Component("viewCdsImport2Action") @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ViewCdsImport2Action extends ActionSupport {
    private final transient CdsImportAccessService access;
    private final transient CdsImportViewModelAssembler assembler;
    public ViewCdsImport2Action(CdsImportAccessService access, CdsImportViewModelAssembler assembler) {
        this.access = access; this.assembler = assembler;
    }
    @Override public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        var actor = CdsWeb.authorize(request, access);
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            CdsWeb.json(response, 409, Map.of("ok", false, "code", "IMPORT_CLIENT_REFRESH_REQUIRED",
                    "message", "Reload Demographic Import before uploading. The previous synchronous upload protocol is no longer accepted."));
            return NONE;
        }
        response.setHeader("Cache-Control", "no-store");
        request.setAttribute("model", assembler.assemble(actor));
        return SUCCESS;
    }
}
