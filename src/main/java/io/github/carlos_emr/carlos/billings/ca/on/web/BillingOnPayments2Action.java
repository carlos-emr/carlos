/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnThirdPartyPaymentsViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingThirdPartyService;


/**
 * @author rjonasz
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
/**
 * Struts action for the {@code BillingOnPayments2Action} request flow.
 *
 * <p>The action owns web-layer orchestration: privilege checks, request
 * parameter normalization, delegation to services or assemblers, and the
 * Struts result used to render the next JSP. Keep billing rules and database
 * work outside the JSP when changing this flow.</p>
 */

public class BillingOnPayments2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    private BillingONItemDao billingONItemDao = SpringUtils.getBean(BillingONItemDao.class);
    private BillingONPaymentDao billingONPaymentDao = SpringUtils.getBean(BillingONPaymentDao.class);
    private BillingPaymentTypeDao billingPaymentTypeDao = SpringUtils.getBean(BillingPaymentTypeDao.class);
    private BillingONCHeader1Dao billingClaimDAO = SpringUtils.getBean(BillingONCHeader1Dao.class);
    private BillingONExtDao billingONExtDao = SpringUtils.getBean(BillingONExtDao.class);
    private BillingOnItemPaymentDao billingOnItemPaymentDao = SpringUtils.getBean(BillingOnItemPaymentDao.class);
    private BillingOnTransactionDao billingOnTransactionDao = SpringUtils.getBean(BillingOnTransactionDao.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String method = request.getParameter("method");
        if ("listPayments".equals(method)) {
            return listPayments();
        } else if ("savePayment".equals(method)) {
            return savePayment();
        } else if ("deletePayment".equals(method)) {
            return deletePayment();
        } else if ("viewPayment".equals(method)) {
            return viewPayment();
        } else if ("viewPayment_ext".equals(method)) {
            return viewPayment_ext();
        }
        return listPayments();
    }

    public String listPayments() {
        Integer billingNo = Integer.parseInt(request.getParameter("billingNo"));

        List<BillingONPayment> paymentLists = billingONPaymentDao.find3rdPartyPaymentsByBillingNo(billingNo);
        if (paymentLists == null) {
            paymentLists = new ArrayList<BillingONPayment>();
        }

        BillingONCHeader1Dao ch1Dao = SpringUtils.getBean(BillingONCHeader1Dao.class);
        BillingONCHeader1 cheader = ch1Dao.find(billingNo);
        BigDecimal total = cheader.getTotal();

        request.setAttribute("totalInvoiced", cheader.getTotal());

        BigDecimal payments = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;

        for (BillingONPayment pmt : paymentLists) {
            payments = payments.add(pmt.getTotal_payment());
            discounts = discounts.add(pmt.getTotal_discount());
            refunds = refunds.add(pmt.getTotal_refund());
            credits = credits.add(pmt.getTotal_credit());
        }

        BigDecimal balance = total.subtract(payments).subtract(discounts).add(credits);
        request.setAttribute("balance", balance);


        request.setAttribute("paymentsList", paymentLists);

        List<BillingONItem> items = billingONItemDao.getActiveBillingItemByCh1Id(billingNo);
        List<BillingClaimItemDto> itemDataList = new ArrayList<BillingClaimItemDto>();
        for (BillingONItem item : items) {
            List<BillingOnItemPayment> paymentList = billingOnItemPaymentDao.getAllByItemId(item.getId());
            BigDecimal payment = BigDecimal.ZERO;
            BigDecimal discount = BigDecimal.ZERO;
            BigDecimal refund = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;
            for (BillingOnItemPayment payIter : paymentList) {
                payment = payment.add(payIter.getPaid());
                discount = discount.add(payIter.getDiscount());
                refund = refund.add(payIter.getRefund());
                credit = credit.add(payIter.getCredit());
            }

            BillingClaimItemDto itemData = new BillingClaimItemDto();
            itemData.setId(item.getId().toString());
            itemData.setService_code(item.getServiceCode());
            itemData.setFee(item.getFee());
            itemData.setPaid(payment.toString());
            itemData.setDiscount(discount.toString());
            itemData.setRefund(refund.toString());
            itemData.setCredit(credit.toString());

            itemDataList.add(itemData);
        }

        request.setAttribute("itemDataList", itemDataList);
        List<BillingPaymentType> paymentTypes = billingPaymentTypeDao.findAll();
        request.setAttribute("paymentTypeList", paymentTypes);

        request.setAttribute("paymentsViewModel",
                buildPaymentsViewModel(billingNo, itemDataList, paymentLists, total, balance, java.util.Collections.emptyList()));

        return SUCCESS;
    }

    /**
     * Assembles the BillingOnThirdPartyPaymentsViewModel that the JSP renders. Public
     * so the defensive JSP fallback can invoke it directly when the action
     * chain wasn't traversed.
     *
     * <p>Replicates the per-item arithmetic the legacy JSP scriptlet ran
     * inline (fee/paid/discount/credit → balance, paid sign, balance sign)
     * and the per-payment total/balance computation.</p>
     *
     * @param billingNo billing-record id (echoed back in the form)
     * @param itemDataList per-item data (already loaded)
     * @param paymentLists existing payment rows (already loaded)
     * @param total invoice total (BigDecimal, may be null)
     * @param balance overall balance (BigDecimal, may be null)
     * @param errors validation errors to surface at the bottom of the page
     * @return populated view model (never null)
     */
    public BillingOnThirdPartyPaymentsViewModel buildPaymentsViewModel(
            Integer billingNo,
            List<BillingClaimItemDto> itemDataList,
            List<BillingONPayment> paymentLists,
            BigDecimal total,
            BigDecimal balance,
            List<String> errors) {

        NumberFormat currency = NumberFormat.getCurrencyInstance();

        // Per-item summary rows
        List<BillingOnThirdPartyPaymentsViewModel.ItemSummary> items = new ArrayList<>();
        if (itemDataList != null) {
            for (BillingClaimItemDto billItemData : itemDataList) {
                BigDecimal itemTotal = parseDec(billItemData.getFee()).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal itemPaid = parseDec(billItemData.getPaid()).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal itemDiscount = parseDec(billItemData.getDiscount()).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal itemCredit = parseDec(billItemData.getCredit()).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal itemBalance = itemTotal.subtract(itemPaid).subtract(itemDiscount).add(itemCredit);
                BigDecimal realPaid = itemPaid.subtract(itemCredit);

                String realPaidSign = realPaid.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";
                String balanceSign = itemBalance.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";

                items.add(new BillingOnThirdPartyPaymentsViewModel.ItemSummary(
                        nullToEmpty(billItemData.getId()),
                        nullToEmpty(billItemData.getService_code()),
                        nullToEmpty(billItemData.getFee()),
                        realPaidSign + currency.format(realPaid.abs()),
                        balanceSign + currency.format(itemBalance.abs())));
            }
        }

        // Existing payment rows
        List<BillingOnThirdPartyPaymentsViewModel.PaymentRow> paymentRows = new ArrayList<>();
        BigDecimal sumOfPay = BigDecimal.ZERO;
        BigDecimal sumOfDiscount = BigDecimal.ZERO;
        BigDecimal sumOfCredit = BigDecimal.ZERO;
        BigDecimal headerTotal = total;
        if (paymentLists != null && !paymentLists.isEmpty() && headerTotal == null) {
            headerTotal = paymentLists.get(0).getBillingONCheader1().getTotal();
        }
        if (paymentLists != null) {
            for (BillingONPayment pmt : paymentLists) {
                sumOfPay = sumOfPay.add(pmt.getTotal_payment());
                sumOfDiscount = sumOfDiscount.add(pmt.getTotal_discount());
                sumOfCredit = sumOfCredit.add(pmt.getTotal_credit());
                BigDecimal rowBalance = headerTotal == null
                        ? BigDecimal.ZERO
                        : headerTotal.subtract(sumOfPay).subtract(sumOfDiscount).add(sumOfCredit);
                String rowBalSign = rowBalance.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";

                String paymentTypeName = "";
                int paymentTypeId = pmt.getPaymentTypeId();
                if (paymentTypeId > 0) {
                    BillingPaymentType ptype = billingPaymentTypeDao.find(paymentTypeId);
                    if (ptype != null && ptype.getPaymentType() != null) {
                        paymentTypeName = ptype.getPaymentType();
                    }
                }

                paymentRows.add(new BillingOnThirdPartyPaymentsViewModel.PaymentRow(
                        String.valueOf(pmt.getId()),
                        String.valueOf(pmt.getTotal_payment()),
                        paymentTypeName,
                        pmt.getPaymentDateFormatted(),
                        String.valueOf(pmt.getTotal_discount()),
                        String.valueOf(pmt.getTotal_credit()),
                        String.valueOf(pmt.getTotal_refund()),
                        rowBalSign + currency.format(rowBalance.abs())));
            }
        }

        BigDecimal totalForDisplay = total == null ? BigDecimal.ZERO : total;
        BigDecimal balanceForDisplay = balance == null ? BigDecimal.ZERO : balance;
        String totalDisplay = currency.format(totalForDisplay);
        String balanceDisplay = balanceForDisplay.compareTo(BigDecimal.ZERO) < 0
                ? "-" + currency.format(balanceForDisplay.abs())
                : currency.format(balanceForDisplay);

        return BillingOnThirdPartyPaymentsViewModel.builder()
                .today(new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                .billingNo(billingNo == null ? "" : String.valueOf(billingNo))
                .itemCount(itemDataList == null ? 0 : itemDataList.size())
                .items(items)
                .payments(paymentRows)
                .totalDisplay(totalDisplay)
                .balanceDisplay(balanceDisplay)
                .errors(errors == null ? java.util.Collections.emptyList() : errors)
                .build();
    }

    private static BigDecimal parseDec(String s) {
        if (s == null || s.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public String savePayment() throws ParseException {

        Date curDate = new Date();
        String paymentdate1 = request.getParameter("paymentDate");
        SimpleDateFormat sim = new SimpleDateFormat("yyyy-MM-dd");
        Date paymentdate = sim.parse(paymentdate1);

        int itemSize = Integer.parseInt(request.getParameter("size"));
        int billNo = Integer.parseInt(request.getParameter("billingNo"));
        String curProviderNo = (String) request.getSession().getAttribute("user");
        String paymentTypeId = request.getParameter("paymentType");
        if (paymentTypeId == null || paymentTypeId.isEmpty()) {
            paymentTypeId = "0";
        }

        // Validate every amount upfront. If anything fails, write a rejection
        // JSON response and abort BEFORE the persist phase below — silently
        // zeroing on parse failure used to mask user typos and write $0 rows.
        BigDecimal sumPaid = BigDecimal.ZERO;
        BigDecimal sumRefund = BigDecimal.ZERO;
        BigDecimal sumCredit = BigDecimal.ZERO;
        BigDecimal sumDiscount = BigDecimal.ZERO;
        for (int i = 0; i < itemSize; i++) {
            String payment = request.getParameter("payment" + i);
            String discount = request.getParameter("discount" + i);
            String itemId = request.getParameter("itemId" + i);
            if (billingONItemDao.find(Integer.parseInt(itemId)) != null) {
                String sel = request.getParameter("sel" + i);
                try {
                    if ("payment".equals(sel)) {
                        BigDecimal pay = parseStrictAmount(payment);
                        BigDecimal dicnt = parseStrictAmount(discount);
                        if (pay.compareTo(BigDecimal.ZERO) > 0) {
                            sumPaid = sumPaid.add(pay);
                        }
                        if (dicnt.compareTo(BigDecimal.ZERO) > 0) {
                            sumDiscount = sumDiscount.add(dicnt);
                        }
                    } else if ("refund".equals(sel)) {
                        BigDecimal refundTmp = parseStrictAmount(payment);
                        if (refundTmp.compareTo(BigDecimal.ZERO) > 0) {
                            sumRefund = sumRefund.add(refundTmp);
                        }
                    } else if ("credit".equals(sel)) {
                        BigDecimal creditTmp = parseStrictAmount(payment);
                        if (creditTmp.compareTo(BigDecimal.ZERO) > 0) {
                            sumCredit = sumCredit.add(creditTmp);
                        }
                    }
                } catch (NumberFormatException e) {
                    return writeRejectionJson("Invalid amount on row " + i + ": " + e.getMessage()
                            + "; payment not saved");
                }
            }
        }

        BillingONCHeader1 cheader1 = billingClaimDAO.find(billNo);
        if (cheader1 == null) {
            return "failure";
        }
        String status = request.getParameter("status");
        boolean toUpdateChl = false;
        if (status != null && !status.equals(cheader1.getStatus())) {
            cheader1.setStatus(status);
            toUpdateChl = true;
        }

        ObjectNode ret = objectMapper.createObjectNode();
        if (sumPaid.compareTo(BigDecimal.ZERO) == 0
                && sumDiscount.compareTo(BigDecimal.ZERO) == 0
                && sumRefund.compareTo(BigDecimal.ZERO) == 0
                && sumCredit.compareTo(BigDecimal.ZERO) == 0) {

            if (toUpdateChl) {
                billingClaimDAO.merge(cheader1);
                ret.put("ret", 0);
            } else {
                ret.put("ret", 1);
                ret.put("reason", "Payments, discounts and refunds can't be all zeros!!");
            }
            response.setCharacterEncoding("utf-8");
            response.setContentType("application/json");
            try {
                response.getWriter().print(ret.toString());
                response.getWriter().flush();
                response.getWriter().close();
            } catch (Exception e) {
                logger.info(e.toString());
                return "failure";
            }
            return null;
        }

        // count sum of paid,refund,discount
        String demographicNo = cheader1.getDemographicNo().toString();

        // 1.billing_on_ext table: payment
        BillingThirdPartyService tExtObj = SpringUtils.getBean(BillingThirdPartyService.class);
        if (sumPaid.compareTo(BigDecimal.ZERO) == 1) {
            toUpdateChl = true;
            BigDecimal sumPaidTmp = sumPaid.add(cheader1.getPaid());
            cheader1.setPaid(sumPaidTmp);
            if (tExtObj.keyExists(Integer.toString(billNo), BillingONExtDao.KEY_PAYMENT)) {
                tExtObj.updateKeyValue(Integer.toString(billNo), BillingONExtDao.KEY_PAYMENT, sumPaidTmp.toString());
            } else {
                tExtObj.add3rdBillExt(Integer.toString(billNo), demographicNo, BillingONExtDao.KEY_PAYMENT, sumPaidTmp.toString());
            }
        }
        if (toUpdateChl) {
            billingClaimDAO.merge(cheader1);
        }

        // 2.update billing_on_ext table: discount
        if (sumDiscount.compareTo(BigDecimal.ZERO) == 1) {
            BigDecimal extDiscount = billingONExtDao.getAccountVal(billNo, BillingONExtDao.KEY_DISCOUNT);
            BigDecimal sumDiscountTmp = sumDiscount.add(extDiscount);
            if (tExtObj.keyExists(Integer.toString(billNo), BillingONExtDao.KEY_DISCOUNT)) {
                tExtObj.updateKeyValue(Integer.toString(billNo), BillingONExtDao.KEY_DISCOUNT, sumDiscountTmp.toString());
            } else {
                tExtObj.add3rdBillExt(Integer.toString(billNo), demographicNo, BillingONExtDao.KEY_DISCOUNT, sumDiscountTmp.toString());
            }
        }

        // 3.update billing_on_ext table: refund
        if (sumRefund.compareTo(BigDecimal.ZERO) == 1) {
            BigDecimal extRefund = billingONExtDao.getAccountVal(billNo, BillingONExtDao.KEY_REFUND);
            BigDecimal sumRefundTmp = sumRefund.add(extRefund);
            if (tExtObj.keyExists(Integer.toString(billNo), BillingONExtDao.KEY_REFUND)) {
                tExtObj.updateKeyValue(Integer.toString(billNo), BillingONExtDao.KEY_REFUND, sumRefundTmp.toString());
            } else {
                tExtObj.add3rdBillExt(Integer.toString(billNo), demographicNo, BillingONExtDao.KEY_REFUND, sumRefundTmp.toString());
            }
        }

        // 3.update billing_on_ext table: credit
        if (sumCredit.compareTo(BigDecimal.ZERO) == 1) {
            BigDecimal extCredit = billingONExtDao.getAccountVal(billNo, BillingONExtDao.KEY_CREDIT);
            BigDecimal sumCreditTmp = sumCredit.add(extCredit);
            if (tExtObj.keyExists(Integer.toString(billNo), BillingONExtDao.KEY_CREDIT)) {
                tExtObj.updateKeyValue(Integer.toString(billNo), BillingONExtDao.KEY_CREDIT, sumCreditTmp.toString());
            } else {
                tExtObj.add3rdBillExt(Integer.toString(billNo), demographicNo, BillingONExtDao.KEY_CREDIT, sumCreditTmp.toString());
            }
        }

        // update billing_on_ext table: KEY_PAY_METHOD
        if (paymentTypeId != null) {
            BillingONExt extCredit = billingONExtDao.getClaimExtItem(Integer.valueOf(billNo), Integer.valueOf(demographicNo), BillingONExtDao.KEY_PAY_METHOD);
            if (tExtObj.keyExists(Integer.toString(billNo), BillingONExtDao.KEY_PAY_METHOD)) {
                tExtObj.updateKeyValue(Integer.toString(billNo), BillingONExtDao.KEY_PAY_METHOD, paymentTypeId);
            } else {
                tExtObj.add3rdBillExt(Integer.toString(billNo), demographicNo, BillingONExtDao.KEY_PAY_METHOD, paymentTypeId);
            }
        }

        // 4.update billing_on_payment
        BillingONPayment billPayment = new BillingONPayment();
        billPayment.setBillingOnCheader1(cheader1);
        billPayment.setBillingNo(billNo);
        billPayment.setCreator(curProviderNo);
        billPayment.setPaymentDate(paymentdate);
        billPayment.setPaymentTypeId(Integer.parseInt(paymentTypeId));
        billPayment.setTotal_payment(sumPaid);
        billPayment.setTotal_discount(sumDiscount);
        billPayment.setTotal_refund(sumRefund);
        billPayment.setTotal_credit(sumCredit);
        billingONPaymentDao.persist(billPayment);

        // 5.update biling_on_item_payment
        for (int i = 0; i < itemSize; i++) {
            String payment = request.getParameter("payment" + i);
            String discount = request.getParameter("discount" + i);
            String itemId = request.getParameter("itemId" + i);
            BillingONItem billItem = billingONItemDao.find(Integer.parseInt(itemId));
            if (billItem == null) continue;

            String str = paymentdate1 + " 00:00:00";
            SimpleDateFormat sim1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date paymentdatetmp = sim1.parse(str);
            BillingOnItemPayment billItemPayment = new BillingOnItemPayment();
            billItemPayment.setBillingOnItemId(Integer.parseInt(itemId));
            billItemPayment.setBillingOnPaymentId(billPayment.getId());
            billItemPayment.setCh1Id(billNo);
            billItemPayment.setPaymentTimestamp(new Timestamp(paymentdatetmp.getTime()));

            // Amounts were validated upfront in the summation loop above,
            // so parseStrictAmount can be called here without a catch — any
            // throw at this point indicates a logic regression worth raising.
            String selThis = request.getParameter("sel" + i);
            if ("payment".equals(selThis)) {
                BigDecimal itemPayment = parseStrictAmount(payment);
                BigDecimal itemDiscnt = parseStrictAmount(discount);
                if (itemPayment.compareTo(BigDecimal.ZERO) == 0 && itemDiscnt.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                billItemPayment.setPaid(itemPayment);
                billItemPayment.setDiscount(itemDiscnt);
                billingOnItemPaymentDao.persist(billItemPayment);
                BillingOnTransaction billTrans = billingOnTransactionDao.getTransTemplate(cheader1, billItem, billPayment, curProviderNo, billItemPayment.getId());
                billTrans.setServiceCodePaid(itemPayment);
                billTrans.setServiceCodeDiscount(itemDiscnt);
                billingOnTransactionDao.persist(billTrans);
            } else if ("refund".equals(selThis)) {
                BigDecimal itemRefund = parseStrictAmount(payment);
                if (itemRefund.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                billItemPayment.setRefund(itemRefund);
                billingOnItemPaymentDao.persist(billItemPayment);
                BillingOnTransaction billTrans = billingOnTransactionDao.getTransTemplate(cheader1, billItem, billPayment, curProviderNo, billItemPayment.getId());
                billTrans.setServiceCodeRefund(itemRefund);
                billingOnTransactionDao.persist(billTrans);
            } else if ("credit".equals(selThis)) {
                BigDecimal itemCredit = parseStrictAmount(payment);
                if (itemCredit.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                billItemPayment.setCredit(itemCredit);
                billingOnItemPaymentDao.persist(billItemPayment);
                BillingOnTransaction billTrans = billingOnTransactionDao.getTransTemplate(cheader1, billItem, billPayment, curProviderNo, billItemPayment.getId());
                billTrans.setServiceCodeCredit(itemCredit);
                billingOnTransactionDao.persist(billTrans);
            }
        }
        ret.put("ret", 0);
        response.setCharacterEncoding("utf-8");
        response.setContentType("application/json");
        try {
            response.getWriter().print(ret.toString());
            response.getWriter().flush();
            response.getWriter().close();
        } catch (Exception e) {
            logger.info(e.toString());
            return "failure";
        }
        return null;

    }

    public String deletePayment() {

        Date curDate = new Date();
        try {
            Integer paymentId = Integer.parseInt(request.getParameter("id"));
            BillingONPayment payment = billingONPaymentDao.find(paymentId);
            BillingONCHeader1 ch1 = payment.getBillingONCheader1();
            Integer billingNo = payment.getBillingONCheader1().getId();

            billingONPaymentDao.remove(paymentId);

            BigDecimal paid = billingONPaymentDao.getPaymentsSumByBillingNo(billingNo);
            BigDecimal refund = billingONPaymentDao.getPaymentsRefundByBillingNo(billingNo).negate();
            NumberFormat currency = NumberFormat.getCurrencyInstance();
            ch1.setPaid(paid.subtract(refund));
            billingClaimDAO.merge(ch1);

            billingONExtDao.setExtItem(billingNo, ch1.getDemographicNo(),
                    BillingONExtDao.KEY_PAYMENT,
                    currency.format(paid).replace("$", ""), curDate, '1');
            billingONExtDao.setExtItem(billingNo, ch1.getDemographicNo(),
                    BillingONExtDao.KEY_REFUND, currency.format(refund)
                            .replace("$", ""), curDate, '1');

        } catch (Exception ex) {
            logger.error(
                    "Failed to delete payment: " + request.getParameter("id"),
                    ex);
            return "failure";
        }

        return listPayments();

    }

    public String viewPayment() {
        String id = request.getParameter("paymentId");
        int paymentId = 0;
        try {
            paymentId = Integer.parseInt(id);
            if (paymentId == 0) {
                return "failure";
            }
        } catch (Exception e) {
            logger.info(e.toString());
            return "failure";
        }
        BillingONPayment billPayment = billingONPaymentDao.find(paymentId);
        if (billPayment == null) {
            return "failure";
        }
        List<BillingOnItemPayment> itemPaymentList = billingOnItemPaymentDao.getItemsByPaymentId(paymentId);
        if (itemPaymentList == null) {
            return "failure";
        }
        ArrayNode payDetail = objectMapper.createArrayNode();

        // payment date object
        ObjectNode paymentDateObj = objectMapper.createObjectNode();
        paymentDateObj.put("paymentDate", new SimpleDateFormat("yyyy-MM-dd").format(billPayment.getPaymentDate()));
        payDetail.add(paymentDateObj);

        // payment type object
        ObjectNode typeObj = objectMapper.createObjectNode();
        typeObj.put("paymentType", billPayment.getPaymentTypeId());
        payDetail.add(typeObj);

        for (BillingOnItemPayment itemPayment : itemPaymentList) {
            ObjectNode itemObj = objectMapper.createObjectNode();
            itemObj.put("id", itemPayment.getBillingOnItemId());
            if (itemPayment.getRefund().compareTo(BigDecimal.ZERO) == 0) {
                itemObj.put("type", "payment");
                itemObj.put("payment", itemPayment.getPaid());
                itemObj.put("discount", itemPayment.getDiscount());
            } else {
                itemObj.put("type", "refund");
                itemObj.put("refund", itemPayment.getRefund());
            }
            payDetail.add(itemObj);
        }
        response.setCharacterEncoding("utf-8");
        response.setContentType("application/json");
        try {
            response.getWriter().print(payDetail.toString());
            response.getWriter().flush();
            response.getWriter().close();
        } catch (Exception e) {
            logger.info(e.toString());
            return "failure";
        }

        return null;
    }

    public String viewPayment_ext() {
        // 1.get payment details according to billing_on_item_payment
        int billPaymentId = 0;
        try {
            billPaymentId = Integer.parseInt(request.getParameter("billPaymentId"));
        } catch (Exception e) {
            MiscUtils.getLogger().info(e.toString());
            return null;
        }
        BillingONPayment billPayment = billingONPaymentDao.find(billPaymentId);
        if (billPayment == null) {
            return null;
        }
        request.setAttribute("billPayment", billPayment);
        // Pre-resolve the human-readable payment-type name so the JSP body
        // (billingON3rdViewPayment.jsp) does not need to call
        // BillingPaymentTypeDao via SpringUtils.getBean inline.
        String paymentTypeName = "";
        BillingPaymentType paymentType = billingPaymentTypeDao.find(billPayment.getPaymentTypeId());
        if (paymentType != null && paymentType.getPaymentType() != null) {
            paymentTypeName = paymentType.getPaymentType();
        }
        request.setAttribute("paymentTypeName", paymentTypeName);

        List<BillingClaimItemDto> itemDataList = new ArrayList<BillingClaimItemDto>();
        List<BillingOnItemPayment> itemPaymentList = billingOnItemPaymentDao.getItemsByPaymentId(billPaymentId);
        for (BillingOnItemPayment itemPayment : itemPaymentList) {
            BillingONItem billItemList = billingONItemDao.find(itemPayment.getBillingOnItemId());
            if (billItemList == null) {
                continue;
            }
            BillingClaimItemDto itemData = new BillingClaimItemDto();
            itemData.setId(Integer.toString(itemPayment.getBillingOnItemId()));
            itemData.setService_code(billItemList.getServiceCode());
            itemData.setFee(billItemList.getFee());
            itemData.setPaid(itemPayment.getPaid().toString());
            itemData.setDiscount(itemPayment.getDiscount().toString());
            itemData.setRefund(itemPayment.getRefund().toString());
            itemData.setCredit(itemPayment.getCredit().toString());
            itemData.setCh1_id(String.valueOf(itemPayment.getCh1Id()));
            String ptName = "";
            Integer ch1_id = itemPayment.getCh1Id();
            BillingONCHeader1 ch1 = billingClaimDAO.find(ch1_id);
            if (ch1 != null) {
                ptName = ch1.getDemographicName();
                if (ptName == null)
                    ptName = "";
            }
            itemData.setPatientName(ptName);
            itemDataList.add(itemData);
        }

        request.setAttribute("itemDataList", itemDataList);

        return "viewPayment";
    }

    public void setBillingONPaymentDao(BillingONPaymentDao paymentDao) {
        this.billingONPaymentDao = paymentDao;
    }

    public void setBillingPaymentTypeDao(BillingPaymentTypeDao paymentTypeDao) {
        this.billingPaymentTypeDao = paymentTypeDao;
    }

    public void setBillingONCHeader1Dao(BillingONCHeader1Dao billingDao) {
        this.billingClaimDAO = billingDao;
    }

    public void setBillingONExtDao(BillingONExtDao billingExtDao) {
        this.billingONExtDao = billingExtDao;
    }

    public void setBillingONItemDao(BillingONItemDao billingOnItemDao) {
        this.billingONItemDao = billingOnItemDao;
    }

    public void setBillingOnItemPaymentDao(BillingOnItemPaymentDao billingOnItemPaymentDao) {
        this.billingOnItemPaymentDao = billingOnItemPaymentDao;
    }

    public void setBillingOnTransactionDao(
            BillingOnTransactionDao billingOnTransactionDao) {
        this.billingOnTransactionDao = billingOnTransactionDao;
    }

    /**
     * Parse a user-entered amount strictly. Empty/null is allowed and yields
     * zero (the user simply did not fill that cell); any other unparseable
     * input throws {@link NumberFormatException} with the offending value
     * embedded so the caller can surface a useful rejection message.
     */
    static BigDecimal parseStrictAmount(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(String.format("[%s] is not a valid number", raw));
        }
    }

    /**
     * Write a {@code {"ret":1,"reason":...}} JSON rejection to the response
     * and return the appropriate Struts result string. Mirrors the existing
     * "all zeros" rejection at the top of {@link #savePayment()}.
     */
    private String writeRejectionJson(String reason) {
        ObjectNode ret = objectMapper.createObjectNode();
        ret.put("ret", 1);
        ret.put("reason", reason);
        response.setCharacterEncoding("utf-8");
        response.setContentType("application/json");
        try {
            response.getWriter().print(ret.toString());
            response.getWriter().flush();
            response.getWriter().close();
        } catch (Exception e) {
            logger.info(e.toString());
            return "failure";
        }
        return null;
    }
}
