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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Document;
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
    private transient CtlDocumentDao ctlDocumentDao = SpringUtils.getBean(CtlDocumentDao.class);
    private transient DocumentDao documentDao = SpringUtils.getBean(DocumentDao.class);
    private transient OutboundEmailArchiveDao outboundEmailArchiveDao = SpringUtils.getBean(OutboundEmailArchiveDao.class);

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String[] files = request.getParameterValues("docNo");
        String ContentDisposition = request.getParameter("ContentDisposition");
        ArrayList<Object> alist = new ArrayList<Object>();
        if (files != null) {
            MiscUtils.getLogger().debug("size = " + files.length);
            List<Integer> documentNos = new ArrayList<>();
            for (String file : files) {
                try {
                    documentNos.add(Integer.valueOf(file));
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return NONE;
                }
            }
            Set<Integer> archiveDocumentNos = outboundEmailArchiveDao.findExistingDocumentNos(documentNos);
            if (archiveDocumentNos != null && !archiveDocumentNos.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
            List<CtlDocument> documentLinks = ctlDocumentDao.findByDocumentNos(documentNos);
            Map<Integer, List<CtlDocument>> linksByDocumentNo = new HashMap<>();
            for (CtlDocument documentLink : documentLinks) {
                if (documentLink == null || documentLink.getId() == null
                        || documentLink.getId().getDocumentNo() == null) {
                    continue;
                }
                linksByDocumentNo
                        .computeIfAbsent(documentLink.getId().getDocumentNo(), ignored -> new ArrayList<>())
                        .add(documentLink);
            }
            List<Document> documents = new ArrayList<>();
            for (Integer documentNo : documentNos) {
                Document document = documentDao.find(documentNo.intValue());
                if (document == null || document.getDocfilename() == null || document.getDocfilename().isBlank()) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return NONE;
                }
                if (!isAuthorizedDocumentScope(loggedInInfo, document, linksByDocumentNo.get(documentNo))) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return NONE;
                }
                documents.add(document);
            }
            File documentDir = PathValidationUtils.resolveConfiguredDirectory(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
            Path filePath;
            for (Document document : documents) {
                filePath = PathValidationUtils.validateExistingPath(
                        new File(documentDir, document.getDocfilename()), documentDir).toPath();
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
                File tempPdf = null;
                try {
                    // Merge to a secure temp file (not an in-memory buffer): the skipped count is known
                    // before streaming, so we can refuse to serve a silently-truncated PDF, without
                    // risking high memory / OOM on large or large-count combined documents.
                    tempPdf = PathValidationUtils.createSecureTempFile("combinedPDF" + System.currentTimeMillis(), ".pdf");
                    int skipped;
                    try (FileOutputStream tmpOut = new FileOutputStream(tempPdf)) {
                        skipped = ConcatPDF.concat(alist, tmpOut);
                    }
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
                        Files.copy(tempPdf.toPath(), response.getOutputStream());
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
                } finally {
                    if (tempPdf != null && !tempPdf.delete()) {
                        MiscUtils.getLogger().warn("Failed to delete temporary combined-PDF file; leaving for OS temp sweep");
                    }
                }
                // This branch streams the PDF directly; returning NONE prevents Struts from
                // resolving the success result and appending a JSP/error page to the PDF.
                return NONE;
            }
        }
        return SUCCESS;
    }

    boolean isAuthorizedDocumentScope(
            LoggedInInfo loggedInInfo, Document document, List<CtlDocument> documentLinks) {
        if (documentLinks == null || documentLinks.isEmpty()) {
            return false;
        }

        boolean hasDemographicLink = false;
        for (CtlDocument documentLink : documentLinks) {
            String module = documentLink.getId().getModule();
            Integer moduleId = documentLink.getId().getModuleId();
            if ("demographic".equals(module)) {
                hasDemographicLink = true;
                if (moduleId == null
                        || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, moduleId)) {
                    return false;
                }
            }
        }
        if (hasDemographicLink) {
            return true;
        }

        boolean hasProviderLink = documentLinks.stream()
                .anyMatch(documentLink -> EDocUtil.isProviderModule(documentLink.getId().getModule()));
        if (!hasProviderLink) {
            return false;
        }
        if (document.getPublic1() == 1) {
            return true;
        }
        Integer providerNo = parseProviderNo(loggedInInfo.getLoggedInProviderNo());
        return providerNo != null && documentLinks.stream()
                .anyMatch(documentLink -> EDocUtil.isProviderModule(documentLink.getId().getModule())
                        && providerNo.equals(documentLink.getId().getModuleId()));
    }

    private Integer parseProviderNo(String providerNo) {
        try {
            return providerNo != null ? Integer.valueOf(providerNo) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Creates a new instance of CombinePDF2Action
     */
    public CombinePDF2Action() {
    }

}
