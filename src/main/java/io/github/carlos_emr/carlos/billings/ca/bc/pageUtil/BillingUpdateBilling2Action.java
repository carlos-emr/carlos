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


/*
 * BillingUpdateBilling2Action.java
 *
 * Created on August 30, 2004, 1:52 PM
 */

package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillRecipient;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingNote;

/**
 * @author root
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Represents the BillingUpdateBilling2Action functionality within the CARLOS EMR system.
 * Handles data representation and core logic for BillingUpdateBilling2Action.
 */
public final class BillingUpdateBilling2Action
        extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() throws IOException,
            ServletException {
        // Ensure that BillingUpdateBilling2Action correctly interprets the retrieved data to maintain data integrity.

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String creator = (String) request.getSession().getAttribute("user");

        BillRecipient recip = new BillRecipient();
        recip.setName(this.getRecipientName());
        recip.setAddress(this.getRecipientAddress());
        recip.setCity(this.getRecipientCity());
        recip.setProvince(this.getRecipientProvince());
        recip.setPostal(this.getRecipientPostal());
        recip.setBillingNoString(this.getBillingNo());
        MSPReconcile msprec = new MSPReconcile();
        BillingViewBean bean = new BillingViewBean();
        bean.updateBill(this.getBillingNo(), request.getParameter("billingProvider"));

        msprec.saveOrUpdateBillRecipient(recip);
        BillingNote n = new BillingNote();
        try {
            n.addNoteFromBillingNo(this.getBillingNo(), creator, this.getMessageNotes());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "BC billing note update failed for billingNo="
                            + LogSafe.sanitizeForDisplay(this.getBillingNo()),
                    e);
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

    @StrutsParameter
    public void setRequestId(String id) {
        this.requestId = id;
    }

    @StrutsParameter
    public void setAmountReceived(String amountReceived) {
        this.amountReceived = amountReceived;
    }

    @StrutsParameter
    public void setMessageNotes(String messageNotes) {
        this.messageNotes = messageNotes;
    }

    @StrutsParameter
    public void setRecipientAddress(String recipientAddress) {
        this.recipientAddress = recipientAddress;
    }

    @StrutsParameter
    public void setRecipientCity(String recipientCity) {
        this.recipientCity = recipientCity;
    }

    @StrutsParameter
    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    @StrutsParameter
    public void setRecipientPostal(String recipientPostal) {
        this.recipientPostal = recipientPostal;
    }

    @StrutsParameter
    public void setRecipientProvince(String recipientProvince) {
        this.recipientProvince = recipientProvince;
    }

    @StrutsParameter
    public void setBillStatus(String billStatus) {
        this.billStatus = billStatus;
    }

    @StrutsParameter
    public void setBillingNo(String billingNo) {
        this.billingNo = billingNo;
    }

    @StrutsParameter
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    @StrutsParameter
    public void setBillPatient(String billPatient) {
        this.billPatient = billPatient;
    }
}
