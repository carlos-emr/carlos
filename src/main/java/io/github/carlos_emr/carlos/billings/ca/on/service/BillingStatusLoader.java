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

package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.util.LabelValueBean;

import java.util.ArrayList;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Read-only filter for the bill-status screens — turns the operator's form
 * inputs (bill types, status, provider, date range, demographic, visit
 * location, payment date range) into a query against the underlying
 * {@link BillingOnClaimLoader}, normalizing the "any" sentinels
 * ({@code "all"}, {@code "%"}, {@code "---"}, {@code "0000"}) to the
 * {@code null}-shaped wildcards the loader expects.
 *
 * <p>Web security is enforced at the action layer before invocation.</p>
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class BillingStatusLoader {
    private static final String ANY_PROVIDER = "all";
    private static final String ANY_STATUS_TYPE = "%";
    private static final String ANY_SERVICE_CODE = "%";
    private static final String ANY_BILLING_FORM = "---";
    public static final String ANY_VISIT_LOCATION = "0000";

    private final BillingOnClaimLoader claimLoader;

    BillingStatusLoader(BillingOnClaimLoader claimLoader) {
        this.claimLoader = claimLoader;
    }

    public List<BillingClaimHeaderDto> getBills(String[] billTypes, String statusType, String providerNo, String startDate, String endDate,
                                                  String demoNo, String visitLocation, String paymentStartDate, String paymentEndDate) {
        billTypes = billTypes == null || billTypes.length == 0 ? null : billTypes;
        statusType = statusType == null || statusType.length() == 0 || statusType.equals(ANY_STATUS_TYPE) ? null : statusType;
        providerNo = providerNo == null || providerNo.length() == 0 || providerNo.equals(ANY_PROVIDER) ? null : providerNo;
        startDate = startDate == null || startDate.length() == 0 ? null : startDate;
        endDate = endDate == null || endDate.length() == 0 ? null : endDate;
        demoNo = demoNo == null || demoNo.length() == 0 ? null : demoNo;
        visitLocation = visitLocation == null || visitLocation.length() == 0 || visitLocation.equals(ANY_VISIT_LOCATION) ? null : visitLocation;
        paymentStartDate = paymentStartDate == null || paymentStartDate.length() == 0 ? null : paymentStartDate;
        paymentEndDate = paymentEndDate == null || paymentEndDate.length() == 0 ? null : paymentEndDate;

        return claimLoader.getBill(billTypes, statusType, providerNo, startDate, endDate, demoNo, visitLocation, paymentStartDate, paymentEndDate);
    }


    public List<BillingClaimHeaderDto> getBills(String[] billType, String statusType, String providerNo, String startDate, String endDate,
                                                  String demoNo, String serviceCodeParams, String dx, String visitType, String billingForm, String visitLocation, String paymentStartDate, String paymentEndDate) {
        return getBillsWithSorting(billType, statusType, providerNo, startDate, endDate, demoNo, serviceCodeParams, dx, visitType, billingForm, visitLocation, null, null, paymentStartDate, paymentEndDate, null);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public List<BillingClaimHeaderDto> getBillsWithSorting(String[] billType, String statusType, String providerNo, String startDate, String endDate,
                                                             String demoNo, String serviceCodeParams, String dx, String visitType, String billingForm, String visitLocation, String sortName, String sortOrder,
                                                             String paymentStartDate, String paymentEndDate, String claimNo) {
        billType = billType == null || billType.length == 0 ? null : billType;
        statusType = statusType == null || statusType.length() == 0 || statusType.equals(ANY_STATUS_TYPE) ? null : statusType;
        providerNo = providerNo == null || providerNo.length() == 0 || providerNo.equals(ANY_PROVIDER) ? null : providerNo;
        startDate = startDate == null || startDate.length() == 0 ? null : startDate;
        endDate = endDate == null || endDate.length() == 0 ? null : endDate;
        demoNo = demoNo == null || demoNo.length() == 0 ? null : demoNo;
        dx = dx == null || dx.length() < 2 ? null : dx;
        visitType = visitType == null || visitType.length() < 2 ? null : visitType;
        serviceCodeParams = serviceCodeParams == null || serviceCodeParams.length() == 0 || serviceCodeParams.equals(ANY_SERVICE_CODE) ? null :
                serviceCodeParams.toUpperCase();
        billingForm = billingForm == null || billingForm.length() == 0 || billingForm.equals(ANY_BILLING_FORM) ? null : billingForm;
        visitLocation = visitLocation == null || visitLocation.length() == 0 || visitLocation.equals(ANY_VISIT_LOCATION) ? null : visitLocation;

        paymentStartDate = paymentStartDate == null || paymentStartDate.length() == 0 ? null : paymentStartDate;
        paymentEndDate = paymentEndDate == null || paymentEndDate.length() == 0 ? null : paymentEndDate;

        claimNo = claimNo == null || claimNo.length() == 0 ? null : claimNo;

        List<String> serviceCodeList = claimLoader.mergeServiceCodes(serviceCodeParams, billingForm);
        List<BillingClaimHeaderDto> retval = claimLoader.getBillWithSorting(billType, statusType, providerNo, startDate, endDate, demoNo, serviceCodeList, dx, visitType, visitLocation, sortName, sortOrder, paymentStartDate, paymentEndDate, claimNo);
        return retval;
    }


    public List<LabelValueBean> listBillingForms() {
        List<LabelValueBean> billingFormsList = claimLoader.listBillingForms();
        if (billingFormsList == null) billingFormsList = new ArrayList<LabelValueBean>();
        return billingFormsList;
    }
}
