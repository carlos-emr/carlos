/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleApplyResult;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleChange;
import io.github.carlos_emr.carlos.billings.ca.on.service.FeeScheduleImportService;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleSelectedChange;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
/**
 * Apply step for the OHIP fee-schedule import flow: takes the operator's
 * {@link FeeScheduleSelectedChange} picks from the preview screen
 * ({@code ScheduleOfBenefitsUpload2Action}) and runs them through
 * {@link FeeScheduleImportService}, returning a {@link FeeScheduleApplyResult}
 * for the JSP to render row-level success / failure. Requires
 * {@code _admin.billing w}.
 */
public class ScheduleOfBenefitsUpdate2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final FeeScheduleImportService feeScheduleImportService;

    public ScheduleOfBenefitsUpdate2Action(SecurityInfoManager securityInfoManager,
                                           FeeScheduleImportService feeScheduleImportService) {
        this.securityInfoManager = securityInfoManager;
        this.feeScheduleImportService = feeScheduleImportService;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() throws java.io.IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        // POST-only — fee-schedule application mutates billingservice rows; a
        // GET request must not trigger applySelected/applyAll.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        boolean forceUpdate = request.getAttribute("forceUpdate") == null ? "true".equals(request.getParameter("forceUpdate")) : (Boolean) request.getAttribute("forceUpdate");

        if (forceUpdate) {
            FeeScheduleApplyResult result = feeScheduleImportService.applyAll(feeScheduleChanges(request));
            request.setAttribute("changes", result.viewMaps());
            request.setAttribute("validationErrors", result.validationErrors());
            request.setAttribute("warnings", null);
        } else {
            String[] changes = request.getParameterValues("change");
            if (changes != null) {
                List<FeeScheduleSelectedChange> selectedChanges = new ArrayList<>();
                List<String> parseErrors = new ArrayList<>();
                MiscUtils.getLogger().debug("changes #" + changes.length);

                for (int i = 0; i < changes.length; i++) {
                    try {
                        selectedChanges.add(FeeScheduleSelectedChange.fromSubmittedValue(changes[i]));
                    } catch (IllegalArgumentException e) {
                        String error = "Invalid selected fee schedule change at row " + (i + 1);
                        parseErrors.add(error);
                        MiscUtils.getLogger().warn(error, e);
                    }
                }
                if (!parseErrors.isEmpty()) {
                    request.setAttribute("changes", List.of());
                    request.setAttribute("validationErrors", parseErrors);
                    return SUCCESS;
                }
                FeeScheduleApplyResult result = feeScheduleImportService.applySelected(selectedChanges);
                request.setAttribute("changes", result.viewMaps());
                request.setAttribute("validationErrors", result.validationErrors());
            }
        }
        return SUCCESS;
    }

    private List<FeeScheduleChange> feeScheduleChanges(HttpServletRequest request) {
        Object typedChanges = request.getAttribute("feeScheduleChanges");
        if (typedChanges instanceof List<?> list && (list.isEmpty() || list.get(0) instanceof FeeScheduleChange)) {
            return (List<FeeScheduleChange>) list;
        }

        List<FeeScheduleChange> changes = new ArrayList<>();
        Object warnings = request.getAttribute("warnings");
        if (warnings instanceof List<?> list) {
            for (Object warning : list) {
                if (warning instanceof Map<?, ?> map) {
                    changes.add(FeeScheduleChange.fromWarningMap(map));
                }
            }
        }
        return changes;
    }

}
