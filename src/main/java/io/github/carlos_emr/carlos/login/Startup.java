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
                            + " not found in user home or WEB-INF; refusing to start.");
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
