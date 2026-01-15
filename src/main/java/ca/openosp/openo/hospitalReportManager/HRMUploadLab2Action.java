//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
package ca.openosp.openo.hospitalReportManager;

import ca.openosp.openo.lab.ca.all.util.Utilities;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HRMUploadLab2Action extends ActionSupport {
    private List<File> uploads;
    private List<String> uploadsContentType;
    private List<String> uploadsFileName;

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // Allowed file types for HRM uploads
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
        "text/plain",
        "text/xml",
        "application/xml",
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/gif"
    );

    public void setUploads(List<File> uploads) {
        this.uploads = uploads;
    }

    public void setUploadsContentType(List<String> uploadsContentType) {
        this.uploadsContentType = uploadsContentType;
    }

    public void setUploadsFileName(List<String> uploadsFileName) {
        this.uploadsFileName = uploadsFileName;
    }

    public enum FileStatus {
        COMPLETED,
        FAILED,
        INVALID
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // MANDATORY security check
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        if (uploads != null && !uploads.isEmpty()) {
            Map<String, FileStatus> filesStatusMap = processFiles(loggedInInfo);
            request.setAttribute("filesStatusMap", filesStatusMap);
        } else {
            // No files uploaded - return empty map to preserve original flow
            request.setAttribute("filesStatusMap", new HashMap<>());
        }

        return SUCCESS;
    }

    private Map<String, FileStatus> processFiles(LoggedInInfo loggedInInfo) {
        Map<String, FileStatus> filesStatusMap = new HashMap<>();

        for (int i = 0; i < uploads.size(); i++) {
            File file = uploads.get(i);
            String fileName = uploadsFileName.get(i);
            String contentType = (uploadsContentType != null && i < uploadsContentType.size())
                ? uploadsContentType.get(i) : null;

            // Validate content type
            if (!isValidContentType(contentType)) {
                MiscUtils.getLogger().warn("Invalid content type '{}' for file '{}'", contentType, fileName);
                filesStatusMap.put(fileName, FileStatus.INVALID);
                continue;
            }

            // Sanitize filename to prevent path traversal attacks
            String sanitizedFileName = sanitizeFileName(fileName);
            if (sanitizedFileName == null || sanitizedFileName.isEmpty()) {
                MiscUtils.getLogger().error("Invalid filename provided: '{}'", fileName);
                filesStatusMap.put(fileName, FileStatus.INVALID);
                continue;
            }

            try (InputStream inputStream = new FileInputStream(file)) {
                String filePath = Utilities.saveFile(inputStream, sanitizedFileName);
                HRMReport report = HRMReportParser.parseReport(loggedInInfo, filePath);
                FileStatus fileStatus = handleHRMReport(loggedInInfo, report);
                filesStatusMap.put(fileName, fileStatus);
            } catch (IOException e) {
                MiscUtils.getLogger().error("Error occurred while processing file '{}': {}", fileName, e);
                filesStatusMap.put(fileName, FileStatus.INVALID);
            } catch (SecurityException e) {
                MiscUtils.getLogger().error("Security violation while processing file '{}': {}", fileName, e);
                filesStatusMap.put(fileName, FileStatus.INVALID);
            }
        }

        return filesStatusMap;
    }

    private FileStatus handleHRMReport(LoggedInInfo loggedInInfo, HRMReport report) {
        if (report == null) {
            return FileStatus.INVALID;
        }

        try {
            HRMReportParser.addReportToInbox(loggedInInfo, report);
            return FileStatus.COMPLETED;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't handle uploaded HRM report", e);
            return FileStatus.FAILED;
        }
    }

    /**
     * Validates that the content type is allowed for HRM uploads.
     * @param contentType the MIME type to validate
     * @return true if content type is allowed, false otherwise
     */
    private boolean isValidContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return false;
        }

        // Normalize content type (remove charset, etc.)
        String normalizedType = contentType.toLowerCase().split(";")[0].trim();
        return ALLOWED_CONTENT_TYPES.contains(normalizedType);
    }

    /**
     * Sanitizes filename to prevent path traversal attacks and other security issues.
     * Based on the pattern from BillingDocumentErrorReportUpload2Action.
     * @param fileName the original filename from user upload
     * @return sanitized filename or null if invalid
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }

        // First normalize Unicode to prevent homoglyph attacks
        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFKC);

        // URL decode to catch encoded traversal attempts like %2e%2e
        String decoded = normalized;
        try {
            // Decode multiple times to catch double-encoding
            for (int i = 0; i < 3; i++) {
                String temp = URLDecoder.decode(decoded, "UTF-8");
                if (temp.equals(decoded)) {
                    break; // No more encoding layers
                }
                decoded = temp;
            }
        } catch (Exception e) {
            // If decoding fails, reject the filename
            return null;
        }

        // Extract just the filename (remove any path components)
        String baseName = new File(decoded).getName();

        // Remove dangerous characters and sequences
        String sanitized = baseName
            .replaceAll("[\\\\/:*?\"<>|]", "") // Windows illegal chars
            .replaceAll("\\.{2,}", ".")        // Multiple dots (../)
            .replaceAll("^\\.", "")            // Leading dot
            .replaceAll("\\.$", "")            // Trailing dot
            .trim();

        // Additional security checks
        if (sanitized.isEmpty() ||
            sanitized.contains("..") ||
            sanitized.startsWith("/") ||
            sanitized.startsWith("\\") ||
            sanitized.length() > 255) {
            return null;
        }

        // Ensure we have a reasonable filename
        if (sanitized.length() < 1) {
            return null;
        }

        return sanitized;
    }
}
