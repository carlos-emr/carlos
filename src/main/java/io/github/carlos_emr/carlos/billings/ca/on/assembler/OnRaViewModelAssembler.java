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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnRaService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Assembles {@link OnRaViewModel} for {@code billing/CA/ON/onGenRA.jsp},
 * the Billing Reconciliation list page. Hoists the inline scriptlet logic the
 * JSP body used to perform: a privacy-filtered Rahd lookup that decides
 * between {@code getTeamRahd / getSiteRahd / getAllRahd} based on the
 * caller's {@code _team_billing_only}, {@code _team_access_privacy}, and
 * {@code _site_access_privacy} role privileges.
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class OnRaViewModelAssembler {

    private final SecurityInfoManager securityInfoManager;
    private final BillingOnRaService raService;

    public OnRaViewModelAssembler(SecurityInfoManager securityInfoManager,
                                BillingOnRaService raService) {
        this.securityInfoManager = securityInfoManager;
        this.raService = raService;
    }

    /**
     * Build the view model.
     *
     * @param request live request — supplies the session attribute {@code user}
     *                which selects the privacy-filtered list.
     */
    public OnRaViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        boolean isTeamBillingOnly = securityInfoManager.hasPrivilege(
                loggedInInfo, "_team_billing_only", "r", null);
        boolean isTeamAccessPrivacy = securityInfoManager.hasPrivilege(
                loggedInInfo, "_team_access_privacy", "r", null);
        boolean isSiteAccessPrivacy = securityInfoManager.hasPrivilege(
                loggedInInfo, "_site_access_privacy", "r", null);

        String user = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();

        List<Properties> raList;
        if (isTeamBillingOnly || isTeamAccessPrivacy) {
            raList = raService.getTeamRahd("D", user);
        } else if (isSiteAccessPrivacy) {
            raList = raService.getSiteRahd("D", user);
        } else {
            raList = raService.getAllRahd("D");
        }

        List<OnRaViewModel.Row> rows = new ArrayList<>(raList == null ? 0 : raList.size());
        if (raList != null) {
            for (Properties p : raList) {
                rows.add(new OnRaViewModel.Row(
                        p.getProperty("raheader_no", ""),
                        p.getProperty("readdate", ""),
                        p.getProperty("paymentdate", ""),
                        p.getProperty("payable", ""),
                        p.getProperty("claims", ""),
                        p.getProperty("records", ""),
                        p.getProperty("totalamount", ""),
                        p.getProperty("status", "")));
            }
        }

        return OnRaViewModel.builder().rows(rows).build();
    }
}
