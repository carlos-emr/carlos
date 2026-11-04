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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.BillingStatus;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService;

/**
 * Struts 2Action for Ontario billing deletion by billing number without appointment
 * (Billing History ▸ Unbill). Soft-deletes a billing record by setting its status to 'D'.
 *
 * <p>Whether a claim may still be unbilled is decided from the status <em>stored</em> on the
 * claim, never from the request: a claim that is billed (B) or settled (S) is refused with the
 * {@code cannotDelete} result. The Billing History page posts the claim's status as
 * {@code billCode}, but a hand-built POST can post any value, so this action ignores it
 * (issue #4538).</p>
 *
 * <p>Migrated from {@code billing/CA/ON/billingDeleteNoAppt.jsp}.
 *
 * @since 2026-04-08
 */
public class BillingDeleteNoAppt2Action extends ActionSupport {

    /** Struts result name, and the request attribute the cannot-delete JSP tests, for a refused unbill. */
    private static final String CANNOT_DELETE = "cannotDelete";

    private final SecurityInfoManager securityInfoManager;
    private final BillingDao billingDao;
    private final BillingCorrectionRecordService correctionPrep;

    public BillingDeleteNoAppt2Action(SecurityInfoManager securityInfoManager,
                                      BillingDao billingDao,
                                      BillingCorrectionRecordService correctionPrep) {
        this.securityInfoManager = securityInfoManager;
        this.billingDao = billingDao;
        this.correctionPrep = correctionPrep;
    }
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        String curUserNo = loggedInInfo.getLoggedInProviderNo();
        String billingNoStr = request.getParameter("billing_no");
        CarlosProperties props = CarlosProperties.getInstance();

        if (props.getProperty("isNewONbilling", "").equals("true")) {
            int headerId;
            try {
                headerId = Integer.parseInt(billingNoStr);
            } catch (NumberFormatException _) {
                // Not the exception: NumberFormatException's message repeats the raw parameter, which
                // LogSafe exists to keep out of the log unsanitized.
                MiscUtils.getLogger().error("Invalid billing_no: {}", LogSafe.sanitize(billingNoStr));
                return ERROR;
            }
            switch (correctionPrep.unbillClaim(headerId, curUserNo)) {
                case DELETED:
                    return SUCCESS;
                case REFUSED_BILLED_OR_SETTLED:
                    request.setAttribute(CANNOT_DELETE, Boolean.TRUE);
                    return CANNOT_DELETE;
                default:
                    return ERROR;
            }
        }

        // Legacy `billing` table (isNewONbilling=false): same rule, applied to the stored status.
        try {
            Billing b = billingDao.find(Integer.parseInt(billingNoStr));
            if (b == null) {
                return ERROR;
            }
            if (BillingStatus.isSubmittedOrSettled(b.getStatus())) {
                request.setAttribute(CANNOT_DELETE, Boolean.TRUE);
                return CANNOT_DELETE;
            }
            b.setStatus("D");
            billingDao.merge(b);
        } catch (NumberFormatException _) {
            // Not the exception: its message repeats the raw parameter (see the claim-header path).
            MiscUtils.getLogger().error("Invalid billing_no: {}", LogSafe.sanitize(billingNoStr));
            return ERROR;
        }

        return SUCCESS;
    }
}
