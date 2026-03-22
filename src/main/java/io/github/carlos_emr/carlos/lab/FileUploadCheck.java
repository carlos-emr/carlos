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

package io.github.carlos_emr.carlos.lab;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Utility class for tracking and preventing duplicate file uploads in the lab module.
 * Uses MD5 checksums to detect whether a file has already been uploaded, and provides
 * methods to persist and retrieve upload metadata.
 *
 * @since 2007-01-18
 */
public final class FileUploadCheck {

    private FileUploadCheck() {
        // no instantiation allowed
    }

    /**
     * Checks whether a file with the given MD5 checksum has already been uploaded.
     *
     * @param md5sum String the MD5 hash of the file content
     * @return boolean {@code true} if a record with this checksum exists in the database
     */
    private static boolean hasFileBeenUploaded(String md5sum) {
        FileUploadCheckDao dao = SpringUtils.getBean(FileUploadCheckDao.class);
        List<io.github.carlos_emr.carlos.commn.model.FileUploadCheck> checks = dao.findByMd5Sum(md5sum);
        return !checks.isEmpty();
    }

    /**
     * Determines whether a file at the given filesystem path has already been uploaded
     * by computing its MD5 checksum and checking against the database.
     *
     * @param fileLocation String the absolute path to the file on disk
     * @return boolean {@code true} if this file has been previously uploaded
     * @throws IOException if the file cannot be read
     */
    public static boolean hasFileBeenUploadedByFileLocation(String fileLocation) throws IOException {
        InputStream is = null;

        try {
            is = new FileInputStream(fileLocation);
            String md5sum = DigestUtils.md5Hex(IOUtils.toByteArray(is));
            return hasFileBeenUploaded(md5sum);
        } finally {
            IOUtils.closeQuietly(is);
        }

    }

    /**
     * Retrieves file upload metadata by its database identifier.
     *
     * @param id Integer the primary key of the file upload record
     * @return Map&lt;String, String&gt; containing keys "providerNo", "filename", "md5sum", and "dateTime";
     *         empty map if no record is found
     */
    public static Map<String, String> getFileInfo(Integer id) {
        Map<String, String> fileInfo = new HashMap<String, String>();
        FileUploadCheckDao dao = SpringUtils.getBean(FileUploadCheckDao.class);
        io.github.carlos_emr.carlos.commn.model.FileUploadCheck c = dao.find(id);
        if (c != null) {
            toMap(fileInfo, c);
        }
        return fileInfo;
    }

    private static void toMap(Map<String, String> fileInfo, io.github.carlos_emr.carlos.commn.model.FileUploadCheck c) {
        fileInfo.put("providerNo", c.getProviderNo());
        fileInfo.put("filename", c.getFilename());
        fileInfo.put("md5sum", c.getMd5sum());
        fileInfo.put("dateTime", ConversionUtils.toTimestampString(c.getDateTime()));
    }

    /**
     * Retrieves file upload metadata by its MD5 checksum.
     *
     * @param md5sum String the MD5 hash to search for
     * @return Hashtable&lt;String, String&gt; containing keys "providerNo", "filename", "md5sum", and "dateTime";
     *         empty if no matching record is found
     */
    public static Hashtable<String, String> getFileInfo(String md5sum) {
        Hashtable<String, String> fileInfo = new Hashtable<String, String>();
        FileUploadCheckDao dao = SpringUtils.getBean(FileUploadCheckDao.class);
        List<io.github.carlos_emr.carlos.commn.model.FileUploadCheck> checks = dao.findByMd5Sum(md5sum);

        if (!checks.isEmpty()) {
            io.github.carlos_emr.carlos.commn.model.FileUploadCheck c = checks.get(0);
            toMap(fileInfo, c);
        }

        return fileInfo;
    }

    /** Return value indicating that the file was not saved (duplicate or error). */
    public static final int UNSUCCESSFUL_SAVE = -1;

    /**
     * Adds a new file upload record to the database if the file has not been previously uploaded.
     * This method is synchronized to prevent race conditions during concurrent uploads.
     *
     * @param name String the original filename
     * @param is InputStream the file content stream used to compute the MD5 checksum
     * @param provider String the provider number of the uploading user
     * @return int the generated record ID on success, or {@link #UNSUCCESSFUL_SAVE} if the file
     *         was already uploaded or an error occurred
     */
    public static synchronized int addFile(String name, InputStream is, String provider) {
        int fileUploaded = UNSUCCESSFUL_SAVE;
        try {
            String md5sum = DigestUtils.md5Hex(IOUtils.toByteArray(is));
            if (!hasFileBeenUploaded(md5sum)) {

                io.github.carlos_emr.carlos.commn.model.FileUploadCheck f = new io.github.carlos_emr.carlos.commn.model.FileUploadCheck();
                f.setProviderNo(provider);
                f.setFilename(name);
                f.setMd5sum(md5sum);
                f.setDateTime(new Date());

                FileUploadCheckDao dao = SpringUtils.getBean(FileUploadCheckDao.class);
                dao.persist(f);

                fileUploaded = f.getId();
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        MiscUtils.getLogger().debug("returning " + fileUploaded);
        return fileUploaded;
    }

}
