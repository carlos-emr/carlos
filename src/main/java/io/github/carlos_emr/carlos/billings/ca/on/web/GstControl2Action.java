/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
/*
 * GstControl2Action.java
 *
 * Created on July 18, 2007, 12:08 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.math.BigDecimal;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.service.GstSettingsService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GstControlViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.action.ServletRequestAware;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
/**
 * Admin form gate for the GST percent setting: GET renders the current
 * percent (via {@link GstControlViewModel}), POST parses the submitted
 * percent and persists it through {@link GstSettingsService}. Requires
 * {@code _admin.billing w}.
 */
public class GstControl2Action extends ActionSupport implements ServletRequestAware {
    private HttpServletRequest request;

    private final SecurityInfoManager securityInfoManager;
    private final GstSettingsService gstSettingsService;

    public GstControl2Action(SecurityInfoManager securityInfoManager,
                             GstSettingsService gstSettingsService) {
        this.securityInfoManager = securityInfoManager;
        this.gstSettingsService = gstSettingsService;
    }

    @Override
    public void withServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        String submittedPercent = this.getGstPercent();
        if (submittedPercent != null) {
            submittedPercent = submittedPercent.trim();
        }
        if (submittedPercent != null && !submittedPercent.isEmpty()
                && "POST".equalsIgnoreCase(request.getMethod())) {
            try {
                gstSettingsService.setCurrentPercent(new BigDecimal(submittedPercent));
            } catch (NumberFormatException e) {
                addActionError("Invalid GST percent.");
            }
        }

        // Always re-read the persisted value so the JSP renders the source-of-
        // truth (post-save value when this came in as a POST, or the existing
        // value on a fresh GET).
        BigDecimal current = gstSettingsService.getCurrentPercent();
        String currentPercent = current == null ? "" : current.toPlainString();
        request.setAttribute("gstControlModel", new GstControlViewModel(currentPercent));

        return SUCCESS;
    }

    String gstPercent;

    public String getGstPercent() {
        return gstPercent;
    }

    @StrutsParameter
    public void setGstPercent(String gstPercent) {
        this.gstPercent = gstPercent;
    }
}
