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

import io.github.carlos_emr.OscarProperties;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
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
	private OscarProperties p = OscarProperties.getInstance();

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
                    logger.error("Configuration file: " + propName + " cannot be found, it should be put either in the User's home or in WEB-INF ");
                    return;
                } catch (Exception e) {
                    logger.error("Error", e);
                    return;
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

                // Temporary Testing of new ECHART
                // To be removed
                String newDocs = p.getProperty("DOCS_NEW_ECHART");

                if (newDocs != null) {
                    String[] arrnewDocs = newDocs.split(",");
                    ArrayList<String> newDocArr = new ArrayList<String>(Arrays.asList(arrnewDocs));
                    Collections.sort(newDocArr);
                    sc.getServletContext().setAttribute("newDocArr", newDocArr);
                }

                String echartSwitch = p.getProperty("USE_NEW_ECHART");
                if (echartSwitch != null && echartSwitch.equalsIgnoreCase("yes")) {
                    sc.getServletContext().setAttribute("useNewEchart", true);
                }

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
			if (Objects.isNull(secretKey)) {
				try {
					secretKey = EncryptionUtils.generateSecretKey();
					p.saveProperty(propFileName, EncryptionUtils.SECRET_KEY_ENV_VAR, secretKey);
					EncryptionUtils.prepareSecretKeySpec();
					logger.info("New Secret Key generated...");
				} catch (IOException | NoSuchAlgorithmException e) {
					logger.error("Error generating new Secret Key - ", e);
				}
			} else {
				logger.info("Using existing Secret Key...");
			}

			// CHECK FOR DEFAULT PROPERTIES
			String baseDocumentDir = p.getProperty("BASE_DOCUMENT_DIR");
			if (baseDocumentDir != null) {
				logger.info("Found Base Document Dir: " + baseDocumentDir);
				checkAndSetProperty(baseDocumentDir, contextPath, "HOME_DIR", "/billing/download/");
				checkAndSetProperty(baseDocumentDir, contextPath, "DOCUMENT_DIR", "/document/");
				checkAndSetProperty(baseDocumentDir, contextPath, "DOCUMENT_CACHE_DIR", "/document_cache/");
				checkAndSetProperty(baseDocumentDir, contextPath, "EFORM_IMAGES_DIR", "/eform/images/");

                checkAndSetProperty(baseDocumentDir, contextPath, "oscarMeasurement_css_upload_path", "/oscarEncounter/oscarMeasurements/styles/");
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
            if (!(new File(propertyDir)).exists()) {
                logger.warn("Directory does not exist:  " + propertyDir + ". Creating.");
                boolean success = (new File(propertyDir)).mkdirs();
                if (!success) logger.error("An error occured when creating " + propertyDir);
            }
        }
    }

    public void contextDestroyed(ServletContextEvent arg0) {
        // nothing to do right now
    }

}
