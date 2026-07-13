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
package io.github.carlos_emr.carlos.hospitalReportManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.CarlosProperties;

/**
 * See bug 4195.
 * <p>
 * The issue is that there could be some files that are relative, but the file is not in DOCUMENT_DIR (still in the downloads directory)
 * <p>
 * This script (runs once on startup), goes through the HRM db records, and tries to file any missing files.
 *
 * @author marc
 */
public class HRMFixMissingReportHelper {

    private String downloadsDirectory = CarlosProperties.getInstance().getProperty("OMD_downloads");

    private HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);

    private Logger logger = MiscUtils.getLogger();

    private PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);

    public static final String SCRIPT_PROPERTY = "HRMFixMissingReportHelper.Run";


    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use; path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use; path derived from trusted configuration/constant/DB value, not user-controllable input")
    public void fixIt() {

        if (hasThisRunBefore()) {
            return;
        }

        logger.info("Running HRMFixMissingReportHelper");
        int offset = 0;
        int limit = 100;
        List<HRMDocument> documents = null;

        while (true) {
            documents = hrmDocumentDao.findAll(offset, limit);

            for (HRMDocument doc : documents) {
                // A blank DOCUMENT_DIR or a malformed/traversal-bearing report path throws an unchecked
                // SecurityException from PathValidationUtils; skip that one document and keep fixing the
                // rest of the batch rather than aborting the whole run.
                try {
                    String hrmReportFileLocation = doc.getReportFile();

                    File tmpXMLholder = PathValidationUtils.resolveTrustedPath(new File(hrmReportFileLocation));

                    if (tmpXMLholder.exists()) {
                        continue;
                    }
                    String place = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
                    File documentDir = PathValidationUtils.resolveConfiguredDirectory(place, "DOCUMENT_DIR");
                    tmpXMLholder = PathValidationUtils.validateExistingPath(new File(documentDir, hrmReportFileLocation), documentDir);

                    if (tmpXMLholder.exists()) {
                        continue;
                    }

                    // Sanitize the report path before logging: it is a DB-sourced, PHI-adjacent value,
                    // so route it through LogSafe to neutralize CRLF log-forging and bound its length.
                    logger.info("Searching for report file: {}", LogSafe.sanitize(hrmReportFileLocation));

                    //if we got to here, it means we can't find the file..let's go on a hunt
                    File file = searchForFile(tmpXMLholder.getName());

                    if (file != null) {
                        //copy it over to document_dir
                        try {
                            FileUtils.copyFileToDirectory(file, documentDir);
                            logger.info("Fixed: {}", LogSafe.sanitize(hrmReportFileLocation));
                        } catch (IOException e) {
                            logger.error("Unable to copy the file to DOCUMENT_DIR: {}", LogSafe.sanitize(file.getPath()));
                        }
                    } else {
                        logger.warn("UNABLE TO FIND THE FILE: {}", LogSafe.sanitize(tmpXMLholder.getPath()));
                    }
                } catch (SecurityException e) {
                    logger.error("Skipping HRM document with an invalid report path", e);
                }
            }

            if (documents.size() < limit) {
                break;
            }
            offset += limit;
        }

        logger.info("Done running HRMFixMissingReportHelper");

        setAsRun();
    }

    private File searchForFile(String fileName) {
        //root dir = downloadsDirectory
        File rootDir = PathValidationUtils.validateConfiguredDirectory(downloadsDirectory, "OMD_downloads");
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            logger.error("HRM Downloads directory not found..can't continue with 4195 fixer");
            throw new IllegalArgumentException("Directory not found: " + downloadsDirectory);
        }
        //these are the dated directories like 18092013
        for (File datedDirectory : rootDir.listFiles()) {
            //should be a decrypted directory within
            if (!datedDirectory.isDirectory()) {
                logger.warn("skipping file in the root directory:" + datedDirectory);
                continue;
            }
            File decryptedDirectory = PathValidationUtils.validateGeneratedChildPath("decrypted", datedDirectory);
            if (!decryptedDirectory.exists() || !decryptedDirectory.isDirectory()) {
                logger.warn("skipping.decrypted subdirectory not found in :" + datedDirectory);
                continue;
            }
            //we can now check for the file
            File theFile = PathValidationUtils.validateGeneratedChildPath(fileName, decryptedDirectory);
            if (theFile != null && theFile.exists()) {
                logger.info("Found the file we were missing: {}", LogSafe.sanitize(theFile.getPath()));
                return theFile;
            }
        }

        return null;
    }

    private boolean hasThisRunBefore() {
        List<Property> propList = propertyDao.findByName(SCRIPT_PROPERTY);
        if (propList.isEmpty()) {
            return false;
        }
        Property prop = propList.get(0);
        if (prop != null && "1".equals(prop.getValue())) {
            return true;
        }
        return false;
    }

    private void setAsRun() {
        List<Property> propList = propertyDao.findByName(SCRIPT_PROPERTY);
        Property prop = null;
        if (!propList.isEmpty()) {
            prop = propList.get(0);
        }

        if (prop == null) {
            prop = new Property();
            prop.setName(SCRIPT_PROPERTY);
            prop.setValue("1");
            propertyDao.persist(prop);
        } else {
            prop.setValue("1");
            propertyDao.merge(prop);
        }
    }
}

