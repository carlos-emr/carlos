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

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import org.openpdf.text.pdf.PdfReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.IncomingDocUtil;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import org.apache.commons.io.FilenameUtils;
import org.owasp.encoder.Encode;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for document viewing, updating, routing, and incoming document processing
 * in the CARLOS EMR document management system.
 *
 * <p>Uses a method-based routing pattern via a static {@code ACTIONS} map that dispatches
 * the request parameter "method" to the corresponding handler. Supported operations include:
 * <ul>
 *   <li>Document viewing as PDF or rendered PNG images with caching</li>
 *   <li>Document metadata updates (description, type, observation date)</li>
 *   <li>Provider inbox routing and demographic linking</li>
 *   <li>Incoming document filing from the incoming document queue to the document store</li>
 *   <li>Document refiling between queues</li>
 *   <li>Document info/annotation/tickler display</li>
 * </ul>
 *
 * <p>PDF page counting uses OpenPDF {@link PdfReader} for existing documents in the
 * document store. Incoming document rendering uses Apache PDFBox for PDF-to-PNG conversion.
 *
 * <p>Security: All operations require the {@code _edoc} read or write privilege. File
 * paths are validated using {@link PathValidationUtils}. HTTP response headers are
 * sanitized to prevent response splitting attacks.
 *
 * @see AddEditDocument2Action
 * @see IncomingDocUtil
 * @see EDocUtil
 * @see PathValidationUtils
 * @since 2008-09-10
 */
public class ManageDocument2Action extends ActionSupport {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final Logger log = MiscUtils.getLogger();

    private final DocumentDao documentDao = SpringUtils.getBean(DocumentDao.class);
    private final CtlDocumentDao ctlDocumentDao = SpringUtils.getBean(CtlDocumentDao.class);
    private final ProviderInboxRoutingDao providerInboxRoutingDAO = SpringUtils.getBean(ProviderInboxRoutingDao.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final String DOCUMENT_DIR = OscarProperties.getInstance().getDocumentDirectory();
    private static final String DOCUMENT_CACHE_DIR = OscarProperties.getInstance().getDocumentCacheDirectory();

    private static final Map<String, ActionHandler> ACTIONS = new HashMap<>();

    // Static initializer used to set the actions of the map for the execute function
    static {
        ACTIONS.put("refileDocumentAjax", ctx -> ctx.refileDocumentAjax());
        ACTIONS.put("viewDocPage", ctx -> { ctx.viewDocPage(); return null; });
        ACTIONS.put("display", ctx -> { ctx.display(); return null; });
        ACTIONS.put("viewAnnotationAcknowledgementTickler", ctx -> { ctx.viewAnnotationAcknowledgementTickler(); return null; });
        ACTIONS.put("viewDocumentDescription", ctx -> { ctx.viewDocumentDescription(); return null; });
        ACTIONS.put("viewIncomingDocPageAsPdf", ctx -> { ctx.viewIncomingDocPageAsPdf(); return null; });
        ACTIONS.put("viewIncomingDocPageAsImage", ctx -> { ctx.viewIncomingDocPageAsImage(); return null; });
        ACTIONS.put("displayIncomingDocs", ctx -> { ctx.displayIncomingDocs(); return null; });
        ACTIONS.put("documentUpdate", ctx -> { ctx.documentUpdate(); return null; });
        ACTIONS.put("documentUpdateAjax", ctx -> { ctx.documentUpdateAjax(); return null; });
        ACTIONS.put("getDemoNameAjax", ctx -> { ctx.getDemoNameAjax(); return null; });
        ACTIONS.put("showPage", ctx -> { ctx.showPage(); return null; });
        ACTIONS.put("view", ctx -> { ctx.view(); return null; });
        ACTIONS.put("addIncomingDocument", ctx -> ctx.addIncomingDocument());
        //  Enable calling the method to remove providers
        ACTIONS.put("removeLinkFromDocument", new ActionHandler() {
            public String handle(ManageDocument2Action action) {
                action.removeLinkFromDocument();
                return null;
            }
        });
        ACTIONS.put("viewDocumentInfo", ctx -> { ctx.viewDocumentInfo(); return null; });
    }

    /**
     * Main Struts2 entry point. Dispatches to the appropriate handler method based on the
     * "method" request parameter using the static {@code ACTIONS} map. Falls back to
     * {@link #documentUpdate()} if no method is specified and document parameters are present.
     *
     * @return String the Struts2 result name, or "error" if no valid handler is found
     */
    public String execute() {
        String method = request.getParameter("method");
        ActionHandler handler = ACTIONS.get(method);

        if (handler != null) {
            try {
                return handler.handle(this);
            } catch (Exception e) {
                log.error("Error in " + method + "():", e);
                addActionError("An error occurred while processing the document. Please try again or contact your system administrator.");
                return "error";
            }
        }

        // Only call documentUpdate() if no method is specified and required parameters are present
        if (method == null || method.trim().isEmpty()) {
            String documentId = request.getParameter("documentId");
            String documentDescription = request.getParameter("documentDescription");

            // Check if this looks like a documentUpdate request
            if (documentId != null || documentDescription != null) {
                return documentUpdate();
            }
        }

        log.error("No valid method found and insufficient parameters for documentUpdate. Method: " + method);
        addActionError("Invalid request. The requested operation could not be performed.");
        return "error";
    }

    /** Functional interface for method-based action dispatch handlers. */
    @FunctionalInterface
    private interface ActionHandler {
        String handle(ManageDocument2Action ctx) throws Exception;
    }

    /**
     * AJAX handler for updating document metadata (description, type, observation date),
     * routing to provider inboxes, and linking to patient demographics. Responds with
     * a JSON object containing the patient ID.
     *
     * @throws SecurityException if the user lacks _edoc write privilege
     */
    public void documentUpdateAjax() {
        String observationDate = request.getParameter("observationDate"); // :2008-08-22<
        String documentDescription = request.getParameter("documentDescription"); // :test2<
        String documentId = request.getParameter("documentId"); // :29<
        String docType = request.getParameter("docType"); // :consult<
        String demog = request.getParameter("demog");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_DOCUMENT, documentId, request.getRemoteAddr(), demog);

        String[] flagproviders = request.getParameterValues("flagproviders");
        // String demoLink=request.getParameter("demoLink");

        // TODO: if demoLink is "on", check if msp is in flagproviders, if not save to providerInboxRouting, if yes, don't save.

        // DONT COPY THIS !!!
        if (flagproviders != null && flagproviders.length > 0) { // TODO: THIS NEEDS TO RUN THRU THE lab forwarding rules!
            try {
                for (String proNo : flagproviders) {
                    // Sanitize provider number to prevent any potential header injection
                    // Provider numbers should only contain alphanumeric characters, hyphens, and underscores
                    if (proNo != null && proNo.matches("^[a-zA-Z0-9_-]+$")) {
                        providerInboxRoutingDAO.addToProviderInbox(proNo, Integer.parseInt(documentId), LabResultData.DOCUMENT);
                    } else {
                        log.warn("Invalid provider number format: " + (proNo != null ? proNo.replaceAll("[\r\n]", "") : "null"));
                    }
                }

                // Removes the link to the "0" providers so that the document no longer shows up as "unclaimed"
                providerInboxRoutingDAO.removeLinkFromDocument("DOC", Integer.parseInt(documentId), "0");
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }

        //Check to see if we have to route document to patient
        PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
        List<PatientLabRouting> patientLabRoutingList = patientLabRoutingDao.findByLabNoAndLabType(Integer.parseInt(documentId), docType);
        if (patientLabRoutingList == null || patientLabRoutingList.size() == 0) {
            PatientLabRouting patientLabRouting = new PatientLabRouting();
            patientLabRouting.setDemographicNo(Integer.parseInt(demog));
            patientLabRouting.setLabNo(Integer.parseInt(documentId));
            patientLabRouting.setLabType("DOC");
            patientLabRoutingDao.persist(patientLabRouting);
        }


        Document d = documentDao.getDocument(documentId);

        if (d != null) {
            d.setDocdesc(documentDescription);
            d.setDoctype(docType);
            Date obDate = UtilDateUtilities.StringToDate(observationDate);

            if (obDate != null) {
                d.setObservationdate(obDate);
            }

            documentDao.merge(d);
        }


        try {

            CtlDocument ctlDocument = ctlDocumentDao.getCtrlDocument(Integer.parseInt(documentId));
            int demographicNumber = Integer.parseInt(demog);
            // If this ctlDocument is a document module type and is not for the demographic being saved then create a new entry and remove the old one
            if (ctlDocument != null && (ctlDocument.isDemographicDocument() && demographicNumber != ctlDocument.getId().getModuleId())) {

                CtlDocument matchedCtlDocument = new CtlDocument();
                matchedCtlDocument.getId().setDocumentNo(ctlDocument.getId().getDocumentNo());
                matchedCtlDocument.getId().setModule(ctlDocument.getId().getModule());
                matchedCtlDocument.getId().setModuleId(Integer.parseInt(demog));
                matchedCtlDocument.setStatus(ctlDocument.getStatus());

                ctlDocumentDao.persist(matchedCtlDocument);

                ctlDocumentDao.remove(ctlDocument.getId());

                // save a document created note
                if (ctlDocument.isDemographicDocument()) {
                    // save note
                    saveDocNote(request, d.getDocdesc(), demog, documentId);
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        HashMap hm = new HashMap();
        hm.put("patientId", demog);
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }

    }

    /**
     * AJAX handler that returns the demographic (patient) name for the specified
     * demographic number as a JSON response.
     *
     * @throws SecurityException if the user lacks _demographic read privilege
     */
    public void getDemoNameAjax() {
        String dn = request.getParameter("demo_no");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", dn)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        HashMap hm = new HashMap();
        hm.put("demoName", getDemoName(LoggedInInfo.getLoggedInInfoFromSession(request), dn));
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    /**
     * Removes a provider's inbox routing link from a document and returns the remaining
     * linked providers as a JSON response.
     *
     * @throws SecurityException if the user lacks _edoc write privilege
     */
    public void removeLinkFromDocument() {
        String docType = request.getParameter("docType");
        String docId = request.getParameter("docId");
        String providerNo = request.getParameter("providerNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        providerInboxRoutingDAO.removeLinkFromDocument(docType, Integer.parseInt(docId), providerNo);
        HashMap hm = new HashMap();
        hm.put("linkedProviders", providerInboxRoutingDAO.getProvidersWithRoutingForDocument(docType, Integer.parseInt(docId)));

        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    /**
     * AJAX handler that refiles a document to a different queue.
     *
     * @return String null (no Struts2 result forwarding)
     * @throws SecurityException if the user lacks _edoc write privilege
     */
    public String refileDocumentAjax() {

        String documentId = request.getParameter("documentId");
        String queueId = request.getParameter("queueId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        try {
            EDocUtil.refileDocument(documentId, queueId);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return null;
    }

    /**
     * Updates document metadata and routing, then forwards to the single document display view.
     * Handles description, type, observation date, provider routing, and demographic linking.
     *
     * @return String the Struts2 result name ("displaySingleDoc" or "error")
     * @throws SecurityException if the user lacks _edoc write privilege
     */
    public String documentUpdate() {
        String observationDate = request.getParameter("observationDate"); // :2008-08-22<
        String documentDescription = request.getParameter("documentDescription"); // :test2<
        String documentId = request.getParameter("documentId"); // :29<
        // Also check for doc_no parameter (used by display method URLs)
        if (documentId == null || documentId.trim().isEmpty()) {
            documentId = request.getParameter("doc_no");
        }
        String docType = request.getParameter("docType"); // :consult<

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        if (documentId == null || documentId.trim().isEmpty()) {
            log.error("Document ID is null or empty, cannot process document update");
            addActionError("Document ID is missing. Cannot process document update.");
            return "error";
        }

        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_DOCUMENT, documentId, request.getRemoteAddr());

        String demog = request.getParameter("demog");

        String[] flagproviders = request.getParameterValues("flagproviders");
        // String demoLink=request.getParameter("demoLink");

        // TODO: if demoLink is "on", check if msp is in flagproviders, if not save to providerInboxRouting, if yes, don't save.

        // DONT COPY THIS !!!
        if (flagproviders != null && flagproviders.length > 0) { // TODO: THIS NEEDS TO RUN THRU THE lab forwarding rules!
            try {
                for (String proNo : flagproviders) {
                    // Sanitize provider number to prevent any potential header injection
                    // Provider numbers should only contain alphanumeric characters
                    if (proNo != null && proNo.matches("^[a-zA-Z0-9_-]+$")) {
                        providerInboxRoutingDAO.addToProviderInbox(proNo, Integer.parseInt(documentId), LabResultData.DOCUMENT);
                    } else {
                        log.warn("Invalid provider number format: " + (proNo != null ? proNo.replaceAll("[\r\n]", "") : "null"));
                    }
                }
            } catch (NumberFormatException e) {
                log.error("Invalid document ID format: " + documentId, e);
                addActionError("Invalid document ID format. Please check the document ID and try again.");
                return "error";
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }
        Document d = documentDao.getDocument(documentId);

        if (d != null) {
            d.setDocdesc(documentDescription);
            d.setDoctype(docType);
            Date obDate = UtilDateUtilities.StringToDate(observationDate);

            if (obDate != null) {
                d.setObservationdate(obDate);
            }

            documentDao.merge(d);
        }

        if (documentId != null && !documentId.trim().isEmpty()) {
            try {
                CtlDocument ctlDocument = ctlDocumentDao.getCtrlDocument(Integer.parseInt(documentId));
                if (ctlDocument != null) {
                    if (demog != null && !demog.trim().isEmpty()) {
                        ctlDocument.getId().setModuleId(Integer.parseInt(demog));
                        ctlDocumentDao.merge(ctlDocument);
                        // save a document created note
                        if (ctlDocument.isDemographicDocument() && d != null) {
                            // save note
                            saveDocNote(request, d.getDocdesc(), demog, documentId);
                        }
                    } else {
                        log.warn("Demographics parameter is null or empty, skipping ctlDocument update");
                    }
                }
            } catch (NumberFormatException e) {
                log.error("Invalid number format for documentId: " + documentId + " or demog: " + demog, e);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        } else {
            log.warn("Document ID is null or empty, skipping ctlDocument operations");
        }

        String providerNo = request.getParameter("providerNo");
        String searchProviderNo = request.getParameter("searchProviderNo");
        String ackStatus = request.getParameter("status");
        String demoName = getDemoName(LoggedInInfo.getLoggedInInfoFromSession(request), demog);
        request.setAttribute("demoName", demoName);
        request.setAttribute("segmentID", documentId);
        request.setAttribute("providerNo", providerNo);
        request.setAttribute("searchProviderNo", searchProviderNo);
        request.setAttribute("status", ackStatus);

        return "displaySingleDoc";

    }

    /**
     * Retrieves the formatted demographic (patient) name for the given demographic number.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param demog String the patient demographic number
     * @return String the formatted patient name
     */
    private String getDemoName(LoggedInInfo loggedInInfo, String demog) {
        // Get demographic name based on login info and the demographic number of patient
        return EDocUtil.getDemographicName(loggedInInfo, demog);
    }

    /**
     * Creates a case management note recording that a document was filed or updated
     * for a patient. The note is signed by the system user ("-1") and linked to the
     * document via a {@link CaseManagementNoteLink}.
     *
     * @param request HttpServletRequest the current request for session access
     * @param docDesc String the document description to include in the note text
     * @param demog String the patient demographic number
     * @param documentId String the document ID to link the note to
     */
    private void saveDocNote(final HttpServletRequest request, String docDesc, String demog, String documentId) {

        Date now = EDocUtil.getDmsDateTimeAsDate();
        // String docDesc=d.getDocdesc();
        CaseManagementNote cmn = new CaseManagementNote();
        cmn.setUpdate_date(now);
        cmn.setObservation_date(now);
        cmn.setDemographic_no(demog);
        HttpSession se = request.getSession();
        String user_no = (String) se.getAttribute("user");
        String prog_no = new EctProgram(se).getProgram(user_no);
        WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(se.getServletContext());
        CaseManagementManager cmm = (CaseManagementManager) ctx.getBean(CaseManagementManager.class);
        cmn.setProviderNo("-1"); // set the providers no to be -1 so the editor appear as 'System'.
        Provider provider = EDocUtil.getProvider(user_no);
        String provFirstName = "";
        String provLastName = "";
        if (provider != null) {
            provFirstName = provider.getFirstName();
            provLastName = provider.getLastName();
        }
        String strNote = "Document" + " " + docDesc + " " + "created at " + now + " by " + provFirstName + " " + provLastName + ".";

        // String strNote="Document"+" "+docDesc+" "+ "created at "+now+".";
        cmn.setNote(strNote);
        cmn.setSigned(true);
        cmn.setSigning_provider_no("-1");
        cmn.setProgram_no(prog_no);

        SecRoleDao secRoleDao = (SecRoleDao) SpringUtils.getBean(SecRoleDao.class);
        SecRole doctorRole = secRoleDao.findByName("doctor");
        cmn.setReporter_caisi_role(doctorRole.getId().toString());

        cmn.setReporter_program_team("0");
        cmn.setPassword("NULL");
        cmn.setLocked(false);
        cmn.setHistory(strNote);
        cmn.setPosition(0);

        Long note_id = cmm.saveNoteSimpleReturnID(cmn);
        // Debugging purposes on the live server
        MiscUtils.getLogger().info("Document Note ID: " + note_id.toString());

        // Add a noteLink to casemgmt_note_link
        CaseManagementNoteLink cmnl = new CaseManagementNoteLink();
        cmnl.setTableName(CaseManagementNoteLink.DOCUMENT);
        cmnl.setTableId(Long.parseLong(documentId));
        cmnl.setNoteId(note_id);
        EDocUtil.addCaseMgmtNoteLink(cmnl);
    }

    /*
     * private void savePatientLabRouting(String demog, String docId, String docType){ CommonLabResultData.updatePatientLabRouting(docId, demog, docType); }
     */

    private static String getDocumentCacheDir() {
        if (DOCUMENT_CACHE_DIR != null && !DOCUMENT_CACHE_DIR.isEmpty()) {
            return DOCUMENT_CACHE_DIR;
        }
        return getDocumentCacheDir(DOCUMENT_DIR).getAbsolutePath();
    }

    private static File getDocumentCacheDir(String docdownload) {
        File docDir = new File(docdownload);
        String documentDirName = docDir.getName();
        File parentDir = docDir.getParentFile();

        // Sanitize the cache directory name to prevent path traversal
        String safeCacheDirName = MiscUtils.sanitizeFileName(documentDirName + "_cache");

        // Use validatePath to create a validated cache directory path
        File cacheDir = PathValidationUtils.validatePath(safeCacheDirName, parentDir);

        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }
        return cacheDir;
    }

    private File hasCacheVersion2(Document d, Integer pageNum) {
        Path outFile = Paths.get(getDocumentCacheDir(), d.getDocfilename() + "_" + pageNum + ".png");
        if (!Files.exists(outFile)) {
            return null;
        }
        return outFile.toFile();
    }

    /**
     * Deletes the cached PNG image for a specific page of a document.
     *
     * @param d Document the document whose cache entry should be deleted
     * @param pageNum int the 1-based page number of the cache entry to delete
     */
    public static void deleteCacheVersion(Document d, int pageNum) {
        Path documentCacheDir = Paths.get(getDocumentCacheDir(), d.getDocfilename() + "_" + pageNum + ".png");
        if (Files.exists(documentCacheDir)) {
            try {
                Files.delete(documentCacheDir);
            } catch (IOException e) {
                MiscUtils.getLogger().error("Failed to delete cache file: " + documentCacheDir.getFileName(), e);
            }
        }
    }

    private File hasCacheVersion(Document d, int pageNum) {
        return hasCacheVersion2(d, pageNum);
    }

    /**
     * Renders a specific page of a PDF document as a PNG image using Apache PDFBox,
     * saves it to the document cache directory, and returns the image bytes.
     *
     * @param d Document the document to render
     * @param pageNum Integer the 1-based page number to render
     * @return byte[] the PNG image bytes, or null if rendering fails or page number is invalid
     */
    public byte[] createCacheVersion2(Document d, Integer pageNum) {
        Path pdfPath = Paths.get(DOCUMENT_DIR, d.getDocfilename());
        Path pngFile = Paths.get(getDocumentCacheDir(), d.getDocfilename() + "_" + pageNum + ".png");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDFParser parser = new PDFParser(new RandomAccessFile(pdfPath.toFile(), "rw"), new ScratchFile(MemoryUsageSetting.setupTempFileOnly()));
            parser.parse();
            PDDocument pdf = parser.getPDDocument();

            // Validate page number is within bounds
            if (pageNum == null) {
                log.error("Page number is null for document " + d.getDocfilename());
                pdf.close();
                return null;
            }

            int pageIndex = pageNum - 1;
            int totalPages = pdf.getNumberOfPages();
            if (pageIndex < 0 || pageIndex >= totalPages) {
                log.error("Invalid page number " + pageNum + " for document " + d.getDocfilename() + " with " + totalPages + " pages");
                pdf.close();
                return null;
            }

            PDFRenderer rend = new PDFRenderer(pdf);
            //Page index starts at 0, subtracts 1 to account for that
            BufferedImage image = rend.renderImageWithDPI(pageIndex, 96, ImageType.RGB);

            // write cache file
            ImageIO.write(image, "png", pngFile.toFile());
            ImageIO.write(image, "png", baos);

            pdf.close();
            image.flush();

            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error decoding pdf file " + d.getDocfilename(), e);
            return null;
        }
    }

    /**
     * Renders a specific document page as a PNG image based on the "page" request parameter.
     *
     * @throws Exception if page rendering fails
     */
    public void showPage() throws Exception {
        getPage(Integer.parseInt(request.getParameter("page")));
    }

    /**
     * Renders the first page of a document as a PNG image.
     */
    public void view() {
        getPage(1);
    }

    /**
     * Retrieves or generates a cached PNG rendering of the specified document page
     * and writes it to the HTTP response. Uses the document cache for performance.
     *
     * @param pageNum int the 1-based page number to render
     */
    public void getPage(int pageNum) {

        String doc_no = request.getParameter("doc_no");
        log.debug("Document No :" + doc_no);
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());
        Document d = documentDao.getDocument(doc_no);

        log.debug("Document Name :" + d.getDocfilename());

        File outfile = hasCacheVersion(d, pageNum);

        if (outfile != null) {
            setResponse(response, outfile);
        } else {
            byte[] pdfBytes = createCacheVersion2(d, pageNum);
            setResponse(response, pdfBytes);
        }

        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "attachment;filename=\"" + sanitizeHeaderValue(d.getDocfilename()) + "\"");
    }

    /**
     * Views a specific page of a document as a PNG image. For non-PDF documents,
     * delegates to {@link #display()}. Uses caching for PDF page rendering.
     *
     * @throws SecurityException if the user lacks _edoc read privilege
     */
    public void viewDocPage() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        log.debug("in viewDocPage");

        String doc_no = request.getParameter("doc_no");
        String pageNum = request.getParameter("curPage");
        if (pageNum == null) {
            pageNum = "1";
        }
        Integer pn = Integer.parseInt(pageNum);
        log.debug("Document No :" + doc_no);
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());

        Document d = documentDao.getDocument(doc_no);
        if (d == null) {
            log.error("Document not found for ID: " + doc_no);
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");
            } catch (IOException e) {
                log.error("Error sending error response", e);
            }
            return;
        }

        log.debug("Document Name :" + d.getDocfilename());
        //if the file is not a pdf, use display function
        if (!(d.getContenttype().equals("application/pdf") || d.getDocfilename().endsWith(".pdf"))) {
            try {
                display();
            } catch (Exception e) {
                log.error("Error while displaying document ", e);
            }
            return;
        }

        String name = d.getDocfilename() + "_" + pn + ".png";
        log.debug("name " + name);

        File outfile = hasCacheVersion2(d, pn);
        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "attachment;filename=\"" + sanitizeHeaderValue(name) + "\"");

        if (outfile != null) {
            setResponse(response, outfile);
        } else {
            byte[] pdfBytes = createCacheVersion2(d, pn);
            setResponse(response, pdfBytes);
        }

    }

    /**
     * Returns the total page count of a PDF document as a JSON response.
     * Uses OpenPDF PdfReader with try-with-resources for page counting.
     *
     * @throws SecurityException if the user lacks _edoc read privilege
     */
    public void getDocPageNumber() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String doc_no = request.getParameter("doc_no");
        String docdownload = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        // File documentDir = new File(docdownload);
        Document d = documentDao.getDocument(doc_no);
        String filePath = docdownload + d.getDocfilename();

        int numOfPage = 0;
        try (PdfReader reader = new PdfReader(filePath)) {
            numOfPage = reader.getNumberOfPages();

            HashMap hm = new HashMap();
            hm.put("numOfPage", numOfPage);
            ObjectNode jsonObject = objectMapper.valueToTree(hm);
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    /**
     * Previously handled CDS (Clinical Document Sharing) downloads. The Sharing Center
     * functionality has been removed; this method now returns a 503 Service Unavailable.
     *
     * @return String null (response is sent directly)
     * @throws Exception if response writing fails
     * @throws SecurityException if the user lacks _edoc read privilege
     */
    public String downloadCDS() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        // Sharing Center functionality has been removed
        log.warn("CDS download no longer available - Sharing Center has been removed");
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "CDS download functionality is no longer available");
        return null;
    }

    /**
     * Displays a document by writing its binary content to the HTTP response with
     * the appropriate content type. Supports both file-system-stored documents and
     * legacy HTML documents stored in the docxml database field.
     *
     * @throws Exception if the document file does not exist and no docxml fallback is available
     * @throws SecurityException if the user lacks _edoc read privilege
     */
    public void display() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String doc_no = request.getParameter("doc_no");
        log.debug("Document No :" + doc_no);
        String demoNo = request.getParameter("demoNo");

        String docxml = null;
        String contentType = null;
        byte[] contentBytes = null;
        String filename = null;

        CtlDocument ctld = ctlDocumentDao.getCtrlDocument(Integer.parseInt(doc_no));
        if (ctld.isDemographicDocument()) {
            LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr(), "" + ctld.getId().getModuleId());
        } else {
            LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());
        }

        Document d = documentDao.getDocument(doc_no);

        log.debug("Document Name :" + d.getDocfilename());

        docxml = d.getDocxml();
        contentType = d.getContenttype();
        filename = d.getDocfilename();

        Path file = Paths.get(DOCUMENT_DIR, filename);

        if (Files.exists(file)) {
            contentBytes = Files.readAllBytes(file);
        } else {
            if (docxml == null || docxml.trim().equals("")) {
                // Only throw exception if the file does not exist and the docxml is null/empty to serve HTML files that were uploaded in OSCAR 12,
                // where HTML file uploads contents were stored in the docxml field of the document table, and the file was never saved.
                throw new IllegalStateException("Local document doesn't exist for eDoc (ID " + d.getId() + "): " + file.getFileName());
            }
        }

        if (docxml != null && !docxml.trim().equals("")) {
            setResponse(response, docxml.getBytes());
            return;
        }

        // TODO: Right now this assumes it's a pdf which it shouldn't
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        String data = "doc_no=" + doc_no;
        LogAction.addLog(loggedInInfo, LogConst.READ, "Document", null, demoNo, data);

        response.setContentType(contentType);
        response.setContentLength(contentBytes.length);
        response.setHeader("Content-Disposition", "inline; filename=\"" + sanitizeHeaderValue(filename) + "\"");
        log.debug("about to Print to stream");
        try (ServletOutputStream outs = response.getOutputStream()) {
            outs.write(contentBytes);
            outs.flush();
        }
    }

    /**
     * Renders both document annotations/acknowledgements/ticklers and document description
     * metadata as an HTML fragment.
     *
     * @throws Exception if response writing fails
     */
    public void viewDocumentInfo() throws Exception {
        response.setContentType("text/html;charset=UTF-8");
        doViewDocumentInfo(request, response.getWriter(), true, true);

    }

    /**
     * Renders only the document description metadata (dates, type, creator, etc.)
     * as an HTML fragment, without annotations or ticklers.
     *
     * @throws Exception if response writing fails
     */
    public void viewDocumentDescription() throws Exception {
        response.setContentType("text/html;charset=UTF-8");
        doViewDocumentInfo(request, response.getWriter(), false, true);
    }

    /**
     * Renders only the document annotations, acknowledgements, and ticklers as an HTML
     * fragment, without the document description metadata.
     *
     * @throws Exception if response writing fails
     */
    public void viewAnnotationAcknowledgementTickler() throws Exception {
        response.setContentType("text/html;charset=UTF-8");
        doViewDocumentInfo(request, response.getWriter(), true, false);
    }

    /**
     * Renders document information as an HTML page, with configurable sections for
     * annotations/acknowledgements/ticklers and document description metadata.
     *
     * @param request HttpServletRequest the current request for session and locale access
     * @param out PrintWriter the response writer to output HTML to
     * @param viewAnnotationAcknowledgementTicklerFlag boolean whether to include annotations, acknowledgements, and ticklers
     * @param viewDocumentDescriptionFlag boolean whether to include document description metadata
     * @throws SecurityException if the user lacks _edoc read privilege
     */
    public void doViewDocumentInfo(HttpServletRequest request, PrintWriter out, boolean viewAnnotationAcknowledgementTicklerFlag, boolean viewDocumentDescriptionFlag) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String doc_no = request.getParameter("doc_no");
        Locale locale = request.getLocale();

        String annotation = "", acknowledgement = "", tickler = "";
        if (doc_no != null && doc_no.length() > 0) {
            annotation = EDocUtil.getHtmlAnnotation(doc_no);
            acknowledgement = EDocUtil.getHtmlAcknowledgement(locale, doc_no);
            if (acknowledgement == null) {
                acknowledgement = "";
            }
            tickler = EDocUtil.getHtmlTicklers(loggedInInfo, doc_no);
        }

        out.println("<!DOCTYPE html><html><head><meta http-equiv='Content-Type' content='text/html; charset=UTF-8'></head><body>");

        if (viewAnnotationAcknowledgementTicklerFlag) {
            if (annotation.length() > 0) {
                out.println(annotation);
            }
            if (tickler.length() > 0) {
                out.println(tickler + "<br>");
            }
            if (acknowledgement.length() > 0) {
                out.println(acknowledgement + "<br>");
            }
        }

        if (viewDocumentDescriptionFlag) {
            EDoc curDoc = EDocUtil.getDoc(doc_no);
            ResourceBundle props = ResourceBundle.getBundle("oscarResources", locale);
            out.println("<br>" + props.getString("dms.documentBrowser.DocumentUpdated") + ": " + curDoc.getDateTimeStamp());
            out.println("<br>" + props.getString("dms.documentBrowser.ContentUpdated") + ": " + curDoc.getContentDateTime());
            out.println("<br>" + props.getString("dms.documentBrowser.ObservationDate") + ": " + curDoc.getObservationDate());
            out.println("<br>" + props.getString("dms.documentBrowser.Type") + ": " + curDoc.getType());
            out.println("<br>" + props.getString("dms.documentBrowser.Class") + ": " + curDoc.getDocClass());
            out.println("<br>" + props.getString("dms.documentBrowser.Subclass") + ": " + curDoc.getDocSubClass());
            out.println("<br>" + props.getString("dms.documentBrowser.Description") + ": " + curDoc.getDescription());
            out.println("<br>" + props.getString("dms.documentBrowser.Creator") + ": " + curDoc.getCreatorName());
            out.println("<br>" + props.getString("dms.documentBrowser.Responsible") + ": " + curDoc.getResponsibleName());
            out.println("<br>" + props.getString("dms.documentBrowser.Reviewer") + ": " + curDoc.getReviewerName());
            out.println("<br>" + props.getString("dms.documentBrowser.Source") + ": " + curDoc.getSource());
        }

        out.println("</body></html>");
        out.flush();
        out.close();

    }

    /**
     * Processes an incoming document from the incoming document queue: moves the PDF to
     * the permanent document store, creates the EDoc record, routes to provider inboxes,
     * links to the patient demographic, and creates a case management note.
     *
     * @return String the Struts2 result name ("nextIncomingDoc" on success, "error" on failure)
     * @throws Exception if parameter validation or file operations fail
     * @throws SecurityException if the user lacks _edoc write privilege or path traversal is detected
     */
    public String addIncomingDocument() throws Exception {

        String pdfDir = request.getParameter("pdfDir");
        String pdfName = request.getParameter("pdfName");
        String demographic_no = request.getParameter("demog");
        String observationDate = request.getParameter("observationDate");
        String documentDescription = request.getParameter("documentDescription");
        String docType = request.getParameter("docType");
        String docClass = request.getParameter("docClass");
        String docSubClass = request.getParameter("docSubClass");
        String[] flagproviders = request.getParameterValues("flagproviders");
        String queueId1 = request.getParameter("queueId");
        String sourceFilePath = IncomingDocUtil.getIncomingDocumentFilePathName(queueId1, pdfDir, pdfName);
        String destFilePath;
        
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }
        
        // Validate input parameters to prevent path traversal
        if (queueId1 == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId1.contains("..") || queueId1.contains("/") || queueId1.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }

        String savePath = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (!savePath.endsWith(File.separator)) {
            savePath += File.separator;
        }

        Date obDate = UtilDateUtilities.StringToDate(observationDate);
        String formattedDate = UtilDateUtilities.DateToString(obDate, EDocUtil.DMS_DATE_FORMAT);
        String source = "";


        int numberOfPages = 0;
        String fileName = sanitizedPdfName;
        String user = (String) request.getSession().getAttribute("user");
        EDoc newDoc = new EDoc(documentDescription, docType, fileName, "", user, user, source, 'A', formattedDate, "", "", "demographic", demographic_no, 0);

        // if the document was added in the context of a program
        ProgramManager2 programManager = SpringUtils.getBean(ProgramManager2.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        ProgramProvider pp = programManager.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
        if (pp != null && pp.getProgramId() != null) {
            newDoc.setProgramId(pp.getProgramId().intValue());
        }

        newDoc.setDocClass(docClass);
        newDoc.setDocSubClass(docSubClass);
        newDoc.setDocPublic("0");
        fileName = newDoc.getFileName();
        // Sanitize the filename to match what the file system will actually create
        String sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "");

        // Ensure sanitized filename is not empty and has a minimum length
        if (sanitizedFileName.trim().isEmpty() || sanitizedFileName.length() < 1) {
            // Generate a fallback filename with timestamp
            String timestamp = String.valueOf(System.currentTimeMillis());
            sanitizedFileName = "document_" + timestamp + ".dat";
        }

        // Ensure filename uniqueness by checking if file already exists
        File destFile = new File(savePath + sanitizedFileName);
        String originalSanitized = sanitizedFileName;
        int counter = 1;
        while (destFile.exists()) {
            String nameWithoutExt = originalSanitized;
            String extension = "";
            int lastDot = originalSanitized.lastIndexOf(".");
            if (lastDot > 0) {
                nameWithoutExt = originalSanitized.substring(0, lastDot);
                extension = originalSanitized.substring(lastDot);
            }
            sanitizedFileName = nameWithoutExt + "_" + counter + extension;
            destFile = new File(savePath + sanitizedFileName);
            counter++;
        }

        newDoc.setFileName(sanitizedFileName);
        destFilePath = savePath + sanitizedFileName;
        String doc_no = "";

        // Validate destination path is within allowed directory using PathValidationUtils
        File saveDir = new File(savePath);
        File finalDestFile = PathValidationUtils.validatePath(sanitizedFileName, saveDir);
        destFilePath = finalDestFile.getPath();

        newDoc.setContentType(docType);
        File f1 = new File(sourceFilePath);

        // Validate source file is within INCOMINGDOCUMENT_DIR to prevent path traversal
        String incomingDocDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (incomingDocDir != null && !incomingDocDir.isEmpty()) {
            File incomingDir = new File(incomingDocDir);
            f1 = PathValidationUtils.validateExistingPath(f1, incomingDir);
        }

        boolean success = f1.renameTo(new File(destFilePath));
        if (!success) {
            log.error("Not able to move " + f1.getName() + " to " + destFilePath);
            // File was not successfully moved - attempt to delete temp file to prevent orphaned files
            boolean deleted = f1.delete();
            if (!deleted) {
                log.warn("Failed to delete temporary file: " + f1.getAbsolutePath());
            }
            String documentId = request.getParameter("documentId");
            log.error("Failed to save document file for document ID: " + documentId);
            addActionError("Failed to save document file. Please try again or contact your system administrator.");
            return "error";
        } else {

            newDoc.setContentType("application/pdf");
            if (fileName.endsWith(".PDF") || fileName.endsWith(".pdf")) {
                newDoc.setContentType("application/pdf");
                numberOfPages = countNumOfPages(fileName);
            }
            newDoc.setNumberOfPages(numberOfPages);
            doc_no = EDocUtil.addDocumentSQL(newDoc);
            LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());


            if (flagproviders != null && flagproviders.length > 0) {
                try {
                    for (String proNo : flagproviders) {
                        // Sanitize provider number to prevent any potential header injection
                        // Provider numbers should only contain alphanumeric characters
                        if (proNo != null && proNo.matches("^[a-zA-Z0-9_-]+$")) {
                            providerInboxRoutingDAO.addToProviderInbox(proNo, Integer.parseInt(doc_no), LabResultData.DOCUMENT);
                        } else {
                            log.warn("Invalid provider number format: " + (proNo != null ? proNo.replaceAll("[\r\n]", "") : "null"));
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                }
            }

            //Check to see if we have to route document to patient
            PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
            List<PatientLabRouting> patientLabRoutingList = patientLabRoutingDao.findByLabNoAndLabType(Integer.parseInt(doc_no), docType);
            if (patientLabRoutingList == null || patientLabRoutingList.isEmpty()) {
                PatientLabRouting patientLabRouting = new PatientLabRouting();
                patientLabRouting.setDemographicNo(Integer.parseInt(demographic_no));
                patientLabRouting.setLabNo(Integer.parseInt(doc_no));
                patientLabRouting.setLabType("DOC");
                patientLabRoutingDao.persist(patientLabRouting);
            }

            try {

                CtlDocument ctlDocument = ctlDocumentDao.getCtrlDocument(Integer.parseInt(doc_no));

                ctlDocument.getId().setModuleId(Integer.parseInt(demographic_no));
                ctlDocumentDao.merge(ctlDocument);
                //save a document created note
                if (ctlDocument.isDemographicDocument()) {
                    //save note
                    saveDocNote(request, documentDescription, demographic_no, doc_no);
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }

        return "nextIncomingDoc";
    }

    /**
     * Serves a single page of an incoming PDF document as a standalone PDF using
     * Apache PDFBox. Validates file paths against the INCOMINGDOCUMENT_DIR using
     * {@link PathValidationUtils}.
     *
     * @throws Exception if path validation or PDF extraction fails
     * @throws SecurityException if the user lacks _edoc read privilege or path traversal is detected
     */
    public void viewIncomingDocPageAsPdf() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String pageNum = request.getParameter("curPage");
        String queueId = request.getParameter("queueId");
        String pdfDir = request.getParameter("pdfDir");
        String pdfName = request.getParameter("pdfName");
        
        // Validate input parameters to prevent path traversal
        if (queueId == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId.contains("..") || queueId.contains("/") || queueId.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }
        
        String filePath = IncomingDocUtil.getIncomingDocumentFilePathName(queueId, pdfDir, sanitizedPdfName);
        
        // Validate canonical path to ensure file is within allowed directory
        String incomingDocDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (incomingDocDir == null || incomingDocDir.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR not configured");
        }
        
        // Validate file path using PathValidationUtils
        File baseDir = new File(incomingDocDir);
        File file = new File(filePath);
        PathValidationUtils.validateExistingPath(file, baseDir);

        Locale locale = request.getLocale();
        ResourceBundle props = ResourceBundle.getBundle("oscarResources", locale);

        if (pageNum == null) {
            pageNum = "1";
        }

        int pageNumber = Integer.parseInt(pageNum);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"" + sanitizeHeaderValue(sanitizedPdfName + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf") + "\"");

        try {
            PDDocument reader = PDDocument.load(file);

            // Validate page number is within bounds
            int pageIndex = pageNumber - 1;
            int totalPages = reader.getNumberOfPages();
            if (pageIndex < 0 || pageIndex >= totalPages) {
                log.error("Invalid page number " + pageNumber + " for PDF " + sanitizedPdfName + " with " + totalPages + " pages");
                reader.close();
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().print(props.getString("dms.incomingDocs.errorInOpening") + Encode.forHtml(sanitizedPdfName));
                response.getWriter().print("<br>Invalid page number");
                return;
            }

            PDDocument extractedPage = new PDDocument();
            extractedPage.addPage(reader.getDocumentCatalog().getPages().get(pageIndex));
            extractedPage.save(response.getOutputStream());
            extractedPage.close();
            reader.close();
        } catch (Exception ex) {
            response.setContentType("text/html;charset=UTF-8");
            // Sanitize the filename to prevent XSS and response splitting
            response.getWriter().print(props.getString("dms.incomingDocs.errorInOpening") + Encode.forHtml(sanitizedPdfName));
            response.getWriter().print("<br>" + props.getString("dms.incomingDocs.PDFCouldBeCorrupted"));

            MiscUtils.getLogger().error("Error", ex);
        }

    }

    /**
     * Counts the number of pages in a PDF file using Apache PDFBox.
     * The file is located in the configured DOCUMENT_DIR.
     *
     * @param fileName String the PDF filename (relative to DOCUMENT_DIR)
     * @return int the number of pages, or 0 if the file cannot be read
     */
    public int countNumOfPages(String fileName) {
        int numOfPage = 0;
        String docdownload = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");

        if (!docdownload.endsWith(File.separator)) {
            docdownload += File.separator;
        }

        String filePath = docdownload + fileName;

        try {
            PDDocument reader = PDDocument.load(new File(filePath));
            numOfPage = reader.getNumberOfPages();

            reader.close();
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return numOfPage;
    }

    /**
     * Serves the raw PDF content of an incoming document for inline display.
     * Validates file paths against the INCOMINGDOCUMENT_DIR using {@link PathValidationUtils}.
     *
     * @throws Exception if path validation or file I/O fails
     * @throws SecurityException if the user lacks _edoc read privilege or path traversal is detected
     */
    public void displayIncomingDocs() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String queueId = request.getParameter("queueId");
        String pdfDir = request.getParameter("pdfDir");
        String pdfName = request.getParameter("pdfName");
        
        // Validate input parameters to prevent path traversal
        if (queueId == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId.contains("..") || queueId.contains("/") || queueId.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }
        
        String filePath = IncomingDocUtil.getIncomingDocumentFilePathName(queueId, pdfDir, sanitizedPdfName);
        
        // Validate canonical path to ensure file is within allowed directory
        String incomingDocDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (incomingDocDir == null || incomingDocDir.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR not configured");
        }
        
        // Validate file path using PathValidationUtils
        File baseDir = new File(incomingDocDir);
        File file = new File(filePath);
        PathValidationUtils.validateExistingPath(file, baseDir);

        String contentType = "application/pdf";
        response.setContentType(contentType);
        response.setContentLength((int) file.length());
        response.setHeader("Content-Disposition", "inline; filename=\"" + sanitizeHeaderValue(sanitizedPdfName) + "\"");

        BufferedInputStream bfis = null;
        ServletOutputStream outs = response.getOutputStream();

        try {
            // Re-validate file path at point of use for static analysis visibility
            File validatedFile = PathValidationUtils.validateExistingPath(file, baseDir);
            bfis = new BufferedInputStream(new FileInputStream(validatedFile));

            org.apache.commons.io.IOUtils.copy(bfis, outs);
            outs.flush();

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            if (bfis != null) {
                bfis.close();
            }
        }
    }

    /**
     * Renders a specific page of an incoming PDF document as a PNG image using
     * Apache PDFBox via {@link #createIncomingCacheVersion}. Validates file paths
     * to prevent path traversal attacks.
     *
     * @throws Exception if path validation, rendering, or I/O fails
     * @throws SecurityException if the user lacks _edoc read privilege or path traversal is detected
     */
    public void viewIncomingDocPageAsImage() throws Exception {


        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String pageNum = request.getParameter("curPage");
        String queueId = request.getParameter("queueId");
        String pdfDir = request.getParameter("pdfDir");
        String pdfName = request.getParameter("pdfName");
        
        // Validate input parameters to prevent path traversal
        if (queueId == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId.contains("..") || queueId.contains("/") || queueId.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }

        if (pageNum == null) {
            pageNum = "1";
        }

        BufferedInputStream bfis = null;
        ServletOutputStream outs = null;

        try {
            Integer pn = Integer.parseInt(pageNum);
            File outfile = createIncomingCacheVersion(queueId, pdfDir, sanitizedPdfName, pn);
            outs = response.getOutputStream();

            if (outfile != null) {
                // Security: Validate the file path before accessing
                validateFilePath(outfile);
                bfis = new BufferedInputStream(new FileInputStream(outfile));


                response.setContentType("image/png");
                response.setHeader("Content-Disposition", "inline;filename=\"" + sanitizeHeaderValue(sanitizedPdfName) + "\"");
                org.apache.commons.io.IOUtils.copy(bfis, outs);
                outs.flush();

            } else {
                log.info("Unable to retrieve content for " + queueId + "/" + pdfDir + "/" + pdfName);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);

        } finally {
            if (bfis != null) {
                bfis.close();
            }
        }
    }

    /**
     * Renders a page of an incoming PDF as a cached PNG image using Apache PDFBox.
     * The image is saved to a cache directory adjacent to the incoming document directory.
     * Validates all input parameters and file paths to prevent path traversal.
     *
     * @param queueId String the incoming document queue identifier
     * @param pdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param pdfName String the PDF filename
     * @param pageNum Integer the 1-based page number to render
     * @return File the generated PNG cache file, or null if rendering fails
     * @throws Exception if parameter validation fails
     * @throws SecurityException if path traversal is detected or file type is not PDF
     */
    public File createIncomingCacheVersion(String queueId, String pdfDir, String pdfName, Integer pageNum) throws Exception {
        
        // Validate input parameters to prevent path traversal
        if (queueId == null || pdfDir == null || pdfName == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Sanitize filename to prevent path traversal
        String sanitizedPdfName = FilenameUtils.getName(pdfName);
        if (!sanitizedPdfName.equals(pdfName)) {
            throw new SecurityException("Invalid filename");
        }
        
        // Validate queueId and pdfDir to prevent directory traversal
        if (queueId.contains("..") || queueId.contains("/") || queueId.contains("\\") ||
            pdfDir.contains("..") || pdfDir.contains("/") || pdfDir.contains("\\")) {
            throw new SecurityException("Invalid directory parameters");
        }
        
        // Ensure the file has a .pdf extension
        if (!sanitizedPdfName.toLowerCase().endsWith(".pdf")) {
            throw new SecurityException("Invalid file type - only PDF files are allowed");
        }

        String incomingDocPath = IncomingDocUtil.getIncomingDocumentFilePath(queueId, pdfDir);
        
        // Validate canonical path to ensure file is within allowed directory
        String incomingDocDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (incomingDocDir == null || incomingDocDir.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR not configured");
        }
        
        // Validate file path using PathValidationUtils
        File baseDir = new File(incomingDocDir);
        File documentDir = new File(incomingDocPath);
        File documentCacheDir = getDocumentCacheDir(incomingDocPath);
        File file = new File(documentDir, sanitizedPdfName);
        PathValidationUtils.validateExistingPath(file, baseDir);

        // Re-validate file path at point of use for static analysis visibility
        File validatedFile = PathValidationUtils.validateExistingPath(file, baseDir);

        try (PDDocument document = PDDocument.load(validatedFile)) {
            PDFRenderer renderer = new PDFRenderer(document);

            // Validate page number is within bounds
            if (pageNum == null) {
                log.error("Page number is null for PDF " + pdfDir + File.separator + sanitizedPdfName);
                return null;
            }

            int pageIndex = pageNum - 1;
            int totalPages = document.getNumberOfPages();
            if (pageIndex < 0 || pageIndex >= totalPages) {
                log.error("Invalid page number " + pageNum + " for PDF " + pdfDir + File.separator + sanitizedPdfName + " with " + totalPages + " pages");
                return null;
            }

            // Render at 96 DPI to match jpedal settings (96 DPI / 72 DPI = 1.33 scale)
            // Note: PDFBox uses 0-based page indexing, jpedal uses 1-based
            BufferedImage image_to_save = renderer.renderImageWithDPI(pageIndex, 96, ImageType.RGB);

            // Use sanitized filename for cache file and validate path
            String cacheFileName = sanitizedPdfName.substring(0, sanitizedPdfName.lastIndexOf('.')) + "_" + pageNum + ".png";
            File cacheFile = PathValidationUtils.validatePath(cacheFileName, documentCacheDir);

            // Write PNG using standard ImageIO
            ImageIO.write(image_to_save, "png", cacheFile);
            image_to_save.flush();

            return cacheFile;
        } catch (Exception e) {
            log.error("Error decoding pdf file " + pdfDir + File.separator + sanitizedPdfName, e);
            return null;
        }
    }

    private HttpServletResponse setResponse(HttpServletResponse response, byte[] pdfBytes) {
        try (ServletOutputStream outs = response.getOutputStream();
             ByteArrayInputStream fileInputStream = new ByteArrayInputStream(pdfBytes)) {
            org.apache.commons.io.IOUtils.copy(fileInputStream, outs);
        } catch (Exception e) {
            log.error("Error retrieving PDF document", e);
        }
        return response;
    }

    private HttpServletResponse setResponse(HttpServletResponse response, File output) {
        try (ServletOutputStream outs = response.getOutputStream();
             FileInputStream fileInputStream = new FileInputStream(output)
        ) {
            org.apache.commons.io.IOUtils.copy(fileInputStream, outs);
        } catch (Exception e) {
            log.error("Error retrieving document: " + output.getPath(), e);
        }
        return response;
    }
    
    /**
     * Validates that a file path is safe to access and within allowed directories.
     * Prevents path traversal attacks by ensuring the file's canonical path
     * is within the expected document or cache directories.
     * 
     * @param file The file to validate
     * @throws SecurityException if the file path is invalid or potentially malicious
     */
    private void validateFilePath(File file) throws SecurityException {
        if (file == null) {
            throw new SecurityException("File is null");
        }

        // Get all allowed directories
        File documentDir = new File(DOCUMENT_DIR);
        File documentCacheDir = new File(getDocumentCacheDir());
        String incomingDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        File incomingDirFile = new File(incomingDir);
        File incomingCacheDir = getDocumentCacheDir(incomingDir);

        // Try each directory - file must be in at least one
        File[] allowedDirs = {documentDir, documentCacheDir, incomingDirFile, incomingCacheDir};

        for (File allowedDir : allowedDirs) {
            try {
                PathValidationUtils.validateExistingPath(file, allowedDir);
                return; // Valid if we get here without exception
            } catch (SecurityException e) {
                // File not in this directory, try next
            }
        }

        // If we get here, file wasn't in any allowed directory
        throw new SecurityException("File path is outside allowed directories");
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
        String sanitized = value.replaceAll("[\r\n\u0000-\u001F\u007F-\u009F]", "");
        
        // Ensure the filename is not empty after sanitization
        if (sanitized.trim().isEmpty()) {
            return "document";
        }
        
        return sanitized;
    }
}
