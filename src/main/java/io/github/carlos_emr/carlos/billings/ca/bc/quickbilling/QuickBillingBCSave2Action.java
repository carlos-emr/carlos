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

package io.github.carlos_emr.carlos.billings.ca.bc.quickbilling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;



/**
 * @author Dennis Warren
 * Company Colcamex Resources
 * Date Jun 4, 2012
 * Revised Jun 6, 2012
 * Comment
 * One action here: save the collection of bills from the
 * session form bean.
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData;
import io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSessionBean;

public class QuickBillingBCSave2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    public QuickBillingBCSave2Action() {
    }


    public String execute()
            throws ServletException, IOException {

        if (request.getSession().getAttribute("user") == null) {
            return "Logout";
        }

        QuickBillingBCHandler quickBillingHandler = new QuickBillingBCHandler();

        if (quickBillingHandler.saveBills()) {

            quickBillingHandler.reset();
            request.setAttribute("saved", quickBillingHandler.getNumberSaved());
            return "saved";

        } else {

            request.setAttribute("saved", Boolean.valueOf(false));
            return "error";

        }

    }
    private ArrayList<BillingSessionBean> billingData;
    private String billingProvider;
    private String billingProviderNo;
    private String serviceDate;
    private String visitLocation;
    private List<BillingFormData.BillingVisit> billingVisitTypes;
    private List<ProviderData> providerList;
    private Boolean isHeaderSet;
    private String creator;
    private String halfBilling;

    public String getHalfBilling() {
        return halfBilling;
    }

    @StrutsParameter
    public void setHalfBilling(String halfBilling) {
        this.halfBilling = halfBilling;
    }

    public String getCreator() {
        return creator;
    }

    @StrutsParameter
    public void setCreator(String creator) {
        this.creator = creator;
    }

    @StrutsParameter
    public void setIsHeaderSet(Boolean set) {
        this.isHeaderSet = set;
    }

    public Boolean getIsHeaderSet() {
        return isHeaderSet;
    }

    @StrutsParameter(depth = 1)
    public List<ProviderData> getProviderList() {
        return providerList;
    }

    @StrutsParameter
    public void setProviderList(List<ProviderData> providerList) {
        this.providerList = providerList;
    }

    public String getBillingProviderNo() {
        return billingProviderNo;
    }

    @StrutsParameter
    public void setBillingProviderNo(String billingProviderNo) {
        this.billingProviderNo = billingProviderNo;
    }

    @StrutsParameter(depth = 1)
    public List<BillingFormData.BillingVisit> getBillingVisitTypes() {
        return billingVisitTypes;
    }

    @StrutsParameter
    public void setBillingVisitTypes(List<BillingFormData.BillingVisit> billingVisitTypes) {
        this.billingVisitTypes = billingVisitTypes;
    }

    public String getBillingProvider() {
        return billingProvider;
    }

    @StrutsParameter
    public void setBillingProvider(String billingProvider) {
        this.billingProvider = billingProvider;
    }

    public String getServiceDate() {
        return serviceDate;
    }

    @StrutsParameter
    public void setServiceDate(String serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getVisitLocation() {
        return visitLocation;
    }

    @StrutsParameter
    public void setVisitLocation(String visitLocation) {
        this.visitLocation = visitLocation;
    }

    @StrutsParameter(depth = 1)
    public ArrayList<BillingSessionBean> getBillingData() {
        return billingData;
    }

    @StrutsParameter
    public void setBillingData(ArrayList<BillingSessionBean> billingData) {
        this.billingData = billingData;
    }
}
