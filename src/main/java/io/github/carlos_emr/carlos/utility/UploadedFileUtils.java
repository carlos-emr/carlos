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

import java.io.File;
import org.apache.struts2.dispatcher.multipart.UploadedFile;

/**
 * Helpers for extracting backing {@link File} handles from Struts
 * {@link UploadedFile} objects.
 */
public final class UploadedFileUtils {

    private UploadedFileUtils() {
        // utility class — no instances
    }

    /**
     * Returns the validated backing {@link File} for a Struts {@link UploadedFile}.
     *
     * <p>Delegates to {@link PathValidationUtils#validateUploadContent(Object)} so the
     * returned file is canonicalized, exists, is a regular file, and resides in an
     * allowed temp directory.</p>
     *
     * @param upload the uploaded file; must not be {@code null}
     * @return the canonicalized validated backing file
     * @throws IllegalArgumentException if {@code upload} is null
     * @throws SecurityException if the upload content is not a file or validation fails
     */
    public static File getUploadedFile(UploadedFile upload) {
        if (upload == null) {
            throw new IllegalArgumentException("Upload is null");
        }
        return PathValidationUtils.validateUploadContent(upload.getContent());
    }
    /**
     * Returns the validated backing {@link File}, or {@code null} when the upload is
     * {@code null} or fails validation, instead of throwing.
     *
     * @param upload the uploaded file; may be {@code null}
     * @return the canonicalized validated backing file, or {@code null} when unavailable/invalid
     */
    public static File getUploadedFileOrNull(UploadedFile upload) {
        if (upload == null) {
            return null;
        }
        try {
            return PathValidationUtils.validateUploadContent(upload.getContent());
        } catch (SecurityException e) {
            return null;
        }
    }
}
