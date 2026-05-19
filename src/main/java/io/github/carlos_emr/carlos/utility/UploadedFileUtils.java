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

