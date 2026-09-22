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
package io.github.carlos_emr.carlos.hospitalReportManager;

import org.apache.struts2.ActionSupport;

import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.StringUtils;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HRMDownloadFile2Action extends ActionSupport {
    private HttpServletRequest request = ServletActionContext.getRequest();
    private HttpServletResponse response = ServletActionContext.getResponse();

    private HRMDocumentDao hrmDocumentDao = SpringUtils.getBean(HRMDocumentDao.class);
    /**
     * Content types the report frame may render inline. Deliberately excludes {@code text/html}
     * and {@code application/octet-stream}: an inline HTML report would run in the
     * application's origin, and an opaque blob has no reason to render in place.
     */
    private static final java.util.Set<String> INLINE_SAFE_CONTENT_TYPES = java.util.Set.of(
            "application/pdf", "image/tiff", "image/jpeg", "image/gif", "image/png");

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Decides the {@code Content-Disposition} for a report body.
     *
     * @param requested   the caller's {@code disposition} parameter; only the exact value
     *                    {@code "inline"} opts in, so every existing caller keeps a download
     * @param contentType the type resolved from the report's file extension
     * @return {@code "inline"} only for a requested, render-safe type; {@code "attachment"}
     *         otherwise — including for {@code text/html}, which inline would execute in the
     *         application's own origin
     */
    static String dispositionFor(String requested, String contentType) {
        boolean inline = "inline".equals(requested) && INLINE_SAFE_CONTENT_TYPES.contains(contentType);
        return inline ? "inline" : "attachment";
    }

    public String execute() throws Exception {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "r", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        String hash = request.getParameter("hash");
        if (StringUtils.isNullOrEmpty(hash)) {
            throw new Exception("no hash parameter passed");
        }

        List<Integer> ids = hrmDocumentDao.findByHash(hash);

        if (ids == null || ids.size() == 0) {
            throw new Exception("no documents found for hash - " + hash);
        }

        if (ids.size() > 1) {
            throw new Exception("too many documents found for hash - " + hash);
        }

        HRMDocument hd = hrmDocumentDao.find(ids.get(0));

        if (hd == null) {
            throw new Exception("HRMDocument not found - " + ids.get(0));
        }

        HRMReport report = HRMReportParser.parseReport(loggedInInfo, hd.getReportFile());

        if (report == null) {
            throw new Exception("Failed to parse HRMDocument with id " + hd.getId());
        }

        if (!report.isBinary()) {
            throw new Exception("no binary document found");
        }

        byte[] data = report.getBinaryContent();


        String fileName = (report.getLegalLastName() + "-" + report.getLegalFirstName() + "-" + report.getFirstReportClass() + report.getFileExtension()).replaceAll("\\s", "_");
        
        String contentType = switch (report.getFileExtension()) {
            case ".pdf" -> "application/pdf";
            case ".tiff" -> "image/tiff";
            case ".rtf" -> "text/enriched";
            case ".jpg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".png" -> "image/png";
            case ".html" -> "text/html";
            default -> "application/octet-stream";
        };

        response.setContentType(contentType);
        response.setContentLength(data.length);

        // Encode filename per RFC 5987 using UTF-8
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        // The report viewer frames this endpoint to show the report in place; an attachment
        // disposition makes the browser download it from that frame instead of rendering it,
        // leaving the preview blank and starting a download nobody asked for. Inline is opt-in
        // (?disposition=inline) so the download link keeps its attachment behaviour.
        //
        // Inline is allowed only for the formats a browser renders as data. HTML in particular
        // stays an attachment: serving attacker-influenced report HTML inline from the
        // application's own origin would execute it in that origin.
        String disposition = dispositionFor(request.getParameter("disposition"), contentType);

        // Set both headers for compatibility
        response.setHeader("Content-Disposition", disposition + "; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName);

        try (ServletOutputStream out = response.getOutputStream()) {
            out.write(data);
        } catch (IOException e) {
            throw new IOException("Error streaming HRM report: " + e.getMessage(), e);
        }

        return NONE;
    }
}
