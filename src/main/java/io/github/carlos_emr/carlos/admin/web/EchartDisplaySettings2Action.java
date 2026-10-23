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
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.chartspace.ChartSpaceLayout;
import io.github.carlos_emr.carlos.chartspace.ChartSpaceLayoutService;
import io.github.carlos_emr.carlos.commn.dao.OceanSettingDao;
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.List;
import java.util.Optional;

/**
 * Security gate for the eChart Display Settings admin page.
 *
 * <p>Requires {@code _admin} read privilege before forwarding to the JSP, matching
 * {@link BillingSettings2Action}. The finer-grained {@code _admin.encounter} object
 * referenced by the eChart nav section in {@code leftNav.jspf} has no grants in seed
 * security data (it gates nav visibility only, in an OR list with {@code _admin}), so
 * gating this action on it would lock out every role, admins included.
 * POST requires write access before changing the preference.</p>
 *
 * <p>Also edits the ChartSpace clinic default layout: the submitted id is validated against
 * {@link ChartSpaceLayout} first, an invalid id writes nothing, and the saving is delegated to
 * {@link ChartSpaceLayoutService}.</p>
 *
 * @since 2026-09-18
 */
public class EchartDisplaySettings2Action extends ActionSupport {

    private static final SystemPreferences.ECHART_PREFERENCE_KEYS LAYOUT_KEY =
            SystemPreferences.ECHART_PREFERENCE_KEYS.chartspace_default_layout;

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        String method = request.getMethod();
        boolean saveIntent = "Save".equals(request.getParameter("dboperation"));
        if ((!"GET".equals(method) && !"HEAD".equals(method) && !"POST".equals(method))
                || (saveIntent && !"POST".equals(method))) {
            ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            ServletActionContext.getResponse().setHeader("Allow", saveIntent ? "POST" : "GET, HEAD, POST");
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        String requiredPrivilege = "POST".equals(request.getMethod()) ? "w" : "r";
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", requiredPrivilege, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        var key = SystemPreferences.ECHART_PREFERENCE_KEYS.echart_show_ocean;
        ChartSpaceLayoutService layoutService = SpringUtils.getBean(ChartSpaceLayoutService.class);
        boolean saveRequested = "POST".equals(method) && saveIntent;
        // Validate the layout against the allow-list BEFORE any write, so an invalid value
        // saves nothing (neither Ocean nor ChartSpace) and is never echoed back to the page.
        String submittedLayout = saveRequested ? request.getParameter(LAYOUT_KEY.name()) : null;
        Optional<ChartSpaceLayout> layoutToSave = ChartSpaceLayout.fromId(submittedLayout);
        boolean layoutInvalid = submittedLayout != null && layoutToSave.isEmpty();
        boolean saved = saveRequested && !layoutInvalid;
        boolean displayOcean;
        if (saved) {
            displayOcean = "true".equals(request.getParameter(key.name()));
            SpringUtils.getBean(OceanSettingDao.class).saveDisplayPreference(displayOcean,
                    loggedInInfo.getLoggedInProviderNo());
            // After Ocean: its committed write is what makes the SystemPreferences table non-empty
            // (see ChartSpaceLayoutService#saveClinicDefault). The two saves are not atomic.
            layoutToSave.ifPresent(layoutService::saveClinicDefault);
        } else {
            var preference = SpringUtils.getBean(SystemPreferencesDao.class).findPreferenceByName(key);
            displayOcean = preference == null || preference.getValueAsBoolean();
        }
        request.setAttribute("displayOceanUI", displayOcean);
        request.setAttribute("saved", saved);
        request.setAttribute("layoutInvalid", layoutInvalid);
        // A database error while reading propagates: never show an invented value the admin could re-save.
        request.setAttribute("chartSpaceLayout", layoutToSave.orElseGet(layoutService::loadClinicDefault));
        request.setAttribute("chartSpaceLayouts", List.of(ChartSpaceLayout.values()));
        return SUCCESS;
    }
}
