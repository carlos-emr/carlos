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

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import io.github.carlos_emr.carlos.entities.PaymentType;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class ViewReceivePayment2Action
        extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        this.setPaymentMethodList(manualPaymentTypes());
        this.setBillingmasterNo(request.getParameter("lineNo"));
        this.setBillNo(request.getParameter("billNo"));
        // receivePayment.jsp reads this action through the "receivePaymentActionForm" request
        // attribute, a name left over from the Struts 1 ActionForm; nothing else publishes it.
        request.setAttribute(ReceivePayment2Action.FORM_ATTRIBUTE, this);
        return SUCCESS;
    }

    /**
     * The payment methods a front-desk user can record by hand. ELECTRONIC is an MSP remittance
     * type that only the reconciliation import writes, so it is never offered here.
     *
     * @return a new list of payment types without ELECTRONIC
     */
    static List<PaymentType> manualPaymentTypes() {
        List<PaymentType> paymentTypes = new BillingViewBean().getPaymentTypes();
        // Removing by index while counting up skipped the entry after each removal.
        paymentTypes.removeIf(tp -> "ELECTRONIC".equals(tp.getPaymentType()));
        return paymentTypes;
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

    @StrutsParameter
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    @StrutsParameter
    public void setPayment(String payment) {
        this.payment = payment;
    }

    public void setPaymentMethodList(List paymentMethodList) {
        this.paymentMethodList = paymentMethodList;
    }

    @StrutsParameter
    public void setBillingmasterNo(String billingmasterNo) {
        this.billingmasterNo = billingmasterNo;
    }

    @StrutsParameter
    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    @StrutsParameter
    public void setPaymentReceived(boolean paymentReceived) {
        this.paymentReceived = paymentReceived;
    }

    @StrutsParameter
    public void setIsRefund(String isRefund) {

        this.isRefund = isRefund;
    }

    @StrutsParameter
    public void setPayeeProviderNo(String payeeProviderNo) {
        this.payeeProviderNo = payeeProviderNo;
    }

    public String getPayment() {
        return payment;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public List getPaymentMethodList() {
        return paymentMethodList;
    }

    public String getBillingmasterNo() {
        return billingmasterNo;
    }

    public String getBillNo() {
        return billNo;
    }

    public boolean isPaymentReceived() {
        return paymentReceived;
    }

    public String getIsRefund() {
        return isRefund;
    }

    public String getPayeeProviderNo() {
        return payeeProviderNo;
    }
}
