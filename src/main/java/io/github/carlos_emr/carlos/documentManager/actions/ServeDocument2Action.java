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
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Streams a document's raw bytes to the browser so PDF.js can render it client-side.
 *
 * <p>This action exists so the document file on disk is never referenced by a
 * user-controlled URL path. The caller supplies only the {@code docId}; the action
 * resolves the real file path server-side, validates the caller's {@code _edoc r}
 * privilege, and streams the bytes with no-store cache headers to prevent PHI from
 * being cached by proxies or browsers.
 *
 * <p>Used exclusively by {@code FaxAnnotateViewer.jsp} to load the PDF into PDF.js.
 *
 * @since 2026-06
 */
public class ServeDocument2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String docIdStr = StringUtils.trimToNull(request.getParameter("docId"));
        if (docIdStr == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "docId is required");
            return NONE;
        }

        int docId;
        try {
            docId = Integer.parseInt(docIdStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid docId");
            return NONE;
        }

        EDoc doc = EDocUtil.getDoc(String.valueOf(docId));
        if (doc == null || StringUtils.isBlank(doc.getFilePath())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        String filePath = doc.getFilePath();
        Path docPath = Paths.get(filePath);

        try {
            PathValidationUtils.validateExistingPath(docPath.toFile(), docPath.getParent().toFile());
        } catch (SecurityException e) {
            logger.error("Path traversal attempt for docId={}: {}", docId, filePath);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        if (!Files.exists(docPath) || !Files.isRegularFile(docPath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        String contentType = StringUtils.defaultIfBlank(doc.getContentType(), "application/pdf");
        response.setContentType(contentType);
        response.setContentLengthLong(Files.size(docPath));
        // PHI document — must not be cached anywhere
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Content-Disposition", "inline; filename=\"document.pdf\"");

        try (OutputStream os = response.getOutputStream()) {
            Files.copy(docPath, os);
        }

        return NONE;
    }
}
