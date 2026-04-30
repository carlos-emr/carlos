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
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingPaymentSaveService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnThirdPartyPaymentsViewModel;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
/**
 * Multi-method third-party payment manager: routes by {@code method=}
 * request parameter to {@code listPayments}, {@code savePayment},
 * {@code deletePayment}, {@code viewPayment}, or {@code viewPayment_ext}.
 * Strict-parses each item amount through {@link BillingMoney} and rejects
 * the request as a JSON {@code {"ret":1,"reason":...}} body if any value
 * is malformed. Requires {@code _billing w}.
 *
 * <p>Split candidate per the one-method-per-action convention; carved out
 * later because the JSP serializes a variable-width "items" list and the
 * single-method split needs a typed command first.</p>
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
        // billingNo is required for the legitimate flow (caller passes it from
        // the parent invoice). A direct GET without the param previously NPE'd
        // here with NumberFormatException("Cannot parse null string"); 400 with
        // a clear message is the right response.
        String billingNoParam = request.getParameter("billingNo");
        Integer billingNo;
        try {
            billingNo = Integer.parseInt(billingNoParam);
        } catch (NumberFormatException e) {
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "billingNo parameter is required");
            } catch (java.io.IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }

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
        // POST gate: financial write — HttpMethodGuardFilter's MUTATOR_METHOD_PARAMS
        // matches the bare token "save" but not "savePayment", so a forged GET
        // would otherwise drive ~10 DAO writes. CSRFGuard's body-token check
        // only fires on non-GET, so this gate is the line of defense.
        if (!BillingRequestGuards.requirePost(request, response)) {
            return null;
        }

        String paymentdate1 = request.getParameter("paymentDate");
        SimpleDateFormat sim = new SimpleDateFormat("yyyy-MM-dd");
        Date paymentdate = sim.parse(paymentdate1);

        // Required-numeric inputs from the JSP form. listPayments was hardened
        // earlier; the same defensive parse belongs here so a malformed/forged
        // request rejects cleanly with the same JSON contract instead of 500.
        int itemSize;
        int billNo;
        try {
            itemSize = Integer.parseInt(request.getParameter("size"));
        } catch (NumberFormatException e) {
            return writeRejectionJson("Missing or non-numeric 'size' parameter; payment not saved");
        }
        try {
            billNo = Integer.parseInt(request.getParameter("billingNo"));
        } catch (NumberFormatException e) {
            return writeRejectionJson("Missing or non-numeric 'billingNo' parameter; payment not saved");
        }
        String curProviderNo = (String) request.getSession().getAttribute("user");
        String paymentTypeId = request.getParameter("paymentType");
        if (paymentTypeId == null || paymentTypeId.isEmpty()) {
            paymentTypeId = "0";
        }

        // Validate every amount upfront AND collect into a Line list. If
        // anything fails, write a rejection JSON response and abort BEFORE
        // any DAO write — silently zeroing on parse failure used to mask user
        // typos and write $0 rows.
        BigDecimal sumPaid = BigDecimal.ZERO;
        BigDecimal sumRefund = BigDecimal.ZERO;
        BigDecimal sumCredit = BigDecimal.ZERO;
        BigDecimal sumDiscount = BigDecimal.ZERO;
        java.util.List<BillingPaymentSaveService.Line> lines = new java.util.ArrayList<>();
        for (int i = 0; i < itemSize; i++) {
            String payment = request.getParameter("payment" + i);
            String discount = request.getParameter("discount" + i);
            String itemId = request.getParameter("itemId" + i);
            int itemIdInt;
            try {
                itemIdInt = Integer.parseInt(itemId);
            } catch (NumberFormatException e) {
                return writeRejectionJson("Invalid itemId on row " + i + ": " + itemId
                        + "; payment not saved");
            }
            // The pre-fix code only summed when the item already existed in
            // the DB. Preserve that — looking up by id here is read-only.
            if (billingONItemDao.find(itemIdInt) == null) {
                continue;
            }
            String sel = request.getParameter("sel" + i);
            try {
                BigDecimal amount;
                BigDecimal disc = BigDecimal.ZERO;
                switch (sel) {
                    case "payment" -> {
                        amount = parseStrictAmount(payment);
                        disc = parseStrictAmount(discount);
                        if (amount.signum() > 0) sumPaid = sumPaid.add(amount);
                        if (disc.signum() > 0) sumDiscount = sumDiscount.add(disc);
                    }
                    case "refund" -> {
                        amount = parseStrictAmount(payment);
                        if (amount.signum() > 0) sumRefund = sumRefund.add(amount);
                    }
                    case "credit" -> {
                        amount = parseStrictAmount(payment);
                        if (amount.signum() > 0) sumCredit = sumCredit.add(amount);
                    }
                    default -> {
                        // unknown selection — keep the row out of the persist list
                        continue;
                    }
                }
                lines.add(new BillingPaymentSaveService.Line(itemIdInt, sel, amount, disc));
            } catch (NumberFormatException e) {
                return writeRejectionJson("Invalid amount on row " + i + ": " + e.getMessage()
                        + "; payment not saved");
            }
        }

        // Pre-flight: confirm the bill row still exists. The transactional
        // service re-fetches inside the tx and would throw BVE on a concurrent
        // delete, but checking here lets us return the same "failure" forward
        // we used pre-fix instead of falling through to a generic error page.
        BillingONCHeader1 cheader1 = billingClaimDAO.find(billNo);
        if (cheader1 == null) {
            return "failure";
        }
        String status = request.getParameter("status");
        boolean statusChanges = status != null && !status.equals(cheader1.getStatus());

        ObjectNode ret = objectMapper.createObjectNode();
        if (sumPaid.signum() == 0 && sumDiscount.signum() == 0
                && sumRefund.signum() == 0 && sumCredit.signum() == 0) {
            // All-zeros early-return path: nothing to persist except possibly
            // the status change. Keep this single merge in the action — it's
            // a one-shot write with a single failure mode.
            if (statusChanges) {
                cheader1.setStatus(status);
                billingClaimDAO.merge(cheader1);
                ret.put("ret", 0);
            } else {
                ret.put("ret", 1);
                ret.put("reason", "Payments, discounts and refunds can't be all zeros!!");
            }
            return writeJsonResponse(ret, "savePayment zero-totals JSON response");
        }

        // Atomic write phase: hand the validated, parsed input to the
        // @Transactional service so any DAO failure rolls the whole batch
        // back instead of leaving the header + ext keys + payment row in
        // inconsistent state.
        BillingPaymentSaveService.Command cmd = new BillingPaymentSaveService.Command(
                billNo, paymentdate, curProviderNo,
                Integer.parseInt(paymentTypeId), paymentTypeId,
                sumPaid, sumDiscount, sumRefund, sumCredit,
                statusChanges ? status : null,
                lines);
        try {
            SpringUtils.getBean(BillingPaymentSaveService.class).saveThirdPartyPayment(cmd);
        } catch (io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException e) {
            logger.warn("savePayment rejected: {}", e.getMessage());
            return writeRejectionJson(e.getMessage());
        }

        ret.put("ret", 0);
        return writeJsonResponse(ret, "savePayment success JSON response");
    }

    private String writeJsonResponse(ObjectNode body, String label) {
        response.setCharacterEncoding("utf-8");
        response.setContentType("application/json");
        try {
            response.getWriter().print(body.toString());
            response.getWriter().flush();
            response.getWriter().close();
        } catch (Exception e) {
            logger.error("Failed to write {}", label, e);
            return "failure";
        }
        return null;
    }

    public String deletePayment() {
        // POST gate: destructive write — HttpMethodGuardFilter's MUTATOR_METHOD_PARAMS
        // matches the bare token "delete" but not "deletePayment", so a forged
        // GET could otherwise wipe a payment + rebalance the header. CSRFGuard's
        // body-token check only fires on non-GET.
        if (!BillingRequestGuards.requirePost(request, response)) {
            return "failure";
        }

        // The four writes (payment.remove + header.merge + 2× ext.setExtItem)
        // are now bundled under @Transactional inside BillingPaymentDeletionService.
        // Pre-fix any mid-sequence failure left the header `paid` total stale
        // and the ext keys out of sync with the underlying payment table.
        try {
            int paymentId = Integer.parseInt(request.getParameter("id"));
            io.github.carlos_emr.carlos.utility.SpringUtils
                    .getBean(io.github.carlos_emr.carlos.billings.ca.on.service.BillingPaymentDeletionService.class)
                    .deletePayment(paymentId);
        } catch (NumberFormatException nfe) {
            logger.warn("deletePayment: invalid id parameter: {}",
                    io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(request.getParameter("id")));
            return "failure";
        } catch (io.github.carlos_emr.carlos.billings.ca.on.service.BillingPaymentDeletionService.PaymentNotFoundException notFound) {
            // Distinct outcome — operator hit "delete" on a row that's
            // already gone (concurrent edit, or stale page). Render the
            // current payment list rather than the generic failure page so
            // they see the up-to-date state.
            logger.warn("deletePayment: paymentId not found: {}",
                    io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(request.getParameter("id")));
            return listPayments();
        } catch (Exception ex) {
            logger.error("Failed to delete payment: {}",
                    io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(request.getParameter("id")), ex);
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
            logger.error("Invalid paymentId parameter {}", LogSanitizer.sanitize(id), e);
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
            logger.error("Failed to write viewPayment JSON response", e);
            return "failure";
        }

        return null;
    }

    public String viewPayment_ext() {
        // 1.get payment details according to billing_on_item_payment
        String billPaymentIdRaw = request.getParameter("billPaymentId");
        int billPaymentId;
        try {
            billPaymentId = Integer.parseInt(billPaymentIdRaw);
        } catch (NumberFormatException e) {
            // Pre-fix this returned null + logged at INFO, producing a blank
            // page with no operator feedback. Forward to the failure result so
            // the user sees an error instead of a silent empty render.
            logger.error("Invalid billPaymentId parameter {}", LogSanitizer.sanitize(billPaymentIdRaw), e);
            return "failure";
        }
        BillingONPayment billPayment = billingONPaymentDao.find(billPaymentId);
        if (billPayment == null) {
            logger.warn("viewPayment_ext: billPaymentId not found: {}",
                    LogSanitizer.sanitize(billPaymentIdRaw));
            return "failure";
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
            logger.error("Failed to write rejection JSON response", e);
            return "failure";
        }
        return null;
    }
}
