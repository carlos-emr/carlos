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

/**
 * Utility class for ZIP file operations including compressing form record files into
 * a ZIP archive and extracting XML files from ZIP archives. Used for form data
 * import/export in the medical forms subsystem.
 *
 * @since 2001-01-01
 */
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

    /**
     * Compresses all files matching the specified format in the form record directory
     * into a single ZIP archive named {@code formRecords.zip}.
     *
     * @param fileformat String the file extension to filter (e.g., "xml")
     */
    public void write2Zip(String fileformat) {
        MiscUtils.getLogger().debug("writing to Zip");
        try {
            BufferedInputStream origin = null;
            int BUFFER = 1024;
            String form_record_path = CarlosProperties.getInstance().getProperty("form_record_path", "/root");
            FileOutputStream dest = new FileOutputStream(form_record_path + "formRecords.zip");
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
            out.setMethod(ZipOutputStream.DEFLATED);
            byte data[] = new byte[BUFFER];
            //get a list of files from current directory
            File f = new File(form_record_path + ".");
            String files[] = f.list();

            for (int i = 0; i < files.length; i++) {
                MiscUtils.getLogger().debug("Adding: " + files[i]);
                if (files[i].endsWith("." + fileformat)) {
                    FileInputStream fi = new FileInputStream(form_record_path + files[i]);
                    origin = new BufferedInputStream(fi, BUFFER);
                    ZipEntry entry = new ZipEntry(files[i]);
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

    /**
     * Extracts XML files from a ZIP archive into the specified directory. Uses
     * {@link PathValidationUtils} to prevent zip slip attacks. After extraction,
     * the ZIP file is moved to an {@code unzip_archive} subdirectory.
     *
     * @param dirName String the target extraction directory (must end with separator)
     * @param fName String the ZIP filename (must have {@code .zip} extension)
     * @return boolean true if extraction succeeded, false on failure
     */
    public static boolean unzipXML(String dirName, String fName) {
        int BUFFER = 2048;

        Enumeration<? extends ZipEntry> entries;
        boolean result = false;
        String fullpath = dirName + fName;
        if (!fName.substring(fName.length() - 4).equalsIgnoreCase(".zip")) {
            logger.error("unzipXML: " + fName + " does not have .zip extension.");
            return result;
        }
        BufferedOutputStream dest = null;
        BufferedInputStream is = null;
        ZipEntry entry;

        try {
            ZipFile zipfile = new ZipFile(fullpath);
            File targetDir = new File(dirName);

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
                    z = PathValidationUtils.validatePath(zName, targetDir);
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
            File afile = new File(fullpath);
            File dir = new File(dirName + "unzip_archive/");
            Boolean success = afile.renameTo(new File(dir, afile.getName()));
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

