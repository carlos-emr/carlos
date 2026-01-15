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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.model.Demographic;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.PropertyDao;
import ca.openosp.openo.commn.model.Property;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.billings.ca.bc.data.BillRecipient;
import ca.openosp.openo.billings.ca.bc.data.BillingPreference;
import ca.openosp.openo.billings.ca.bc.data.BillingPreferencesDAO;
import ca.openosp.openo.billings.ca.bc.pageUtil.BillingBillingManager.BillingItem;
import ca.openosp.openo.demographic.data.DemographicData;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class BillingView2Action
        extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();

    public String execute() throws IOException,
            ServletException {

        Properties oscarVars = OscarProperties.getInstance();

        if (oscarVars.getProperty("billregion").equals("ON")) {
            String newURL = "/billing/CA/ON/billingOB2.jsp";
            newURL = newURL + "?" + request.getQueryString();
            response.sendRedirect(newURL);
            return NONE;
        } else {
            BillingViewBean bean = new BillingViewBean();
            bean.loadBilling(request.getParameter("billing_no"));
            BillingBillingManager bmanager = new BillingBillingManager();
            ArrayList<BillingItem> billItem = new ArrayList<BillingItem>();
            String[] billingN = request.getParameterValues("billing_no");

            for (int i = 0; i < billingN.length; i++) {
                log.debug("billn " + i + " " + billingN[i]);
                ArrayList<BillingItem> tempBillItem = bmanager.getBillView(billingN[i]);
                billItem.addAll(tempBillItem);
            }

            log.debug("Calling getGrandTotal");
            bean.setBillItem(billItem);

            bean.calculateSubtotal();
            log.debug("GrandTotal" + bmanager.getGrandTotal(billItem));
            bean.setGrandtotal(bmanager.getGrandTotal(billItem));
            DemographicData demoData = new DemographicData();
            log.debug("Calling Demo");

            Demographic demo = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), bean.getPatientNo());
            bean.setPatientLastName(demo.getLastName());
            bean.setPatientFirstName(demo.getFirstName());
            bean.setPatientDoB(demo.getDateOfBirth());
            bean.setPatientAddress1(demo.getAddress());
            bean.setPatientAddress2(demo.getCity());
            bean.setPatientPostal(demo.getPostal());
            bean.setPatientSex(demo.getSex());
            bean.setPatientPHN(demo.getHin() + demo.getVer());
            bean.setPatientHCType(demo.getHcType());
            bean.setPatientAge(demo.getAge());
            this.setBillingNo(bean.getBillingNo());
            log.debug("End Demo Call billing No" + request.getParameter("billing_no"));
            //Loading bill Recipient Data
            List<BillRecipient> billRecipList = bean.getBillRecipient(request.getParameter("billing_no"));
            if (!billRecipList.isEmpty()) {
                log.debug("Filling recep with last details");
                BillRecipient rec = billRecipList.get(0);
                this.setRecipientAddress(rec.getAddress());
                this.setRecipientCity(rec.getCity());
                this.setRecipientName(rec.getName());
                this.setRecipientPostal(rec.getPostal());
                this.setRecipientProvince(rec.getProvince());
                this.setBillPatient("0");
            } else {
                log.debug("Filling recep with demo details");
                this.setRecipientName(demo.getFirstName() + " " + demo.getLastName());
                this.setRecipientCity(demo.getCity());
                this.setRecipientAddress(demo.getAddress());
                this.setRecipientPostal(demo.getPostal());
                this.setRecipientProvince(demo.getProvince());
                this.setBillPatient("1");
            }
            this.setMessageNotes(bean.getMessageNotes());
            this.setBillStatus(bean.getBillingType());
            this.setPaymentMethod(bean.getPaymentMethod());
            request.getSession().setAttribute("billingViewBean", bean);
            String receipt = request.getParameter("receipt");
            if (receipt != null && receipt.equals("yes")) {

                BillingPreferencesDAO billingPreferencesDAO = SpringUtils.getBean(BillingPreferencesDAO.class);
                BillingPreference pref = billingPreferencesDAO.getUserBillingPreference(bean.getBillingProvider());

                if (pref == null || "NONE".equals(pref.getDefaultPayeeNo())) {
                    bean.setDefaultPayeeInfo("");
                } else if ("CUSTOM".equals(pref.getDefaultPayeeNo())) {
                    PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);
                    List<Property> propList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.invoice_payee_info, bean.getBillingProvider());
                    bean.setDefaultPayeeInfo(!propList.isEmpty() ? propList.get(0).getValue() : "");
                } else {
                    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
                    Provider p = providerDao.getProvider(pref.getDefaultPayeeNo());
                    bean.setDefaultPayeeInfo(p != null ? p.getFormattedName() : "");
                }
                return "private";
            }


            return SUCCESS;
        }
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
