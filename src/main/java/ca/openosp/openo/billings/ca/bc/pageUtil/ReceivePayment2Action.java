//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.billings.ca.bc.pageUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import ca.openosp.openo.billings.ca.bc.MSP.MSPReconcile;
import ca.openosp.openo.billings.ca.bc.data.BillingHistoryDAO;


/**
 * <p>Responible for executing logic for receiving a private payment</p>
 * <p>When a payment is recieved the method of payment is updated and the staus is set to paidprivate
 * <p>if the entire balance owing is recovered</p>
 *
 * @version 1.0
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.List;

public class ReceivePayment2Action
        extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {
        double dblAmount = Double.valueOf(this.getAmountReceived()).doubleValue();
        if ("true".equals(this.getIsRefund())) {

            this.setAmountReceived(String.valueOf(dblAmount * -1.0));
        }
        this.receivePayment(this.getBillingmasterNo(), dblAmount, this.getPaymentMethod());
        this.setPaymentReceived(true);
        return SUCCESS;
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

    public void setAmountReceived(String amountReceived) {
        this.amountReceived = amountReceived;
    }

    public String getPayment() {
        return payment;
    }

    public void setPayment(String payment) {
        this.payment = payment;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public List getPaymentMethodList() {
        return paymentMethodList;
    }

    public void setPaymentMethodList(List paymentMethodList) {
        this.paymentMethodList = paymentMethodList;
    }

    public String getBillingmasterNo() {
        return billingmasterNo;
    }

    public void setBillingmasterNo(String billingmasterNo) {
        this.billingmasterNo = billingmasterNo;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public boolean isPaymentReceived() {
        return paymentReceived;
    }

    public void setPaymentReceived(boolean paymentReceived) {
        this.paymentReceived = paymentReceived;
    }

    public String getIsRefund() {
        return isRefund;
    }

    public void setIsRefund(String isRefund) {
        this.isRefund = isRefund;
    }

    public String getPayeeProviderNo() {
        return payeeProviderNo;
    }

    public void setPayeeProviderNo(String payeeProviderNo) {
        this.payeeProviderNo = payeeProviderNo;
    }
}
