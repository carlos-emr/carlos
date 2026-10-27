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

import io.github.carlos_emr.carlos.demographic.cdsimport.assembler.CdsSourceFieldsViewModelAssembler;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import java.util.Set;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Clinical category source values require patient read authorization independently of import operator access. */
@Component("viewCdsSourceFields2Action") @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ViewCdsSourceFields2Action extends ActionSupport {
    private final transient CdsSourceFieldsViewModelAssembler assembler;
    private int demographicNo; private String category, after="", reviewAfter="";
    public ViewCdsSourceFields2Action(CdsSourceFieldsViewModelAssembler assembler){this.assembler=assembler; }
    @Override public String execute() throws Exception {
        var request=ServletActionContext.getRequest(); var response=ServletActionContext.getResponse();
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))){response.setHeader("Allow", "GET, HEAD"); response.sendError(405); return NONE; }
        try {
            var actor=CdsWeb.authenticated(request); CdsWeb.parameters(request, Set.of("demographicNo", "category", "after", "reviewAfter"));
            request.setAttribute("model", assembler.assemble(actor, demographicNo, category, after, reviewAfter));
            response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff"); return SUCCESS;
        }catch (Exception failure){CdsWeb.failure(response, failure); return NONE; }
    }
    @StrutsParameter public void setDemographicNo(int value){demographicNo=value; }
    @StrutsParameter public void setCategory(String value){category=value; }
    @StrutsParameter public void setAfter(String value){after=value; }
    @StrutsParameter public void setReviewAfter(String value){reviewAfter=value; }
}
