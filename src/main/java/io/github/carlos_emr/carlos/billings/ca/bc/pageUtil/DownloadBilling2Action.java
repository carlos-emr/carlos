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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.http.ContentDisposition;

/**
 * Downloads one Teleplan batch file, the MSP claim file or its HTML report, for the file links of the Generate
 * Teleplan File page's Activity List ({@code billing/CA/BC/DownloadBilling?filename=...}). The links answered 404:
 * no action was mapped there (issue #4439 D5).
 *
 * <p>The files hold patients' PHNs and claims, so the route needs {@code _billing} read and serves only a file that
 * a batch in {@code billactivity} names, from HOME_DIR, where {@code TeleplanSubmission.commit} writes them. The
 * name must be a single path component; anything else is refused before the filesystem is touched. Every file goes
 * out as an attachment of type {@code application/octet-stream}, so the HTML report is saved, never rendered in
 * CARLOS's origin.</p>
 *
 * @since 2026-10-22
 */
public final class DownloadBilling2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager;
    private final BillActivityDao billActivityDao;

    /**
     * @param securityInfoManager checks the requester's {@code _billing} read right
     * @param billActivityDao names the Teleplan batch files the route may serve
     */
    public DownloadBilling2Action(SecurityInfoManager securityInfoManager, BillActivityDao billActivityDao) {
        this.securityInfoManager = securityInfoManager;
        this.billActivityDao = billActivityDao;
    }

    /**
     * Streams the named batch file, or answers 400 for a name that is not a plain file name and 404 for one no
     * batch names or that is not on disk.
     *
     * @return {@link #NONE}; this action always writes its own response
     * @throws IOException if the response cannot be written
     */
    // FindSecBugs PATH_TRAVERSAL_IN: the name is a single validated path component that a billactivity row names,
    // and the resolved file is checked to lie inside the canonical HOME_DIR before it is read.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "the name is a single validated path component that a billactivity row names, and the resolved file is checked to lie inside the canonical HOME_DIR before it is read")
    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String filename;
        try {
            filename = PathValidationUtils.validatePathComponent(request.getParameter("filename"), "Teleplan file name");
        } catch (FileValidationException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        // Only Teleplan batch files: HOME_DIR holds other billing output (Ontario claim files) too.
        if (billActivityDao.findCurrentByFilename(filename).isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        byte[] contents;
        try {
            File homeDirectory = PathValidationUtils.getRequiredHomeDirectory();
            File file = PathValidationUtils.validateExistingPath(new File(homeDirectory, filename), homeDirectory);
            if (!file.isFile()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return NONE;
            }
            // Read whole before any header is set, so a failed read still gets a clean error status.
            contents = Files.readAllBytes(file.toPath());
        } catch (IOException | SecurityException e) {
            logger.warn("Teleplan batch file could not be read: {}", e.getClass().getSimpleName());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        response.setContentType("application/octet-stream");
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Batch names are generated ASCII (H, month code, date, batch number); ContentDisposition quotes them.
        response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(filename).build().toString());
        response.setContentLength(contents.length);
        response.getOutputStream().write(contents); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- attachment download, octet-stream with nosniff
        return NONE;
    }
}
