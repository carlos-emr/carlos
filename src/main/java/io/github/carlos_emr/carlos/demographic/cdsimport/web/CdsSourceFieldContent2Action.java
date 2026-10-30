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

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalSourceService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Set;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Original logical value download uses exact patient/category/opaque field ownership and bounded streaming. */
@Component("cdsSourceFieldContent2Action") @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CdsSourceFieldContent2Action extends ActionSupport {
    private final transient SecurityInfoManager securityInfoManager;
    private final transient CdsClinicalSourceService source;
    private int demographicNo; private String category, fieldId;
    private final transient CdsWebService web;
    public CdsSourceFieldContent2Action(CdsWebService web, CdsClinicalSourceService source, SecurityInfoManager securityInfoManager){
        this.web = web;
        this.securityInfoManager = securityInfoManager; this.source=source; }
    @Override public String execute() throws Exception {
        var request=ServletActionContext.getRequest(); var response=ServletActionContext.getResponse();
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))){response.setHeader("Allow", "GET, HEAD"); response.sendError(405); return NONE; }
        LoggedInInfo loggedInInfo = request.getSession(false) == null ? null
                : LoggedInInfo.getLoggedInInfoFromSession(request.getSession(false));
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", (String) null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        try {
            var actor=web.authenticated(request); web.readOnlyParameters(request, Set.of("demographicNo", "category", "fieldId"));
            var field=source.original(actor, demographicNo, category, fieldId);
            response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy", "sandbox"); response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"cds-source-field-"+field.id()+".bin\"");
            response.setContentLengthLong(source.contentLength(field));
            if (!"HEAD".equals(request.getMethod()))source.writeOriginal(field, response.getOutputStream());
        }catch (Exception failure){web.failure(response, failure); }
        return NONE;
    }
    @StrutsParameter public void setDemographicNo(int value){demographicNo=value; }
    @StrutsParameter public void setCategory(String value){category=value; }
    @StrutsParameter public void setFieldId(String value){fieldId=value; }
}
