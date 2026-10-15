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
package io.github.carlos_emr.carlos.prevention.web;

import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Read-only gate for the National Vaccine Catalogue (NVC) administration page: shows what is
 * installed and, for administrators with write rights, offers the update button that posts to
 * {@link UpdateVaccineCatalogue2Action}.
 *
 * <p>Requires {@code _admin} read. The update itself is separately gated on {@code _admin}
 * write; {@code canUpdate} only decides whether the button is rendered.
 *
 * @since 2026-09-24
 */
public class ViewVaccineCatalogue2Action extends ActionSupport {

    /** Outcomes the update action may redirect back with; anything else is ignored. */
    static final Set<String> UPDATE_RESULTS = Set.of("updated", "failed", "unavailable");

    private final SecurityInfoManager securityInfoManager;
    private final CanadianVaccineCatalogueManager catalogueManager;

    public ViewVaccineCatalogue2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(CanadianVaccineCatalogueManager.class));
    }

    ViewVaccineCatalogue2Action(SecurityInfoManager securityInfoManager,
                                CanadianVaccineCatalogueManager catalogueManager) {
        this.securityInfoManager = securityInfoManager;
        this.catalogueManager = catalogueManager;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        List<CVCImmunization> immunizations = catalogueManager.getImmunizationList();
        long generics = immunizations.stream().filter(CVCImmunization::isGeneric).count();

        request.setAttribute("catalogueSourceUrl", CanadianVaccineCatalogueManager.getCVCURL());
        request.setAttribute("catalogueLastUpdated", catalogueManager.getLastUpdated());
        request.setAttribute("catalogueVersion", catalogueManager.getInstalledVersion());
        request.setAttribute("catalogueGenericCount", generics);
        request.setAttribute("catalogueTradenameCount", immunizations.size() - generics);
        request.setAttribute("canUpdate",
                securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null));

        String result = request.getParameter("result");
        if (result != null && UPDATE_RESULTS.contains(result)) {
            request.setAttribute("updateResult", result);
        }
        return SUCCESS;
    }
}
