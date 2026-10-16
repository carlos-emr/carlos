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

import io.github.carlos_emr.CarlosProperties;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.WebappShutdownResources;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * This ContextListener is used to Initialize classes at startup - Initialize the DBConnection Pool.
 *
 * @author Jay Gallagher
 */
public class Startup implements ServletContextListener {
	private static final Logger logger = MiscUtils.getLogger();

	/**
	 * Set to {@code true}, {@code yes} or {@code on} to let startup generate a new
	 * {@code encryption.util.secret.key} although data encrypted with the lost key exists, accepting
	 * that the data becomes unreadable (#3939). Remove it once the new key has been generated.
	 */
	public static final String ACKNOWLEDGE_KEY_LOSS_PROPERTY = EncryptionUtils.SECRET_KEY_ENV_VAR + ".acknowledge_loss";

	private CarlosProperties p = CarlosProperties.getInstance();

    public void contextInitialized(ServletContextEvent sc) {
        logger.info("Starting OSCAR application ");

        try {
            logger.debug("contextInit");

            String contextPath = "";
            String propFileName = "";

            try {
                // Anyone know a better way to do this?
                String url = sc.getServletContext().getResource("/").getPath();
                logger.debug(url);
                int idx = url.lastIndexOf('/');
                url = url.substring(0, idx);

                idx = url.lastIndexOf('/');
                url = url.substring(idx + 1);

                idx = url.lastIndexOf('.');
                if (idx > 0) url = url.substring(0, idx);

                contextPath = url;
            } catch (Exception e) {
                logger.error("Error", e);
            }

            String propName = contextPath + ".properties";

            char sep = System.getProperty("file.separator").toCharArray()[0];
            propFileName = System.getProperty("user.home") + sep + propName;
            logger.info("looking up " + propFileName);

            try {
                // This has been used to look in the users home directory that started tomcat
                p.readFromFile(propFileName);
                logger.info("loading properties from " + propFileName);
            } catch (java.io.FileNotFoundException ex) {
                logger.info(propFileName + " not found");
            }
            if (p.isEmpty()) {
                /* if the file not found in the user root, look in the WEB-INF directory */
                try {
                    logger.info("looking up  /WEB-INF/" + propName);
                    p.readFromFile("/WEB-INF/" + propName);
                    logger.info("loading properties from /WEB-INF/" + propName);
                } catch (java.io.FileNotFoundException e) {
                    /*
                     * No configuration in either location means the app has no DB connection and,
                     * critically, no encryption key. Booting on would defer the failure to the first
                     * PHI/credential operation. Fail fast at startup instead.
                     */
                    throw new IllegalStateException("Configuration file " + propName
                            + " not found in user home or WEB-INF; refusing to start.", e);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to read configuration file " + propName
                            + "; refusing to start.", e);
                }
            }
            try {
                // Specify who will see new casemanagement screen
                ArrayList<String> listUsers;
                String casemgmtscreen = p.getProperty("CASEMANAGEMENT");
                if (casemgmtscreen != null) {
                    String[] arrUsers = casemgmtscreen.split(",");
                    listUsers = new ArrayList<String>(Arrays.asList(arrUsers));
                    Collections.sort(listUsers);
                } else listUsers = new ArrayList<String>();

                sc.getServletContext().setAttribute("CaseMgmtUsers", listUsers);

                logger.info("BILLING REGION : " + p.getProperty("billregion", "NOTSET"));
                logger.info("DB PROPS: Username :" + p.getProperty("db_username", "NOTSET") + " db name: " + p.getProperty("db_name", "NOTSET"));
                p.setProperty("OSCAR_START_TIME", "" + System.currentTimeMillis());

            } catch (Exception e) {
                String s = "Property file not found at:" + propFileName;
                logger.error(s, e);
            }


			// 	Ensure that a secret key for encryption is available when OSCAR starts, either by retrieving a
			// 	previously saved key or generating a new one and storing it for future use.
			String secretKey = p.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
			if (Objects.isNull(secretKey) || secretKey.isBlank()) {
				generateKeyUnlessItOrphansData(propFileName);
			} else {
				logger.info("Using existing Secret Key...");
				if (isKeyLossAcknowledged()) {
					logger.warn("{} is set but has no effect while {} is configured. Remove it, so that a"
									+ " future loss of the key stops startup instead of being accepted.",
							ACKNOWLEDGE_KEY_LOSS_PROPERTY, EncryptionUtils.SECRET_KEY_ENV_VAR);
				}
			}

			/*
			 * EncryptionUtils may be loaded before the application properties are read, leaving its
			 * cached SecretKeySpec unset even when a key already exists in the properties file.
			 * Always prepare the key after startup has ensured a key exists so credential saves can
			 * encrypt passwords reliably. An invalid existing key is NOT auto-rotated: regenerating
			 * over it would permanently orphan everything already encrypted under the real key.
			 * Instead, abort startup so an operator can restore the correct key.
			 */
			try {
				EncryptionUtils.prepareSecretKeySpec();
			} catch (IllegalArgumentException e) {
				throw new IllegalStateException("Configured encryption key is invalid (" + e.getMessage()
						+ "); refusing to start. Restore the correct encryption key, or remove it to have a new one generated.", e);
			}

			// CHECK FOR DEFAULT PROPERTIES
			String baseDocumentDir = p.getProperty("BASE_DOCUMENT_DIR");
			if (baseDocumentDir != null) {
				logger.info("Found Base Document Dir: " + baseDocumentDir);
				checkAndSetProperty(baseDocumentDir, contextPath, "HOME_DIR", "/billing/download/");
				checkAndSetProperty(baseDocumentDir, contextPath, "DOCUMENT_DIR", "/document/");
				checkAndSetProperty(baseDocumentDir, contextPath, "DOCUMENT_CACHE_DIR", "/document_cache/");
				checkAndSetProperty(baseDocumentDir, contextPath, "EFORM_IMAGES_DIR", "/eform/images/");

                checkAndSetProperty(baseDocumentDir, contextPath, "oscarMeasurement_css_upload_path", "/encounter/oscarMeasurements/styles/");
                checkAndSetProperty(baseDocumentDir, contextPath, "TMP_DIR", "/export/");
                checkAndSetProperty(baseDocumentDir, contextPath, "form_record_path", "/form/records/");

                //HRM Directories
                checkAndSetProperty(baseDocumentDir, contextPath, "OMD_hrm", "/hrm/");
                checkAndSetProperty(baseDocumentDir, contextPath, "OMD_downloads", "/hrm/sftp_downloads/");


            }

            logger.debug("LAST LINE IN contextInitialized");
        } catch (EncryptionKeyRefusedException e) {
            // Already logged once, as the single operator-facing ERROR. Fail the deployment the same
            // way as every other startup failure, without a second "Unexpected error." copy.
            throw new RuntimeException(e);
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            throw (new RuntimeException(e));
        }
    }

    /**
     * Handles a missing or blank {@code encryption.util.secret.key} (#3939).
     *
     * <p>A new key cannot decrypt anything the lost key encrypted, so generating one on a server
     * that already holds encrypted data silently orphans that data. The key is generated only when
     * nothing encrypted is found (a fresh install), or when the operator has set
     * {@link #ACKNOWLEDGE_KEY_LOSS_PROPERTY} to accept the loss. Otherwise startup is refused, as it
     * is for an invalid key.</p>
     *
     * <p>The check fails closed: if the database cannot be read, CARLOS cannot show that nothing
     * would be orphaned, so it refuses rather than guess. The database is needed to run anyway.</p>
     */
    private void generateKeyUnlessItOrphansData(String propFileName) {
        EncryptedDataCountLoader.Result existing = EncryptedDataCountLoader.fromProperties(p).load();
        boolean mayOrphanData = existing.total() > 0 || !existing.complete();
        boolean lossAcknowledged = isKeyLossAcknowledged();
        if (mayOrphanData && !lossAcknowledged) {
            String message = refusalMessage(existing);
            logger.error(message);
            throw new EncryptionKeyRefusedException(message);
        }

        try {
            String secretKey = EncryptionUtils.generateSecretKey();
            p.saveProperty(propFileName, EncryptionUtils.SECRET_KEY_ENV_VAR, secretKey);
        } catch (IOException | NoSuchAlgorithmException e) {
            /*
             * A usable encryption key is mandatory: it protects stored PHI and provider
             * credentials. Fail fast rather than booting with no key, which would defer the
             * failure to the first credential save (an opaque runtime error for clinicians).
             */
            throw new IllegalStateException("Unable to generate and persist a new encryption key at startup", e);
        }

        if (mayOrphanData) {
            // ERROR, not WARN: data is now unreadable and people must act on it.
            logger.error(() -> acknowledgedLossMessage(existing));
        } else {
            logger.info("New Secret Key generated...");
            if (lossAcknowledged) {
                logger.warn("{} is set but nothing encrypted was found, so no data was lost. Remove it, so that"
                                + " a future loss of the key stops startup instead of being accepted.",
                        ACKNOWLEDGE_KEY_LOSS_PROPERTY);
            }
        }
    }

    private boolean isKeyLossAcknowledged() {
        // containsKey first: CarlosProperties.getProperty logs a warning for every absent key, and this
        // flag is absent on every healthy server. Matched like other flags: true, yes or on.
        return p.containsKey(ACKNOWLEDGE_KEY_LOSS_PROPERTY) && p.isPropertyActive(ACKNOWLEDGE_KEY_LOSS_PROPERTY);
    }

    /** One sanitized message: kinds and counts, the fix, and the override. Never values. */
    private static String refusalMessage(EncryptedDataCountLoader.Result existing) {
        String key = EncryptionUtils.SECRET_KEY_ENV_VAR;
        StringBuilder message = new StringBuilder(key).append(" is missing or blank, ");
        if (existing.complete()) {
            message.append("but ").append(items(existing.total(), ""))
                    .append(" in the database may be encrypted with the original key (")
                    .append(existing.describeCounts())
                    .append("). Refusing to start: a new key cannot decrypt data encrypted with the original key.");
        } else {
            message.append("and CARLOS could not check whether the database holds data encrypted with the original key")
                    .append(" (could not read ").append(existing.describeFailures())
                    .append("; found so far: ").append(existing.describeCounts())
                    .append("). Refusing to start rather than risk making that data unreadable.");
        }
        message.append(" Fix: restore the original ").append(key)
                .append(" from backup into the properties file, then restart.");
        if (existing.onlySignatures()) {
            // Signatures carry no encryption marker, so an older install whose signatures were
            // never encrypted is refused too; its operator has no original key to restore.
            // The question is the database's history, not this server's: a server rebuilt from a
            // backup never had a key, yet its database's signatures may be encrypted. Restoring the
            // key comes first; the case where the override is safe comes last and is narrow.
            message.append(" Only signature images were found; without the key, a plaintext image cannot be told")
                    .append(" apart from an encrypted one. Look for the key first. Plain OSCAR never had an ")
                    .append(key).append(" line, so if the old server's properties file, or a backup of it, has one,")
                    .append(" restore it. CARLOS and OpenO EMR (since September 2024) create the key by themselves, so")
                    .append(" any database they have run on had a key, even if nobody set one, and even if this server")
                    .append(" was later rebuilt from a backup without it. On the old server (or this one) or in its")
                    .append(" backup, look in /etc/carlos-emr/carlos.properties (packaged install), in")
                    .append(" <context>.properties in the home directory of the user Tomcat runs as (for example")
                    .append(" carlos.properties or oscar.properties), and in the file named by")
                    .append(" -Dcarlos_override_properties or, on an OpenO EMR server, -Doscar_override_properties. On")
                    .append(" a packaged install, until the key is restored, do not run carlos-ctl init-config or")
                    .append(" finish-install, and do not install, upgrade, reconfigure or remove the carlos-emr")
                    .append(" packages: each of these can write a new key, and CARLOS then starts without this check.")
                    .append(" Only if the database comes straight from OSCAR, or from an OpenO EMR build from before")
                    .append(" December 2024, and no OpenO EMR build from December 2024 or later and no CARLOS ran on")
                    .append(" it, other than starts refused like this one, are the signatures plaintext; then setting ")
                    .append(ACKNOWLEDGE_KEY_LOSS_PROPERTY).append("=true loses nothing. If you are not sure, treat")
                    .append(" them as encrypted and keep looking for the key. See \"Limits of the check\" in")
                    .append(" https://github.com/carlos-emr/carlos/blob/develop/docs/email/provider-to-patient-email-operations.md")
                    .append("#credential-encryption-key (the copy in the docs folder of your release may differ).");
        }
        if (!existing.complete()) {
            message.append(" If the database could not be reached, fix that and restart so the check can run.");
        }
        message.append(" Only if the original key is lost for good: set ").append(ACKNOWLEDGE_KEY_LOSS_PROPERTY)
                .append("=true and restart. CARLOS then generates a new key and everything encrypted with the old key")
                .append(" stays unreadable");
        if (existing.total() > 0) {
            message.append(" (").append(existing.describeRemedies()).append(')');
        }
        return message.append('.').toString();
    }

    /** Logged when the override was used over data the new key cannot read. Never values. */
    private static String acknowledgedLossMessage(EncryptedDataCountLoader.Result existing) {
        StringBuilder message = new StringBuilder(ACKNOWLEDGE_KEY_LOSS_PROPERTY).append(" is set: generated a new ")
                .append(EncryptionUtils.SECRET_KEY_ENV_VAR);
        if (existing.complete()) {
            message.append(" over ").append(items(existing.total(), "possibly encrypted")).append(" (")
                    .append(existing.describeCounts()).append("). Any data encrypted with the old key is now unreadable.");
        } else {
            message.append(". Any data encrypted with the old key is now unreadable. Found ")
                    .append(items(existing.total(), ""))
                    .append(" (").append(existing.describeCounts()).append("), but could not read ")
                    .append(existing.describeFailures()).append(", so there may be more.");
        }
        if (existing.total() > 0) {
            message.append(" Now: ").append(existing.describeRemedies()).append('.');
        }
        return message.append(" Then remove ").append(ACKNOWLEDGE_KEY_LOSS_PROPERTY)
                .append(" from the properties file.").toString();
    }

    /** "1 item" or "N items", with an optional word before "item". */
    private static String items(int count, String qualifier) {
        return count + " " + qualifier + (qualifier.isEmpty() ? "" : " ") + (count == 1 ? "item" : "items");
    }

    /** Startup refused because a new key would orphan encrypted data; already logged when thrown. */
    private static final class EncryptionKeyRefusedException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        EncryptionKeyRefusedException(String message) {
            super(message);
        }
    }

    // Checks for default property with name propName. If the property does not exist,
    // the property is set with value equal to the base directory, plus /, plus the webapp context
    // path and any further extensions. If the formed directory does not exist in the system,
    // it is created.
    private void checkAndSetProperty(String baseDir, String context, String propName, String endDir) {
        String propertyDir = p.getProperty(propName);
        if (propertyDir == null) {
            propertyDir = baseDir + "/" + context + endDir;
            logger.debug("Setting property " + propName + " with value " + propertyDir);
            p.setProperty(propName, propertyDir);
            // Create directory if it does not exist
            File propertyDirectory = PathValidationUtils.resolveConfiguredDirectory(propertyDir, propName);
            if (!propertyDirectory.exists()) {
                logger.warn("Directory does not exist:  " + propertyDir + ". Creating.");
                boolean success = propertyDirectory.mkdirs();
                if (!success) logger.error("An error occured when creating " + propertyDir);
            }
        }
    }

    public void contextDestroyed(ServletContextEvent arg0) {
        WebappShutdownResources.ShutdownReport report = WebappShutdownResources.releaseForContext(getWebappClassLoader(arg0));
        if (report.successful()) {
            logger.info("Webapp shutdown cleanup completed; deregistered JDBC drivers={}", report.deregisteredDriverCount());
        } else {
            logger.warn("Webapp shutdown cleanup completed with {} failed step(s); deregistered JDBC drivers={}",
                    report.failureCount(), report.deregisteredDriverCount());
        }
    }

    /**
     * Resolves the stopping webapp class loader, falling back to the context class
     * loader for direct unit calls or unusual container callbacks with no event.
     */
    private ClassLoader getWebappClassLoader(ServletContextEvent event) {
        if (event != null) {
            ServletContext servletContext = event.getServletContext();
            if (servletContext != null) {
                return servletContext.getClassLoader();
            }
        }
        return Thread.currentThread().getContextClassLoader();
    }

}
