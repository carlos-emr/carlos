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
 * Modifications by CARLOS Contributors, 2026: hold ordinary admission across each report copy and completion marker.
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.sql.SQLException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    private final String downloadsDirectory;
    private final String documentDirectory;
    private final HRMDocumentDao hrmDocumentDao;
    private final PropertyDao propertyDao;
    private final TransactionTemplate transaction;
    private final ReportCopier copier;
    private final Logger logger = MiscUtils.getLogger();

    @FunctionalInterface
    public interface ReportCopier { void copy(File source, File directory) throws IOException; }

    /** Legacy startup boundary; infrastructure is constructor-injected in the implementation. */
    public HRMFixMissingReportHelper() {
        this(CarlosProperties.getInstance().getProperty("OMD_downloads"),
                CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), SpringUtils.getBean(HRMDocumentDao.class),
                SpringUtils.getBean(PropertyDao.class), SpringUtils.getBean(PlatformTransactionManager.class),
                FileUtils::copyFileToDirectory);
    }

    public HRMFixMissingReportHelper(String downloadsDirectory, String documentDirectory,
            HRMDocumentDao documents, PropertyDao properties, PlatformTransactionManager manager, ReportCopier copier) {
        this.downloadsDirectory = downloadsDirectory;
        this.documentDirectory = documentDirectory;
        this.hrmDocumentDao = Objects.requireNonNull(documents);
        this.propertyDao = Objects.requireNonNull(properties);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(manager));
        this.copier = Objects.requireNonNull(copier);
    }

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
            requireRepairRunning();
            documents = hrmDocumentDao.findAll(offset, limit);

            for (HRMDocument doc : documents) {
                requireRepairRunning();
                // Directory search never holds a database transaction. The actual mutation
                // re-reads its source identity under ordinary admission before touching files.
                try {
                    String expected = doc.getReportFile();
                    File existing = PathValidationUtils.resolveTrustedPath(new File(expected));
                    if (existing.exists()) continue;
                    File directory = PathValidationUtils.resolveConfiguredDirectory(documentDirectory, "DOCUMENT_DIR");
                    File target = PathValidationUtils.validateExistingPath(new File(directory, expected), directory);
                    if (target.exists()) continue;
                    logger.info("Searching for report file: {}", LogSafe.sanitize(expected));
                    File source = searchForFile(target.getName());
                    if (source == null) {
                        logger.warn("UNABLE TO FIND THE FILE: {}", LogSafe.sanitize(target.getPath()));
                        continue;
                    }
                    withAdmission(() -> {
                        requireRepairRunning();
                        String current = hrmDocumentDao.reportFileForRepair(doc.getId());
                        if (current == null) return; // The document was deleted before repair.
                        if (!current.equals(expected)) throw new HrmReportRepairDeferredException("HRM_REPAIR_SOURCE_CHANGED");
                        File validated = PathValidationUtils.validateExistingPath(new File(directory, current), directory);
                        if (validated.exists()) return;
                        try {
                            copier.copy(source, directory);
                            requireRepairRunning();
                            logger.info("Fixed: {}", LogSafe.sanitize(current));
                        } catch (IOException failure) {
                            // Preserve the existing best-effort per-file failure policy.
                            logger.error("Unable to copy the file to DOCUMENT_DIR: {}", LogSafe.sanitize(source.getPath()));
                            requireRepairRunning();
                        }
                    });
                } catch (SecurityException invalidPath) {
                    logger.error("Skipping HRM document with an invalid report path", invalidPath);
                }
            }

            if (documents.size() < limit) {
                break;
            }
            offset += limit;
        }

        logger.info("Done running HRMFixMissingReportHelper");

        withAdmission(() -> {
            requireRepairRunning();
            hrmDocumentDao.requireRepairAdmission();
            setAsRun();
        });
    }

    private static void requireRepairRunning() {
        if (Thread.currentThread().isInterrupted()) throw new HrmReportRepairDeferredException("HRM_REPAIR_INTERRUPTED");
    }

    private void withAdmission(Runnable work) {
        try { transaction.executeWithoutResult(status -> work.run()); }
        catch (RuntimeException failure) {
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "45000".equals(sql.getSQLState())) {
                    throw new HrmReportRepairDeferredException(failure);
                }
            }
            throw failure;
        }
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

