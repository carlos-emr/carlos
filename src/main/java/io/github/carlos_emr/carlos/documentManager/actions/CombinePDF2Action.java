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


package io.github.carlos_emr.carlos.documentManager.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class CombinePDF2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_doc)");
        }

        String[] files = request.getParameterValues("docNo");
        String ContentDisposition = request.getParameter("ContentDisposition");
        ArrayList<Object> alist = new ArrayList<Object>();
        if (files != null) {
            MiscUtils.getLogger().debug("size = " + files.length);
            EDocUtil docData = new EDocUtil();
            String path = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
            Path filePath;
            for (int i = 0; i < files.length; i++) {
                String filename = docData.getDocumentName(files[i]);
                filePath = PathValidationUtils.validateExistingPath(new File(path, filename), new File(path)).toPath();
                alist.add(filePath.toAbsolutePath().toString());
            }
            if (alist.size() > 0) {
                response.setContentType("application/pdf");  //octet-stream
                if (ContentDisposition != null && ContentDisposition.equals("inline")) {
                    response.setHeader("Transfer-Encoding", "chunked");
                    response.setHeader("Cache-Control", "cache, must-revalidate"); // IE workaround
                    response.setHeader("Pragma", "public"); // IE workaround
                    response.setHeader("Content-Disposition", "inline; filename=\"combinedPDF-" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf\"");
                } else {

                    response.setHeader("Content-Disposition", "attachment; filename=\"combinedPDF-" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf\"");
                }
                try {
                    // Buffer the merge first: skippedFiles is fully known before any byte is written,
                    // so we can surface an error instead of streaming a silently-truncated PDF.
                    java.io.ByteArrayOutputStream pdfBuffer = new java.io.ByteArrayOutputStream();
                    int skipped = ConcatPDF.concat(alist, pdfBuffer);
                    if (skipped > 0) {
                        // Some documents could not be included: refuse to serve a truncated PDF.
                        MiscUtils.getLogger().error("Combine PDF: {} of {} document(s) could not be included",
                                skipped, alist.size());
                        if (!response.isCommitted()) {
                            response.reset();
                            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                    skipped + " of " + alist.size() + " document(s) could not be included; combined PDF not produced");
                        }
                    } else {
                        response.getOutputStream().write(pdfBuffer.toByteArray());
                    }
                } catch (IOException | RuntimeException ex) {
                    // RuntimeException covers ConcatPDF's merge failure; own the error rather than letting
                    // Struts write an HTML error page into the application/pdf download.
                    MiscUtils.getLogger().error("Combine PDF failed", ex);
                    if (!response.isCommitted()) {
                        try {
                            response.reset();
                            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate the combined PDF");
                        } catch (IOException sendErr) {
                            MiscUtils.getLogger().error("Failed to send combine-PDF error response", sendErr);
                        }
                    }
                }
                // This branch streams the PDF directly; returning NONE prevents Struts from
                // resolving the success result and appending a JSP/error page to the PDF.
                return NONE;
            }
        }
        return SUCCESS;
    }

    /**
     * Creates a new instance of CombinePDF2Action
     */
    public CombinePDF2Action() {
    }

}
