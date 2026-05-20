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

package io.github.carlos_emr.carlos.util;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.logging.log4j.Logger;

public class BackupDownload extends GenericDownload {
    private static final String BACKUP_DOWNLOAD_PRIVILEGE_REQUIRED = "Backup download privilege required.";
    private static final String DEFAULT_BACKUP_DIRECTORY = "/home/mysql/";

    private static final Logger log = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager;

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            // check the rights - sanitize filename to prevent XSS and path traversal
            String rawFilename = req.getParameter("filename");
            String filename = rawFilename == null ? null : MiscUtils.sanitizeFileName(rawFilename);
            if (filename == null || filename.isBlank()) {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required filename parameter.");
                return;
            }

            HttpSession session = req.getSession(false);
            if (session == null) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, BACKUP_DOWNLOAD_PRIVILEGE_REQUIRED);
                return;
            }

            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(session);
            if (loggedInInfo == null || !hasBackupDownloadPrivilege(loggedInInfo)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, BACKUP_DOWNLOAD_PRIVILEGE_REQUIRED);
                return;
            }

            String dir = (String) session.getAttribute("backupfilepath");
            if (dir == null) {
                dir = DEFAULT_BACKUP_DIRECTORY;
            }

            download(true, res, dir, filename, null);
        } catch (IOException e) {
            throw e;
        } catch (SecurityException e) {
            log.warn("SecurityException in BackupDownload", e);
            if (!res.isCommitted()) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, BACKUP_DOWNLOAD_PRIVILEGE_REQUIRED);
            }
        } catch (Exception e) {
            log.error("Unexpected error in BackupDownload", e);
            if (!res.isCommitted()) {
                res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
            }
        }
    }

    private boolean hasBackupDownloadPrivilege(LoggedInInfo loggedInInfo) {
        SecurityInfoManager manager = getSecurityInfoManager();
        return manager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                || manager.hasPrivilege(loggedInInfo, "_admin.backup", "r", null);
    }

    private synchronized SecurityInfoManager getSecurityInfoManager() {
        if (securityInfoManager == null) {
            securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        }
        return securityInfoManager;
    }
}
