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
import java.util.LinkedHashSet;

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
import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // The bill's number comes from the receipt's hidden field; without it there is nothing to
        // update, so refuse the request plainly instead of failing on the number (issue #4717).
        // The same shape the receipt view takes, so an accepted update never ends on a refused view.
        if (this.getBillingNo() == null || !this.getBillingNo().matches("\\d{1,9}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        // The receipt this update came from, shown again afterwards: the updated bill first, then
        // the receipt's other bills (a private bill is one row per service line). Anything but a
        // bill number is refused before any write.
        LinkedHashSet<String> receiptBills = new LinkedHashSet<>();
        receiptBills.add(this.getBillingNo());
        for (String receiptBillingNo : this.receiptBillingNo) {
            if (receiptBillingNo == null || !receiptBillingNo.matches("\\d{1,9}")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            receiptBills.add(receiptBillingNo);
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
        // The receipt posts no provider. Change the payee only when one is sent, so updating an
        // invoice's recipient or note never blanks it (issue #4717).
        String payee = request.getParameter("billingProvider");
        if (StringUtils.isNotBlank(payee)) {
            bean.updateBill(this.getBillingNo(), payee);
        }

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

        // Shown by the view action, which loads the receipt again from the database: rendering it
        // here read the invoice viewed last in any window (issue #4673).
        this.receiptView = "/billing/CA/BC/billingView?billing_no=" + String.join("&billing_no=", receiptBills)
                + "&receipt=yes";
        return SUCCESS;
    }

    /**
     * The receipt to show after the update: the view of the updated bill and of every other bill
     * its receipt combined, as an application-relative path built from validated bill numbers.
     *
     * @return the redirect target, or null before a successful update
     */
    public String getReceiptView() {
        return receiptView;
    }

    /**
     * The bill numbers of the receipt the update was posted from.
     *
     * @param receiptBillingNo every bill the receipt combines
     */
    @StrutsParameter
    public void setReceiptBillingNo(String[] receiptBillingNo) {
        this.receiptBillingNo = receiptBillingNo == null ? new String[0] : receiptBillingNo.clone();
    }

    /**
     * Creates a new instance of BillingUpdateBilling2Action
     */
    public BillingUpdateBilling2Action() {
    }
    private String[] receiptBillingNo = new String[0];
    private String receiptView;
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
