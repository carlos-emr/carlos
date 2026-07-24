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


package io.github.carlos_emr.carlos.eform.actions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import io.github.carlos_emr.carlos.eform.util.EFormAssetContentType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.HtmlResponse;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.RequestNegotiation;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action that streams eform image and asset files (images, CSS, JavaScript, JSON)
 * directly to the HTTP response with the correct MIME content type.
 *
 * <p>Files are resolved relative to the configured eform image directory
 * ({@code CarlosProperties.getEformImageDirectory()}). Path traversal attempts are
 * rejected before any file I/O is performed.</p>
 *
 * <p>Supported file types include common raster image formats (PNG, JPEG, BMP, GIF, TIFF,
 * ICO, etc.), SVG, CSS, JavaScript, JSON, and HTML. An unsupported extension causes the
 * action to throw an exception rather than serving content with an ambiguous MIME type.</p>
 *
 * <p>This action is also used to serve admin-uploaded JSON catalogues (e.g.
 * {@code vaccine-brands.json}) for client-side autocomplete features.</p>
 *
 * @since 2026-03-06
 */
public class DisplayImage2Action extends ActionSupport {
    private static final org.apache.logging.log4j.Logger logger = MiscUtils.getLogger();
    static final String VACCINE_BRANDS_FILE = "vaccine-brands.json";
    private HttpServletRequest request = ServletActionContext.getRequest();
    private HttpServletResponse response = ServletActionContext.getResponse();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private record StreamData(InputStream stream, String contentType) {}

    public DisplayImage2Action() {
    }

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            logger.warn("DisplayImage2Action rejected: no authenticated session");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return NONE;
        }

        String fileName = request.getParameter("imagefile");
        boolean hasEformRead = securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null);
        if (VACCINE_BRANDS_FILE.equals(fileName)) {
            if (!hasEformRead
                    && !securityInfoManager.hasPrivilege(loggedInInfo, "_prevention", "r", null)) {
                throw new SecurityException("missing required sec object (_eform or _prevention)");
            }
        } else if (!hasEformRead) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        File validatedFile = getValidatedImageFile(fileName);
        if (!validatedFile.exists() || !validatedFile.isFile()) {
            logger.debug("eForm asset not found: {}", LogSafe.sanitize(fileName));
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        final StreamData data;
        try {
            data = process(validatedFile, fileName);
        } catch (FileNotFoundException e) {
            logger.debug("eForm asset disappeared before streaming: {}", LogSafe.sanitize(fileName));
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        } catch (IllegalArgumentException e) {
            logger.debug("eForm asset request rejected (unsupported type): {}", LogSafe.sanitize(fileName));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return NONE;
        }
        String contentType = data.contentType();
        try (InputStream stream = data.stream()) {
            if (RequestNegotiation.isHtmlContentType(contentType)) {
                // HtmlResponse owns the content type and charset for writer-backed HTML so the
                // logout listener remains injectable and charset handling stays centralized.
                // LogoutBroadcastFilter can only append the cross-window logout listener to writer-backed HTML.
                HtmlResponse.writeStoredHtml(response, contentType, stream);
                return NONE;
            }
            response.setContentType(contentType);
            OutputStream outputStream = response.getOutputStream();
            IOUtils.copy(stream, outputStream);
            return NONE;
        } catch (IOException | IllegalStateException e) {
            logger.error("Error streaming eform image to response", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            return NONE;
        }
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private File getValidatedImageFile(String fileName) throws Exception {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("imagefile parameter is required");
        }

        validateRequestedFileName(fileName);

        String home_dir = CarlosProperties.getInstance().getEformImageDirectory();
        File directory = new File(home_dir);
        if (!directory.exists()) {
            throw new Exception("Directory: " + home_dir + " does not exist");
        }

        return PathValidationUtils.validatePath(fileName, directory);
    }

    private void validateRequestedFileName(String fileName) {
        if (!fileName.equals(FilenameUtils.getName(fileName))) {
            logger.warn("Path traversal attempt in imagefile parameter: {}", LogSafe.sanitize(fileName)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            throw new SecurityException("Path traversal detected in imagefile parameter");
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    StreamData process(File file, String fileName) throws IOException {
        String contentType = resolveContentType(file);
        // nosniff: the declared allowlist type is the contract; a browser second-guessing bytes
        // into a scriptable type is never wanted on an asset route.
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (RequestNegotiation.isHtmlContentType(contentType)) {
            // A stored eForm asset served as text/html executes in the authenticated origin —
            // a stored-XSS channel if asset-upload rights are ever broader than admin. The
            // sandbox directive (no allow-* tokens) strips scripts/forms/origin from the served
            // document while keeping passive embedding working, so legacy html/rtl assets stay
            // servable without staying scriptable.
            response.setHeader("Content-Security-Policy", "sandbox");
        }
        response.setContentType(contentType);
        response.setHeader("Content-disposition", "inline; filename=\"" + sanitizeHeaderValue(fileName) + "\"");

        InputStream fileStream = new FileInputStream(file);
        return new StreamData(fileStream, contentType);
    }

    private String resolveContentType(File file) {
        // Shared with EFormImageViewForPdfGenerationServlet: EFormAssetContentType owns the
        // allowlist AND the extension parsing/lowercasing, so the paths cannot drift on either.
        return EFormAssetContentType.forFilename(file.getName())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported eform asset type"));
    }

    /**
     * Sanitizes a header value to prevent HTTP response splitting attacks.
     * Removes all control characters including CR (\r) and LF (\n) that could
     * be used to inject additional headers or split the HTTP response.
     * 
     * @param value The header value to sanitize
     * @return The sanitized header value safe for use in HTTP headers
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        
        // Remove all control characters including CR (\r) and LF (\n)
        // This prevents HTTP response splitting attacks
        // Also remove other control characters that could cause issues
        String sanitized = value
            .replaceAll("[\r\n\u0000-\u001F\u007F-\u009F]", "")  // Control chars
            .replaceAll("[\"';]", "");  // Quotes and semicolons

        // Ensure the filename is not empty after sanitization
        if (sanitized.trim().isEmpty()) {
            return "image";
        }
        
        return sanitized;
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static File getImageFile(String imageFileName) throws Exception {
        String home_dir = CarlosProperties.getInstance().getEformImageDirectory();
        File directory = new File(home_dir);
        if (!directory.exists()) {
            throw new Exception("Directory: " + home_dir + " does not exist");
        }
        return PathValidationUtils.validatePath(imageFileName, directory);
    }

    /**
     * Process only files under dir
     * This method used to list images for eform generator
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public String[] visitAllFiles(File dir) {
        String[] children = null;
        if (dir.isDirectory()) {
            children = dir.list();
            for (int i = 0; i < children.length; i++) {
                visitAllFiles(new File(dir, children[i]));
            }
        }
        return children;
    }

    public static String[] getRichTextLetterTemplates(File dir) {
        ArrayList<String> results = getFiles(dir, ".*(rtl)$", null);
        return results.toArray(new String[0]);
    }

    public static ArrayList<String> getFiles(File dir, String ext, ArrayList<String> files) {
        if (files == null) {
            files = new ArrayList<String>();
        }
        if (dir.isDirectory()) {
            for (String fileName : dir.list()) {
                if (fileName.toLowerCase().matches(ext)) {
                    files.add(fileName);
                }
            }
        }
        return files;
    }
}
