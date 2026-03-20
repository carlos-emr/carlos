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
package io.github.carlos_emr.carlos.login;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import io.github.carlos_emr.carlos.commn.service.AcceptableUseAgreementManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Bean that provides customizable login page resources and branding configuration for CARLOS EMR.
 *
 * <p>This bean loads login page customization settings from a .env file in the BASE_DOCUMENT_DIR/login
 * directory, allowing clinics to customize their login page appearance and support information without
 * modifying code.
 *
 * <p>Customizable properties (loaded from BASE_DOCUMENT_DIR/login/.env):
 * <ul>
 *   <li>clinicName - Name of the clinic/organization displayed on login page</li>
 *   <li>clinicText - Descriptive text about the clinic</li>
 *   <li>clinicLink - URL to clinic website</li>
 *   <li>supportName - Name of support contact/organization</li>
 *   <li>supportText - Support contact information text</li>
 *   <li>supportLink - URL to support resources or help desk</li>
 *   <li>tabName - Browser tab title for login page</li>
 * </ul>
 *
 * <p>System-provided properties (from CarlosProperties):
 * <ul>
 *   <li>buildTag - Software build version and date</li>
 *   <li>econsultURL - Ontario eConsult integration URL (if configured)</li>
 * </ul>
 *
 * <p>Integration features:
 * <ul>
 *   <li>Acceptable use agreement management via {@link AcceptableUseAgreementManager}</li>
 *   <li>Ontario eConsult integration (legacy, may not be active)</li>
 * </ul>
 *
 * <p>Configuration file location:
 * <pre>
 * {BASE_DOCUMENT_DIR}/login/.env
 * </pre>
 *
 * <p>Example .env file:
 * <pre>
 * clinicName=My Family Practice
 * clinicText=Serving the community since 1995
 * clinicLink=https://www.myfamilypractice.ca
 * supportName=IT Helpdesk
 * supportText=For technical support, call 1-800-555-1234
 * supportLink=https://support.myfamilypractice.ca
 * tabName=My Practice EMR Login
 * </pre>
 *
 * <p>NOTE: All setter methods ignore their parameters and set fields to null.
 * This appears to be a defensive pattern to prevent external modification of
 * configuration loaded from files.
 *
 * @see AcceptableUseAgreementManager for acceptable use agreement enforcement
 * @since 2026-02-10
 */
public class LoginResourceBean {

    /** URL to clinic website (loaded from .env file) */
    private String supportLink;

    /** Support contact information text (loaded from .env file) */
    private String supportText;

    /** Descriptive text about the clinic (loaded from .env file) */
    private String clinicText;

    /** URL to clinic website (loaded from .env file) */
    private String clinicLink;

    /** Name of the clinic/organization (loaded from .env file) */
    private String clinicName;

    /** Name of support contact/organization (loaded from .env file) */
    private String supportName;

    /** Browser tab title for login page (loaded from .env file) */
    private String tabName;

    /** Software build version and date (from CarlosProperties) */
    private String buildTag;

    /** Ontario eConsult integration URL (from CarlosProperties, legacy feature) */
    private String econsultURL;

    /** Manager for acceptable use agreement enforcement */
    private AcceptableUseAgreementManager acceptableUseAgreementManager;

    /**
     * Constructs a new LoginResourceBean and loads customization settings.
     *
     * <p>This constructor:
     * <ol>
     *   <li>Loads custom properties from {BASE_DOCUMENT_DIR}/login/.env</li>
     *   <li>Populates clinic and support fields from .env properties</li>
     *   <li>Sets buildTag from CarlosProperties build date and tag</li>
     *   <li>Loads eConsult URL from backendEconsultUrl property</li>
     *   <li>Initializes AcceptableUseAgreementManager</li>
     * </ol>
     *
     * <p>If the .env file doesn't exist or can't be read, the customization
     * fields will remain null and the login page will use default branding.
     *
     * <p>NOTE: Ontario eConsult integration may no longer be an active service.
     * The eConsultURL field is populated for backward compatibility but may
     * require verification before use.
     */
    public LoginResourceBean() {
        CarlosProperties oscarProperties = CarlosProperties.getInstance();
        // Construct path to login customization file
        String oscarDocuments = oscarProperties.getProperty("BASE_DOCUMENT_DIR") + File.separator + "login";
        Properties loginProperties = new Properties();
        File propertiesFile = new File(oscarDocuments, ".env");

        // Load custom properties from .env file if it exists
        if (propertiesFile.exists()) {
            try (FileInputStream fileInputStream = new FileInputStream(propertiesFile)) {
                loginProperties.load(fileInputStream);
            } catch (Exception e) {
                MiscUtils.getLogger().warn("Problem with fetching login resources " + e);
            }
        }

        // Populate clinic branding fields from loaded properties
        if (loginProperties.containsKey("clinicText")) {
            this.clinicText = loginProperties.getProperty("clinicText");
        }

        if (loginProperties.containsKey("clinicLink")) {
            this.clinicLink = loginProperties.getProperty("clinicLink");
        }

        if (loginProperties.containsKey("clinicName")) {
            this.clinicName = loginProperties.getProperty("clinicName");
        }

        if (loginProperties.containsKey("supportName")) {
            this.supportName = loginProperties.getProperty("supportName");
        }

        if (loginProperties.containsKey("supportText")) {
            this.supportText = loginProperties.getProperty("supportText");
        }

        if (loginProperties.containsKey("supportLink")) {
            this.supportLink = loginProperties.getProperty("supportLink");
        }

        if (loginProperties.containsKey("tabName")) {
            this.tabName = loginProperties.getProperty("tabName");
        }

        // Set build version information from system properties
        this.buildTag = CarlosProperties.getBuildDate() + " " + CarlosProperties.getBuildTag();

        // Load eConsult URL (legacy Ontario eConsult integration)
        // NOTE: Ontario eConsult service status is uncertain, verify before use
        this.econsultURL = oscarProperties.getProperty("backendEconsultUrl");

        // Initialize acceptable use agreement manager
        this.acceptableUseAgreementManager = new AcceptableUseAgreementManager();
    }

    /**
     * Gets the support link URL.
     *
     * @return String URL to support resources or help desk
     */
    public String getSupportLink() {
        return supportLink;
    }

    /**
     * Setter that ignores parameter and sets field to null (defensive pattern).
     *
     * @param supportLink String parameter is ignored
     */
    public void setSupportLink(@SuppressWarnings("unused") String supportLink) {
        this.supportLink = null;
    }

    /**
     * Gets the support contact information text.
     *
     * @return String support contact information text
     */
    public String getSupportText() {
        return supportText;
    }

    /**
     * Setter that ignores parameter and sets field to null (defensive pattern).
     *
     * @param supportText String parameter is ignored
     */
    public void setSupportText(@SuppressWarnings("unused") String supportText) {
        this.supportText = null;
    }

    /**
     * Gets the clinic descriptive text.
     *
     * @return String descriptive text about the clinic
     */
    public String getClinicText() {
        return clinicText;
    }

    /**
     * Setter that ignores parameter and sets field to null (defensive pattern).
     *
     * @param clinicText String parameter is ignored
     */
    public void setClinicText(@SuppressWarnings("unused") String clinicText) {
        this.clinicText = null;
    }

    /**
     * Gets the clinic website URL.
     *
     * @return String URL to clinic website
     */
    public String getClinicLink() {
        return clinicLink;
    }

    /**
     * Setter that ignores parameter and sets field to null (defensive pattern).
     *
     * @param clinicLink String parameter is ignored
     */
    public void setClinicLink(@SuppressWarnings("unused") String clinicLink) {
        this.clinicLink = null;
    }

    /**
     * Gets the clinic/organization name.
     *
     * @return String name of the clinic/organization
     */
    public String getClinicName() {
        return clinicName;
    }

    /**
     * Setter that ignores parameter and sets field to null (defensive pattern).
     *
     * @param clinicName String parameter is ignored
     */
    public void setClinicName(@SuppressWarnings("unused") String clinicName) {
        this.clinicName = null;
    }

    /**
     * Gets the software build version and date.
     *
     * @return String build version and date (e.g., "2026-02-10 v1.2.3")
     */
    public String getBuildTag() {
        return buildTag;
    }

    /**
     * Setter that ignores parameter and sets field to null (defensive pattern).
     *
     * @param buildTag String parameter is ignored
     */
    public void setBuildTag(@SuppressWarnings("unused") String buildTag) {
        this.buildTag = null;
    }

    /**
     * Gets the Ontario eConsult integration URL.
     *
     * <p>NOTE: Ontario eConsult service status is uncertain. Verify service
     * is still active before using this URL.
     *
     * @return String eConsult backend URL or null if not configured
     */
    public String getEconsultURL() {
        return econsultURL;
    }

    /**
     * Setter that ignores parameter and sets field to null (defensive pattern).
     *
     * @param econsultURL String parameter is ignored
     */
    public void setEconsultURL(@SuppressWarnings("unused") String econsultURL) {
        this.econsultURL = null;
    }

    /**
     * Gets the acceptable use agreement manager.
     *
     * @return AcceptableUseAgreementManager manager for acceptable use agreement enforcement
     */
    public AcceptableUseAgreementManager getAcceptableUseAgreementManager() {
        return acceptableUseAgreementManager;
    }

    /**
     * Setter that ignores parameter and sets field to null (defensive pattern).
     *
     * @param acceptableUseAgreementManager AcceptableUseAgreementManager parameter is ignored
     */
    public void setAcceptableUseAgreementManager(@SuppressWarnings("unused") AcceptableUseAgreementManager acceptableUseAgreementManager) {
        this.acceptableUseAgreementManager = null;
    }

    /**
     * Gets the support contact/organization name.
     *
     * @return String name of support contact/organization
     */
    public String getSupportName() {
        return supportName;
    }

    /**
     * Setter that ignores parameter and sets field to null (defensive pattern).
     *
     * @param supportName String parameter is ignored
     */
    public void setSupportName(@SuppressWarnings("unused") String supportName) {
        this.supportName = null;
    }

    /**
     * Gets the browser tab title for login page.
     *
     * @return String browser tab title
     */
    public String getTabName() {
        return tabName;
    }

    /**
     * Sets the browser tab title for login page.
     *
     * @param tabName String browser tab title
     */
    public void setTabName(String tabName) {
        this.tabName = tabName;
    }

}
