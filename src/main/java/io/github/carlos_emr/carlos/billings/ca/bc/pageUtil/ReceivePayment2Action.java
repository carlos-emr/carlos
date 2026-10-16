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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingHistoryDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.entities.Billingmaster;


/**
 * <p>Responible for executing logic for receiving a private payment</p>
 * <p>When a payment is recieved the method of payment is updated and the staus is set to paidprivate
 * <p>if the entire balance owing is recovered</p>
 *
 * @version 1.0
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class ReceivePayment2Action
        extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /** Request attribute under which receivePayment.jsp finds this action. */
    static final String FORM_ATTRIBUTE = "receivePaymentActionForm";

    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,4}(\\.\\d{1,2})?");
    /** billing_history.amount_received is varchar(7); a refund's minus sign counts. */
    private static final int AMOUNT_COLUMN_WIDTH = 7;

    private final BillingDao billingDao = SpringUtils.getBean(BillingDao.class);

    /**
     * Records a private payment against one billing line. POST only: a GET or HEAD is refused
     * before anything is read or written. A malformed submission re-renders the form with the
     * payment methods rather than saving.
     */
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        // HTTP method names are case-sensitive (RFC 9110), so only an exact POST may save.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        request.setAttribute(FORM_ATTRIBUTE, this);
        // paymentReceived is bindable from the request; a forged true would send an error
        // re-render into the confirmation branch, which formats the (possibly malformed) amount.
        this.setPaymentReceived(false);
        double dblAmount = 0;
        String problem = isWellFormed() ? null : "Enter an amount such as 25.00 and choose a payment method.";
        if (problem == null) {
            dblAmount = Double.parseDouble(this.getAmountReceived());
            if ("true".equals(this.getIsRefund())) {
                dblAmount *= -1.0;
            }
            problem = rejection(dblAmount);
        }
        if (problem != null) {
            this.setPaymentMethodList(ViewReceivePayment2Action.manualPaymentTypes());
            addActionError(problem);
            return SUCCESS;
        }

        this.setAmountReceived(String.valueOf(dblAmount));
        this.receivePayment(this.getBillingmasterNo(), dblAmount, this.getPaymentMethod());
        this.setPaymentReceived(true);
        return SUCCESS;
    }

    /**
     * Checks what the form shape cannot: that the amount is a real payment that fits the history
     * column, that the method is one the form offers, and that the submitted invoice owns the
     * billing line and is a private bill (the history this writes is only read back for those).
     *
     * @return the message to show, or null when the payment may be recorded
     */
    private String rejection(double amount) {
        if (amount == 0 || String.valueOf(amount).length() > AMOUNT_COLUMN_WIDTH) {
            return "Enter a payment between 0.01 and 9999.99 (a refund up to 999.99).";
        }
        boolean offered = ViewReceivePayment2Action.manualPaymentTypes().stream()
                .anyMatch(type -> type.getId().equals(getPaymentMethod()));
        if (!offered) {
            return "Choose one of the listed payment methods.";
        }
        List<Object[]> rows = billingDao.findByBillingMasterNo(Integer.valueOf(getBillingmasterNo()));
        if (rows.isEmpty()) {
            return "That billing line was not found.";
        }
        Billingmaster line = (Billingmaster) rows.get(0)[0];
        Billing bill = (Billing) rows.get(0)[1];
        if (!String.valueOf(line.getBillingNo()).equals(getBillNo())) {
            return "That billing line does not belong to the invoice.";
        }
        if (!MSPReconcile.BILLTYPE_PRI.equals(bill.getBillingtype())) {
            return "Payments are received here for private bills only.";
        }
        return null;
    }

    private boolean isWellFormed() {
        return matches(DIGITS, getBillingmasterNo())
                && matches(DIGITS, getBillNo())
                && matches(DIGITS, getPaymentMethod())
                && matches(AMOUNT, getAmountReceived());
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    public void receivePayment(String billingMasterNo, double amount, String paymentType) {
        BillingHistoryDAO dao = new BillingHistoryDAO();
        MSPReconcile msp = new MSPReconcile();
        dao.createBillingHistoryArchive(billingMasterNo, amount, paymentType);
        msp.settleIfBalanced(billingMasterNo);
    }

    private String amountReceived;
    private String payment;
    private String paymentMethod;
    private List paymentMethodList;
    private String billingmasterNo;
    private String billNo;
    private boolean paymentReceived;
    private String isRefund;
    private String payeeProviderNo;

    public String getAmountReceived() {
        return amountReceived;
    }

    @StrutsParameter
    public void setAmountReceived(String amountReceived) {
        this.amountReceived = amountReceived;
    }

    public String getPayment() {
        return payment;
    }

    @StrutsParameter
    public void setPayment(String payment) {
        this.payment = payment;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    @StrutsParameter
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    @StrutsParameter(depth = 1)
    public List getPaymentMethodList() {
        return paymentMethodList;
    }

    @StrutsParameter
    public void setPaymentMethodList(List paymentMethodList) {
        this.paymentMethodList = paymentMethodList;
    }

    public String getBillingmasterNo() {
        return billingmasterNo;
    }

    @StrutsParameter
    public void setBillingmasterNo(String billingmasterNo) {
        this.billingmasterNo = billingmasterNo;
    }

    public String getBillNo() {
        return billNo;
    }

    @StrutsParameter
    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public boolean isPaymentReceived() {
        return paymentReceived;
    }

    @StrutsParameter
    public void setPaymentReceived(boolean paymentReceived) {
        this.paymentReceived = paymentReceived;
    }

    public String getIsRefund() {
        return isRefund;
    }

    @StrutsParameter
    public void setIsRefund(String isRefund) {
        this.isRefund = isRefund;
    }

    public String getPayeeProviderNo() {
        return payeeProviderNo;
    }

    @StrutsParameter
    public void setPayeeProviderNo(String payeeProviderNo) {
        this.payeeProviderNo = payeeProviderNo;
    }
}
