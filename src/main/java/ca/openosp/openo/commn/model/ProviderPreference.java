//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package ca.openosp.openo.commn.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.PostLoad;
import javax.persistence.PreUpdate;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.ElementCollection;
//import org.hibernate.annotations.CollectionOfElements;

import ca.openosp.OscarProperties;

@Entity
public class ProviderPreference extends AbstractModel<String> implements Serializable {

    @Embeddable
    public static class QuickLink {
        private String name;
        private String url;

        public QuickLink() {

        }

        public QuickLink(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    @Embeddable
    public static class EformLink {
        private int appointmentScreenEForm;
        private String eFormName;

        public EformLink() {

        }

        public EformLink(int appointmentScreenEForm, String eFormName) {
            this.appointmentScreenEForm = appointmentScreenEForm;
            this.eFormName = eFormName;
        }

        public int getAppointmentScreenEForm() {
            return appointmentScreenEForm;
        }

        public void setAppointmentScreenEForm(int appointmentScreenEForm) {
            this.appointmentScreenEForm = appointmentScreenEForm;
        }

        public String geteFormName() {
            return eFormName;
        }

        public void seteFormName(String eFormName) {
            this.eFormName = eFormName;
        }
    }

    @Id
    private String providerNo;
    private Integer startHour = 8;
    private Integer endHour = 18;
    private Integer everyMin = 15;
    private String myGroupNo = null;
    private String colourTemplate = "deepblue";
    private String newTicklerWarningWindow = "disabled";
    private String defaultServiceType = "no";
    private String defaultCaisiPmm = "disabled";
    private String defaultNewOscarCme = "disabled";
    private boolean printQrCodeOnPrescriptions = Boolean.valueOf(OscarProperties.getInstance().getProperty("QR_CODE_ENABLED_PROVIDER_DEFAULT"));
    private int appointmentScreenLinkNameDisplayLength = 3;
    private int defaultDoNotDeleteBilling = 0;
    private String defaultDxCode = null;

    /**
     * Whether or not the providers has enabled the use of an external
     * prescription service.
     */
    private boolean eRxEnabled = false;
    /**
     * The URL of the external prescription SSO (Single Sign On) www service that is used on the rx ERx link.
     * <p>
     * FUTURE: Once ca.openosp.openo.rx.erx supports more than one external
     * prescription service, this field should return the URL for the service
     * that the providers has chosen to use. At this point, the property oscarRx_SSO_URL
     * will probably go away.
     */
    private String eRx_SSO_URL = OscarProperties.getInstance().getProperty("util.erx.oscarRx_sso_url");
    /**
     * The providers's username on the external prescription www service.
     * <p>
     * FUTURE: ca.openosp.openo.rx.erx should, in the future, support more than
     * one external prescription service, which may not necessarily require a
     * username. Storing and retrieving some sort of authentication structure
     * (the contents of which depend on the external prescription service that
     * the providers has chosen to use) would be better.
     */
    private String eRxUsername = null;
    /**
     * The providers's password on the external prescription www service.
     * <p>
     * FUTURE: ca.openosp.openo.rx.erx should, in the future, support more than
     * one external prescription service, which may not necessarily require a
     * password. Storing and retrieving some sort of authentication structure
     * (the contents of which depend on the external prescription service that
     * the providers has chosen to use) would be better.
     */
    private String eRxPassword = null;
    /**
     * The ID of the facility that the providers is associated with in the external
     * prescription service's authentication system.
     * <p>
     * FUTURE: ca.openosp.openo.rx.erx should, in the future, support more than
     * one external prescription service, which may not necessarily require a
     * facilityId. Storing and retrieving some sort of authentication structure
     * (the contents of which depend on the external prescription service that
     * the providers has chosen to use) would be better.
     */
    private String eRxFacility = null;
    /**
     * Whether or not the providers has enabled training mode in the external prescriber
     */
    private boolean eRxTrainingMode = false;


    private String defaultBillingLocation;


    @ElementCollection
    @JoinTable(name = "ProviderPreferenceAppointmentScreenForm", joinColumns = @JoinColumn(name = "providerNo"))
    @Column(name = "appointmentScreenForm")
    private Collection<String> appointmentScreenForms = new HashSet<>();

    @ElementCollection
    @JoinTable(name = "ProviderPreferenceAppointmentScreenEForm", joinColumns = @JoinColumn(name = "providerNo"))
    @Column(name = "appointmentScreenEForm")
    private Collection<EformLink> appointmentScreenEForms = new HashSet<>();

    @ElementCollection
    @JoinTable(name = "ProviderPreferenceAppointmentScreenQuickLink", joinColumns = @JoinColumn(name = "providerNo"))
    private Collection<QuickLink> appointmentScreenQuickLinks = new HashSet<>();

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdated = new Date();

    @PostLoad
    protected void hibernatePreFetchCollectionsFix() {
        // forces eager fetching which can't be done normally as hibernate doesn't allow mulitple collection eager fetching
        appointmentScreenForms.size();
        appointmentScreenEForms.size();
        appointmentScreenQuickLinks.size();
    }

    @PreUpdate
    protected void jpaUpdateLastUpdateTime() {
        lastUpdated = new Date();
    }

    @Override
    public String getId() {
        return (providerNo);
    }

    public String getProviderNo() {
        return providerNo;
    }

    /**
     * The providerNo is also the primarykey, as such it should not
     * be changed on a persisted record (only set it before persistence, i.e. upon creation)
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Integer getStartHour() {
        return startHour;
    }

    public void setStartHour(Integer startHour) {
        this.startHour = startHour;
    }

    public Integer getEndHour() {
        return endHour;
    }

    public void setEndHour(Integer endHour) {
        this.endHour = endHour;
    }

    public Integer getEveryMin() {
        return everyMin;
    }

    public void setEveryMin(Integer everyMin) {
        this.everyMin = everyMin;
    }

    public String getMyGroupNo() {
        return myGroupNo;
    }

    public void setMyGroupNo(String myGroupNo) {
        this.myGroupNo = myGroupNo;
    }

    public String getColourTemplate() {
        return colourTemplate;
    }

    public void setColourTemplate(String colourTemplate) {
        this.colourTemplate = colourTemplate;
    }

    public String getNewTicklerWarningWindow() {
        return newTicklerWarningWindow;
    }

    public void setNewTicklerWarningWindow(String newTicklerWarningWindow) {
        this.newTicklerWarningWindow = newTicklerWarningWindow;
    }

    public String getDefaultServiceType() {
        return defaultServiceType;
    }

    public void setDefaultServiceType(String defaultServiceType) {
        this.defaultServiceType = defaultServiceType;
    }

    public String getDefaultBillingLocation() {
        return defaultBillingLocation;
    }

    public void setDefaultBillingLocation(String defaultBillingLocation) {
        this.defaultBillingLocation = defaultBillingLocation;
    }

    public String getDefaultCaisiPmm() {
        return defaultCaisiPmm;
    }

    public void setDefaultCaisiPmm(String defaultCaisiPmm) {
        this.defaultCaisiPmm = defaultCaisiPmm;
    }

    public String getDefaultNewOscarCme() {
        return defaultNewOscarCme;
    }

    public void setDefaultNewOscarCme(String defaultNewOscarCme) {
        this.defaultNewOscarCme = defaultNewOscarCme;
    }

    public boolean isPrintQrCodeOnPrescriptions() {
        return printQrCodeOnPrescriptions;
    }

    public void setPrintQrCodeOnPrescriptions(boolean printQrCodeOnPrescriptions) {
        this.printQrCodeOnPrescriptions = printQrCodeOnPrescriptions;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public Collection<String> getAppointmentScreenForms() {
        return appointmentScreenForms;
    }

    public Collection<EformLink> getAppointmentScreenEForms() {
        return appointmentScreenEForms;
    }

    public int getAppointmentScreenLinkNameDisplayLength() {
        return appointmentScreenLinkNameDisplayLength;
    }

    public void setAppointmentScreenLinkNameDisplayLength(int appointmentScreenLinkNameDisplayLength) {
        this.appointmentScreenLinkNameDisplayLength = appointmentScreenLinkNameDisplayLength;
    }

    public int getDefaultDoNotDeleteBilling() {
        return defaultDoNotDeleteBilling;
    }

    public void setDefaultDoNotDeleteBilling(int defaultDoNotDeleteBilling) {
        this.defaultDoNotDeleteBilling = defaultDoNotDeleteBilling;
    }

    public String getDefaultDxCode() {
        return defaultDxCode;
    }

    public void setDefaultDxCode(String defaultDxCode) {
        this.defaultDxCode = defaultDxCode;
    }

    public Collection<QuickLink> getAppointmentScreenQuickLinks() {
        return appointmentScreenQuickLinks;
    }

    /**
     * Returns whether or not the providers has enabled the use of an external
     * prescription service.
     *
     * @return TRUE if the ERx service is enabled, FALSE otherwise.
     */
    public boolean isERxEnabled() {
        return this.eRxEnabled;
    }

    /**
     * Returns the ID of the facility that the providers is associated with in the
     * external prescription service's authentication system.
     *
     * @return The current value of eRxFacility.
     */
    public String getERxFacility() {
        return this.eRxFacility;
    }

    /**
     * Returns the providers's password on the external prescription www service.
     *
     * @return The current value of eRxPassword.
     */
    public String getERxPassword() {
        return this.eRxPassword;
    }

    /**
     * Returns the providers's username on the external prescription www service.
     *
     * @return The current value of eRxUsername.
     */
    public String getERxUsername() {
        return this.eRxUsername;
    }

    /**
     * Returns whether or not the providers has enabled the training mode of the external
     * prescription service.
     *
     * @return TRUE if the training mode is enabled, FALSE otherwise.
     */
    public boolean isERxTrainingMode() {
        return eRxTrainingMode;
    }

    /**
     * Returns the URL of the external prescription www service.
     *
     * @return The current value of eRx_SSO_URL.
     */
    public String getERx_SSO_URL() {
        return this.eRx_SSO_URL;
    }

    /**
     * Change whether or not the providers has enabled the use of an external
     * prescription service.
     *
     * @param eRxEnabled TRUE to enable the use of an external prescription service;
     *                   FALSE otherwise.
     */
    public void setERxEnabled(boolean eRxEnabled) {
        this.eRxEnabled = eRxEnabled;
    }

    /**
     * Change the ID of the facility that the doctor is associated with in the
     * external prescription service's authentication system.
     *
     * @param eRxFacility the new eRxFacility
     */
    public void setERxFacility(String eRxFacility) {
        this.eRxFacility = eRxFacility;
    }

    /**
     * @param eRxPassword the new eRxPassword
     */
    public void setERxPassword(String eRxPassword) {
        this.eRxPassword = eRxPassword;
    }

    /**
     * @param eRxUsername the eRxUsername to set
     */
    public void setERxUsername(String eRxUsername) {
        this.eRxUsername = eRxUsername;
    }

    /**
     * Change whether or not the providers has enabled the training mode of the external
     * prescription service.
     *
     * @param eRxTrainingMode TRUE to enable the use of an external prescription service;
     *                        FALSE otherwise.
     */
    public void setERxTrainingMode(boolean eRxTrainingMode) {
        this.eRxTrainingMode = eRxTrainingMode;
    }

    /**
     * @param eRx_SSO_URL the eRx_SSO_URL to set
     */
    public void setERx_SSO_URL(String eRx_SSO_URL) {
        this.eRx_SSO_URL = eRx_SSO_URL;
    }



}
