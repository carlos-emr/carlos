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

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.commn.dao.PropertyDao;
import ca.openosp.openo.commn.model.Property;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.billings.ca.bc.data.BillingPreference;
import ca.openosp.openo.billings.ca.bc.data.BillingPreferencesDAO;

import java.util.List;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for saving British Columbia billing preferences for a provider.
 * <p>
 * This action persists multiple types of billing preferences including:
 * <ul>
 * <li>Default billing form preference</li>
 * <li>Default billing provider</li>
 * <li>Default Teleplan service location (visit type)</li>
 * <li>Referral settings</li>
 * <li>Payee provider number</li>
 * <li>Invoice payee information display settings</li>
 * <li>GST number preferences</li>
 * <li>Auto-populate referral setting</li>
 * </ul>
 * <p>
 * All properties are automatically populated by Struts 2 from request parameters
 * before the execute() method is called.
 *
 * @since 2006-04-20
 */
public class SaveBillingPreferences2Action
        extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Saves British Columbia billing preferences for the specified provider.
     * <p>
     * This method persists or updates the following preferences:
     * <ul>
     * <li>Default billing form - stored in Property table</li>
     * <li>Default billing provider - stored in Property table (if provided)</li>
     * <li>Default service location (Teleplan visit type) - stored in Property table (if provided)</li>
     * <li>Auto-populate referral flag - stored in Property table</li>
     * <li>Invoice payee information - stored in Property table</li>
     * <li>Invoice display clinic info flag - stored in Property table</li>
     * <li>Referral number and payee provider - stored in BillingPreference table</li>
     * </ul>
     * <p>
     * For each preference, the method checks if a record exists and either creates
     * a new one or updates the existing record. The providerNo is set as a request
     * attribute for use by the result page.
     *
     * @return String "success" to forward to the result page
     */
    public String execute() {
        BillingPreferencesDAO dao = SpringUtils.getBean(BillingPreferencesDAO.class);
        PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);

        List<Property> defaultBillingFormPropertyList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.default_billing_form, this.getProviderNo());
        Property defaultBillingFormProperty = defaultBillingFormPropertyList.isEmpty() ? null : defaultBillingFormPropertyList.get(0);
        String selectedDefaultBillingForm = this.getDefaultBillingForm();
        if (defaultBillingFormProperty == null) {
            defaultBillingFormProperty = new Property();
            defaultBillingFormProperty.setValue(selectedDefaultBillingForm);
            defaultBillingFormProperty.setProviderNo(this.getProviderNo());
            defaultBillingFormProperty.setName(Property.PROPERTY_KEY.default_billing_form.name());
            propertyDao.persist(defaultBillingFormProperty);
        } else {
            defaultBillingFormProperty.setValue(selectedDefaultBillingForm);
            propertyDao.merge(defaultBillingFormProperty);
        }

        // Default Billing Provider
        String selectedDefaultBillingProvider = this.getDefaultBillingProvider();
        if (StringUtils.isNotEmpty(selectedDefaultBillingProvider)) {
            List<Property> defaultBillingProviderPropertyList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.default_billing_provider, this.getProviderNo());
            Property defaultBillingProviderProperty = defaultBillingProviderPropertyList.isEmpty() ? null : defaultBillingProviderPropertyList.get(0);
            if (defaultBillingProviderProperty == null) {
                defaultBillingProviderProperty = new Property();
                defaultBillingProviderProperty.setProviderNo(this.getProviderNo());
                defaultBillingProviderProperty.setValue(selectedDefaultBillingProvider);
                defaultBillingProviderProperty.setName(Property.PROPERTY_KEY.default_billing_provider.name());
                propertyDao.persist(defaultBillingProviderProperty);
            } else {
                defaultBillingProviderProperty.setValue(selectedDefaultBillingProvider);
                propertyDao.merge(defaultBillingProviderProperty);
            }
        }

        // Default Service Location
        String selectedDefaultServiceLocation = this.getDefaultServiceLocation();
        if (StringUtils.isNotEmpty(selectedDefaultServiceLocation)) {
            List<Property> defaultServiceLocationPropertyList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.bc_default_service_location, this.getProviderNo());
            Property defaultServiceLocationProperty = defaultServiceLocationPropertyList.isEmpty() ? null : defaultServiceLocationPropertyList.get(0);
            if (defaultServiceLocationProperty == null) {
                defaultServiceLocationProperty = new Property();
                defaultServiceLocationProperty.setProviderNo(this.getProviderNo());
                defaultServiceLocationProperty.setValue(selectedDefaultServiceLocation);
                defaultServiceLocationProperty.setName(Property.PROPERTY_KEY.bc_default_service_location.name());
                propertyDao.persist(defaultServiceLocationProperty);
            } else {
                defaultServiceLocationProperty.setValue(selectedDefaultServiceLocation);
                propertyDao.merge(defaultServiceLocationProperty);
            }
        }

        List<Property> propList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.invoice_payee_info, this.getProviderNo());
        Property invoicePayeeInfo = propList.isEmpty() ? null : propList.get(0);
        propList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.invoice_payee_display_clinic, this.getProviderNo());
        Property invoiceDisplayClinicInfo = propList.isEmpty() ? null : propList.get(0);


        propList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.auto_populate_refer, this.getProviderNo());
        Property autoPopulateRefer = propList.isEmpty() ? null : propList.get(0);
        if (autoPopulateRefer == null) {
            autoPopulateRefer = new Property();
            autoPopulateRefer.setValue(Boolean.toString(this.isAutoPopulateRefer()));
            autoPopulateRefer.setProviderNo(this.getProviderNo());
            autoPopulateRefer.setName(Property.PROPERTY_KEY.auto_populate_refer.name());
            propertyDao.persist(autoPopulateRefer);
        } else {
            autoPopulateRefer.setValue(Boolean.toString(this.isAutoPopulateRefer()));
            propertyDao.merge(autoPopulateRefer);
        }

        if (invoicePayeeInfo == null) {
            invoicePayeeInfo = new Property();
            invoicePayeeInfo.setName(Property.PROPERTY_KEY.invoice_payee_info.name());
            invoicePayeeInfo.setProviderNo(this.getProviderNo());
            invoicePayeeInfo.setValue(this.getInvoicePayeeInfo());
            propertyDao.persist(invoicePayeeInfo);
        } else {
            invoicePayeeInfo.setValue(this.getInvoicePayeeInfo());
            propertyDao.merge(invoicePayeeInfo);
        }

        if (invoiceDisplayClinicInfo == null) {
            invoiceDisplayClinicInfo = new Property();
            invoiceDisplayClinicInfo.setName(Property.PROPERTY_KEY.invoice_payee_display_clinic.name());
            invoiceDisplayClinicInfo.setProviderNo(this.getProviderNo());
            invoiceDisplayClinicInfo.setValue("" + this.isInvoicePayeeDisplayClinicInfo());
            propertyDao.persist(invoiceDisplayClinicInfo);
        } else {
            invoiceDisplayClinicInfo.setValue("" + this.isInvoicePayeeDisplayClinicInfo());
            propertyDao.merge(invoiceDisplayClinicInfo);
        }

        BillingPreference pref = dao.getUserBillingPreference(this.getProviderNo());
        if (pref == null) {
            pref = new BillingPreference();
            pref.setProviderNo(this.getProviderNo());
            pref.setReferral(Integer.parseInt(this.getReferral()));
            pref.setDefaultPayeeNo(this.getPayeeProviderNo());
            dao.persist(pref);
        } else {
            pref.setReferral(Integer.parseInt(this.getReferral()));
            pref.setDefaultPayeeNo(this.getPayeeProviderNo());
            dao.merge(pref);
        }
        request.setAttribute("providerNo", this.getProviderNo());
        return SUCCESS;
    }

    // Core provider and billing identifiers
    /** Provider number for whom preferences are being saved */
    private String providerNo;

    /** Referral number for billing purposes */
    private String referral;

    /** Default payee provider number for this provider's billing */
    private String payeeProviderNo;

    // GST preferences
    /** GST number for billing */
    private String gstNo;

    /** Whether to use the clinic's GST number instead of provider-specific GST */
    private boolean useClinicGstNo;

    // Referral preferences
    /** Whether to automatically populate referral information in billing forms */
    private boolean autoPopulateRefer;

    // Invoice payee display preferences
    /** Custom text to display for payee info when this provider is referenced as a payee on an invoice */
    private String invoicePayeeInfo;

    /** Whether to display clinic information on invoices where this provider is the payee */
    private boolean invoicePayeeDisplayClinicInfo;

    // Default billing form preferences
    /** Default billing form to use for this provider */
    private String defaultBillingForm;

    /** Form code for billing */
    private String formCode;

    /** Description of the billing form */
    private String description;

    // Default billing provider preference
    /** Default billing provider number to use when creating bills */
    private String defaultBillingProvider;

    // Default Teleplan service location preference (BC-specific)
    /** Default Teleplan service location code (visit type) for BC MSP billing */
    private String defaultServiceLocation;

    public String getProviderNo() {

        return providerNo;
    }

    public void setProviderNo(String providerNo) {

        this.providerNo = providerNo;
    }

    public void setReferral(String referral) {
        this.referral = referral;
    }

    public void setPayeeProviderNo(String payeeProviderNo) {
        this.payeeProviderNo = payeeProviderNo;
    }

    public String getReferral() {
        return referral;
    }

    public String getPayeeProviderNo() {
        return payeeProviderNo;
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

    public boolean isAutoPopulateRefer() {
        return autoPopulateRefer;
    }

    public void setAutoPopulateRefer(boolean autoPopulateRefer) {
        this.autoPopulateRefer = autoPopulateRefer;
    }
}
