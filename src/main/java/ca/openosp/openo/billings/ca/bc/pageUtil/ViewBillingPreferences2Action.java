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

import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.PropertyDao;
import ca.openosp.openo.commn.model.Property;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.billings.ca.bc.data.BillingFormData;
import ca.openosp.openo.billings.ca.bc.data.BillingPreference;
import ca.openosp.openo.billings.ca.bc.data.BillingPreferencesDAO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * Forwards flow of control to Billing Preferences Screen
 *
 * @version 1.0
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ViewBillingPreferences2Action
        extends ActionSupport {
    HttpServletRequest servletRequest = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private final BillingPreferencesDAO dao = SpringUtils.getBean(BillingPreferencesDAO.class);
    private final PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);
    private final ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(servletRequest);
        List<Property> propList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.invoice_payee_info, this.getProviderNo());
        Property invoicePayeeInfo = propList.isEmpty() ? null : propList.get(0);

        if (invoicePayeeInfo == null || invoicePayeeInfo.getValue() == null) {
            Provider provider = providerDao.getProvider(this.getProviderNo());
            this.setInvoicePayeeInfo(provider.getFirstName() + " " + provider.getLastName());
        } else {
            this.setInvoicePayeeInfo(invoicePayeeInfo.getValue());
        }
        //Default to true when nothing is set
        this.setInvoicePayeeDisplayClinicInfo(propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.invoice_payee_display_clinic, this.getProviderNo()).isEmpty() || propertyDao.isActiveBooleanProperty(Property.PROPERTY_KEY.invoice_payee_display_clinic, this.getProviderNo()));

        BillingPreference pref = dao.getUserBillingPreference(this.getProviderNo());
        //If the user doesn't have a BillingPreference record create one
        if (pref == null) {
            pref = new BillingPreference();
            pref.setProviderNo(this.getProviderNo());
            dao.saveUserPreferences(pref);
        }
        BillingFormData billform = new BillingFormData();
        ArrayList billingFormList = new ArrayList<>();
        BillingFormData.BillingForm defaultBillingForm = billform.new BillingForm("Clinic Default", Property.PROPERTY_VALUE.clinicdefault.name());
        billingFormList.add(defaultBillingForm);
        billingFormList.addAll(Arrays.asList(billform.getFormList()));
        servletRequest.setAttribute("billingFormList", billingFormList);

        List<Property> defaultBillingFormPropertyList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.default_billing_form, this.getProviderNo());
        this.setDefaultBillingForm(defaultBillingFormPropertyList.isEmpty() ? null : defaultBillingFormPropertyList.get(0).getValue());

        List<Property> defaultBillingProviderPropertyList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.default_billing_provider, this.getProviderNo());
        this.setDefaultBillingProvider(defaultBillingProviderPropertyList.isEmpty() ? null : defaultBillingProviderPropertyList.get(0).getValue());

        this.setReferral(String.valueOf(pref.getReferral()));
        this.setPayeeProviderNo(String.valueOf(pref.getDefaultPayeeNo()));

        List<Provider> providerList = providerDao.getBillableProvidersInBC(loggedInInfo);
        // add a selection to trigger a "custom" option
        Provider customProvider = new Provider("CUSTOM", "", null, null, null, "Custom");
        Provider blankProvider = new Provider("NONE", "", null, null, null, "");
        if (providerList == null) {
            providerList = Arrays.asList(customProvider, blankProvider);
        } else {
            providerList.add(customProvider);
            providerList.add(blankProvider);
        }
        servletRequest.setAttribute("providerList", providerList);

        providerList = providerDao.getProvidersWithNonEmptyOhip(loggedInInfo);
        // add the clinic default selection to the select list
        Provider defaultProvider = new Provider(Property.PROPERTY_VALUE.clinicdefault.name(), "", null, null, null, "Clinic Default");
        if (providerList == null) {
            providerList = Collections.singletonList(defaultProvider);
        } else {
            providerList.add(defaultProvider);
        }
        servletRequest.setAttribute("billingProviderList", providerList);

        // Check for a per-provider property and if none set it to CLINICDEFAULT
        List<Property> defaultServiceLocationPropertyList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.bc_default_service_location, this.getProviderNo());
        if (!defaultServiceLocationPropertyList.isEmpty()) {
            this.setDefaultServiceLocation(defaultServiceLocationPropertyList.get(0).getValue());
        } else {
            this.setDefaultServiceLocation(Property.PROPERTY_VALUE.clinicdefault.name());
        }

        // Prepare a formatted list of service locations
        String billRegion = OscarProperties.getInstance().getProperty("billregion", "");
        BillingFormData billingFormData = new BillingFormData();
        ArrayList<BillingFormData.BillingVisit> billingVisits = new ArrayList<>();
        billingVisits.add(new BillingFormData.BillingVisit(Property.PROPERTY_VALUE.clinicdefault.name(), "Clinic Default"));
        billingVisits.addAll(billingFormData.getVisitType(billRegion));
        servletRequest.setAttribute("serviceLocationList", billingVisits);

        // Prepare a list of default yes/no/clinic values for use on the preferences page (eg. display dx2/3)
        Map<String, String> defaultYesNoList = new HashMap<>();
        defaultYesNoList.put(Property.PROPERTY_VALUE.clinicdefault.name(), "Clinic Default");
        defaultYesNoList.put("true", "Yes");
        defaultYesNoList.put("false", "No");
        servletRequest.setAttribute("defaultYesNoList", defaultYesNoList);

        return SUCCESS;
    }

    private String providerNo;
    private String referral;
    private String payeeProviderNo;
    private String gstNo;
    private boolean useClinicGstNo;
    private boolean autoPopulateRefer;

    //What to display for payee info when this providers gets referred to as a payee on an invoice
    private String invoicePayeeInfo;
    private boolean invoicePayeeDisplayClinicInfo;

    //Default billing form preference
    private String defaultBillingForm;
    private String formCode;
    private String description;

    //Default billing provider preference
    private String defaultBillingProvider;

    //Default Teleplan service location (visittype)
    private String defaultServiceLocation;

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getReferral() {
        return referral;
    }

    public void setReferral(String referral) {
        this.referral = referral;
    }

    public String getPayeeProviderNo() {
        return payeeProviderNo;
    }

    public void setPayeeProviderNo(String payeeProviderNo) {
        this.payeeProviderNo = payeeProviderNo;
    }

    public String getGstNo() {
        return gstNo;
    }

    public void setGstNo(String gstNo) {
        this.gstNo = gstNo;
    }

    public boolean isUseClinicGstNo() {
        return useClinicGstNo;
    }

    public void setUseClinicGstNo(boolean useClinicGstNo) {
        this.useClinicGstNo = useClinicGstNo;
    }

    public boolean isAutoPopulateRefer() {
        return autoPopulateRefer;
    }

    public void setAutoPopulateRefer(boolean autoPopulateRefer) {
        this.autoPopulateRefer = autoPopulateRefer;
    }

    public String getInvoicePayeeInfo() {
        return invoicePayeeInfo;
    }

    public void setInvoicePayeeInfo(String invoicePayeeInfo) {
        this.invoicePayeeInfo = invoicePayeeInfo;
    }

    public boolean isInvoicePayeeDisplayClinicInfo() {
        return invoicePayeeDisplayClinicInfo;
    }

    public void setInvoicePayeeDisplayClinicInfo(boolean invoicePayeeDisplayClinicInfo) {
        this.invoicePayeeDisplayClinicInfo = invoicePayeeDisplayClinicInfo;
    }

    public String getDefaultBillingForm() {
        return defaultBillingForm;
    }

    public void setDefaultBillingForm(String defaultBillingForm) {
        this.defaultBillingForm = defaultBillingForm;
    }

    public String getFormCode() {
        return formCode;
    }

    public void setFormCode(String formCode) {
        this.formCode = formCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultBillingProvider() {
        return defaultBillingProvider;
    }

    public void setDefaultBillingProvider(String defaultBillingProvider) {
        this.defaultBillingProvider = defaultBillingProvider;
    }

    public String getDefaultServiceLocation() {
        return defaultServiceLocation;
    }

    public void setDefaultServiceLocation(String defaultServiceLocation) {
        this.defaultServiceLocation = defaultServiceLocation;
    }
}
