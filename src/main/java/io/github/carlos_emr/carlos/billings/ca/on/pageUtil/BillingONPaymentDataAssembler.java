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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONPaymentViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingONPremium;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.service.BillingONService;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles {@link BillingONPaymentViewModel} for {@code billingONPayment.jsp}.
 * Owns the 9 inline {@code SpringUtils.getBean} lookups the JSP used to perform
 * across its scriptlet body, exposing structured records (RA rows, premium rows,
 * 3rd-party bill rows with nested item + payment lists) instead of raw entities.
 *
 * <p>Pure read: privilege flags + DAO lookups → DTO. Instantiated by
 * {@link BillingONPayment2Action} on each request.</p>
 *
 * <p>The assembler is intentionally large (≈300 lines) because the
 * report aggregates 5 separate result sets and computes per-row
 * derived state (alternating row colors, "first row of bill" flags,
 * outstanding balances). Keeping that off the JSP is the entire goal
 * of this round.</p>
 *
 * @since 2026-04-26
 */
public final class BillingONPaymentDataAssembler {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final ProviderDao providerDao;
    private final RaDetailDao raDetailDao;
    private final BillingONCHeader1Dao bCh1Dao;
    private final BillingONPremiumDao bPremiumDao;
    private final BillingONPaymentDao bPaymentDao;
    private final BillingOnItemPaymentDao bItemPaymentDao;
    private final DemographicDao demographicDao;
    private final BillingONService billingONService;

    /**
     * Production constructor used by Struts; resolves dependencies from the
     * Spring context via {@link SpringUtils#getBean}. Tests use the
     * package-private constructor below to inject mocks directly.
     */
    public BillingONPaymentDataAssembler() {
        this(SpringUtils.getBean(ProviderDao.class),
             SpringUtils.getBean(RaDetailDao.class),
             SpringUtils.getBean(BillingONCHeader1Dao.class),
             SpringUtils.getBean(BillingONPremiumDao.class),
             SpringUtils.getBean(BillingONPaymentDao.class),
             SpringUtils.getBean(BillingOnItemPaymentDao.class),
             SpringUtils.getBean(DemographicDao.class),
             SpringUtils.getBean(BillingONService.class));
    }

    BillingONPaymentDataAssembler(ProviderDao providerDao,
                                  RaDetailDao raDetailDao,
                                  BillingONCHeader1Dao bCh1Dao,
                                  BillingONPremiumDao bPremiumDao,
                                  BillingONPaymentDao bPaymentDao,
                                  BillingOnItemPaymentDao bItemPaymentDao,
                                  DemographicDao demographicDao,
                                  BillingONService billingONService) {
        this.providerDao = providerDao;
        this.raDetailDao = raDetailDao;
        this.bCh1Dao = bCh1Dao;
        this.bPremiumDao = bPremiumDao;
        this.bPaymentDao = bPaymentDao;
        this.bItemPaymentDao = bItemPaymentDao;
        this.demographicDao = demographicDao;
        this.billingONService = billingONService;
    }

    /**
     * Build the payment-report view model for the given request.
     *
     * @param request in-flight request (provides locale + filter params)
     * @param curProviderNo logged-in provider's ID (from session)
     * @param isThisProviderOnly true when {@code _admin.invoices} grants
     *                           but {@code _admin}/{@code _admin.billing} don't
     *                           — restricts the dropdown to just the user
     * @param isTeamBillingOnly true when {@code _team_billing_only} grants
     *                          — restricts to billable providers on the team
     * @return populated view model. {@link BillingONPaymentViewModel#isReportRendered()}
     *         is false when the form has no provider selected yet, in which
     *         case all three reports stay empty.
     * @throws SecurityException when {@code isThisProviderOnly} is set but
     *         the logged-in provider has no OHIP number — the JSP-era code
     *         redirected to {@code /noRights.html}; tests/callers must
     *         translate this exception as needed
     */
    public BillingONPaymentViewModel assemble(HttpServletRequest request,
                                              String curProviderNo,
                                              boolean isThisProviderOnly,
                                              boolean isTeamBillingOnly) {
        Locale locale = request.getLocale();
        Calendar cal = Calendar.getInstance();
        String today = DateUtils.formatDate(cal, locale);

        String startDateStr = request.getParameter("startDateText");
        if (startDateStr == null || startDateStr.isEmpty()) {
            cal.add(Calendar.MONTH, -1);
            startDateStr = DateUtils.formatDate(cal, locale);
        }
        String endDateStr = request.getParameter("endDateText");
        if (endDateStr == null || endDateStr.isEmpty()) {
            endDateStr = today;
        }

        String errorMsg = "";
        Date startDate = null;
        Date endDate = null;
        try {
            startDate = DateUtils.parseDate(startDateStr, locale);
            endDate = DateUtils.parseDate(endDateStr, locale);
            if (DateUtils.calculateDayDifference(startDate, endDate) < 0) {
                errorMsg = LocaleUtils.getMessage(locale, "oscar.billing.paymentReceived.errorEndDateGreater");
            }
        } catch (ParseException e) {
            errorMsg = LocaleUtils.getMessage(locale, "oscar.billing.paymentReceived.errorOnDate");
        }

        Provider currentProvider = providerDao.getProvider(curProviderNo);
        List<Provider> pList = resolveProviderDropdown(currentProvider, isThisProviderOnly, isTeamBillingOnly);

        List<BillingONPaymentViewModel.ProviderEntry> providerOptions = new ArrayList<>();
        for (Provider p : pList) {
            providerOptions.add(new BillingONPaymentViewModel.ProviderEntry(
                    nullToEmpty(p.getProviderNo()),
                    nullToEmpty(p.getLastName()) + ", " + nullToEmpty(p.getFirstName())));
        }

        // Reformat dates back through the same formatter so the rendered
        // input value comes from the parsed Date (not raw user input). When
        // parse failed, fall back to the raw strings so the form still
        // round-trips what the user typed.
        String startDateRender = startDate == null ? startDateStr : DateUtils.formatDate(startDate, locale);
        String endDateRender = endDate == null ? endDateStr : DateUtils.formatDate(endDate, locale);

        String providerNo = request.getParameter("providerList");
        BillingONPaymentViewModel.Builder b = BillingONPaymentViewModel.builder()
                .providerOptions(providerOptions)
                .allProvidersOption(pList.size() > 1)
                .selectedProviderNo(providerNo)
                .startDateStr(startDateRender)
                .endDateStr(endDateRender)
                .today(today)
                .errorMsg(errorMsg)
                .isThisProviderOnly(isThisProviderOnly);

        if (!errorMsg.isEmpty() || providerNo == null) {
            return b.reportRendered(false).build();
        }

        b.reportRendered(true);

        // RA pulls "the last month" relative to endDate (not the raw filter).
        Calendar raStart = Calendar.getInstance();
        Calendar raEnd = Calendar.getInstance();
        raStart.setTime(endDate);
        raEnd.setTime(endDate);
        raStart.set(Calendar.DATE, raStart.getActualMinimum(Calendar.DATE));
        raEnd.set(Calendar.DATE, raEnd.getActualMaximum(Calendar.DATE));

        List<RaDetail> raList;
        List<BillingONCHeader1> ptList;
        List<BillingONPremium> premiumList;
        if (providerNo.isEmpty()) {
            raList = raDetailDao.getRaDetailByDate(raStart.getTime(), raEnd.getTime(), locale);
            ptList = bCh1Dao.get3rdPartyInvoiceByDate(startDate, endDate, locale);
            premiumList = bPremiumDao.getActiveRAPremiumsByPayDate(startDate, endDate, locale);
        } else {
            Provider p = providerDao.getProvider(providerNo);
            raList = raDetailDao.getRaDetailByDate(p, raStart.getTime(), raEnd.getTime(), locale);
            ptList = bCh1Dao.get3rdPartyInvoiceByProvider(p, startDate, endDate, locale);
            premiumList = bPremiumDao.getActiveRAPremiumsByProvider(p, startDate, endDate, locale);
        }

        buildRaReport(b, raList, providerNo);
        buildPremiumReport(b, premiumList, locale);
        buildThirdPartyReport(b, ptList, startDate, endDate, locale);

        BigDecimal raPaidTotal = new BigDecimal(b.build().getRaPaidTotal());
        BigDecimal threePaid = new BigDecimal(b.build().getThirdPartyPaidTotal());
        BigDecimal threeRefund = new BigDecimal(b.build().getThirdPartyRefundedTotal());
        BigDecimal premiumTotal = new BigDecimal(b.build().getPremiumTotal());
        BigDecimal finalAmt = raPaidTotal.add(threePaid).subtract(threeRefund).add(premiumTotal);
        b.finalTotal(finalAmt.toPlainString());

        return b.build();
    }

    private List<Provider> resolveProviderDropdown(Provider currentProvider,
                                                   boolean isThisProviderOnly,
                                                   boolean isTeamBillingOnly) {
        if (isThisProviderOnly) {
            // _admin.invoices grants access to *only* the user's own
            // invoices. The pre-refactor JSP redirected to /noRights.html
            // when the logged-in provider had no OHIP number — preserved
            // here as an exception so the action layer can translate.
            if (currentProvider == null
                    || currentProvider.getOhipNo() == null
                    || currentProvider.getOhipNo().isEmpty()) {
                throw new SecurityException("Provider has no OHIP number; cannot view invoices");
            }
            List<Provider> only = new ArrayList<>();
            only.add(currentProvider);
            return only;
        }
        if (isTeamBillingOnly && currentProvider != null) {
            return providerDao.getBillableProvidersOnTeam(currentProvider);
        }
        return providerDao.getBillableProviders();
    }

    private void buildRaReport(BillingONPaymentViewModel.Builder b,
                               List<RaDetail> raList,
                               String providerNo) {
        BigDecimal feeTotal = ZERO;
        BigDecimal claimTotal = ZERO;
        BigDecimal paidTotal = ZERO;
        BigDecimal adjTotal = ZERO;
        int numItems = 0;

        List<BillingONPaymentViewModel.RaReportRow> rows = new ArrayList<>();
        if (raList == null) {
            b.raRows(rows).raItemCount(0)
                    .raFeeTotal(feeTotal.toPlainString())
                    .raClaimTotal(claimTotal.toPlainString())
                    .raPaidTotal(paidTotal.toPlainString())
                    .raAdjTotal(adjTotal.toPlainString());
            return;
        }

        String rowColor = "myWhite";
        int curBillingNo = 0;
        BillingONItem prevItem = null;

        for (RaDetail rad : raList) {
            BillingONCHeader1 bCh1 = bCh1Dao.find(rad.getBillingNo());
            if (bCh1 == null) {
                continue;
            }
            if (providerNo != null && !providerNo.isEmpty()
                    && !providerNo.equals(bCh1.getProviderNo())) {
                continue;
            }
            numItems++;

            int lastBillingNo = curBillingNo;
            curBillingNo = rad.getBillingNo();

            String serviceCode = rad.getServiceCode();
            BillingONItem bItem = bCh1Dao.findBillingONItemByServiceCode(bCh1, serviceCode);

            String dxCode = "";
            String claimAmtStr = rad.getAmountClaim();
            String bItemFee = "D";
            if (bItem != null) {
                dxCode = nullToEmpty(bItem.getDx());
                if (!BillingONItem.DELETED.equals(bItem.getStatus())) {
                    bItemFee = nullToEmpty(bItem.getFee());
                }
                if (bItem.equals(prevItem)) {
                    serviceCode = "";
                    dxCode = "";
                    bItemFee = "";
                }
            }
            prevItem = bItem;

            Integer demoNo = bCh1.getDemographicNo();
            Demographic d = demoNo == null ? null : demographicDao.getDemographicById(demoNo);
            String demographicName = "";
            String billStatus = "";
            String serviceDate = "";
            boolean firstRowForBill = false;

            if (lastBillingNo != curBillingNo) {
                firstRowForBill = true;
                serviceDate = nullToEmpty(rad.getServiceDate());
                demographicName = d == null ? "" : d.getFormattedName();
                billStatus = nullToEmpty(bCh1.getStatus());
                rowColor = "myWhite".equals(rowColor) ? "myPurple" : "myWhite";
            }

            BigDecimal feeAmt = ZERO;
            if (!bItemFee.isEmpty() && !"D".equals(bItemFee)) {
                feeAmt = new BigDecimal(bItemFee);
            }
            BigDecimal claimAmt = new BigDecimal(claimAmtStr);
            BigDecimal paidAmt = new BigDecimal(rad.getAmountPay().trim());
            BigDecimal adjAmt = claimAmt.subtract(paidAmt);

            feeTotal = feeTotal.add(feeAmt);
            claimTotal = claimTotal.add(claimAmt);
            paidTotal = paidTotal.add(paidAmt);
            adjTotal = adjTotal.add(adjAmt);

            // Highlight rows where claimed != paid, OR a deleted bill still
            // has a non-zero paid amount (the "ghost-payment" warning case).
            if (feeAmt.compareTo(paidAmt) != 0
                    || (BillingONCHeader1.DELETED.equals(billStatus)
                        && paidAmt.compareTo(ZERO) != 0)) {
                rowColor = "myPink";
            }

            rows.add(new BillingONPaymentViewModel.RaReportRow(
                    String.valueOf(curBillingNo), billStatus, serviceDate,
                    demoNo == null ? "" : demoNo.toString(), demographicName,
                    dxCode, serviceCode, nullToEmpty(rad.getServiceCount()),
                    bItemFee, claimAmtStr, paidAmt.toPlainString(), adjAmt.toPlainString(),
                    nullToEmpty(rad.getBillType()), nullToEmpty(rad.getClaimNo()),
                    nullToEmpty(rad.getErrorCode()),
                    firstRowForBill, rowColor));
        }

        b.raRows(rows).raItemCount(numItems)
                .raFeeTotal(feeTotal.toPlainString())
                .raClaimTotal(claimTotal.toPlainString())
                .raPaidTotal(paidTotal.toPlainString())
                .raAdjTotal(adjTotal.toPlainString());
    }

    private void buildPremiumReport(BillingONPaymentViewModel.Builder b,
                                    List<BillingONPremium> premiumList,
                                    Locale locale) {
        BigDecimal totalPremiums = ZERO;
        int numItems = 0;
        List<BillingONPaymentViewModel.PremiumRow> rows = new ArrayList<>();

        if (premiumList == null) {
            b.premiumRows(rows).premiumItemCount(0).premiumTotal(totalPremiums.toPlainString());
            return;
        }

        String rowColor = "myWhite";
        for (BillingONPremium bPremium : premiumList) {
            numItems++;
            rowColor = "myWhite".equals(rowColor) ? "myPurple" : "myWhite";

            String amountPaid = "0.00";
            try {
                amountPaid = nullToEmpty(bPremium.getAmountPay());
                if (amountPaid.isEmpty()) {
                    amountPaid = "0.00";
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().warn("Premium Amount Paid not a number", e);
            }

            String providerName = "";
            String premProviderNo = bPremium.getProviderNo();
            if (premProviderNo != null) {
                Provider p = providerDao.getProvider(premProviderNo);
                if (p != null) {
                    providerName = p.getFormattedName();
                }
            }
            String payDateStr = DateUtils.formatDate(bPremium.getPayDate(), locale);

            rows.add(new BillingONPaymentViewModel.PremiumRow(
                    providerName, payDateStr, amountPaid, rowColor));
            try {
                totalPremiums = totalPremiums.add(new BigDecimal(amountPaid));
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().warn("Premium Amount Paid not a number on tally", e);
            }
        }
        b.premiumRows(rows).premiumItemCount(numItems).premiumTotal(totalPremiums.toPlainString());
    }

    private void buildThirdPartyReport(BillingONPaymentViewModel.Builder b,
                                       List<BillingONCHeader1> ptList,
                                       Date startDate, Date endDate,
                                       Locale locale) {
        BigDecimal total3rdPaid = ZERO;
        BigDecimal total3rdRefunded = ZERO;
        BigDecimal total3rdBilled = ZERO;
        int num3rdItems = 0;
        List<BillingONPaymentViewModel.ThirdPartyBillRow> rows = new ArrayList<>();

        if (ptList == null) {
            b.thirdPartyRows(rows).thirdPartyItemCount(0)
                    .thirdPartyBilledTotal(total3rdBilled.toPlainString())
                    .thirdPartyPaidTotal(total3rdPaid.toPlainString())
                    .thirdPartyRefundedTotal(total3rdRefunded.toPlainString());
            return;
        }

        String rowColor = "myWhite";
        for (BillingONCHeader1 bCh1 : ptList) {
            List<BillingONPayment> bPayList = bPaymentDao.find3rdPartyPayRecordsByBill(bCh1, startDate, endDate);
            BigDecimal totalPaid = BillingONPaymentDao.calculatePaymentTotal(bPayList);
            BigDecimal totalRefunded = BillingONPaymentDao.calculateRefundTotal(bPayList);

            // Skip bills with no payment activity in the date window — same
            // gate the legacy JSP applied before incrementing num3rdItems.
            if (totalPaid.compareTo(ZERO) == 0 && totalRefunded.compareTo(ZERO) == 0) {
                continue;
            }
            num3rdItems++;
            rowColor = "myWhite".equals(rowColor) ? "myPurple" : "myWhite";

            String billingDateStr = DateUtils.formatDate(bCh1.getBillingDate(), locale);
            Integer demoNo = bCh1.getDemographicNo();
            Demographic d = demoNo == null ? null : demographicDao.getDemographicById(demoNo);
            String demographicName = d == null ? "" : d.getFormattedName();
            String billingNo = String.valueOf(bCh1.getId());

            // Per-item rows: each BillingONItem becomes one item row, with
            // its own payment/refund totals from the per-item payment table.
            BigDecimal totalBilled = ZERO;
            List<BillingONPaymentViewModel.ThirdPartyItemRow> itemRows = new ArrayList<>();
            for (BillingONItem bItem : billingONService.getNonDeletedInvoices(bCh1.getId())) {
                String amtBilled = nullToEmpty(bItem.getFee());
                try {
                    if (!amtBilled.isEmpty()) {
                        totalBilled = totalBilled.add(new BigDecimal(amtBilled));
                    }
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().warn("BillItem fee is not a valid amount: {}", amtBilled);
                }

                List<BillingOnItemPayment> bItemPayList =
                        bItemPaymentDao.getItemPaymentByInvoiceNoItemId(bCh1.getId(), bItem.getId());
                BigDecimal amtPaid = BillingOnItemPaymentDao.calculateItemPaymentTotal(bItemPayList);
                BigDecimal amtRefund = BillingOnItemPaymentDao.calculateItemRefundTotal(bItemPayList);

                itemRows.add(new BillingONPaymentViewModel.ThirdPartyItemRow(
                        nullToEmpty(bItem.getDx()),
                        nullToEmpty(bItem.getServiceCode()),
                        nullToEmpty(bItem.getServiceCount()),
                        amtBilled,
                        amtPaid.toPlainString(),
                        amtRefund.toPlainString()));
            }

            // Per-payment rows: only payments with non-zero pay or refund
            // get rendered, matching the legacy JSP's filter.
            List<BillingONPaymentViewModel.ThirdPartyPaymentRow> paymentRows = new ArrayList<>();
            for (BillingONPayment bPay : bPayList) {
                BigDecimal payAmt = bPay.getTotal_payment();
                BigDecimal refundAmt = bPay.getTotal_refund();
                if (payAmt.compareTo(ZERO) == 0 && refundAmt.compareTo(ZERO) == 0) {
                    continue;
                }
                paymentRows.add(new BillingONPaymentViewModel.ThirdPartyPaymentRow(
                        payAmt.toPlainString(),
                        refundAmt.toPlainString(),
                        DateUtils.formatDate(bPay.getPaymentDate(), locale)));
                total3rdPaid = total3rdPaid.add(payAmt);
                total3rdRefunded = total3rdRefunded.add(refundAmt);
            }

            // Outstanding balance only renders for non-HCP/RMB/WCB and
            // non-DELETED bills — same gate the legacy JSP applied.
            String payProgram = nullToEmpty(bCh1.getPayProgram());
            String status = nullToEmpty(bCh1.getStatus());
            boolean hasOutstanding = !"HCP".equals(payProgram)
                    && !"WCB".equals(payProgram)
                    && !"RMB".equals(payProgram)
                    && !BillingONCHeader1.DELETED.equals(status);
            String outstandingAmt = "";
            boolean outstandingBold = false;
            if (hasOutstanding) {
                BigDecimal outstanding = totalBilled.subtract(totalPaid).add(totalRefunded);
                outstandingAmt = outstanding.toPlainString();
                outstandingBold = outstanding.compareTo(ZERO) != 0;
            }

            total3rdBilled = total3rdBilled.add(totalBilled);

            rows.add(new BillingONPaymentViewModel.ThirdPartyBillRow(
                    billingNo, billingDateStr,
                    demoNo == null ? "" : demoNo.toString(), demographicName,
                    rowColor,
                    itemRows, totalBilled.toPlainString(),
                    paymentRows,
                    hasOutstanding, outstandingAmt, outstandingBold));
        }

        b.thirdPartyRows(rows).thirdPartyItemCount(num3rdItems)
                .thirdPartyBilledTotal(total3rdBilled.toPlainString())
                .thirdPartyPaidTotal(total3rdPaid.toPlainString())
                .thirdPartyRefundedTotal(total3rdRefunded.toPlainString());
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
