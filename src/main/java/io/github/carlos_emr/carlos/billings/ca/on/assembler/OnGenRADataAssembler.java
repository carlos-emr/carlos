/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.DocumentBean;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONRemittanceAdviceService;
import io.github.carlos_emr.carlos.billings.ca.on.data.OnGenRAViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles {@link OnGenRAViewModel} for {@code billing/CA/ON/onGenRA.jsp},
 * the Billing Reconciliation list page. Hoists the inline scriptlet logic the
 * JSP body used to perform: optional RA-file import (when {@code documentBean}
 * carries a filename), then a privacy-filtered Rahd lookup that decides
 * between {@code getTeamRahd / getSiteRahd / getAllRahd} based on the
 * caller's {@code _team_billing_only}, {@code _team_access_privacy}, and
 * {@code _site_access_privacy} role privileges.
 *
 * @since 2026-04-25
 */
public final class OnGenRADataAssembler {

    private final SecurityInfoManager securityInfoManager;

    public OnGenRADataAssembler() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    OnGenRADataAssembler(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Build the view model.
     *
     * @param request live request — supplies the optional {@code documentBean}
     *                request-scope bean (whose {@code filename} property
     *                triggers an RA file import) and the session attribute
     *                {@code user} which selects the privacy-filtered list.
     */
    public OnGenRAViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        // Optional file-import side-effect — preserves the legacy JSP
        // contract where the JSP would import an RA file when the
        // request-scope {@code documentBean} carried a filename.
        Object dbAttr = request.getAttribute("documentBean");
        if (dbAttr instanceof DocumentBean documentBean) {
            String filename = documentBean.getFilename();
            if (filename != null && !filename.isEmpty()) {
                try {
                    String filepath = CarlosProperties.getInstance()
                            .getProperty("DOCUMENT_DIR", "").trim();
                    SpringUtils.getBean(BillingONRemittanceAdviceService.class).importRAFile(filepath + filename);
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Failed to import RA file: " + filename, e);
                }
            }
        }

        boolean isTeamBillingOnly = securityInfoManager.hasPrivilege(
                loggedInInfo, "_team_billing_only", "r", null);
        boolean isTeamAccessPrivacy = securityInfoManager.hasPrivilege(
                loggedInInfo, "_team_access_privacy", "r", null);
        boolean isSiteAccessPrivacy = securityInfoManager.hasPrivilege(
                loggedInInfo, "_site_access_privacy", "r", null);

        String user = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();
        BillingONRemittanceAdviceService dbObj = SpringUtils.getBean(BillingONRemittanceAdviceService.class);

        List<Properties> raList;
        if (isTeamBillingOnly || isTeamAccessPrivacy) {
            raList = dbObj.getTeamRahd("D", user);
        } else if (isSiteAccessPrivacy) {
            raList = dbObj.getSiteRahd("D", user);
        } else {
            raList = dbObj.getAllRahd("D");
        }

        List<OnGenRAViewModel.Row> rows = new ArrayList<>(raList == null ? 0 : raList.size());
        if (raList != null) {
            for (Properties p : raList) {
                rows.add(new OnGenRAViewModel.Row(
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

        return OnGenRAViewModel.builder().rows(rows).build();
    }
}
