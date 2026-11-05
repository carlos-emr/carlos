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
 * Modifications by CARLOS Contributors, 2026.
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr;

import io.github.carlos_emr.carlos.utility.BuildInfo;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Singleton class for managing OpenO EMR system properties and configuration.
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
 */
public class CarlosProperties extends Properties {
    private static final long serialVersionUID = -5965807410049845132L;
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
            justification = "Deserialized properties must not inherit a writable source; only readFromFile establishes provenance.")
    private transient File loadedPropertiesFile;
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
            justification = "The source description is process-local provenance and is intentionally absent after deserialization.")
    private transient String loadedPropertiesLocation;
    // Keep file descriptors and their locks serialized among instances in this class loader.
    // The filesystem locks additionally coordinate separate application processes.
    private static final Object CONFIGURATION_FILE_LOCK = new Object();
    // Declared before the singleton: its constructor reads the loaded file and compares the stored
    // AbandonOldChart value against these markers, and static initializers run in textual order.
    private static final Set<String> activeMarkers = new HashSet<String>(Arrays.asList(new String[]{"true", "yes", "on"}));
    private static CarlosProperties carlosProperties = new CarlosProperties();
    /**
     * Keys already reported as "missing or not configured". That warning is per key per JVM, not
     * per lookup: several of these keys are read on every page render, and at the WARN root default
     * a per-lookup warning made the journal mostly this one line. The set is bounded as defence in
     * depth (keys are code literals, a few hundred at most, but the lookup API is open to any
     * caller): once {@link #MAX_MISSING_KEYS_REMEMBERED} distinct keys are held, further unknown
     * keys are still warned about, on every lookup, and simply not remembered.
     */
    private static final Set<String> MISSING_KEYS_WARNED = ConcurrentHashMap.newKeySet();
    private static final int MAX_MISSING_KEYS_REMEMBERED = 2048;

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
     * Once per key while the bounded set has room; every time once it is full. Never silent.
     */
    private static boolean shouldWarnMissingKey(String key) {
        if (MISSING_KEYS_WARNED.contains(key)) {
            return false;
        }
        if (MISSING_KEYS_WARNED.size() >= MAX_MISSING_KEYS_REMEMBERED) {
            return true;
        }
        return MISSING_KEYS_WARNED.add(key);
    }

    /**
     * Gets the singleton instance of CarlosProperties.
     * 
     * @return the CarlosProperties instance
     */
    public static CarlosProperties getInstance() {
        return carlosProperties;
    }

    /**
     * Gets a property value with validation to filter deprecated namespaces.
     * 
     * <p>This method applies validation to detect and ignore deprecated namespace
     * values (org.oscarehr.*, oscar.*), returning configured defaults when applicable.</p>
     * <p>{@code AbandonOldChart} always returns {@code "true"}, including when absent or
     * explicitly disabled, because the modern chart is mandatory.</p>
     *
     * @param key the property key
     * @return String the effective property value or configured default, or null if neither exists;
     *         {@code AbandonOldChart} always returns {@code "true"}
     * @throws IllegalArgumentException if the key is null or blank
     */
    public String getProperty(String key) {
        if (key == null || key.trim().isEmpty()) {
            MiscUtils.getLogger().warn("Attempted to retrieve property with blank key.");
            throw new IllegalArgumentException("Property key cannot be blank.");
        }

        if (key.equals("FORMS_PROMOTEXT")) {
            return "";
        }

        // The modern chart is mandatory (#4793), including on installs with an old false
        // override. Keep this at the shared reader so getProperty(key, defaultValue) and
        // boolean accessors agree. Remove the obsolete flag and legacy branches in #5599.
        if ("AbandonOldChart".equals(key)) {
            return "true";
        }

        String value = super.getProperty(key);

        // If no value, return the default if one is configured
        if (value == null) {
            if (shouldWarnMissingKey(key)) {
                // key is caller-supplied and may carry CR/LF; sanitize before logging to
                // prevent log forging (CodeQL log-injection). Default values come from the
                // internal PROPERTY_DEFAULTS map and are trusted.
                String warning = new StringBuilder()
                    .append("Property '").append(LogSafe.sanitize(key))
                    .append("' is missing or not configured. Using default value: '")
                    .append(getDefaultValue(key)).append("'.")
                    .toString();
                MiscUtils.getLogger().warn(warning);
            }
            return getDefaultValue(key);
        }

        // Check if the value contains a blacklisted namespace
        if (isValueBlacklisted(value)) {
            // Both key and value are untrusted (key is caller-supplied; value comes from
            // a properties file/override). Sanitize before logging to prevent log forging.
            String warning = new StringBuilder()
                .append("Property '").append(LogSafe.sanitize(key))
                .append("' has blacklisted value '").append(LogSafe.sanitize(value))
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
     * @param value the property value to check
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
     * @param key the property key
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
                String overrideLocation = LogSafe.sanitize(overrideProperties);
                MiscUtils.getLogger().info("Applying override properties : {}", overrideLocation);
                readFromFile(overrideProperties);
            }
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
        warnIfLegacyChartSettingIgnored();
    }

    /**
     * Reports, once at startup, a loaded {@code AbandonOldChart} value that the mandatory modern
     * chart policy (#4793) ignores. The stored value is left as written so the operator's file is
     * not rewritten, but an old {@code false} override must not read as the active mode.
     *
     * @return String the ignored stored value, or null when the setting is absent or agrees with the policy
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive match of a configuration boolean; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive match of a configuration boolean; not a security or authorization decision")
    String warnIfLegacyChartSettingIgnored() {
        String stored = super.getProperty("AbandonOldChart");
        if (stored == null || activeMarkers.contains(stored.trim().toLowerCase(Locale.ROOT))) {
            return null;
        }
        MiscUtils.getLogger().warn("AbandonOldChart={} in the loaded configuration is ignored: the modern chart is"
                + " mandatory (#4793) and the legacy eChart editor is retired. Remove the obsolete setting.",
                LogSafe.sanitize(stored));
        return stored;
    }

    /**
     * Loads configuration and records its source after the read completes successfully.
     * File reads share the locking protocol used by first-key initialization.
     *
     * @param url String classpath resource or filesystem path
     * @throws IOException if the configuration cannot be read
     */
    public void readFromFile(String url) throws IOException {
        synchronized (CONFIGURATION_FILE_LOCK) {
            InputStream resource = getClass().getResourceAsStream(url);
            File propertyFile = null;
            if (resource != null) {
                try (resource) {
                    load(resource);
                }
            } else {
                try {
                    propertyFile = PathValidationUtils.resolveConfiguredFile(url, "carlos properties file");
                } catch (SecurityException e) {
                    throw new IOException("Unable to resolve carlos properties file", e);
                }
                // FileInputStream retains the FileNotFoundException contract used by Startup.
                // Release the lock before closing either owner of the same file descriptor.
                try (FileInputStream input = new FileInputStream(propertyFile);
                     FileChannel channel = input.getChannel();
                     FileLock _ = channel.lock(0, Long.MAX_VALUE, true)) {
                    load(input);
                }
            }
            // Bundled resources are not operator-writable configuration files.
            loadedPropertiesFile = propertyFile;
            loadedPropertiesLocation = url;
        }
    }

    /**
     * Obtains a persisted encryption key from the loaded external configuration.
     * An exclusive file lock covers re-reading, generation, append and flush, so an
     * instance with an outdated blank-key snapshot adopts another instance's key.
     *
     * @return String the persisted key to install in the active configuration
     * @throws IOException if the loaded configuration cannot be updated
     * @throws NoSuchAlgorithmException if AES key generation is unavailable
     */
    public String getOrCreateEncryptionKey() throws IOException, NoSuchAlgorithmException {
        synchronized (CONFIGURATION_FILE_LOCK) {
            String keyName = EncryptionUtils.SECRET_KEY_ENV_VAR;
            if (loadedPropertiesFile == null) {
                throw new IOException("Configuration source " + loadedPropertiesLocation
                        + " is not an external properties file; refusing to generate an unpersisted encryption key. "
                        + "Set " + keyName + " in the loaded configuration, or supply a writable external properties "
                        + "file with -Dcarlos_override_properties=/path/to/carlos.properties before restarting. "
                        + "Restore the original key if encrypted data already exists. "
                        + "See https://github.com/carlos-emr/carlos/blob/release/2026.08/docs/startup-encryption-key.md "
                        + "for configuration and recovery instructions.");
            }
            // Do not create a replacement/stub if the loaded file has since disappeared.
            // Keep one descriptor open for both reading and writing throughout the lock.
            try (FileChannel channel = FileChannel.open(loadedPropertiesFile.toPath(),
                         StandardOpenOption.READ, StandardOpenOption.WRITE);
                 Reader reader = Channels.newReader(channel, StandardCharsets.ISO_8859_1);
                 FileLock _ = channel.lock()) {
                Properties persisted = new Properties();
                persisted.load(reader);
                String key = persisted.getProperty(keyName);
                if (key == null || key.isBlank()) {
                    key = EncryptionUtils.generateSecretKey();
                    // A trailing continuation may consume the first line terminator.
                    ByteBuffer entry = StandardCharsets.ISO_8859_1.encode("\n\n" + keyName + "=" + key + "\n");
                    channel.position(channel.size());
                    while (entry.hasRemaining()) {
                        channel.write(entry);
                    }
                    channel.force(true);
                }
                return key;
            } catch (IOException e) {
                throw new IOException("Unable to update loaded configuration file " + loadedPropertiesFile
                        + ". Set " + keyName + " in that file before restarting; restore the original key "
                        + "if encrypted data already exists.", e);
            }
        }
    }

    /*
     * Check to see if the properties to see if that property exists.
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
     * Will check the properties to see if that property is set and if it's set to the given value.
     * If it is method returns true if not method returns false.
     * This method returns positive response on any "true", "yes" or "on" values.
     *
     * @param key key of property
     * @param val value that will cause a true value to be returned
     * @return boolean
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
     * Will check the properties to see if that property is set and if it's set to "true", "yes" or "on".
     * If it is method returns true if not method returns false.
     *
     * @param key key of property
     * @return boolean whether the property is active
     */
    public boolean isPropertyActive(String key) {
        key = key == null ? null : key.trim();
        return activeMarkers.contains(getProperty(key, "").trim().toLowerCase());
    }

    /*
     * Comma delimited spring configuration modules Options: Caisi Caisi - Required to run the Caisi Shelter Management System.
     */

    /*
     * not being used - commenting out public final String ModuleNames = "ModuleNames";
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

    public boolean isTorontoRFQ() {
        return isPropertyActive("TORONTO_RFQ");
    }

    public boolean isProviderNoAuto() {
        return isPropertyActive("AUTO_GENERATE_PROVIDER_NO");
    }

    public boolean isPINEncripted() {
        return isPropertyActive("IS_PIN_ENCRYPTED");
    }

    public boolean isSiteSecured() {
        return isPropertyActive("security_site_control");
    }

    public boolean isAdminOptionOn() {
        return isPropertyActive("with_admin_option");
    }

    public boolean isLogAccessClient() {
        return isPropertyActive("log_accesses_of_client");
    }

    public boolean isLogAccessProgram() {
        return isPropertyActive("log_accesses_of_program");
    }

    public boolean isAccountLockingEnabled() {
        return isPropertyActive("ENABLE_ACCOUNT_LOCKING");
    }

    public boolean isOntarioBillingRegion() {
        return ("ON".equals(getProperty("billregion")));
    }

    public boolean isBritishColumbiaBillingRegion() {
        return ("BC".equals(getProperty("billregion")));
    }

    public boolean isAlbertaBillingRegion() {
        return ("AB".equals(getProperty("billregion")));
    }

    public boolean isCaisiLoaded() {
        return isPropertyActive("caisi");
    }

    public String getDbType() {
        return getProperty("db_type");
    }

    public String getDbUserName() {
        return getProperty("db_username");
    }

    public String getDbPassword() {
        return getProperty("db_password");
    }

    public String getDbUri() {
        return getProperty("db_uri");
    }

    public String getDbDriver() {
        return getProperty("db_driver");
    }

    /**
     * Build date of the deployed WAR. Read from the in-WAR build stamp via
     * {@link BuildInfo}, never from a properties override: a {@code buildDate} key in an
     * operator's carlos.properties copy is ignored so it cannot mask a WAR upgrade.
     *
     * @return the build date, or empty when the artifact carries no stamp
     */
    public static String getBuildDate() {
        return BuildInfo.getInstance().getBuildDate();
    }

    /**
     * Build tag of the deployed WAR (project version plus optional CI job / build number).
     * Read from the in-WAR build stamp via {@link BuildInfo}, never from a properties
     * override: a {@code buildVersion} key in an operator's carlos.properties copy is ignored.
     *
     * @return the build tag as described by {@link BuildInfo#getBuildTag()}
     */
    public static String getBuildTag() {
        return BuildInfo.getInstance().getBuildTag();
    }

    public boolean faxEnabled() {
        return isPropertyActive("enableFax");
    }

    public boolean isRxFaxEnabled() {
        return isPropertyActive("rx_fax_enabled");
    }

    public boolean isConsultationFaxEnabled() {
        return isPropertyActive("consultation_fax_enabled");
    }

    public boolean isEFormSignatureEnabled() {
        return isPropertyActive("eform_signature_enabled");
    }

    public boolean isEFormFaxEnabled() {
        return isPropertyActive("eform_fax_enabled");
    }

    public boolean isFaxEnabled() {
        return faxEnabled() || isRxFaxEnabled() || isConsultationFaxEnabled() || isEFormFaxEnabled();
    }

    public boolean isRxSignatureEnabled() {
        return isRxFaxEnabled() || isPropertyActive("rx_signature_enabled");
    }

    public boolean isConsultationSignatureEnabled() {
        return isPropertyActive("consultation_signature_enabled");
    }

    /**
     * Whether appointment statuses are editable: the add/edit appointment status pull-down,
     * the Administration "Appointment Status Setting" page link, and custom-status handling.
     *
     * <p>Enabled unless {@code ENABLE_EDIT_APPT_STATUS} is explicitly set to a non-active value
     * (anything other than {@code true}/{@code yes}/{@code on}). An absent or blank key counts as
     * enabled: the OSCAR-era installer wrote {@code yes}, but neither CARLOS installer (legacy
     * deb or turnkey .deb) sets it, and the shipped default was commented out, so defaulting off
     * silently degraded status to a free-text box prefilled with {@code t} and hid the admin page.
     *
     * @return {@code true} unless the key is set to a non-active value
     */
    public boolean isAppointmentStatusEditingEnabled() {
        // Raw read: an absent key is the normal, enabled case here, so it must not trip
        // getProperty(String)'s "property is missing" warning.
        String value = super.getProperty("ENABLE_EDIT_APPT_STATUS");
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        return isPropertyActive("ENABLE_EDIT_APPT_STATUS");
    }


    public String getHL7A04BuildDirectory() {
        return getProperty("hl7_a04_build_dir");
    }

    public String getHL7SendingApplication() {
        return getProperty("HL7_SENDING_APPLICATION");
    }

    public String getHL7SendingFacility() {
        return getProperty("HL7_SENDING_FACILITY");
    }

    public String getHL7ReceivingApplication() {
        return getProperty("HL7_RECEIVING_APPLICATION");
    }

    public String getHL7ReceivingFacility() {
        return getProperty("HL7_RECEIVING_FACILITY");
    }

    public boolean isHL7A04GenerationEnabled() {
        return isPropertyActive("HL7_A04_GENERATION");
    }

    public static String getIntakeProgramAccessServiceId() {
        return carlosProperties.getProperty("form_intake_program_access_service_id");
    }

    public static String getIntakeProgramCashServiceId() {
        return carlosProperties.getProperty("form_intake_program_cash_service_id");
    }

    public static String getIntakeProgramAccessFId() {
        return carlosProperties.getProperty("form_intake_program_access_fid");
    }

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

    public static String getIntakeProgramCashFId() {
        return carlosProperties.getProperty("form_intake_program_cash_fid");
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public String getDocumentDirectory() {
       String documents = carlosProperties.getProperty("DOCUMENT_DIR");

        // String value will equal null if property is not found
        if (documents == null) {
            // Setting derived path for documents incase starting path is not found
            File baseDocumentDir = PathValidationUtils.resolveConfiguredDirectory(carlosProperties.getProperty("BASE_DOCUMENT_DIR"), "BASE_DOCUMENT_DIR");
            documents = Paths.get(baseDocumentDir.getPath(), "document").toString();
        }
       return documents;
    }

    public String getDocumentCacheDirectory() {
        return carlosProperties.getProperty("DOCUMENT_CACHE_DIR");
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public String getEformImageDirectory() {
        String eform_images = carlosProperties.getProperty("EFORM_IMAGES_DIR");

        // String value will equal null if property is not found
        if (eform_images == null) {
            // Setting derived path for eform images incase starting path is not found
            File baseDocumentDir = PathValidationUtils.resolveConfiguredDirectory(carlosProperties.getProperty("BASE_DOCUMENT_DIR"), "BASE_DOCUMENT_DIR");
            eform_images = Paths.get(baseDocumentDir.getPath(), "eform", "images").toString();
        }
        return eform_images;
    }

    /**
     * Returns the directory for inbound fax files awaiting import.
     *
     * <p>Resolution order (first non-null/non-empty value wins):</p>
     * <ol>
     *   <li>{@code FAX_INCOMING_DIR} property from carlos.properties (explicit override)</li>
     *   <li>{@code BASE_DOCUMENT_DIR/fax-incoming} — consistent with DOCUMENT_DIR and EFORM_IMAGES_DIR
     *       patterns; uses a directory that is already configured, writable, and outside the webroot</li>
     *   <li>{@code ${catalina.base}/fax-incoming} — Tomcat instance root fallback</li>
     *   <li>{@code ${java.io.tmpdir}/carlos-fax-incoming} — test/non-Tomcat environments only</li>
     * </ol>
     *
     * @return String path to the fax incoming directory, never null
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public String getFaxIncomingDirectory() {
        String faxIncoming = carlosProperties.getProperty("FAX_INCOMING_DIR");

        if (faxIncoming == null || faxIncoming.trim().isEmpty()) {
            // Prefer BASE_DOCUMENT_DIR — same pattern as DOCUMENT_DIR and EFORM_IMAGES_DIR.
            // This directory is already configured, writable by the app server, and outside the webroot.
            String baseDocDir = carlosProperties.getProperty("BASE_DOCUMENT_DIR");
            if (baseDocDir != null && !baseDocDir.trim().isEmpty()) {
                File baseDocumentDir = PathValidationUtils.resolveConfiguredDirectory(baseDocDir.trim(), "BASE_DOCUMENT_DIR");
                faxIncoming = Paths.get(baseDocumentDir.getPath(), "fax-incoming").toString();
            } else {
                // Fall back to catalina.base only if BASE_DOCUMENT_DIR is not set.
                // NOTE: On Debian/Ubuntu package installs, catalina.base = /var/lib/tomcat9,
                // which is a system directory the Tomcat process may not have write access to.
                // Set FAX_INCOMING_DIR or BASE_DOCUMENT_DIR in carlos.properties to avoid this.
                String catalinaBase = System.getProperty("catalina.base");
                if (catalinaBase != null && !catalinaBase.trim().isEmpty()) {
                    File catalinaBaseDir = PathValidationUtils.resolveConfiguredDirectory(catalinaBase.trim(), "catalina.base");
                    faxIncoming = Paths.get(catalinaBaseDir.getPath(), "fax-incoming").toString();
                } else {
                    // Non-Tomcat environment (tests, standalone): use system temp
                    File tempDir = PathValidationUtils.validateConfiguredDirectory(System.getProperty("java.io.tmpdir"), "java.io.tmpdir");
                    faxIncoming = Paths.get(tempDir.getPath(), "carlos-fax-incoming").toString();
                }
            }
        }
        return faxIncoming;
    }

	/**
	 * Saves property to the specified properties file.
	 * This method appends the new property to the end of the file.
	 * Updates the in-memory reference of the properties.
	 *
	 * @param propFilePath The path to the properties file.
	 * @param key          String the key of the property to be saved.
	 * @param value        String the value of the property to be saved.
	 * @throws IOException If an I/O error occurs while writing to the file.
	 */
	public void saveProperty(String propFilePath, String key, String value) throws IOException {
		File propertyFile;
		try {
			propertyFile = PathValidationUtils.resolveConfiguredFile(propFilePath, "properties file");
		} catch (SecurityException e) {
			// Honour the declared throws IOException for a blank or un-canonicalizable path
			// (resolveConfiguredFile permits a missing one - this append-mode write then creates it).
			throw new IOException("Unable to resolve properties file for saving", e);
		}
		try (FileWriter writer = new FileWriter(propertyFile, true)) {
			// Write the new key-value pair
			writer.write("\n" + key + "=" + value + "\n");
		}
		setProperty(key, value);
	}

}
