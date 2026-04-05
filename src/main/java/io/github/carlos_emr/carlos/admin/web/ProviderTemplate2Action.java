/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.admin.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao;
import io.github.carlos_emr.carlos.commn.model.EncounterTemplate;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Manages encounter/provider templates in the admin module.
 *
 * <p>Handles create, edit, and delete operations on {@link EncounterTemplate} records,
 * then loads the full list of templates for display. Enforces POST for mutating operations
 * and requires {@code _newCasemgmt.templates w} privilege.</p>
 *
 * @since 2026-05-01
 */
public class ProviderTemplate2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private EncounterTemplateDao encounterTemplateDao = SpringUtils.getBean(EncounterTemplateDao.class);

    @Override
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.templates", "w", null)) {
            throw new SecurityException("missing required sec object (_newCasemgmt.templates)");
        }

        String dboperation = request.getParameter("dboperation");

        if ("POST".equalsIgnoreCase(request.getMethod())
                && ("Save".equalsIgnoreCase(dboperation) || "Delete".equalsIgnoreCase(dboperation))) {

            String templateName = request.getParameter("encountertemplate_name");
            if ("Save".equalsIgnoreCase(dboperation)) {
                String templateValue = request.getParameter("encountertemplate_value");
                EncounterTemplate existing = encounterTemplateDao.find(templateName);
                if (existing != null) {
                    existing.setEncounterTemplateValue(templateValue);
                    encounterTemplateDao.merge(existing);
                } else {
                    EncounterTemplate newTemplate = new EncounterTemplate();
                    newTemplate.setEncounterTemplateName(templateName);
                    newTemplate.setEncounterTemplateValue(templateValue);
                    newTemplate.setCreatorProviderNo(loggedInInfo.getLoggedInProviderNo());
                    encounterTemplateDao.persist(newTemplate);
                }
            } else {
                // Delete
                EncounterTemplate toDelete = encounterTemplateDao.find(templateName);
                if (toDelete != null) {
                    encounterTemplateDao.remove(toDelete);
                }
            }
        }

        // Load the specific template for Edit mode (GET or POST with Edit)
        if ("Edit".equalsIgnoreCase(dboperation)) {
            String templateName = request.getParameter("encountertemplate_name");
            EncounterTemplate editTemplate = encounterTemplateDao.find(templateName);
            request.setAttribute("editTemplate", editTemplate);
        }

        // Always load all templates
        List<EncounterTemplate> allTemplates = encounterTemplateDao.findAll();
        request.setAttribute("allTemplates", allTemplates);
        request.setAttribute("curUser_no", loggedInInfo.getLoggedInProviderNo());
        request.setAttribute("dboperation", dboperation);

        return SUCCESS;
    }
}
