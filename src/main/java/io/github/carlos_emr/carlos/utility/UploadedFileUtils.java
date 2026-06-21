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
 * Helpers for extracting canonical {@link File} handles from Struts
 * {@link UploadedFile} objects.
 */
public final class UploadedFileUtils {

    private UploadedFileUtils() {
        // utility class — no instances
    }

    /**
     * Returns the backing {@link File} for a Struts {@link UploadedFile}.
     *
     * @param upload the uploaded file; may be {@code null}
     * @return the backing file
     * @throws IllegalStateException if {@code upload} is null or not file-backed
     */
    public static File getUploadedFile(UploadedFile upload) {
        if (upload == null) {
            throw new IllegalStateException("Upload is null");
        }
        Object content = upload.getContent();
        if (!(content instanceof File)) {
            throw new IllegalStateException("Upload has no backing file");
        }
        return (File) content;
    }

    /**
     * Returns the backing {@link File}, or {@code null} when the upload is
     * unavailable, instead of throwing.
     *
     * @param upload the uploaded file; may be {@code null}
     * @return the backing file, or {@code null}
     */
    public static File getUploadedFileOrNull(UploadedFile upload) {
        if (upload == null) {
            return null;
        }
        Object content = upload.getContent();
        return (content instanceof File) ? (File) content : null;
    }
}
