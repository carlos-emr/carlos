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

    public void write2Zip(String fileformat) {
        MiscUtils.getLogger().debug("writing to Zip");
        try {
            BufferedInputStream origin = null;
            int BUFFER = 1024;
            String form_record_path = CarlosProperties.getInstance().getProperty("form_record_path", "/root");
            File formRecordDir = PathValidationUtils.resolveConfiguredDirectory(form_record_path, "form_record_path");
            FileOutputStream dest = new FileOutputStream(PathValidationUtils.validateGeneratedChildPath("formRecords.zip", formRecordDir));
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
            out.setMethod(ZipOutputStream.DEFLATED);
            byte data[] = new byte[BUFFER];
            //get a list of files from current directory
            File f = formRecordDir;
            String files[] = f.list();

            for (int i = 0; i < files.length; i++) {
                MiscUtils.getLogger().debug("Adding: " + files[i]);
                if (files[i].endsWith("." + fileformat)) {
                    File inputFile = PathValidationUtils.validateGeneratedChildPath(files[i], formRecordDir);
                    FileInputStream fi = new FileInputStream(inputFile);
                    origin = new BufferedInputStream(fi, BUFFER);
                    ZipEntry entry = new ZipEntry(PathValidationUtils.validateZipEntryName(inputFile, formRecordDir));
                    out.putNextEntry(entry);
                    int count;
                    while ((count = origin.read(data, 0, BUFFER)) != -1) {
                        out.write(data, 0, count);
                    }
                    origin.close();
                }
            }
            out.close();
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
        File targetDir = PathValidationUtils.resolveConfiguredDirectory(dirName, "unzip target directory");
        File zipInputFile = PathValidationUtils.validatePath(fName, targetDir);
        String fullpath = zipInputFile.getPath();
        if (!fName.substring(fName.length() - 4).equalsIgnoreCase(".zip")) {
            logger.error("unzipXML: " + fName + " does not have .zip extension.");
            return result;
        }
        BufferedOutputStream dest = null;
        BufferedInputStream is = null;
        ZipEntry entry;

        try {
            ZipFile zipfile = new ZipFile(zipInputFile);

            entries = zipfile.entries();
            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                String zName = entry.getName();

                is = new BufferedInputStream(zipfile.getInputStream(entry));
                int count;
                byte data[] = new byte[BUFFER];
                if (!zName.substring(zName.length() - 4).equalsIgnoreCase(".zip")) {
                    zName = zName + ".xml";
                }

                // Validate the zip entry path using PathValidationUtils
                File z;
                try {
                    z = PathValidationUtils.validateZipEntryPath(new ZipEntry(zName), targetDir);
                } catch (SecurityException e) {
                    logger.error("Skipping potentially malicious zip entry: " + zName);
                    is.close();
                    continue;
                }

                // Create parent directories if they don't exist
                File parentDir = z.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                FileOutputStream fos = new FileOutputStream(z);
                dest = new BufferedOutputStream(fos, BUFFER);
                while ((count = is.read(data, 0, BUFFER)) != -1) {
                    dest.write(data, 0, count);
                }
                dest.flush();
                dest.close();
                is.close();
            }
            zipfile.close();
            //nee to move zip file to archive folder
            File afile = PathValidationUtils.validateExistingPath(zipInputFile, targetDir);
            File dir = PathValidationUtils.resolveConfiguredDirectory(new File(targetDir, "unzip_archive").getPath(), "unzip archive directory");
            if (!dir.exists()) dir.mkdirs();
            Boolean success = afile.renameTo(PathValidationUtils.validateGeneratedChildPath(afile.getName(), dir));
            if (!success) {
                logger.error("io.github.carlos_emr.carlos.util.zip.unzipXML: the zip file " + fullpath + " was not archived");
            }
        } catch (Exception e) {
            logger.error("io.github.carlos_emr.carlos.util.zip.unzipXML Unhandled exception:", e);
            return result;
        }
        result = true;
        return result;
    }
}

