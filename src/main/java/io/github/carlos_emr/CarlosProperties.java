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


package io.github.carlos_emr;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;

/**
 * Singleton class for managing CARLOS EMR system properties and configuration.
 *
 * <p>This class extends {@link Properties} to provide centralized access to
 * system-wide configuration settings including:</p>
 * <ul>
 *   <li>Database connection parameters</li>
 *   <li>File storage paths (documents, images, backups)</li>
 *   <li>Security and authentication settings</li>
 *   <li>Provincial billing configurations</li>
 *   <li>Integration settings (HL7, FHIR, etc.)</li>
 *   <li>UI customization options</li>
 * </ul>
 *
 * <p><strong>Important:</strong> This is a singleton class. Do not instantiate directly.
 * Use {@link #getInstance()} to obtain the instance.</p>
 *
 * <p><strong>Configuration File:</strong> Properties are loaded from the CARLOS properties
 * file (carlos.properties). Any changes to the properties file require a Tomcat restart to take effect.</p>
 *
 * <p><strong>Namespace Migration:</strong> This class includes validation to detect and
 * ignore deprecated namespace values (org.oscarehr.*, oscar.*) that should be migrated
 * to the new io.github.carlos_emr.carlos.* namespace.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * CarlosProperties props = CarlosProperties.getInstance();
 * String docDir = props.getProperty("DOCUMENT_DIR");
 * boolean isCaisiEnabled = props.isPropertyActive("caisi_enabled");
 * </pre>
 *
 * @since 2001-09-11 (originally OscarProperties, renamed to CarlosProperties 2026-02-21)
 */
public class CarlosProperties extends Properties {
    private static final long serialVersionUID = -5965807410049845132L;
    private static CarlosProperties carlosProperties = new CarlosProperties();
    private static final Set<String> activeMarkers = new HashSet<String>(Arrays.asList(new String[]{"true", "yes", "on"}));

    /**
     * Blacklisted namespace patterns for property values that should be ignored.
     * These represent deprecated package names from the namespace migration.
     */
    private static final List<String> BLACKLISTED_NAMESPACES = Arrays.asList(
        "org.oscarehr.",
        "oscar."
    );

    /**
     * Default values for properties that may have blacklisted values.
     * These provide fallback values when properties contain deprecated namespaces
     * (org.oscarehr.* or oscar.*) that need to be migrated to the new io.github.carlos_emr.carlos.* namespace.
     */
    private static final Map<String, String> PROPERTY_DEFAULTS = Map.of(
        "hibernate.dialect", "io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect",
        "ColourClass", "io.github.carlos_emr.carlos.casemgmt.common.Colour"
    );

    /**
     * Gets the singleton instance of CarlosProperties.
     *
     * @return CarlosProperties the singleton instance
     */
    public static CarlosProperties getInstance() {
        return carlosProperties;
    }

    /**
     * Gets a property value with validation to filter deprecated namespaces.
     *
     * <p>This method applies validation to detect and ignore deprecated namespace
     * values (org.oscarehr.*, oscar.*), returning configured defaults when applicable.</p>
     *
     * @param key String the property key
     * @return String the validated property value, or null if not found
     */
    public String getProperty(String key) {
        if (key.equals("FORMS_PROMOTEXT")) {
            return "";
        }

        if (key == null || key.trim().isEmpty()) {
            MiscUtils.getLogger().warn("Attempted to retrieve property with blank key.");
            throw new IllegalArgumentException("Property key cannot be blank.");
        }

        String value = super.getProperty(key);

        // If no value, return the default if one is configured
        if (value == null) {
            String warning = new StringBuilder()
                .append("Property '").append(key)
                .append("' is missing or not configured. Using default value: '")
                .append(getDefaultValue(key)).append("'.")
                .toString();
            MiscUtils.getLogger().warn(warning);
            return getDefaultValue(key);
        }

        // Check if the value contains a blacklisted namespace
        if (isValueBlacklisted(value)) {
            String warning = new StringBuilder()
                .append("Property '").append(key)
                .append("' has blacklisted value '").append(value)
                .append("' (deprecated namespace). ")
                .append("This value has been ignored. Using default value instead.")
                .toString();
            MiscUtils.getLogger().warn(warning);
            return getDefaultValue(key);
        }

        return value;
    }

    /**
     * Checks if a property value contains a blacklisted namespace pattern.
     *
     * @param value String the property value to check
     * @return boolean true if the value contains a blacklisted namespace
     */
    private boolean isValueBlacklisted(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        for (String blacklistedNamespace : BLACKLISTED_NAMESPACES) {
            if (value.startsWith(blacklistedNamespace)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the default value for a property key if one is configured.
     *
     * @param key String the property key
     * @return String the default value, or null if no default is configured
     */
    private String getDefaultValue(String key) {
        return PROPERTY_DEFAULTS.get(key);
    }

    /* Do not use this constructor. Use getInstance instead */
    private CarlosProperties() {
        MiscUtils.getLogger().debug("CARLOS PROPS CONSTRUCTOR");

        try {
            readFromFile("/carlos.properties");

            String overrideProperties = System.getProperty("carlos_override_properties");
            if (overrideProperties != null) {
                MiscUtils.getLogger().info("Applying override properties : " + overrideProperties);
                readFromFile(overrideProperties);
            }
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    /**
     * Reads properties from the specified file path or classpath resource.
     *
     * @param url String the file path or classpath resource URL
     * @throws IOException if an I/O error occurs while reading the file
     */
    public void readFromFile(String url) throws IOException {
        InputStream is = getClass().getResourceAsStream(url);
        if (is == null) is = new FileInputStream(url);

        try {
            load(is);
        } finally {
            is.close();
        }
    }

    /**
     * Checks whether a property with the given key exists and has a non-null value.
     *
     * @param key String the property key to check
     * @return boolean true if the property exists and has a value
     */
    public boolean hasProperty(String key) {
        boolean prop = false;
        String propertyValue = getProperty(key.trim());
        if (propertyValue != null) {
            prop = true;
        }
        return prop;
    }

    /**
     * Checks whether a property is set to the given value.
     * This method returns positive response on any "true", "yes" or "on" values
     * when checking for a positive/active value.
     *
     * @param key String key of property
     * @param val String value that will cause a true value to be returned
     * @return boolean true if the property matches the given value
     */
    public boolean getBooleanProperty(String key, String val) {
        key = key == null ? null : key.trim();
        val = val == null ? null : val.trim();
        // if we're checking for positive value, any "active" one will do
        if (val != null && activeMarkers.contains(val.toLowerCase())) {
            return isPropertyActive(key);
        }

        return getProperty(key, "").trim().equalsIgnoreCase(val);
    }

    /**
     * Checks whether a property is set to an "active" value ("true", "yes", or "on").
     *
     * @param key String key of property
     * @return boolean true if the property value is "true", "yes", or "on" (case-insensitive)
     */
    public boolean isPropertyActive(String key) {
        key = key == null ? null : key.trim();
        return activeMarkers.contains(getProperty(key, "").trim().toLowerCase());
    }

    /**
     * Returns the application start time, if recorded in the properties.
     *
     * @return Date the start time, or null if not set or unparseable
     */
    public Date getStartTime() {
        String str = getProperty("OSCAR_START_TIME");
        Date ret = null;
        try {
            ret = new Date(Long.parseLong(str));
        } catch (Exception e) {/* No Date Found */
        }
        return ret;
    }

    /**
     * Checks if Toronto RFQ (Request for Quotation) mode is enabled.
     *
     * @return boolean true if TORONTO_RFQ property is active
     */
    public boolean isTorontoRFQ() {
        return isPropertyActive("TORONTO_RFQ");
    }

    /**
     * Checks if automatic provider number generation is enabled.
     *
     * @return boolean true if AUTO_GENERATE_PROVIDER_NO is active
     */
    public boolean isProviderNoAuto() {
        return isPropertyActive("AUTO_GENERATE_PROVIDER_NO");
    }

    /**
     * Checks if PIN encryption is enabled.
     *
     * @return boolean true if IS_PIN_ENCRYPTED is active
     */
    public boolean isPINEncripted() {
        return isPropertyActive("IS_PIN_ENCRYPTED");
    }

    /**
     * Checks if site-level security control is enabled.
     *
     * @return boolean true if security_site_control is active
     */
    public boolean isSiteSecured() {
        return isPropertyActive("security_site_control");
    }

    /**
     * Checks if admin option is enabled.
     *
     * @return boolean true if with_admin_option is active
     */
    public boolean isAdminOptionOn() {
        return isPropertyActive("with_admin_option");
    }

    /**
     * Checks if client access logging is enabled.
     *
     * @return boolean true if log_accesses_of_client is active
     */
    public boolean isLogAccessClient() {
        return isPropertyActive("log_accesses_of_client");
    }

    /**
     * Checks if program access logging is enabled.
     *
     * @return boolean true if log_accesses_of_program is active
     */
    public boolean isLogAccessProgram() {
        return isPropertyActive("log_accesses_of_program");
    }

    /**
     * Checks if account locking after failed login attempts is enabled.
     *
     * @return boolean true if ENABLE_ACCOUNT_LOCKING is active
     */
    public boolean isAccountLockingEnabled() {
        return isPropertyActive("ENABLE_ACCOUNT_LOCKING");
    }

    /**
     * Checks if the billing region is set to Ontario.
     *
     * @return boolean true if billregion equals "ON"
     */
    public boolean isOntarioBillingRegion() {
        return ("ON".equals(getProperty("billregion")));
    }

    /**
     * Checks if the billing region is set to British Columbia.
     *
     * @return boolean true if billregion equals "BC"
     */
    public boolean isBritishColumbiaBillingRegion() {
        return ("BC".equals(getProperty("billregion")));
    }

    /**
     * Checks if the billing region is set to Alberta.
     *
     * @return boolean true if billregion equals "AB"
     */
    public boolean isAlbertaBillingRegion() {
        return ("AB".equals(getProperty("billregion")));
    }

    /**
     * Checks if the CAISI (Client Access to Integrated Services and Information) module is loaded.
     *
     * @return boolean true if caisi property is active
     */
    public boolean isCaisiLoaded() {
        return isPropertyActive("caisi");
    }

    /**
     * Gets the database type (e.g., "mysql", "postgresql").
     *
     * @return String the database type
     */
    public String getDbType() {
        return getProperty("db_type");
    }

    /**
     * Gets the database username.
     *
     * @return String the database username
     */
    public String getDbUserName() {
        return getProperty("db_username");
    }

    /**
     * Gets the database password.
     *
     * @return String the database password
     */
    public String getDbPassword() {
        return getProperty("db_password");
    }

    /**
     * Gets the database URI (JDBC connection prefix).
     *
     * @return String the database URI
     */
    public String getDbUri() {
        return getProperty("db_uri");
    }

    /**
     * Gets the database JDBC driver class name.
     *
     * @return String the JDBC driver class name
     */
    public String getDbDriver() {
        return getProperty("db_driver");
    }

    /**
     * Gets the build date from properties.
     *
     * @return String the build date
     */
    public static String getBuildDate() {
        return carlosProperties.getProperty("buildDate");
    }

    /**
     * Gets the build tag/version from properties.
     *
     * @return String the build tag
     */
    public static String getBuildTag() {
        return carlosProperties.getProperty("buildVersion");
    }

    /**
     * Checks if the legacy fax feature is enabled via the enableFax property.
     *
     * @return boolean true if enableFax is active
     */
    public boolean faxEnabled() {
        return isPropertyActive("enableFax");
    }

    /**
     * Checks if prescription faxing is enabled.
     *
     * @return boolean true if rx_fax_enabled is active
     */
    public boolean isRxFaxEnabled() {
        return isPropertyActive("rx_fax_enabled");
    }

    /**
     * Checks if consultation request faxing is enabled.
     *
     * @return boolean true if consultation_fax_enabled is active
     */
    public boolean isConsultationFaxEnabled() {
        return isPropertyActive("consultation_fax_enabled");
    }

    /**
     * Checks if eForm signature support is enabled.
     *
     * @return boolean true if eform_signature_enabled is active
     */
    public boolean isEFormSignatureEnabled() {
        return isPropertyActive("eform_signature_enabled");
    }

    /**
     * Checks if eForm faxing is enabled.
     *
     * @return boolean true if eform_fax_enabled is active
     */
    public boolean isEFormFaxEnabled() {
        return isPropertyActive("eform_fax_enabled");
    }

    /**
     * Checks if any fax feature is enabled (legacy fax, Rx fax, consultation fax, or eForm fax).
     *
     * @return boolean true if any fax-related property is active
     */
    public boolean isFaxEnabled() {
        return faxEnabled() || isRxFaxEnabled() || isConsultationFaxEnabled() || isEFormFaxEnabled();
    }

    /**
     * Checks if prescription signature support is enabled.
     * Signatures are enabled if either Rx fax or Rx signature is active.
     *
     * @return boolean true if rx_fax_enabled or rx_signature_enabled is active
     */
    public boolean isRxSignatureEnabled() {
        return isRxFaxEnabled() || isPropertyActive("rx_signature_enabled");
    }

    /**
     * Checks if consultation signature support is enabled.
     *
     * @return boolean true if consultation_signature_enabled is active
     */
    public boolean isConsultationSignatureEnabled() {
        return isPropertyActive("consultation_signature_enabled");
    }

    /**
     * Checks if the Spire lab reporting client is enabled.
     *
     * @return boolean true if SPIRE_CLIENT_ENABLED is active
     */
    public boolean isSpireClientEnabled() {
        return isPropertyActive("SPIRE_CLIENT_ENABLED");
    }

    /**
     * Gets the Spire client polling frequency in seconds.
     *
     * @return int the run frequency in seconds
     */
    public int getSpireClientRunFrequency() {
        String prop = getProperty("spire_client_run_frequency");
        return Integer.parseInt(prop);
    }

    /**
     * Gets the Spire server SSH/SFTP username.
     *
     * @return String the Spire server username
     */
    public String getSpireServerUser() {
        return getProperty("spire_server_user");
    }

    /**
     * Gets the Spire server SSH/SFTP password.
     *
     * @return String the Spire server password
     */
    public String getSpireServerPassword() {
        return getProperty("spire_server_password");
    }

    /**
     * Gets the Spire server hostname.
     *
     * @return String the Spire server hostname
     */
    public String getSpireServerHostname() {
        return getProperty("spire_server_hostname");
    }

    /**
     * Gets the local directory for Spire lab file downloads.
     *
     * @return String the Spire download directory path
     */
    public String getSpireDownloadDir() {
        return getProperty("spire_download_dir");
    }

    /**
     * Gets the directory where HL7 A04 messages are built/staged.
     *
     * @return String the HL7 A04 build directory path
     */
    public String getHL7A04BuildDirectory() {
        return getProperty("hl7_a04_build_dir");
    }

    /**
     * Gets the directory where successfully sent HL7 A04 messages are archived.
     *
     * @return String the HL7 A04 sent directory path
     */
    public String getHL7A04SentDirectory() {
        return getProperty("hl7_a04_sent_dir");
    }

    /**
     * Gets the directory where failed HL7 A04 messages are stored.
     *
     * @return String the HL7 A04 fail directory path
     */
    public String getHL7A04FailDirectory() {
        return getProperty("hl7_a04_fail_dir");
    }

    /**
     * Gets the HL7 sending application identifier (MSH-3).
     *
     * @return String the HL7 sending application name
     */
    public String getHL7SendingApplication() {
        return getProperty("HL7_SENDING_APPLICATION");
    }

    /**
     * Gets the HL7 sending facility identifier (MSH-4).
     *
     * @return String the HL7 sending facility name
     */
    public String getHL7SendingFacility() {
        return getProperty("HL7_SENDING_FACILITY");
    }

    /**
     * Gets the HL7 receiving application identifier (MSH-5).
     *
     * @return String the HL7 receiving application name
     */
    public String getHL7ReceivingApplication() {
        return getProperty("HL7_RECEIVING_APPLICATION");
    }

    /**
     * Gets the HL7 receiving facility identifier (MSH-6).
     *
     * @return String the HL7 receiving facility name
     */
    public String getHL7ReceivingFacility() {
        return getProperty("HL7_RECEIVING_FACILITY");
    }

    /**
     * Checks if HL7 A04 (patient registration) message generation is enabled.
     *
     * @return boolean true if HL7_A04_GENERATION is active
     */
    public boolean isHL7A04GenerationEnabled() {
        return isPropertyActive("HL7_A04_GENERATION");
    }

    /**
     * Checks if Emerald HL7 A04 transport task is enabled.
     *
     * @return boolean true if EMERALD_HL7_A04_TRANSPORT_TASK is active
     */
    public boolean isEmeraldHL7A04TransportTaskEnabled() {
        return isPropertyActive("EMERALD_HL7_A04_TRANSPORT_TASK");
    }

    /**
     * Gets the Emerald HL7 A04 transport IP address.
     *
     * @return String the transport address
     */
    public String getEmeraldHL7A04TransportAddr() {
        return getProperty("EMERALD_HL7_A04_TRANSPORT_ADDR");
    }

    /**
     * Gets the Emerald HL7 A04 transport port number.
     *
     * @return int the transport port (defaults to 3987)
     */
    public int getEmeraldHL7A04TransportPort() {
        String prop = getProperty("EMERALD_HL7_A04_TRANSPORT_PORT", "3987"); // default to port 3987
        return Integer.parseInt(prop);
    }

    /**
     * Gets the program access service ID for intake forms.
     *
     * @return String the intake program access service ID
     */
    public static String getIntakeProgramAccessServiceId() {
        return carlosProperties.getProperty("form_intake_program_access_service_id");
    }

    /**
     * Gets the program cash service ID for intake forms.
     *
     * @return String the intake program cash service ID
     */
    public static String getIntakeProgramCashServiceId() {
        return carlosProperties.getProperty("form_intake_program_cash_service_id");
    }

    /**
     * Gets the program access facility ID for intake forms.
     *
     * @return String the intake program access facility ID
     */
    public static String getIntakeProgramAccessFId() {
        return carlosProperties.getProperty("form_intake_program_access_fid");
    }

    /**
     * Gets the latest versioned confidentiality statement for printed materials.
     * Iterates through confidentiality_statement.v1, v2, v3, etc. and returns the last one found.
     *
     * @return String the latest confidentiality statement, or null if none configured
     */
    public static String getConfidentialityStatement() {
        String result = null;
        int count = 1;
        String statement = null;
        while ((statement = carlosProperties.getProperty("confidentiality_statement.v" + count)) != null) {
            count++;
            result = statement;
        }
        return result;
    }

    /**
     * Gets the program cash facility ID for intake forms.
     *
     * @return String the intake program cash facility ID
     */
    public static String getIntakeProgramCashFId() {
        return carlosProperties.getProperty("form_intake_program_cash_fid");
    }

    /**
     * Checks if LDAP authentication is enabled.
     *
     * @return boolean true if ldap.enabled property is "true"
     */
    public static boolean isLdapAuthenticationEnabled() {
        return Boolean.parseBoolean(carlosProperties.getProperty("ldap.enabled"));
    }

    /**
     * Gets the document storage directory path.
     * Falls back to BASE_DOCUMENT_DIR/document if DOCUMENT_DIR is not set.
     *
     * @return String the document directory path
     */
    public String getDocumentDirectory() {
       String documents = carlosProperties.getProperty("DOCUMENT_DIR");

        // String value will equal null if property is not found
        if (documents == null) {
            // Setting derived path for documents incase starting path is not found
            documents = Paths.get(carlosProperties.getProperty("BASE_DOCUMENT_DIR"), "document").toString();
        }
       return documents;
    }

    /**
     * Gets the document cache directory path.
     *
     * @return String the document cache directory path, or null if not configured
     */
    public String getDocumentCacheDirectory() {
        return carlosProperties.getProperty("DOCUMENT_CACHE_DIR");
    }

    /**
     * Gets the eForm images directory path.
     * Falls back to BASE_DOCUMENT_DIR/eform/images if EFORM_IMAGES_DIR is not set.
     *
     * @return String the eForm images directory path
     */
    public String getEformImageDirectory() {
        String eform_images = carlosProperties.getProperty("EFORM_IMAGES_DIR");

        // String value will equal null if property is not found
        if (eform_images == null) {
            // Setting derived path for eform images incase starting path is not found
            eform_images = Paths.get(carlosProperties.getProperty("BASE_DOCUMENT_DIR"), "eform", "images").toString();
        }
        return eform_images;
    }

    /**
     * Returns the directory for inbound fax files downloaded from remote providers.
     * Falls back to ${catalina.base}/fax-incoming or system temp directory.
     *
     * @return String the fax incoming directory path
     */
    public String getFaxIncomingDirectory() {
        String faxIncoming = carlosProperties.getProperty("FAX_INCOMING_DIR");

        if (faxIncoming == null) {
            // Default to a path OUTSIDE the webroot for PHI protection.
            // catalina.base is the Tomcat instance root (e.g., /usr/local/tomcat/)
            // which is NOT under webapps/ and therefore not web-accessible.
            String catalinaBase = System.getProperty("catalina.base");
            if (catalinaBase != null && !catalinaBase.isEmpty()) {
                faxIncoming = Paths.get(catalinaBase, "fax-incoming").toString();
            } else {
                // Non-Tomcat environment (tests, standalone): use system temp
                faxIncoming = Paths.get(System.getProperty("java.io.tmpdir"), "carlos-fax-incoming").toString();
            }
        }
        return faxIncoming;
    }

	/**
	 * Saves property to the specified properties file.
	 * This method appends the new property to the end of the file.
	 * Updates the in-memory reference of the properties.
	 *
	 * @param propFilePath String the path to the properties file
	 * @param key          String the key of the property to be saved
	 * @param value        String the value of the property to be saved
	 * @throws IOException if an I/O error occurs while writing to the file
	 */
	public void saveProperty(String propFilePath, String key, String value) throws IOException {
		try (FileWriter writer = new FileWriter(propFilePath, true)) {
			// Write the new key-value pair
			writer.write("\n" + key + "=" + value + "\n");
			carlosProperties.setProperty(key, value);
		}
	}

}
