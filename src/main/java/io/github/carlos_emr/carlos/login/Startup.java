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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;

/**
 * This ContextListener is used to Initialize classes at startup - Initialize the DBConnection Pool.
 *
 * @author Jay Gallagher
 */
public class Startup implements ServletContextListener {
	private static final Logger logger = MiscUtils.getLogger();
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

            // Decide the /WEB-INF/ fallback from deployment-supplied config only: the singleton is
            // pre-loaded with packaged /carlos.properties defaults, including db_username, so asking it
            // always answers "configured" and the merge would never run.
            DeploymentConfig deployment = readDeploymentProperties(propFileName);
            Properties deploymentProperties = deployment.merged();

            // This has always looked in the home directory of the user that started tomcat. That file
            // was already opened and parsed above, so apply the parsed result rather than reading it a
            // second time: the former p.readFromFile(propFileName) pass re-opened the same path and,
            // whenever it was absent, logged "not found" a second time for it.
            p.putAll(deployment.userHome());
            if (deployment.userHomeFound()) {
                logger.info("loading properties from {}", propFileName);
            }

            if (!hasDatabaseConfiguration(deploymentProperties)) {
                // The deployment supplied no usable DB config, so /WEB-INF/ is still the source of truth.
                try {
                    logger.info("looking up  /WEB-INF/{}", propName);
                    // Keep the deployment key: a /WEB-INF/ placeholder would otherwise overwrite the
                    // real generated key and break decryption of data stored since first startup.
                    String existingKey = deploymentProperties.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
                    p.readFromFile("/WEB-INF/" + propName);
                    if (existingKey != null && !existingKey.isBlank()) {
                        p.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, existingKey); // real key wins
                    }
                    logger.info("loading properties from /WEB-INF/{}", propName);
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
				try {
					secretKey = EncryptionUtils.generateSecretKey();
					p.saveProperty(propFileName, EncryptionUtils.SECRET_KEY_ENV_VAR, secretKey);
					logger.info("New Secret Key generated...");
				} catch (IOException | NoSuchAlgorithmException e) {
					/*
					 * A usable encryption key is mandatory: it protects stored PHI and provider
					 * credentials. Fail fast rather than booting with no key, which would defer the
					 * failure to the first credential save (an opaque runtime error for clinicians).
					 */
					throw new IllegalStateException("Unable to generate and persist a new encryption key at startup", e);
				}
			} else {
				logger.info("Using existing Secret Key...");
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
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            throw (new RuntimeException(e));
        }
    }

    // Ask this about deployment-supplied properties only, never the singleton: packaged defaults
    // always carry a db_username. Package-private for testing.
    static boolean hasDatabaseConfiguration(Properties props) {
        String dbUsername = props.getProperty("db_username");
        return dbUsername != null && !dbUsername.isBlank();
    }

    /**
     * Deployment-supplied configuration, kept split so the user-home slice can be applied to the
     * singleton without opening that file a second time.
     *
     * @param merged        the override file merged with the user-home file, user-home winning on
     *                      conflict; this is what answers "did the deployment supply real config?"
     * @param userHome      the user-home file's properties alone - exactly what the singleton used to
     *                      load on its own separate pass; empty when that file is absent
     * @param userHomeFound whether the user-home file was present and loaded, which an empty
     *                      {@code userHome} cannot distinguish from a present-but-empty file
     */
    record DeploymentConfig(Properties merged, Properties userHome, boolean userHomeFound) {
    }

    /**
     * Loads deployment-supplied configuration, excluding the packaged classpath defaults that make the
     * singleton useless for an "is this configured?" check. Both channels count: the
     * {@code carlos_override_properties} file and the user-home file, loaded last so it wins on conflict.
     *
     * @param userHomePropFileName absolute path of the user-home properties file for this context
     * @return deployment-supplied properties; {@code merged} is empty when neither file exists
     */
    static DeploymentConfig readDeploymentProperties(String userHomePropFileName) {
        Properties merged = new Properties();
        loadIfPresent(merged, System.getProperty("carlos_override_properties"));

        // Read into its own Properties as well as into the merged view: the caller applies this slice
        // to the singleton, which must receive the user-home file only - never the override, which
        // CarlosProperties' constructor has already applied.
        Properties userHome = new Properties();
        boolean userHomeFound = loadIfPresent(userHome, userHomePropFileName);
        merged.putAll(userHome);

        return new DeploymentConfig(merged, userHome, userHomeFound);
    }

    /**
     * Opens a configuration file for reading. Exists as a seam because the distinction this class
     * depends on - a file that is absent versus one that exists but cannot be opened - cannot be
     * produced through filesystem permissions when the test suite runs as root, which it does both
     * locally and in CI. Package-private so tests can substitute a failing opener.
     *
     * <p>Implementations must follow the {@link java.nio.file.Files#newInputStream} contract that
     * {@link #loadIfPresent} depends on: {@link NoSuchFileException} means absent, and every other
     * {@link IOException} - {@link java.nio.file.AccessDeniedException} above all - means present but
     * unusable.
     */
    @FunctionalInterface
    interface ConfigFileOpener {
        InputStream open(Path file) throws IOException;
    }

    /** Replaced by tests; always {@link Files#newInputStream} in production. */
    static ConfigFileOpener configFileOpener = Files::newInputStream;

    /**
     * Merges one configuration file into {@code target}. Absent is normal - either channel may be
     * unused, and an empty result is what drives the {@code /WEB-INF/} fallback. Unreadable is fatal.
     *
     * @return {@code true} when the file was present and loaded, {@code false} when it is simply
     *         absent or no path is configured
     */
    private static boolean loadIfPresent(Properties target, String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return false;
        }
        File file = PathValidationUtils.resolveConfiguredFile(configuredPath, "carlos properties file");
        try (InputStream input = configFileOpener.open(file.toPath())) {
            target.load(input);
            return true;
        } catch (NoSuchFileException e) {
            // The only benign outcome: nothing is deployed at this path.
            logger.info("{} not found", configuredPath);
            return false;
        } catch (IOException e) {
            // Everything else means the file is there but we cannot use it - wrong permissions, wrong
            // Tomcat user, an untraversable parent directory, a directory in place of a file. Treating
            // any of those as "absent" would leave existingKey null and let a /WEB-INF/ placeholder key
            // overwrite the real generated one, silently breaking decryption of every record stored
            // since first startup. So refuse to boot instead.
            //
            // This is why the read goes through java.nio rather than FileInputStream: FileInputStream
            // reports absent and permission-denied with the same FileNotFoundException, and the
            // File.exists() probe that used to tell them apart itself returns false when the parent
            // directory cannot be traversed - the exact misconfiguration most in need of detection.
            throw new IllegalStateException(
                    "Configuration file exists but could not be read: " + configuredPath, e);
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
            logger.debug("Setting property {} with value {}", propName, propertyDir);
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
