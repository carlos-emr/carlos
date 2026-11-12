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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnHistoryViewModel;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnHistoryViewModelAssembler;

/**
 * View gate for {@code billing/CA/ON/billingONHistory.jsp}. Enforces
 * {@code _billing r} privilege and assembles a
 * {@link BillingOnHistoryViewModel} via {@link BillingOnHistoryViewModelAssembler}
 * so the JSP can render pre-resolved billing-history rows instead of doing
 * inline Spring lookups, balance arithmetic, and per-row privilege checks.
 *
 * @since 2026-04-13
 */
public class ViewBillingOnHistory2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final BillingOnHistoryViewModelAssembler billingONHistoryAssembler;

    public ViewBillingOnHistory2Action(SecurityInfoManager securityInfoManager,
                                        BillingOnHistoryViewModelAssembler billingONHistoryAssembler) {
        this.securityInfoManager = securityInfoManager;
        this.billingONHistoryAssembler = billingONHistoryAssembler;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingOnHistoryViewModel model = billingONHistoryAssembler
                .assemble(loggedInInfo, request.getParameter("demographic_no"));
        request.setAttribute("historyModel", model);
        auditHistoryRead(loggedInInfo, request.getParameter("demographic_no"));

        return SUCCESS;
    }

    /**
     * The patient a billing page acts on, written the one way the audit trail keys it.
     *
     * <p>The billing pages read {@code demographic_no} as an integer, so {@code 042} and
     * {@code +42} list patient 42. The audit row must name that same patient: a stricter pattern
     * here would show the patient's billing with no row at all.</p>
     *
     * @param demographicNo the {@code demographic_no} request parameter as received
     * @return the positive patient number in plain decimal, or {@code null} when the value names no patient
     */
    static String auditPatientNumber(String demographicNo) {
        try {
            int patient = Integer.parseInt(demographicNo == null ? "" : demographicNo.trim());
            return patient > 0 ? Integer.toString(patient) : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * Audits the opening of a patient's billing history: the page lists every invoice of the
     * patient (dates, codes, amounts), so it is a read of that patient's billing record. One row
     * for the page, keyed by the patient like the chart's own read rows, never one per invoice
     * listed. A request that names no patient lists nothing and is not audited.
     *
     * @param loggedInInfo the signed-in user (provider, security id and client address of the row)
     * @param demographicNo the {@code demographic_no} request parameter as received
     */
    static void auditHistoryRead(LoggedInInfo loggedInInfo, String demographicNo) {
        String patient = auditPatientNumber(demographicNo);
        if (loggedInInfo != null && patient != null) {
            LogAction.addLog(loggedInInfo, LogConst.READ, LogConst.CON_BILLING_HISTORY, patient, patient, null);
        }
    }
}
