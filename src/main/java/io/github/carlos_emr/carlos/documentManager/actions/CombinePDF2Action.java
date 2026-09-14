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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
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
    private static final int MAX_DOCUMENTS_TO_COMBINE = 100;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final transient SecurityInfoManager securityInfoManager;
    private final transient CtlDocumentDao ctlDocumentDao;
    private final transient DocumentDao documentDao;
    private final transient DemographicDao demographicDao;
    private final transient ProgramManager programManager;
    private final transient ProgramManager2 programManager2;

    /**
     * Authorizes access to every requested eDoc and streams their combined PDF.
     *
     * <p>The caller must have the {@code _edoc} write privilege; otherwise this method throws a
     * {@link SecurityException}. Invalid, unauthorized, missing, or excessive requests return
     * {@link #NONE} with HTTP 400, 403, 404, or 413 respectively. PDF output is streamed directly
     * and also returns {@code NONE}. When no {@code docNo} values are supplied, no response is
     * streamed and {@link #SUCCESS} is returned.
     *
     * @return {@link #NONE} when the request is rejected or handled directly, otherwise
     *         {@link #SUCCESS}
     * @throws SecurityException when the caller lacks the {@code _edoc} write privilege
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String[] files = request.getParameterValues("docNo");
        String contentDisposition = request.getParameter("ContentDisposition");
        List<Object> inputPaths = new ArrayList<>();
        if (files != null) {
            MiscUtils.getLogger().debug("size = " + files.length);
            if (files.length > MAX_DOCUMENTS_TO_COMBINE) {
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return NONE;
            }
            Set<Integer> uniqueDocumentNos = new LinkedHashSet<>();
            for (String file : files) {
                try {
                    Integer documentNo = Integer.valueOf(file);
                    if (documentNo <= 0) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        return NONE;
                    }
                    uniqueDocumentNos.add(documentNo);
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return NONE;
                }
            }
            List<Integer> documentNos = new ArrayList<>(uniqueDocumentNos);
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
            Map<Integer, Boolean> demographicExistence = new HashMap<>();
            Set<Long> programDomain = getProgramDomain(loggedInInfo);
            for (Integer documentNo : documentNos) {
                Document document = documentDao.find(documentNo.intValue());
                if (document == null || document.getDocfilename() == null || document.getDocfilename().isBlank()) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return NONE;
                }
                if (!isAuthorizedDocumentScope(
                        loggedInInfo,
                        document,
                        linksByDocumentNo.get(documentNo),
                        demographicExistence,
                        programDomain)) {
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
                inputPaths.add(filePath.toAbsolutePath().toString());
            }
            if (!inputPaths.isEmpty()) {
                auditDocumentReads(loggedInInfo, documents);
                configurePdfResponse(contentDisposition);
                File tempPdf = null;
                try {
                    // Merge to a secure temp file (not an in-memory buffer): the skipped count is known
                    // before streaming, so we can refuse to serve a silently-truncated PDF, without
                    // risking high memory / OOM on large or large-count combined documents.
                    tempPdf = PathValidationUtils.createSecureTempFile("combinedPDF" + System.currentTimeMillis(), ".pdf");
                    int skipped;
                    try (FileOutputStream tmpOut = new FileOutputStream(tempPdf)) {
                        skipped = ConcatPDF.concat(inputPaths, tmpOut);
                    }
                    if (skipped > 0) {
                        // Some documents could not be included: refuse to serve a truncated PDF.
                        MiscUtils.getLogger().error("Combine PDF: {} of {} document(s) could not be included",
                                skipped, inputPaths.size());
                        if (!response.isCommitted()) {
                            response.reset();
                            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                    skipped + " of " + inputPaths.size() + " document(s) could not be included; combined PDF not produced");
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

    void configurePdfResponse(String contentDisposition) {
        response.setContentType("application/pdf");
        response.setHeader("Cache-Control", "private, no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setHeader("X-Content-Type-Options", "nosniff");
        String disposition = "inline".equals(contentDisposition) ? "inline" : "attachment";
        response.setHeader(
                "Content-Disposition",
                disposition + "; filename=\"combinedPDF-"
                        + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf\"");
    }

    boolean isAuthorizedDocumentScope(
            LoggedInInfo loggedInInfo, Document document, List<CtlDocument> documentLinks) {
        return isAuthorizedDocumentScope(
                loggedInInfo,
                document,
                documentLinks,
                new HashMap<>(),
                getProgramDomain(loggedInInfo));
    }

    private boolean isAuthorizedDocumentScope(
            LoggedInInfo loggedInInfo,
            Document document,
            List<CtlDocument> documentLinks,
            Map<Integer, Boolean> demographicExistence,
            Set<Long> programDomain) {
        if (!isAuthorizedDocumentProgramScope(loggedInInfo, document, programDomain)) {
            return false;
        }
        if (documentLinks == null || documentLinks.isEmpty()) {
            return false;
        }

        DemographicAuthorization demographicAuthorization = evaluateDemographicAuthorization(
                loggedInInfo, documentLinks, demographicExistence);
        if (demographicAuthorization != DemographicAuthorization.NO_VALID_LINK) {
            return demographicAuthorization == DemographicAuthorization.AUTHORIZED;
        }
        return isAuthorizedProviderScope(loggedInInfo, document, documentLinks);
    }

    private DemographicAuthorization evaluateDemographicAuthorization(
            LoggedInInfo loggedInInfo,
            List<CtlDocument> documentLinks,
            Map<Integer, Boolean> demographicExistence) {
        boolean hasValidLink = false;
        for (CtlDocument documentLink : documentLinks) {
            String module = documentLink.getId().getModule();
            Integer moduleId = documentLink.getId().getModuleId();
            if (isDemographicModule(module)) {
                if (!isExistingDemographic(moduleId, demographicExistence)) {
                    continue;
                }
                hasValidLink = true;
                if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, moduleId)) {
                    return DemographicAuthorization.DENIED;
                }
            }
        }
        return hasValidLink
                ? DemographicAuthorization.AUTHORIZED
                : DemographicAuthorization.NO_VALID_LINK;
    }

    private boolean isAuthorizedProviderScope(
            LoggedInInfo loggedInInfo, Document document, List<CtlDocument> documentLinks) {
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

    private enum DemographicAuthorization {
        AUTHORIZED,
        DENIED,
        NO_VALID_LINK
    }

    private Set<Long> getProgramDomain(LoggedInInfo loggedInInfo) {
        List<ProgramProvider> programProviders = programManager2.getProgramDomain(
                loggedInInfo, loggedInInfo.getLoggedInProviderNo());
        if (programProviders == null || programProviders.isEmpty()) {
            return Set.of();
        }
        Set<Long> programIds = new HashSet<>();
        for (ProgramProvider programProvider : programProviders) {
            if (programProvider != null && programProvider.getProgramId() != null) {
                programIds.add(programProvider.getProgramId());
            }
        }
        return programIds;
    }

    private boolean isAuthorizedDocumentProgramScope(
            LoggedInInfo loggedInInfo, Document document, Set<Long> programDomain) {
        Integer programId = document.getProgramId();
        if (programId != null
                && CarlosProperties.getInstance().getBooleanProperty("FILTER_ON_FACILITY", "true")
                && !programManager.hasAccessBasedOnCurrentFacility(loggedInInfo, programId)) {
            return false;
        }
        return !Boolean.TRUE.equals(document.isRestrictToProgram())
                || programId == null
                || programId == -1
                || programDomain.contains(programId.longValue());
    }

    void auditDocumentReads(LoggedInInfo loggedInInfo, List<Document> documents) {
        for (Document document : documents) {
            LogAction.addLog(
                    loggedInInfo,
                    LogConst.READ,
                    LogConst.CON_DOCUMENT,
                    String.valueOf(document.getDocumentNo()),
                    null,
                    "combined PDF");
        }
    }

    private boolean isExistingDemographic(
            Integer demographicNo, Map<Integer, Boolean> demographicExistence) {
        if (demographicNo == null || demographicNo <= 0) {
            return false;
        }
        return demographicExistence.computeIfAbsent(demographicNo, id -> {
            Demographic demographic = demographicDao.getDemographicById(id);
            return demographic != null && id.equals(demographic.getDemographicNo());
        });
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "ASCII database module identifier is matched case-insensitively for legacy compatibility")
    private boolean isDemographicModule(String module) {
        return "demographic".equalsIgnoreCase(module);
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
        this(
                SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(CtlDocumentDao.class),
                SpringUtils.getBean(DocumentDao.class),
                SpringUtils.getBean(DemographicDao.class),
                SpringUtils.getBean(ProgramManager.class),
                SpringUtils.getBean(ProgramManager2.class));
    }

    CombinePDF2Action(
            SecurityInfoManager securityInfoManager,
            CtlDocumentDao ctlDocumentDao,
            DocumentDao documentDao,
            DemographicDao demographicDao,
            ProgramManager programManager,
            ProgramManager2 programManager2) {
        this.securityInfoManager = securityInfoManager;
        this.ctlDocumentDao = ctlDocumentDao;
        this.documentDao = documentDao;
        this.demographicDao = demographicDao;
        this.programManager = programManager;
        this.programManager2 = programManager2;
    }

}
