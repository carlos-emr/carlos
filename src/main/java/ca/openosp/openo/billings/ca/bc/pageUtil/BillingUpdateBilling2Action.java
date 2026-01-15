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


/*
 * BillingUpdateBilling2Action.java
 *
 * Created on August 30, 2004, 1:52 PM
 */

package ca.openosp.openo.billings.ca.bc.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.billings.ca.bc.MSP.MSPReconcile;
import ca.openosp.openo.billings.ca.bc.data.BillRecipient;
import ca.openosp.openo.billings.ca.bc.data.BillingNote;

/**
 * @author root
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class BillingUpdateBilling2Action
        extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();

    public String execute() throws IOException,
            ServletException {
        String creator = (String) request.getSession().getAttribute("user");

        BillRecipient recip = new BillRecipient();
        recip.setName(this.getRecipientName());
        recip.setAddress(this.getRecipientAddress());
        recip.setCity(this.getRecipientCity());
        recip.setProvince(this.getRecipientProvince());
        recip.setPostal(this.getRecipientPostal());
        recip.setBillingNoString(this.getBillingNo());
        log.debug("Name of recip " + recip.getName());
        MSPReconcile msprec = new MSPReconcile();
        BillingViewBean bean = new BillingViewBean();
        bean.updateBill(this.getBillingNo(), request.getParameter("billingProvider"));

        msprec.saveOrUpdateBillRecipient(recip);
        BillingNote n = new BillingNote();
        try {
            n.addNoteFromBillingNo(this.getBillingNo(), creator, this.getMessageNotes());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;
    }

    /**
     * Creates a new instance of BillingUpdateBilling2Action
     */
    public BillingUpdateBilling2Action() {
    }
    private String amountReceived;
    private String messageNotes;
    private String recipientAddress;
    private String recipientCity;
    private String recipientName;
    private String recipientPostal;
    private String recipientProvince;
    String requestId;
    private String billStatus;
    private String billingNo;
    private String paymentMethod;
    private String billPatient;

    public String getRequestId() {
        return requestId;
    }

    public String getAmountReceived() {
        return amountReceived;
    }

    public String getRecipientProvince() {
        return recipientProvince;
    }

    public String getRecipientPostal() {
        return recipientPostal;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientCity() {
        return recipientCity;
    }

    public String getRecipientAddress() {
        return recipientAddress;
    }

    public String getMessageNotes() {
        return messageNotes;
    }

    public String getBillStatus() {
        return billStatus;
    }

    public String getBillingNo() {
        return billingNo;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getBillPatient() {
        return billPatient;
    }

    public void setRequestId(String id) {
        this.requestId = id;
    }

    public void setAmountReceived(String amountReceived) {
        this.amountReceived = amountReceived;
    }

    public void setMessageNotes(String messageNotes) {
        this.messageNotes = messageNotes;
    }

    public void setRecipientAddress(String recipientAddress) {
        this.recipientAddress = recipientAddress;
    }

    public void setRecipientCity(String recipientCity) {
        this.recipientCity = recipientCity;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public void setRecipientPostal(String recipientPostal) {
        this.recipientPostal = recipientPostal;
    }

    public void setRecipientProvince(String recipientProvince) {
        this.recipientProvince = recipientProvince;
    }

    public void setBillStatus(String billStatus) {
        this.billStatus = billStatus;
    }

    public void setBillingNo(String billingNo) {
        this.billingNo = billingNo;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public void setBillPatient(String billPatient) {
        this.billPatient = billPatient;
    }
}
