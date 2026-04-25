/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Thin Struts2 gate for the Ontario billing correction workflow. Mapped at
 * {@code billing/CA/ON/BillingONCorrection} in {@code struts-billing.xml}.
 *
 * <p>This action is the URL endpoint that {@code billingONCorrection.jsp}'s
 * form posts to, plus the GET-load entry from {@code billingONHistory.jsp}
 * / {@code billingONStatus.jsp} popups. All business logic lives in
 * {@link BillingCorrectionService} (~700 lines of header / item update,
 * 3rd-party payment posting, change-detection); the action stays a thin
 * gate that:</p>
 *
 * <ol>
 *   <li>Enforces session presence + {@code _billing w} privilege.</li>
 *   <li>Builds the read-side view model via
 *       {@link BillingONCorrectionDataAssembler}.</li>
 *   <li>Dispatches to the right service method based on the {@code method}
 *       request parameter (legacy URL contract).</li>
 *   <li>Returns the Struts result string the service produced.</li>
 * </ol>
 *
 * <p>The legacy {@code request.getParameter("method")} dispatch remains
 * because the JSP form posts to one URL with a hidden {@code method} field,
 * preserving the URL contract callers depend on. Splitting the URL would
 * require coordinated JSP + caller changes outside this PR's scope.</p>
 *
 * <p>Pre-refactor, this class held all the helpers ({@code updateInvoice},
 * {@code add3rdPartyPayment}, {@code updateBillingONCHeader1},
 * {@code updateBillingItems}, {@code hasInvoiceChanged}) directly — 753
 * lines. The extraction to {@link BillingCorrectionService} brings the
 * action to ~120 lines and makes the business logic independently
 * testable (the service has no Struts / servlet-context coupling beyond
 * the {@link HttpServletRequest} parameter).</p>
 *
 * @since 2026-04-25
 */
public class BillingCorrection2Action extends ActionSupport {

    // Dual-constructor DI: SpringUtils.getBean confined to the no-arg ctor.
    // Tests use the package-private constructor with mocks.
    private final SecurityInfoManager securityInfoManager;
    private final BillingONCorrectionDataAssembler assembler;
    private final BillingCorrectionService service;

    /** Production constructor used by Struts2's Spring object factory. */
    public BillingCorrection2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
             new BillingONCorrectionDataAssembler(),
             new BillingCorrectionService());
    }

    /** Test-friendly constructor — call with mocks. Package-private. */
    BillingCorrection2Action(SecurityInfoManager securityInfoManager,
                             BillingONCorrectionDataAssembler assembler,
                             BillingCorrectionService service) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
        this.service = service;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Reject sessionless requests up front. SecurityInfoManagerImpl.hasPrivilege
        // dereferences loggedInInfo and emits an internal ERROR log on null, so
        // null-checking here keeps the log signal clean for real privilege denials.
        // Matches the pattern in ViewBillingON2Action / ViewBillingONReview2Action /
        // ViewBillingONStatus2Action / ViewBillingShortcutPg12Action.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // Build the view model up front and stash on the request so every
        // result that forwards to billingONCorrection.jsp (success,
        // closeReload, adminReload, loadOnly) sees a populated correctionModel.
        request.setAttribute("correctionModel", assembler.assemble(loggedInInfo, request));

        // Method-param dispatch — preserves the legacy URL contract where
        // billingONCorrection.jsp's form posts to /BillingONCorrection with
        // a hidden method=updateInvoice field. The add3rdPartyPayment branch
        // is reachable via direct URL manipulation; keep it functional even
        // though no current UI surface posts that method.
        if ("add3rdPartyPayment".equals(request.getParameter("method"))) {
            return service.addThirdPartyPayment(loggedInInfo, request);
        }
        return service.updateInvoice(loggedInInfo, request);
    }
}
