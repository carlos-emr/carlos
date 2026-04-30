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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnHistoryViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimLoader;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Assembles {@link BillingOnHistoryViewModel} for {@code billingONHistory.jsp},
 * the patient billing-history popup.
 *
 * <p>Owns the inline {@code SpringUtils.getBean} lookups the legacy JSP
 * performed: {@link BillingONPaymentDao}, {@link BillingONCHeader1Dao},
 * {@link DemographicManager} (for patient name display) and
 * {@link SecurityInfoManager} (for the per-row {@code _billing w} edit-link
 * gate). Also computes the balance column for {@code PAT}-status bills,
 * the bill-type label, and the per-row "unbill" link visibility.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingOnHistoryViewModelAssembler {

    private final BillingONPaymentDao billingOnPaymentDao;
    private final BillingONCHeader1Dao bCh1Dao;
    private final DemographicManager demographicManager;
    private final SecurityInfoManager securityInfoManager;
    private final BillingOnClaimLoader claimQueryService;

    public BillingOnHistoryViewModelAssembler(BillingONPaymentDao billingOnPaymentDao,
                                  BillingONCHeader1Dao bCh1Dao,
                                  DemographicManager demographicManager,
                                  SecurityInfoManager securityInfoManager,
                                  BillingOnClaimLoader claimQueryService) {
        this.billingOnPaymentDao = billingOnPaymentDao;
        this.bCh1Dao = bCh1Dao;
        this.demographicManager = demographicManager;
        this.securityInfoManager = securityInfoManager;
        this.claimQueryService = claimQueryService;
    }

    /**
     * Build the billing-history view model for a demographic.
     *
     * @param loggedInInfo  the current session's {@link LoggedInInfo}
     * @param demographicNo the demographic_no request parameter (string;
     *                      not yet parsed)
     * @return populated view model. Empty rows when {@code demographicNo}
     *         doesn't resolve or the lookup fails.
     */
    public BillingOnHistoryViewModel assemble(LoggedInInfo loggedInInfo, String demographicNo) {
        String safeDemoNo = demographicNo == null ? "" : demographicNo;
        String patientDisplayName = resolvePatientName(loggedInInfo, safeDemoNo);

        boolean canEdit = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null);

        boolean warnOnDelete = CarlosProperties.getInstance()
                .getBooleanProperty("warnOnDeleteBill", "true");

        List<BillingOnHistoryViewModel.HistoryRow> rows = loadRows(safeDemoNo, canEdit);

        return BillingOnHistoryViewModel.builder()
                .demographicNo(safeDemoNo)
                .patientDisplayName(patientDisplayName)
                .warnOnDeleteBill(warnOnDelete)
                .rows(rows)
                .build();
    }

    private String resolvePatientName(LoggedInInfo loggedInInfo, String demographicNo) {
        if (demographicNo.isEmpty() || loggedInInfo == null) return "";
        try {
            Demographic demo = demographicManager.getDemographic(
                    loggedInInfo, Integer.parseInt(demographicNo));
            if (demo != null) {
                return nullToEmpty(demo.getLastName()) + ", "
                        + nullToEmpty(demo.getFirstName());
            }
        } catch (NumberFormatException e) {
            return "";
        } catch (RuntimeException ex) {
            MiscUtils.getLogger().warn(
                    "Could not look up demographic for billing history display", ex);
        }
        return "";
    }

    private List<BillingOnHistoryViewModel.HistoryRow> loadRows(String demographicNo, boolean canEdit) {
        List<BillingOnHistoryViewModel.HistoryRow> rows = new ArrayList<>();
        try {
            @SuppressWarnings("rawtypes")
            List aL = claimQueryService.getBillingHist(demographicNo, 10000, 0, null);
            for (int i = 0; i + 1 < aL.size(); i = i + 2) {
                BillingClaimHeaderDto obj = (BillingClaimHeaderDto) aL.get(i);
                BillingClaimItemDto itObj = (BillingClaimItemDto) aL.get(i + 1);

                String strBillType = obj.getPay_program();
                if (strBillType != null) {
                    if (strBillType.matches(BillingOnConstants.BILLINGMATCHSTRING_3RDPARTY)) {
                        if ("Settled".equals(BillingOnConstants.propBillingType
                                .getProperty(obj.getStatus(), ""))) {
                            strBillType += " Settled";
                        }
                    } else {
                        strBillType = BillingOnConstants.propBillingType
                                .getProperty(obj.getStatus(), "");
                    }
                } else {
                    strBillType = "";
                }

                boolean isPat = "PAT".equals(strBillType) || "PAT Settled".equals(strBillType);
                BigDecimal balance = BigDecimal.ZERO;
                if (isPat) {
                    try {
                        int billingNo = Integer.parseInt(obj.getId());
                        BillingONCHeader1 bCh1 = bCh1Dao.find(billingNo);
                        if (bCh1 != null && bCh1.getTotal() != null) {
                            BigDecimal total = bCh1.getTotal();
                            BigDecimal sumOfPay = BigDecimal.ZERO;
                            BigDecimal sumOfDiscount = BigDecimal.ZERO;
                            BigDecimal sumOfCredit = BigDecimal.ZERO;
                            for (BillingONPayment payment :
                                    billingOnPaymentDao.find3rdPartyPaymentsByBillingNo(billingNo)) {
                                sumOfPay = sumOfPay.add(payment.getTotal_payment());
                                sumOfDiscount = sumOfDiscount.add(payment.getTotal_discount());
                                sumOfCredit = sumOfCredit.add(payment.getTotal_credit());
                            }
                            balance = total.subtract(sumOfPay).subtract(sumOfDiscount).add(sumOfCredit);
                        }
                    } catch (NumberFormatException e) {
                        // billing id wasn't an int — leave balance at zero.
                        balance = BigDecimal.ZERO;
                    }
                }

                String status = nullToEmpty(obj.getStatus());
                // Legacy logic: hide unbill on already-billed/settled status.
                boolean unbillLinkShown = !(BillingONCHeader1.BILLED.equals(status) || BillingONCHeader1.SETTLED.equals(status));

                rows.add(new BillingOnHistoryViewModel.HistoryRow(
                        nullToEmpty(obj.getId()),
                        nullToEmpty(obj.getLast_name()) + ", " + nullToEmpty(obj.getFirst_name()),
                        nullToEmpty(obj.getBilling_date()),
                        strBillType,
                        nullToEmpty(itObj.getService_code()),
                        nullToEmpty(itObj.getDx()),
                        balance.toString(),
                        isPat,
                        nullToEmpty(obj.getTotal()),
                        status,
                        unbillLinkShown,
                        canEdit));
            }
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Error loading billing history", e);
        }
        return rows;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
