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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import java.util.Map;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Old temporary basename links expire explicitly; they never become unrestricted durable-file lookups. */
@Component("legacyCdsImportLog2Action") @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class LegacyCdsImportLog2Action extends ActionSupport {
    private final transient CdsImportAccessService access;
    public LegacyCdsImportLog2Action(CdsImportAccessService access) { this.access = access; }
    @Override public String execute() throws Exception {
        var request = ServletActionContext.getRequest(); var response = ServletActionContext.getResponse();
        CdsWeb.authorize(request, access);
        CdsWeb.json(response, 410, Map.of("ok", false, "code", "IMPORT_LEGACY_LOG_LINK_EXPIRED",
                "message", "This temporary import-log link has expired. Open the durable run and download its protected reports."));
        return NONE;
    }
}
