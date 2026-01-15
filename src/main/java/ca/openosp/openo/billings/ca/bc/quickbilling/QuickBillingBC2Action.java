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

package ca.openosp.openo.billings.ca.bc.quickbilling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.openosp.openo.commn.model.ProviderData;
import ca.openosp.openo.utility.LoggedInInfo;

import ca.openosp.openo.billings.ca.bc.data.BillingFormData;
import ca.openosp.openo.billings.ca.bc.data.BillingFormData.BillingVisit;

/**
 * @author Dennis Warren
 * Company Colcamex Resources
 * Date Jun 4, 2012
 * Revised Jun 6, 2012
 * Comment
 * three actions here
 * 1. get display
 * 2. add entry to bean
 * 3. remove entry from bean
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.billings.ca.bc.pageUtil.BillingSessionBean;

public class QuickBillingBC2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ObjectNode billingEntry;
    private QuickBillingBCHandler quickBillingHandler;

    public QuickBillingBC2Action() {
    }

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws ServletException, IOException {
        String creator = (String) request.getSession().getAttribute("user");
        if (creator == null) {
            return "Logout";
        }
        quickBillingHandler = new QuickBillingBCHandler();

        if (request.getParameter("data") != null) {

            billingEntry = (ObjectNode) objectMapper.readTree(request.getParameter("data"));
            billingEntry.put("creator", creator);

            // check if the main header items are set.
            // if not then set them otherwise go on to get the other input.
            if (!this.getHeaderSet()) {

                quickBillingHandler.setHeader(billingEntry);
                this.setHeaderSet(true);
            }

            // add data to the quick billing session form bean
            quickBillingHandler.addBill(LoggedInInfo.getLoggedInInfoFromSession(request), this.billingEntry);

            return SUCCESS;

            // if request is to remove an entry.
        } else if (request.getParameter("remove") != null) {

            if (quickBillingHandler.removeBill(request.getParameter("remove"))) {

                return SUCCESS;

            }

            // if not adding or removing data then create a fresh form.
        } else {

            // add some needed form elements to the bean
            BillingFormData billingFormData = new BillingFormData();
            List<BillingVisit> billingVisit = billingFormData.getVisitType(QuickBillingBCHandler.BILLING_PROV);

            List<ProviderData> activeProviders = quickBillingHandler.getProviderDao().findAll(false);

            quickBillingHandler.reset();
            this.setProviderList(activeProviders);
            this.setBillingVisitTypes(billingVisit);
        }

        return SUCCESS;
    }
    private ArrayList<BillingSessionBean> billingData;
    private String billingProvider;
    private String billingProviderNo;
    private String serviceDate;
    private String visitLocation;
    private List<BillingVisit> billingVisitTypes;
    private List<ProviderData> providerList;
    private Boolean isHeaderSet;
    private String creator;
    private String halfBilling;

    public ArrayList<BillingSessionBean> getBillingData() {
        return billingData;
    }

    public void setBillingData(ArrayList<BillingSessionBean> billingData) {
        this.billingData = billingData;
    }

    public String getBillingProvider() {
        return billingProvider;
    }

    public void setBillingProvider(String billingProvider) {
        this.billingProvider = billingProvider;
    }

    public String getBillingProviderNo() {
        return billingProviderNo;
    }

    public void setBillingProviderNo(String billingProviderNo) {
        this.billingProviderNo = billingProviderNo;
    }

    public String getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(String serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getVisitLocation() {
        return visitLocation;
    }

    public void setVisitLocation(String visitLocation) {
        this.visitLocation = visitLocation;
    }

    public List<BillingVisit> getBillingVisitTypes() {
        return billingVisitTypes;
    }

    public void setBillingVisitTypes(List<BillingVisit> billingVisitTypes) {
        this.billingVisitTypes = billingVisitTypes;
    }

    public List<ProviderData> getProviderList() {
        return providerList;
    }

    public void setProviderList(List<ProviderData> providerList) {
        this.providerList = providerList;
    }

    public Boolean getHeaderSet() {
        return isHeaderSet;
    }

    public void setHeaderSet(Boolean headerSet) {
        isHeaderSet = headerSet;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getHalfBilling() {
        return halfBilling;
    }

    public void setHalfBilling(String halfBilling) {
        this.halfBilling = halfBilling;
    }
}
