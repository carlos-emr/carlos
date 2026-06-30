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


package io.github.carlos_emr.carlos.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import io.github.carlos_emr.CarlosProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class zip {

    private static Logger logger = MiscUtils.getLogger();

    /**
     * default contructor
     * this constructor is used to avoid unused local variables/for clean code
     */
    zip() {
    }

    zip(String fileformat) {
        write2Zip(fileformat);
    }

    // FindSecBugs CRLF_INJECTION_LOGS: files[i] is an entry name from File.list() on the server-configured form_record_path directory (resolveConfiguredDirectory); it is a server-stored on-disk filename, not request input, so no attacker-controlled CR/LF reaches the log.
    @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS", justification = "logged filename comes from File.list() on the configured form_record_path directory, a server-side directory listing, not request input; no attacker-controlled CR/LF.")
    public void write2Zip(String fileformat) {
        MiscUtils.getLogger().debug("writing to Zip");
        try {
            int BUFFER = 1024;
            String form_record_path = CarlosProperties.getInstance().getProperty("form_record_path", "/root");
            File formRecordDir = PathValidationUtils.resolveConfiguredDirectory(form_record_path, "form_record_path");
            byte data[] = new byte[BUFFER];
            //get a list of files from current directory
            File f = formRecordDir;
            String files[] = f.list();
            if (files == null) {
                // resolveConfiguredDirectory may return a non-existent/unreadable directory;
                // listing it yields null. Treat as nothing to archive instead of NPE-ing.
                MiscUtils.getLogger().warn("form_record_path directory is missing or unreadable; nothing to zip");
                return;
            }

            try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(PathValidationUtils.validateGeneratedChildPath("formRecords.zip", formRecordDir))))) {
                out.setMethod(ZipOutputStream.DEFLATED);
                for (int i = 0; i < files.length; i++) {
                    MiscUtils.getLogger().debug("Adding: " + files[i]);
                    if (files[i].endsWith("." + fileformat)) {
                        File inputFile = PathValidationUtils.validateGeneratedChildPath(files[i], formRecordDir);
                        try (BufferedInputStream origin = new BufferedInputStream(new FileInputStream(inputFile), BUFFER)) {
                            ZipEntry entry = new ZipEntry(PathValidationUtils.validateZipEntryName(inputFile, formRecordDir));
                            out.putNextEntry(entry);
                            int count;
                            while ((count = origin.read(data, 0, BUFFER)) != -1) {
                                out.write(data, 0, count);
                            }
                            out.closeEntry();
                        }
                    }
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static boolean unzipXML(String dirName, String fName) {
        int BUFFER = 2048;

        Enumeration<? extends ZipEntry> entries;
        boolean result = false;
        if (fName == null || fName.length() < 4 || !fName.toLowerCase().endsWith(".zip")) {
            if (logger.isErrorEnabled()) {
                logger.error("unzipXML rejected file without .zip extension; file name omitted from log.");
            }
            return result;
        }
        File targetDir;
        File zipInputFile;
        try {
            // A blank/misconfigured dirName or a traversal-bearing fName throws SecurityException here;
            // honour this method's boolean "never throws" contract by returning false instead.
            targetDir = PathValidationUtils.resolveConfiguredDirectory(dirName, "unzip target directory");
            zipInputFile = PathValidationUtils.validatePath(fName, targetDir);
        } catch (SecurityException e) {
            logger.error("unzipXML: invalid target directory or zip file path", e);
            return result;
        }
        String fullpath = zipInputFile.getPath();
        ZipEntry entry;

        try (ZipFile zipfile = new ZipFile(zipInputFile)) {
            entries = zipfile.entries();
            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                String zName = entry.getName();

                try (BufferedInputStream is = new BufferedInputStream(zipfile.getInputStream(entry))) {
                    int count;
                    byte data[] = new byte[BUFFER];
                    if (!zName.regionMatches(true, zName.length() - 4, ".zip", 0, 4)) {
                        zName = zName + ".xml";
                    }

                    // Validate the zip entry path using PathValidationUtils
                    File z;
                    try {
                        z = PathValidationUtils.validateZipEntryPath(new ZipEntry(zName), targetDir);
                    } catch (SecurityException e) {
                        logger.error("Skipping potentially malicious zip entry: {}", LogSafe.sanitize(zName)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        continue;
                    }

                    // Create parent directories if they don't exist
                    File parentDir = z.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    try (BufferedOutputStream dest = new BufferedOutputStream(new FileOutputStream(z), BUFFER)) {
                        while ((count = is.read(data, 0, BUFFER)) != -1) {
                            dest.write(data, 0, count); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- writes ZIP entry bytes to a validated file target, not HTTP response HTML
                        }
                        dest.flush(); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- flushes file output stream, not HTTP response HTML
                    }
                }
            }
        } catch (Exception e) {
            logger.error("io.github.carlos_emr.carlos.util.zip.unzipXML Unhandled exception:", e);
            return result;
        }

        // Archive the source zip AFTER the ZipFile above is closed: renaming it while ZipFile still
        // holds the file open fails on Windows (file lock). A failed archive does not fail the unzip.
        try {
            File afile = PathValidationUtils.validateExistingPath(zipInputFile, targetDir);
            File dir = PathValidationUtils.resolveConfiguredDirectory(new File(targetDir, "unzip_archive").getPath(), "unzip archive directory");
            if (!dir.exists()) dir.mkdirs();
            boolean success = afile.renameTo(PathValidationUtils.validateGeneratedChildPath(afile.getName(), dir));
            if (!success) {
                logger.error("io.github.carlos_emr.carlos.util.zip.unzipXML: the zip file {} was not archived", LogSafe.sanitize(fullpath)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
            }
        } catch (Exception e) {
            logger.error("io.github.carlos_emr.carlos.util.zip.unzipXML: failed to archive the zip file {}", LogSafe.sanitize(fullpath), e); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
        }
        result = true;
        return result;
    }
}
