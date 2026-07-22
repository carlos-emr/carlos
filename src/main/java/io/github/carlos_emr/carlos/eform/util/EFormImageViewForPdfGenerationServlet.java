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
import jakarta.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.eform.actions.DisplayImage2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Streams shared eForm template assets (background images, JS, CSS — plus the
 * {@code vaccine-brands.json} data file) to the loopback browser PDF renderer and to
 * authenticated in-app callers.
 *
 * <p>Contract: requests must originate from a loopback address (checked first), then satisfy ONE
 * of two authorization regimes — an authenticated session holding {@code _eform} READ
 * ({@code _prevention} READ is an accepted alternative for {@code vaccine-brands.json} only), or a
 * live render-scoped grant minted by {@link EFormRenderTokenService} after the caller's
 * {@code _eform} check (the sessionless render browser's path; the grant is deliberately not
 * bound to the requested asset because these are shared templates, and grant-authorized fetches
 * are logged with the grant's fdid for auditability). Filenames are validated as single path
 * components against the eForm image directory, and content types come from the shared
 * {@link EformAssetContentType} allowlist. No PHI is served by this servlet.</p>
 */
public final class EFormImageViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String VACCINE_BRANDS_FILE = "vaccine-brands.json";
    // Shared with DisplayImage2Action via EformAssetContentType so the two eForm asset-streaming
    // paths cannot drift on the MIME allowlist (cubic CQQa). Header hardening (sanitizeHeaderValue)
    // stays per-class.
    private static final Map<String, String> CONTENT_TYPES = EformAssetContentType.BY_EXTENSION;

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
            // Use the existing session only; never allocate one. The sessionless render browser must
            // not receive a session cookie, and rejected probes must not populate the session manager.
            HttpSession session = request.getSession(false);
            LoggedInInfo loggedInInfo = session == null ? null : LoggedInInfo.getLoggedInInfoFromSession(session);
            if (loggedInInfo != null) {
                enforceAssetReadPrivilege(loggedInInfo, fileName);
                logger.debug("eForm asset request authorized via _eform session");
            } else {
                EFormRenderTokenService.RenderGrant grant = liveRenderGrant(request);
                if (grant == null) {
                    // The server-side PDF renderer fetches an eForm's asset images with no HTTP session
                    // by design (no session cookie ever enters the render browser). Such requests are
                    // authorized instead by a render-scoped grant that was minted only after an _eform
                    // privilege check, is loopback-only, and is invalidated when the render finishes.
                    logger.warn("eForm asset request rejected: no authenticated session and no valid render grant");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                // Log the accepted grant's fdid: assets are shared templates, so the grant is
                // deliberately not asset-bound — the fdid trail is what makes a cross-render
                // asset fetch (any live grant reading the shared image directory) auditable.
                logger.debug("eForm asset request authorized via render grant (sessionless render browser): fdid={}",
                        grant.fdid());
            }

            File file = DisplayImage2Action.getImageFile(fileName);
            if (!file.exists() || !file.isFile()) {
                logger.debug("eForm asset not found: {}", LogSafe.sanitize(fileName));
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            response.setContentType(resolveContentType(file));
            response.setHeader("Content-disposition", "inline; filename=\"" + sanitizeHeaderValue(fileName) + "\"");
            try (InputStream stream = new FileInputStream(file)) {
                OutputStream outputStream = response.getOutputStream();
                IOUtils.copy(stream, outputStream);
            }
            logger.debug("Streamed eForm asset to render browser: {}", LogSafe.sanitize(fileName));
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

    /**
     * Returns the live render-scoped grant carried by the request, or null. The grant authorizes
     * the sessionless render browser to read shared eForm template assets (backgrounds, JS, CSS)
     * over loopback for the duration of one render; it was issued only after an {@code _eform}
     * privilege check. Deliberately not bound to the requested asset — assets are shared
     * templates, unlike the fdid-bound page and signature surfaces — so the caller logs the
     * grant's fdid to keep cross-render asset access auditable.
     */
    private static EFormRenderTokenService.RenderGrant liveRenderGrant(HttpServletRequest request) {
        return EFormRenderTokenService.getInstance().peek(EFormRenderTokenService.RenderToken
                .fromRequestValue(request.getParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM)));
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
        } catch (FileValidationException e) {
            // validatePathComponent signals malformed/blank/traversal input with a
            // FileValidationException (a SecurityException). Translate it to IllegalArgumentException so
            // doGet answers 400 (client error) instead of the 403 the SecurityException handler would
            // emit — a bad imagefile is a bad request, not an authorization failure (cubic Fc2c/SIZkT).
            throw new IllegalArgumentException("Invalid imagefile parameter", e);
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
