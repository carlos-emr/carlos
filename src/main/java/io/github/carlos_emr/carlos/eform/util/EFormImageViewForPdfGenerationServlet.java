/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.eform.actions.DisplayImage2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * The purpose of this servlet is to allow a local process to access eform images.
 */
public final class EFormImageViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String VACCINE_BRANDS_FILE = "vaccine-brands.json";
    private static final String IMAGE_JPEG = "image/jpeg";
    private static final String HTML_CONTENT_TYPE = "text/html";
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpeg", IMAGE_JPEG),
            Map.entry("jpe", IMAGE_JPEG),
            Map.entry("jpg", IMAGE_JPEG),
            Map.entry("bmp", "image/bmp"),
            Map.entry("cod", "image/cis-cod"),
            Map.entry("ief", "image/ief"),
            Map.entry("jfif", "image/pipeg"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("tif", "image/tiff"),
            Map.entry("pbm", "image/x-portable-bitmap"),
            Map.entry("pnm", "image/x-portable-anymap"),
            Map.entry("pgm", "image/x-portable-greymap"),
            Map.entry("ppm", "image/x-portable-pixmap"),
            Map.entry("xbm", "image/x-xbitmap"),
            Map.entry("xpm", "image/x-xpixmap"),
            Map.entry("xwd", "image/x-xwindowdump"),
            Map.entry("rgb", "image/x-rgb"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("cmx", "image/x-cmx"),
            Map.entry("ras", "image/x-cmu-raster"),
            Map.entry("gif", "image/gif"),
            Map.entry("js", "text/javascript"),
            Map.entry("css", "text/css"),
            Map.entry("json", "application/json"),
            Map.entry("rtl", HTML_CONTENT_TYPE),
            Map.entry("html", HTML_CONTENT_TYPE),
            Map.entry("htm", HTML_CONTENT_TYPE)
    );

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String remoteAddress = request.getRemoteAddr();
        logger.debug("EFormImageViewForPdfGenerationServlet request from : {}", remoteAddress);

        if (!isLocalRequest(remoteAddress)) {
            logger.warn("Unauthorised request made to EFormImageViewForPdfGenerationServlet from address : {}", remoteAddress);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        try {
            String fileName = validateRequestedFileName(request.getParameter("imagefile"));
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            if (loggedInInfo == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            enforceAssetReadPrivilege(loggedInInfo, fileName);

            File file = DisplayImage2Action.getImageFile(fileName);
            if (!file.exists() || !file.isFile()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            response.setContentType(resolveContentType(file));
            response.setHeader("Content-disposition", "inline; filename=\"" + sanitizeHeaderValue(fileName) + "\"");
            try (InputStream stream = new FileInputStream(file)) {
                OutputStream outputStream = response.getOutputStream();
                IOUtils.copy(stream, outputStream);
            }
        } catch (ServletException | IOException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            logger.warn("Rejected EFormImageViewForPdfGenerationServlet request", e);
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(),
                    "Unable to send bad-request response for EFormImageViewForPdfGenerationServlet");
        } catch (SecurityException e) {
            logger.warn("Rejected EFormImageViewForPdfGenerationServlet request", e);
            sendErrorQuietly(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage(),
                    "Unable to send forbidden response for EFormImageViewForPdfGenerationServlet");
        } catch (Exception e) {
            logger.error("Unexpected error in EFormImageViewForPdfGenerationServlet", e);
            sendErrorQuietly(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.",
                    "Unable to send internal-error response for EFormImageViewForPdfGenerationServlet");
        }
    }

    private void enforceAssetReadPrivilege(LoggedInInfo loggedInInfo, String fileName) {
        SecurityInfoManager manager = SpringUtils.getBean(SecurityInfoManager.class);
        boolean hasEformRead = manager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null);
        if (VACCINE_BRANDS_FILE.equals(fileName)) {
            if (!hasEformRead && !manager.hasPrivilege(loggedInInfo, "_prevention", SecurityInfoManager.READ, null)) {
                throw new SecurityException("missing required sec object (_eform or _prevention)");
            }
            return;
        }
        if (!hasEformRead) {
            throw new SecurityException("missing required sec object (_eform)");
        }
    }

    private static String validateRequestedFileName(String fileName) {
        try {
            return PathValidationUtils.validatePathComponent(fileName, "imagefile");
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid imagefile parameter", e);
        }
    }

    private static String resolveContentType(File file) {
        String extension = extension(file.getName()).toLowerCase(Locale.ROOT);
        String contentType = CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw new IllegalArgumentException("Unsupported eform asset type");
        }
        return contentType;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1);
    }

    private static String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = value
                .replaceAll("[\u0000-\u001F\u007F-\u009F]", "")
                .replace("\"", "")
                .replace(";", "")
                .replace("'", "");

        if (sanitized.trim().isEmpty()) {
            return "image";
        }

        return sanitized;
    }

    private static boolean isLocalRequest(String remoteAddress) {
        return "127.0.0.1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1".equals(remoteAddress)
                || "::1".equals(remoteAddress);
    }

    private static void sendErrorQuietly(HttpServletResponse response, int statusCode, String message, String logMessage) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.sendError(statusCode, message);
        } catch (IOException ioException) {
            logger.debug(logMessage, ioException);
        }
    }

}
