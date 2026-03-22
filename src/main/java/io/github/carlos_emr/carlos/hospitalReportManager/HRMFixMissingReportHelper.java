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
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * One-time startup helper that fixes missing HRM report files (bug 4195).
 *
 * <p>Some HRM report file paths may be stored as relative paths but the actual
 * file resides in the downloads directory rather than {@code DOCUMENT_DIR}. This
 * helper iterates through all HRM document records in batches, checks if the
 * referenced report file exists, and if not, searches the OMD downloads directory
 * structure (dated subdirectories with {@code decrypted/} folders) and copies
 * the found file into {@code DOCUMENT_DIR}.</p>
 *
 * <p>Runs only once; uses the {@value #SCRIPT_PROPERTY} property to track execution state.</p>
 *
 * @since 2013-10-01
 */
public class HRMFixMissingReportHelper {

    private String downloadsDirectory = CarlosProperties.getInstance().getProperty("OMD_downloads");

    private HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);

    private Logger logger = MiscUtils.getLogger();

    private PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);

    public static final String SCRIPT_PROPERTY = "HRMFixMissingReportHelper.Run";


    /**
     * Executes the fix process to locate and copy missing HRM report files.
     *
     * <p>Skips execution if the fix has already been run (tracked via the
     * {@value #SCRIPT_PROPERTY} property). Processes documents in batches of 100.</p>
     */
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
                String hrmReportFileLocation = doc.getReportFile();

                File tmpXMLholder = new File(hrmReportFileLocation);

                if (tmpXMLholder.exists()) {
                    continue;
                }
                String place = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
                tmpXMLholder = new File(place + File.separator + hrmReportFileLocation);

                if (tmpXMLholder.exists()) {
                    continue;
                }

                logger.info("Searching for report file:" + hrmReportFileLocation);

                //if we got to here, it means we can't find the file..let's go on a hunt
                File file = searchForFile(tmpXMLholder.getName());

                if (file != null) {
                    //copy it over to document_dir
                    try {
                        FileUtils.copyFileToDirectory(file, new File(place));
                        logger.info("Fixed:" + hrmReportFileLocation);
                    } catch (IOException e) {
                        logger.error("Unable to copy the file to DOCUMENT_DIR:" + file);
                    }
                } else {
                    logger.warn("UNABLE TO FIND THE FILE:" + tmpXMLholder);
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
        File rootDir = new File(downloadsDirectory);
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
            File decryptedDirectory = new File(datedDirectory, "decrypted");
            if (!decryptedDirectory.exists() || !decryptedDirectory.isDirectory()) {
                logger.warn("skipping.decrypted subdirectory not found in :" + datedDirectory);
                continue;
            }
            //we can now check for the file
            File theFile = new File(decryptedDirectory, fileName);
            if (theFile != null && theFile.exists()) {
                logger.info("Found the file we were missing:" + theFile);
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

