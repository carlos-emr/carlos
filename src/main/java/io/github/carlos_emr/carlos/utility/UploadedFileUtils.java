/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.apache.struts2.dispatcher.multipart.UploadedFile;

import java.io.File;

/**
 * Helpers for extracting canonical file handles from Struts uploaded files.
 */
public final class UploadedFileUtils {

    private UploadedFileUtils() {
        // Utility class
    }

    /**
     * Extracts the file-backed content from a Struts uploaded file.
     *
     * @param uploadedFile the uploaded file metadata
     * @return the uploaded file on disk
     * @throws FileValidationException if the upload is null or not file-backed
     */
    public static File getUploadedFile(UploadedFile uploadedFile) {
        if (uploadedFile == null) {
            throw new FileValidationException("Uploaded file is null");
        }
        Object content = uploadedFile.getContent();
        if (content instanceof File file) {
            return file;
        }
        throw new FileValidationException("Uploaded file content is not file-backed");
    }

    /**
     * Extracts the file-backed content from a Struts uploaded file, or returns null when unavailable.
     *
     * @param uploadedFile the uploaded file metadata
     * @return the uploaded file on disk, or null if the upload cannot be resolved
     */
    public static File getUploadedFileOrNull(UploadedFile uploadedFile) {
        if (uploadedFile == null) {
            return null;
        }
        Object content = uploadedFile.getContent();
        return content instanceof File file ? file : null;
    }
}
