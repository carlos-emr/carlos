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


import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData.BillingVisit;
import io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSessionBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;


/**
 * The BC quick billing form: the header (billing physician, service location, service date) and the
 * bills added so far. It lives in the HTTP session under {@link #SESSION_ATTRIBUTE} from the page's
 * first open until the bills are saved, because the page adds and removes one bill per request.
 * Struts 1 kept it in session scope ({@code scope="session"}); the Struts 2 actions use
 * {@link #fromSession(HttpSession)} to do the same. It is serializable, as the Struts 1 form was, so a
 * session the container saves and restores (a restart or redeploy) keeps the bills added so far.
 */
public class QuickBillingBCFormBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Session attribute that holds the form. {@code quickBillingBC.jsp} reads it as
     * {@code quickBillingBC}.
     *
     * @since 2026-10-16
     */
    public static final String SESSION_ATTRIBUTE = "quickBillingBC";

    /** Guards the get-or-create in {@link #fromSession(HttpSession)}, so two first requests share one form. */
    private static final Object SESSION_LOCK = new Object();

    /**
     * Guards the bill list. It is private, so no other code can hold it, and an empty array, so it is
     * serializable and a restored form gets its own.
     */
    private final Serializable billsLock = new Object[0];

    /**
     * Returns this session's quick billing form, storing a new empty one (no header, no bills) on
     * first use.
     *
     * @param session the signed-in user's session
     * @return the session's form, never {@code null}
     * @since 2026-10-16
     */
    public static QuickBillingBCFormBean fromSession(HttpSession session) {
        synchronized (SESSION_LOCK) {
            if (session.getAttribute(SESSION_ATTRIBUTE) instanceof QuickBillingBCFormBean form) {
                return form;
            }
            QuickBillingBCFormBean form = new QuickBillingBCFormBean();
            form.setBillingData(new ArrayList<BillingSessionBean>());
            session.setAttribute(SESSION_ATTRIBUTE, form);
            return form;
        }
    }

    /** A second Submit must not process invoices already being written by the first request. */
    private transient boolean savingBills;

    /**
     * Reserves a snapshot for one save request, retaining the queued invoices until they commit.
     * A concurrent Submit gets {@code null}; it must not clear the first request's queue or header.
     */
    public ArrayList<BillingSessionBean> beginBillingSave() {
        synchronized (billsLock) {
            if (savingBills) {
                return null;
            }
            savingBills = true;
            return getBillingData();
        }
    }

    /** Removes only committed invoices; failed and newly added invoices remain available to retry. */
    public void finishBillingSave(List<BillingSessionBean> saved) {
        synchronized (billsLock) {
            if (billingData != null) {
                billingData.removeAll(saved);
            }
            savingBills = false;
            if (billingData == null || billingData.isEmpty()) {
                isHeaderSet = false;
                billingProvider = "";
                billingProviderNo = "";
                serviceDate = "";
                visitLocation = "";
            }
        }
    }

    /**
     * Adds one bill to the list.
     *
     * @param bill the bill to add
     * @return {@code true}, as {@link java.util.List#add(Object)}
     * @since 2026-10-16
     */
    public boolean addBill(BillingSessionBean bill) {
        synchronized (billsLock) {
            if (billingData == null) {
                billingData = new ArrayList<BillingSessionBean>();
            }
            return billingData.add(bill);
        }
    }

    /**
     * Removes the bill at a row of the list.
     *
     * @param index the row the page showed
     * @return {@code true} when the row was removed; {@code false}, with nothing removed, when it is not a
     *         current row (for example a second click on Remove for the last row)
     * @since 2026-10-16
     */
    public boolean removeBill(int index) {
        synchronized (billsLock) {
            if (savingBills || billingData == null || index < 0 || index >= billingData.size()) {
                return false;
            }
            billingData.remove(index);
            return true;
        }
    }

    /**
     * @author Dennis Warren
     * Company Colcamex Resources
     * Date Jun 4, 2012
     */
    private ArrayList<BillingSessionBean> billingData;
    private String billingProvider;
    private String billingProviderNo;
    private String serviceDate;
    private String visitLocation;
    // The page's choice lists are rebuilt on every open, so they are not kept when a session is restored.
    private transient List<BillingVisit> billingVisitTypes;
    private transient List<ProviderData> providerList;
    private Boolean isHeaderSet;
    private String creator;
    private String halfBilling;


    public QuickBillingBCFormBean() {

        this.billingProvider = "";
        this.billingProviderNo = "";
        this.serviceDate = "";
        this.visitLocation = "";
        this.creator = "";
        this.isHeaderSet = false;
        this.halfBilling = "";

    }


    public String getHalfBilling() {
        return halfBilling;
    }


    public void setHalfBilling(String halfBilling) {
        this.halfBilling = halfBilling;
    }


    public String getCreator() {
        return creator;
    }


    public void setCreator(String creator) {
        this.creator = creator;
    }


    public void setIsHeaderSet(Boolean set) {
        this.isHeaderSet = set;
    }

    public Boolean getIsHeaderSet() {
        return isHeaderSet;
    }

    public List<ProviderData> getProviderList() {
        return providerList;
    }

    public void setProviderList(List<ProviderData> providerList) {
        this.providerList = providerList;
    }


    public String getBillingProviderNo() {
        return billingProviderNo;
    }


    public void setBillingProviderNo(String billingProviderNo) {
        this.billingProviderNo = billingProviderNo;
    }


    public List<BillingVisit> getBillingVisitTypes() {
        return billingVisitTypes;
    }


    public void setBillingVisitTypes(List<BillingVisit> billingVisitTypes) {
        this.billingVisitTypes = billingVisitTypes;
    }


    public String getBillingProvider() {
        return billingProvider;
    }


    public void setBillingProvider(String billingProvider) {
        this.billingProvider = billingProvider;
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


    /**
     * The bills added so far, for showing them: a copy taken under the bill list's lock, so the page can
     * go through it while another request of the same session adds or removes a bill. Changes go
     * through {@link #addBill}, {@link #removeBill} and {@link #beginBillingSave} and {@link #finishBillingSave}.
     *
     * @return a copy of the bill list, never {@code null}
     */
    public ArrayList<BillingSessionBean> getBillingData() {
        synchronized (billsLock) {
            return billingData == null ? new ArrayList<BillingSessionBean>() : new ArrayList<BillingSessionBean>(billingData);
        }
    }

    public void setBillingData(ArrayList<BillingSessionBean> billingData) {
        synchronized (billsLock) {
            this.billingData = billingData;
        }
    }

    @Override
    public String toString() {
        return (
                " PROVIDER=" + billingProvider +
                        " PROVIDER NUMBER=" + billingProviderNo +
                        " SERVICE DATE=" + serviceDate +
                        " VISIT LOCATION=" + visitLocation +
                        " IS HEADER SET=" + isHeaderSet +
                        " CREATOR=" + creator +
                        " BILL DATA=" + billingData.size() + " ENTRY(S)"
        );
    }


}
