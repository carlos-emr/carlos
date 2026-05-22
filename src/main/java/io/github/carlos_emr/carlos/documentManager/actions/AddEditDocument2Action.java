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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.CarlosProperties;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DocumentExtraReviewerDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentStorageDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.model.DocumentExtraReviewer;
import io.github.carlos_emr.carlos.commn.model.DocumentStorage;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import org.openpdf.text.pdf.PdfReader;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.owasp.encoder.Encode;

/**
 * Struts2 action for adding and editing documents in the CARLOS EMR document management system.
 *
 * <p>Handles document upload, metadata editing, file persistence, and page count extraction.
 * Supports both standard form-based uploads and HTML5 multi-file uploads. When a document
 * is added under a patient demographic, a case management note is automatically created
 * to record the event.
 *
 * <p>Security: All operations require the {@code _edoc} write privilege, enforced via
 * {@link SecurityInfoManager}. File paths are validated using {@link PathValidationUtils}
 * to prevent path traversal attacks. Filenames are sanitized before storage.
 *
 * <p>PDF page counting uses OpenPDF {@link PdfReader} to determine the number of pages
 * in uploaded PDF documents.
 *
 * @see ManageDocument2Action
 * @see EDocUtil
 * @see PathValidationUtils
 * @since 2006-07-27
 */
public class AddEditDocument2Action extends ActionSupport implements UploadedFilesAware {
    private static final int MAX_SAFE_EXTENSION_LENGTH = 10;
    private static final String PDF_EXTENSION = "pdf";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final byte[] PDF_HEADER = new byte[] {'%', 'P', 'D', 'F', '-'};
    private static final String ERROR_NO_WRITE_KEY = "dms.addDocument.errorNoWrite";
    private static final String ERROR_ZERO_SIZE_KEY = "dms.addDocument.errorZeroSize";
    private static final String PARAM_FUNCTION = "function";
    private static final String PARAM_FUNCTION_ID = "functionid";
    private static final String PARAM_CUR_USER = "curUser";
    private static final String PARAM_APPOINTMENT_NO = "appointmentNo";
    private static final String PARAM_PARENT_AJAX_ID = "parentAjaxId";

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Handles HTML5 multi-file document uploads. Validates file size, saves the file
     * locally, counts PDF pages using OpenPDF, persists the document record, and routes
     * it to the specified provider inbox and queue.
     *
     * @return String null (response is sent directly via HTTP headers)
     * @throws Exception if file write or document persistence fails
     * @throws SecurityException if the user lacks _edoc write privilege
     */
    public String html5MultiUpload() throws Exception {
        ResourceBundle props = ResourceBundle.getBundle("oscarResources");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        File uploadedDocFile = this.getDocFile();
        if (uploadedDocFile == null) {
            sendHtml5UploadError(props, ERROR_ZERO_SIZE_KEY);
            return null;
        }

        int numberOfPages = 0;
        File validatedSource;
        try {
            validatedSource = PathValidationUtils.validateUpload(uploadedDocFile);
        } catch (SecurityException e) {
            MiscUtils.getLogger().error("Invalid uploaded document file");
            sendHtml5UploadError(props, ERROR_NO_WRITE_KEY);
            return null;
        }

        String fileName = resolveSanitizedUploadedFileName(validatedSource, this.docFileFileName, this.docFileContentType);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String user = loggedInInfo.getLoggedInProviderNo();
        EDoc newDoc = new EDoc("", "", fileName, "", user, user, this.getSource(), 'A', UtilDateUtilities.getToday("yyyy-MM-dd"), "", "", "demographic", "-1", 0);
        newDoc.setDocPublic("0");
        newDoc.setAppointmentNo(Integer.parseInt(this.getAppointmentNo()));

        // if the document was added in the context of a program
        ProgramManager2 programManager = SpringUtils.getBean(ProgramManager2.class);
        ProgramProvider pp = programManager.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
        if (pp != null && pp.getProgramId() != null) {
            newDoc.setProgramId(pp.getProgramId().intValue());
        }

        long expectedFileSize = validatedSource.length();
        // save local file;
        if (expectedFileSize == 0) {
            sendHtml5UploadError(props, ERROR_ZERO_SIZE_KEY);
            return null;
        }
        // The upload source was validated above; keep all subsequent file I/O scoped to the
        // validated temp file reference and use try-with-resources for explicit stream cleanup.
        File file;
        try {
            file = writeValidatedUpload(validatedSource, fileName);
        } catch (IOException e) {
            MiscUtils.getLogger().error("Failed to write uploaded document file");
            sendHtml5UploadError(props, ERROR_NO_WRITE_KEY);
            return null;
        }

        if (!isWrittenUploadComplete(file, expectedFileSize)) {
            deleteIncompleteWrittenUpload(file);
            sendHtml5UploadError(props, ERROR_NO_WRITE_KEY);
            return null;
        }

        if (fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            newDoc.setContentType("application/pdf");
            // get number of pages when document is pdf;
            numberOfPages = countNumOfPages(fileName);
        }
        newDoc.setNumberOfPages(numberOfPages);
        String doc_no = EDocUtil.addDocumentSQL(newDoc);
        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());
        String providerId = request.getParameter("providers");

        if (providerId != null) { // TODO: THIS NEEDS TO RUN THRU THE lab forwarding rules!
            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
            ProviderInboxRoutingDao providerInboxRoutingDao = (ProviderInboxRoutingDao) ctx.getBean(ProviderInboxRoutingDao.class);
            providerInboxRoutingDao.addToProviderInbox(providerId, Integer.parseInt(doc_no), "DOC");
        }
        // add to queuelinkdocument
        String queueId = request.getParameter("queue");

        if (queueId != null && !queueId.equals("-1")) {
            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
            QueueDocumentLinkDao queueDocumentLinkDAO = (QueueDocumentLinkDao) ctx.getBean(QueueDocumentLinkDao.class);
            Integer qid = Integer.parseInt(queueId.trim());
            Integer did = Integer.parseInt(doc_no.trim());
            queueDocumentLinkDAO.addActiveQueueDocumentLink(qid, did);
            // nosemgrep: tainted-session-from-http-request -- queueId validated via Integer.parseInt and canonicalized to numeric string; stored after successful DAO operation
            request.getSession().setAttribute("preferredQueue", String.valueOf(qid)); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): qid is Integer.parseInt-validated queue ID
        }

        return null;

    }

    /**
     * Counts the number of pages in a local PDF file using OpenPDF PdfReader.
     * The file is located in the configured DOCUMENT_DIR.
     *
     * @param fileName String the PDF filename (relative to DOCUMENT_DIR)
     * @return int the number of pages, or 0 if the file cannot be read
     */
    public static int countNumOfPages(String fileName) {

        int numOfPage = 0;
        File documentDir = new File(CarlosProperties.getInstance().getDocumentDirectory());
        File validatedFile;
        try {
            validatedFile = PathValidationUtils.validatePath(fileName, documentDir);
        } catch (SecurityException e) {
            MiscUtils.getLogger().error("Invalid PDF page count file path");
            return numOfPage;
        }

        Path filePath = validatedFile.toPath().normalize().toAbsolutePath();
        if (!Files.isRegularFile(filePath)) {
            return numOfPage;
        }

        try (PdfReader reader = new PdfReader(filePath.toString())) {
            numOfPage = reader.getNumberOfPages();
        } catch (IOException e) {
            MiscUtils.getLogger().error("Failed to count document pages");
        }
        return numOfPage;
    }

    /**
     * Main Struts2 entry point. Dispatches to {@link #html5MultiUpload()} if the
     * request method parameter is "html5MultiUpload", otherwise delegates to
     * {@link #execute2()} for standard add/edit operations.
     *
     * @return String the Struts2 result name
     * @throws Exception if document processing fails
     */
    public String execute() throws Exception {
        if ("html5MultiUpload".equals(request.getParameter("method"))) {
            return html5MultiUpload();
        }
        return execute2();
    }

    /**
     * Handles the standard add/edit document workflow. Routes to add mode, edit mode,
     * or returns a file-size error based on the current mode and function parameters.
     *
     * @return String the Struts2 result name ("failEdit", "failAdd", "successEdit", or NONE)
     * @throws SecurityException if the user lacks _edoc write privilege
     */
    public String execute2() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        if (this.getMode().equals("") && this.getFunction().equals("") && this.getFunctionId().equals("")) {
            // file size exceeds the upload limit
            Hashtable errors = new Hashtable();
            errors.put("uploaderror", "dms.error.uploadError");
            request.setAttribute("docerrors", errors);
            request.setAttribute("editDocumentNo", "");
            return "failEdit";
        } else if (this.getMode().equals("add")) {
            // if add/edit success then send redirect, if failed send a forward (need the formdata and errors hashtables while trying to avoid POSTDATA messages)
            if (addDocument(request)) { // if success
                String contextPath = request.getContextPath();
                StringBuilder redirect = new StringBuilder(contextPath + "/documentManager/ViewDocumentReport");
                redirect.append("?docerrors=docerrors"); // Allows the JSP to check if the document was just submitted
                appendQueryParameter(redirect, PARAM_FUNCTION, request.getParameter(PARAM_FUNCTION));
                appendQueryParameter(redirect, PARAM_FUNCTION_ID, request.getParameter(PARAM_FUNCTION_ID));
                appendQueryParameter(redirect, PARAM_CUR_USER, request.getParameter(PARAM_CUR_USER));
                appendQueryParameter(redirect, PARAM_APPOINTMENT_NO, request.getParameter(PARAM_APPOINTMENT_NO));
                String parentAjaxId = request.getParameter(PARAM_PARENT_AJAX_ID);
                // if we're called with parent ajax id inform jsp that parent needs to be updated
                if (filled(parentAjaxId)) {
                    appendQueryParameter(redirect, PARAM_PARENT_AJAX_ID, parentAjaxId);
                    appendQueryParameter(redirect, "updateParent", "true");
                }
                try {
                    response.sendRedirect(redirect.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return NONE;
            } else {
                request.setAttribute(PARAM_FUNCTION, request.getParameter(PARAM_FUNCTION));
                request.setAttribute(PARAM_FUNCTION_ID, request.getParameter(PARAM_FUNCTION_ID));
                request.setAttribute(PARAM_PARENT_AJAX_ID, request.getParameter(PARAM_PARENT_AJAX_ID));
                request.setAttribute(PARAM_CUR_USER, request.getParameter(PARAM_CUR_USER));
                request.setAttribute(PARAM_APPOINTMENT_NO, request.getParameter(PARAM_APPOINTMENT_NO));
                return "failAdd";
            }
        } else {
            return editDocument(request);
        }
    }

    /**
     * Adds a new document: validates required fields, saves the uploaded file locally,
     * persists the EDoc record, creates audit log entries, and generates a case management
     * note when the document is linked to a patient demographic.
     *
     * @param request HttpServletRequest the current request for session and parameter access
     * @return boolean true if the document was added successfully, false on validation or I/O error
     */
    private boolean addDocument(HttpServletRequest request) {

        Hashtable errors = new Hashtable();
        try {
            if ((this.getDocDesc().length() == 0) || (this.getDocDesc().equals("Enter Title"))) {
                errors.put("descmissing", "dms.error.descriptionInvalid");
                throw new Exception();
            }
            if (this.getDocType().length() == 0) {
                errors.put("typemissing", "dms.error.typeMissing");
                throw new Exception();
            }
            File docFile = this.getDocFile();
            if (docFile == null) {
                errors.put("uploaderror", "dms.error.uploadError");
                throw new FileNotFoundException();
            }
            File validatedDocFile = PathValidationUtils.validateUpload(docFile);
            long expectedFileSize = validatedDocFile.length();
            if (expectedFileSize == 0) {
                errors.put("uploaderror", "dms.error.uploadError");
                throw new FileNotFoundException();
            }
            // sanitize the original file name first
            String fileName1 = resolveSanitizedUploadedFileName(validatedDocFile, this.docFileFileName, this.docFileContentType);

            EDoc newDoc = new EDoc(this.getDocDesc(), this.getDocType(), fileName1, "", this.getDocCreator(), this.getResponsibleId(), this.getSource(), 'A', this.getObservationDate(), "", "", this.getFunction(), this.getFunctionId());
            newDoc.setDocPublic(this.getDocPublic());

            newDoc.setAppointmentNo(Integer.parseInt(this.getAppointmentNo()));
            newDoc.setDocClass(this.getDocClass());
            newDoc.setDocSubClass(this.getDocSubClass());
            // get the filename with timestamp prefix from EDoc (after preliminary processing)
            String fileName2 = newDoc.getFileName();

            // save local file
            File writtenFile;
            try {
                writtenFile = writeValidatedUpload(validatedDocFile, fileName2);
            } catch (IOException e) {
                errors.put("uploaderror", "dms.error.uploadError");
                addActionError(getText("dms.error.uploadError"));
                throw e;
            }
            if (!isWrittenUploadComplete(writtenFile, expectedFileSize)) {
                deleteIncompleteWrittenUpload(writtenFile);
                errors.put("uploaderror", "dms.error.uploadError");
                addActionError(getText("dms.error.uploadError"));
                throw new IOException("Failed to write uploaded document");
            }
            newDoc.setContentType(this.docFileContentType);

            if (fileName2.toLowerCase().endsWith(".pdf")) {
                newDoc.setContentType("application/pdf");
                int numberOfPages = countNumOfPages(fileName2);
                newDoc.setNumberOfPages(numberOfPages);
            }


            // if the document was added in the context of a program
            ProgramManager2 programManager = SpringUtils.getBean(ProgramManager2.class);
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            ProgramProvider pp = programManager.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
            if (pp != null && pp.getProgramId() != null) {
                newDoc.setProgramId(pp.getProgramId().intValue());
            }

            String restrictToProgramStr = request.getParameter("restrictToProgram");
            newDoc.setRestrictToProgram("on".equals(restrictToProgramStr));

            // if the document was added in the context of an appointment
            if (this.getAppointmentNo() != null && this.getAppointmentNo().length() > 0) {
                newDoc.setAppointmentNo(Integer.parseInt(this.getAppointmentNo()));
            }

            // If a new document type is added, include it in the database to create filters
            if (!EDocUtil.getDoctypes(this.getFunction()).contains(this.getDocType())) {
                EDocUtil.addDocTypeSQL(this.getDocType(), this.getFunction());
            }


            // ---
            String doc_no = EDocUtil.addDocumentSQL(newDoc);
            LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());
            // add note if document is added under a patient
            String module = this.getFunction().trim();
            String moduleId = this.getFunctionId().trim();
            if (module.equals("demographic")) {// doc is uploaded under a patient, moduleId become demo no.

                Date now = EDocUtil.getDmsDateTimeAsDate();

                String docDesc = EDocUtil.getLastDocumentDesc();

                CaseManagementNote cmn = new CaseManagementNote();
                cmn.setUpdate_date(now);
                java.sql.Date od1 = MyDateFormat.getSysDate(newDoc.getObservationDate());
                cmn.setObservation_date(od1);
                cmn.setDemographic_no(moduleId);
                HttpSession se = request.getSession();
                String user_no = (String) se.getAttribute("user");
                String prog_no = new EctProgram(se).getProgram(user_no);
                WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(se.getServletContext());
                CaseManagementManager cmm = (CaseManagementManager) ctx.getBean(CaseManagementManager.class);
                cmn.setProviderNo("-1"); // set the providers no to be -1 so the editor appear as 'System'.

                Provider provider = EDocUtil.getProvider(this.getDocCreator());
                String provFirstName = "";
                String provLastName = "";
                if (provider != null) {
                    provFirstName = provider.getFirstName();
                    provLastName = provider.getLastName();
                }

                String strNote = "Document" + " " + docDesc + " " + "created at " + now + " by " + provFirstName + " " + provLastName + ".";

                cmn.setNote(strNote);
                cmn.setSigned(true);
                cmn.setSigning_provider_no("-1");
                cmn.setProgram_no(prog_no);

                SecRoleDao secRoleDao = (SecRoleDao) SpringUtils.getBean(SecRoleDao.class);
                SecRole doctorRole = secRoleDao.findByName("doctor");
                cmn.setReporter_caisi_role(doctorRole.getId().toString());

                cmn.setReporter_program_team("0");
                cmn.setLocked(false);
                cmn.setHistory(strNote);
                cmn.setPosition(0);

                Long note_id = cmm.saveNoteSimpleReturnID(cmn);

                // Add a noteLink to casemgmt_note_link
                CaseManagementNoteLink cmnl = new CaseManagementNoteLink();
                cmnl.setTableName(CaseManagementNoteLink.DOCUMENT);
                cmnl.setTableId(Long.parseLong(EDocUtil.getLastDocumentNo()));
                cmnl.setNoteId(note_id);

                EDocUtil.addCaseMgmtNoteLink(cmnl);
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to add uploaded document");
            // ActionRedirect redirect = new ActionRedirect(mapping.findForward("failAdd"));
            request.setAttribute("docerrors", errors);
            return false;
        }

        return true;
    }

    /**
     * Edits an existing document's metadata and optionally replaces its file content.
     * Handles reviewer assignment, extra reviewer tracking, and document type updates.
     *
     * @param request HttpServletRequest the current request for session and parameter access
     * @return String the Struts2 result name ("successEdit" or "failEdit")
     */
    private String editDocument(HttpServletRequest request) {
        Hashtable errors = new Hashtable();

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        try {
            if (this.getDocDesc().length() == 0) {
                errors.put("descmissing", "dms.error.descriptionInvalid");
                throw new Exception();
            }
            if (this.getDocType().length() == 0) {
                errors.put("typemissing", "dms.error.typeMissing");
                throw new Exception();
            }
            String fileName = "";
            boolean updateFileContent = false;
            File validatedDocFile = null;

            if (CarlosProperties.getInstance().getBooleanProperty("ALLOW_UPDATE_DOCUMENT_CONTENT", "true"))
            {
                File docFile = this.getDocFile();
                if (docFile != null && docFile.exists()) {
                    validatedDocFile = PathValidationUtils.validateUpload(docFile);
                    fileName = resolveSanitizedUploadedFileName(validatedDocFile, this.docFileFileName, this.docFileContentType);
                    updateFileContent = true; // set update to true
                }
            }

            String reviewerId = filled(this.getReviewerId()) ? this.getReviewerId() : "";
            String reviewDateTime = filled(this.getReviewDateTime()) ? this.getReviewDateTime() : "";

            if (!filled(reviewerId) && this.getReviewDoc()) {
                LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
                reviewerId = loggedInInfo.getLoggedInProviderNo();
                reviewDateTime = UtilDateUtilities.DateToString(new Date(), EDocUtil.REVIEW_DATETIME_FORMAT);
                if (this.getFunction() != null && this.getFunction().equals("demographic")) {
                    LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.REVIEWED, LogConst.CON_DOCUMENT, this.getMode(),
request.getRemoteAddr(), this.getFunctionId());
                } else {
                    LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.REVIEWED, LogConst.CON_DOCUMENT, this.getMode(),
request.getRemoteAddr());
                }
            }

            EDoc newDoc = new EDoc(this.getDocDesc(), this.getDocType(), fileName, "", this.getDocCreator(), this.getResponsibleId(),
this.getSource(), 'A', this.getObservationDate(), reviewerId, reviewDateTime, this.getFunction(), this.getFunctionId());
            newDoc.setSourceFacility(this.getSourceFacility());
            newDoc.setDocId(this.getMode());
            newDoc.setDocPublic(this.getDocPublic());
            newDoc.setAppointmentNo(Integer.parseInt(this.getAppointmentNo()));
            newDoc.setDocClass(this.getDocClass());
            newDoc.setDocSubClass(this.getDocSubClass());
            newDoc.setAbnormal(this.getAbnormal());
            newDoc.setReceivedDate(this.getReceivedDate());
            String programIdStr = (String) request.getSession().getAttribute(SessionConstants.CURRENT_PROGRAM_ID);
            if (programIdStr != null) newDoc.setProgramId(Integer.valueOf(programIdStr));

            // if the update behavior is true, get the file name
            if (updateFileContent) {
                long expectedFileSize = validatedDocFile.length();
                fileName = MiscUtils.sanitizeFileName(newDoc.getFileName());
                // save local file
                File writtenFile;
                try {
                    writtenFile = writeValidatedUpload(validatedDocFile, fileName);
                } catch (IOException e) {
                    errors.put("uploaderror", "dms.error.uploadError");
                    addActionError(getText("dms.error.uploadError"));
                    throw e;
                }
                if (!isWrittenUploadComplete(writtenFile, expectedFileSize)) {
                    deleteIncompleteWrittenUpload(writtenFile);
                    errors.put("uploaderror", "dms.error.uploadError");
                    addActionError(getText("dms.error.uploadError"));
                    throw new IOException("Failed to write uploaded document");
                }
                if (fileName.toLowerCase().endsWith(".pdf")) {
                    newDoc.setContentType("application/pdf");
                    int numberOfPages = countNumOfPages(fileName);
                    newDoc.setNumberOfPages(numberOfPages);
                }
            }
            if (this.getReviewDoc()) {
                newDoc.setReviewDateTime(UtilDateUtilities.DateToString(new Date(), EDocUtil.REVIEW_DATETIME_FORMAT));
            }

            if (this.isExtraReviewDoc()) {
                DocumentExtraReviewer der = new DocumentExtraReviewer();
                der.setDocumentNo(Integer.parseInt(newDoc.getDocId()));
                der.setReviewDateTime(new Date());
                der.setReviewerProviderNo(this.getExtraReviewerId());

                DocumentExtraReviewerDao derDao = SpringUtils.getBean(DocumentExtraReviewerDao.class);
                derDao.persist(der);

                //don't lose the initial review
                this.setReviewDoc(true);
                newDoc.setReviewDateTime(this.getReviewDateTime());
            }

            EDocUtil.editDocumentSQL(newDoc, this.getReviewDoc());

            if (this.getFunction() != null && this.getFunction().equals("demographic")) {
                LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.UPDATE, LogConst.CON_DOCUMENT, this.getMode(), request.getRemoteAddr(), this.getFunctionId());
            } else {
                LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.UPDATE, LogConst.CON_DOCUMENT, this.getMode(), request.getRemoteAddr());

            }

        } catch (Exception e) {
            request.setAttribute("docerrors", errors);
            request.setAttribute("editDocumentNo", this.getMode());
            MiscUtils.getLogger().error("Failed to edit document", e);
            return "failEdit";
        }
        return "successEdit";
    }


    /**
     * Writes an uploaded file to the local document storage directory. The destination
     * path is validated using {@link PathValidationUtils} to prevent path traversal.
     *
     * @param is InputStream the input stream of the file content to write
     * @param fileName String the target filename (relative to DOCUMENT_DIR)
     * @return File the written file
     * @throws Exception if validation or writing fails
     */
    public static File writeLocalFile(InputStream is, String fileName) throws Exception {
        String docDir = CarlosProperties.getInstance().getDocumentDirectory();
        File baseDirFile = new File(docDir);
        File validatedFile = PathValidationUtils.validatePath(fileName, baseDirFile);
        Path savePath = validatedFile.toPath().normalize().toAbsolutePath();
        Path saveParent = savePath.getParent();
        if (saveParent == null) {
            throw new IOException("Document destination parent is missing");
        }

        Files.createDirectories(saveParent);

        Path tempPath = Files.createTempFile(saveParent, "document-upload-", ".tmp");
        try {
            writeUploadContents(is, tempPath);
            moveUploadedFile(tempPath, savePath);
        } catch (Exception e) {
            deleteTempFile(tempPath, e);
            throw e;
        }

        return savePath.toFile();
    }

    private static void writeUploadContents(InputStream is, Path tempPath) throws IOException {
        try (OutputStream fos = Files.newOutputStream(tempPath, StandardOpenOption.WRITE)) {
            byte[] buf = new byte[128 * 1024];
            int i = 0;
            while ((i = is.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
        }
    }

    private static void moveUploadedFile(Path tempPath, Path savePath) throws IOException {
        try {
            Files.move(tempPath, savePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, savePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTempFile(Path tempPath, Exception originalError) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException deleteError) {
            originalError.addSuppressed(deleteError);
        }
    }

    /**
     * Stores the binary contents of a document file in the database as a {@link DocumentStorage}
     * record. This provides an alternative to file-system-only storage.
     *
     * @param file File the document file to store
     * @param documentNo Integer the document number to associate the storage record with
     * @return int the generated storage record ID, or 0 if an error occurred
     */
    public static int storeDocumentInDatabase(File file, Integer documentNo) {
        Integer ret = 0;
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            byte fileContents[] = new byte[(int) file.length()];
            fin.read(fileContents);
            DocumentStorage docStor = new DocumentStorage();
            docStor.setFileContents(fileContents);
            docStor.setDocumentNo(documentNo);
            docStor.setUploadDate(new Date());
            DocumentStorageDao documentStorageDao = (DocumentStorageDao) SpringUtils.getBean(DocumentStorageDao.class);
            documentStorageDao.persist(docStor);
            ret = docStor.getId();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to store document file in database");
        } finally {
            IOUtils.closeQuietly(fin);
        }
        return ret;
    }

    /**
     * Binds Struts 7 {@link UploadedFile} instances to this action. The {@code docFile}
     * input takes precedence over {@code filedata}; once a {@code docFile} entry has been
     * bound, any subsequent entries (including additional {@code filedata} entries) are
     * ignored to ensure deterministic selection regardless of list ordering.
     * Each selected upload is validated immediately so the action only retains temp
     * files from approved locations; business methods then re-validate at point of use
     * before any file I/O as defense in depth.
     *
     * @param uploadedFiles List&lt;UploadedFile&gt; the uploads provided by the Struts file
     *                      upload interceptor, or {@code null} if none were posted
     */
    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles == null) {
            return;
        }

        UploadedFile selected = null;
        for (UploadedFile uploaded : uploadedFiles) {
            if (uploaded != null) {
                String inputName = uploaded.getInputName();
                if ("docFile".equals(inputName)) {
                    selected = uploaded;
                    break;
                }
                if (selected == null && "filedata".equals(inputName)) {
                    selected = uploaded;
                }
            }
        }

        if (selected != null) {
            // Validate once when binding the Struts 7 upload so the action only stores
            // temp files from approved locations. Business methods re-validate again
            // immediately before file I/O for defense in depth and static-analysis visibility.
            File validatedUpload = PathValidationUtils.validateUpload(resolveUploadedContentFile(selected));
            this.docFile = validatedUpload;
            this.docFileFileName = resolveSanitizedUploadedFileName(validatedUpload, selected.getOriginalName(), selected.getContentType());
            this.docFileContentType = selected.getContentType();
        }
    }

    /**
     * Resolves the safest filename to associate with an uploaded temp file.
     * Extracts only the basename from the supplied original name to discard any
     * path components, sanitizes it, and falls back to the validated temp file
     * name when the original value is null, blank, path-only, or sanitizes to
     * blank. The fallback preserves a safe extension where possible so PDF
     * handling is not bypassed solely because Struts omitted the original name.
     *
     * @param uploadedFile File the validated temporary upload file
     * @param originalName String the original client-supplied filename, if any
     * @param contentType String the upload content type reported by Struts, if any
     * @return String the normalized and sanitized filename to use for storage
     */
    private String resolveSanitizedUploadedFileName(File uploadedFile, String originalName, String contentType) {
        String candidate = filled(originalName) ? FilenameUtils.getName(originalName) : null;
        if (filled(candidate)) {
            String sanitizedName = MiscUtils.sanitizeFileName(candidate);
            if (filled(sanitizedName)) {
                return sanitizedName;
            }
        }

        return resolveFallbackUploadFileName(uploadedFile, originalName, contentType);
    }

    private String resolveFallbackUploadFileName(File uploadedFile, String originalName, String contentType) {
        String fallbackName = MiscUtils.sanitizeFileName(uploadedFile.getName());
        String safeExtension = safeExtension(originalName);
        if (!filled(safeExtension) && isPdfUpload(uploadedFile, contentType)) {
            safeExtension = PDF_EXTENSION;
        }
        if (!filled(safeExtension)) {
            return fallbackName;
        }

        String currentExtension = FilenameUtils.getExtension(fallbackName);
        if (safeExtension.equalsIgnoreCase(currentExtension)) {
            return fallbackName;
        }

        String baseName = FilenameUtils.removeExtension(fallbackName);
        String resolvedBaseName = filled(baseName) ? baseName : fallbackName;
        return resolvedBaseName + "." + safeExtension.toLowerCase(Locale.ROOT);
    }

    private String safeExtension(String fileName) {
        if (!filled(fileName)) {
            return "";
        }

        String extension = FilenameUtils.getExtension(FilenameUtils.getName(fileName));
        if (!filled(extension) || extension.length() > MAX_SAFE_EXTENSION_LENGTH) {
            return "";
        }
        return extension.matches("[A-Za-z0-9]+") ? extension : "";
    }

    private boolean isPdfUpload(File uploadedFile, String contentType) {
        if (PDF_CONTENT_TYPE.equalsIgnoreCase(safeContentType(contentType))) {
            return true;
        }

        try (InputStream inputStream = PathValidationUtils.openValidatedUploadInputStream(uploadedFile)) {
            byte[] header = inputStream.readNBytes(PDF_HEADER.length);
            if (header.length != PDF_HEADER.length) {
                return false;
            }
            for (int i = 0; i < PDF_HEADER.length; i++) {
                if (header[i] != PDF_HEADER[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            MiscUtils.getLogger().warn("Failed to read validated upload for PDF header check", e);
            return false;
        }
    }

    private String safeContentType(String contentType) {
        if (!filled(contentType)) {
            return "";
        }
        int parameterStart = contentType.indexOf(';');
        String mediaType = parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType;
        return mediaType.trim();
    }

    /**
     * Resolves the filesystem-backed content from Struts' upload abstraction.
     * CARLOS requires file-backed Struts uploads here so {@link PathValidationUtils}
     * can canonicalize the temp file and enforce allowed upload-source directories
     * before any file content is read.
     *
     * @param uploadedFile UploadedFile the selected Struts upload
     * @return File the upload content file to validate
     */
    private File resolveUploadedContentFile(UploadedFile uploadedFile) {
        if (uploadedFile.isFile()) {
            String absolutePath = uploadedFile.getAbsolutePath();
            if (filled(absolutePath)) {
                return new File(absolutePath);
            }
        }

        Object content = uploadedFile.getContent();
        if (content instanceof File uploadFile) {
            return uploadFile;
        }

        throw new SecurityException("Selected document upload content must be file-backed");
    }

    /**
     * Opens a stream for a validated upload file.
     * The upload is re-validated immediately before opening and must resolve to one
     * of the allowed upload temp locations enforced by {@link PathValidationUtils}.
     * Callers must close the returned stream; all current callers use try-with-resources.
     *
     * @param validatedUpload File returned by or suitable for {@link PathValidationUtils#validateUpload(File)}
     * @return InputStream for the upload content
     * @throws IOException if the stream cannot be opened
     */
    private InputStream openValidatedUploadInputStream(File validatedUpload) throws IOException {
        return PathValidationUtils.openValidatedUploadInputStream(validatedUpload);
    }

    private File writeValidatedUpload(File validatedUpload, String fileName) throws IOException {
        try (InputStream inputStream = openValidatedUploadInputStream(validatedUpload)) {
            return writeLocalFile(inputStream, fileName);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to write uploaded document", e);
        }
    }

    /**
     * Verifies that an upload write produced a regular file with exactly the expected size.
     *
     * @param writtenFile File the destination returned by {@link #writeLocalFile(InputStream, String)}
     * @param expectedFileSize long the validated source upload size in bytes
     * @return boolean true when the destination file exists, is a regular file, and matches the source size
     */
    private boolean isWrittenUploadComplete(File writtenFile, long expectedFileSize) {
        try {
            Path writtenPath = resolveWrittenDocumentPath(writtenFile);
            return Files.isRegularFile(writtenPath) && Files.size(writtenPath) == expectedFileSize;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Deletes an incomplete destination file produced by a failed upload write.
     *
     * @param writtenFile File the destination returned by {@link #writeLocalFile(InputStream, String)}
     */
    private void deleteIncompleteWrittenUpload(File writtenFile) {
        if (writtenFile == null) {
            return;
        }

        try {
            Path writtenPath = resolveWrittenDocumentPath(writtenFile);
            Files.deleteIfExists(writtenPath); // codeql[java/path-injection] -- writtenPath is constrained to DOCUMENT_DIR by PathValidationUtils.validateExistingPath
        } catch (Exception e) {
            MiscUtils.getLogger().warn("Failed to delete incomplete uploaded document file");
        }
    }

    private void appendQueryParameter(StringBuilder redirect, String name, String value) {
        redirect.append('&')
                .append(name)
                .append('=')
                .append(Encode.forUriComponent(value == null ? "" : value));
    }

    private void sendHtml5UploadError(ResourceBundle props, String errorKey) throws IOException {
        String message = props.getString(errorKey);
        response.setHeader("oscar_error", message);
        response.sendError(500, message);
    }

    /**
     * Resolves a file returned from document storage into a normalized path that
     * remains inside the configured document directory before any status check or
     * cleanup operation uses it.
     *
     * @param writtenFile File the destination returned by {@link #writeLocalFile(InputStream, String)}
     * @return Path the validated, normalized destination path under {@code DOCUMENT_DIR}
     * @throws IOException when the destination is missing or cannot be resolved safely
     * @throws SecurityException when the destination does not resolve inside {@code DOCUMENT_DIR}
     */
    private Path resolveWrittenDocumentPath(File writtenFile) throws IOException {
        if (writtenFile == null) {
            throw new IOException("Written upload file is missing");
        }

        File documentDir = new File(CarlosProperties.getInstance().getDocumentDirectory());
        File validatedWrittenFile = PathValidationUtils.validateExistingPath(writtenFile, documentDir);
        return validatedWrittenFile.toPath().normalize().toAbsolutePath();
    }

    private boolean filled(String s) {
        return (s != null && s.trim().length() > 0);
    }

    private String function = "";
    private String functionId = "";
    private String docType = "";
    private String docClass = "";
    private String docSubClass = "";
    private String docDesc = "";
    private String docCreator = "";
    private String responsibleId = "";
    private String source = "";
    private String sourceFacility = "";
    private File docFile;


    private String docPublic = "";
    private String mode = "";
    private String observationDate = "";
    private String reviewerId = "";
    private String reviewDateTime = "";
    private String contentDateTime = "";
    private boolean reviewDoc = false;
    private String html = "";

    private String appointmentNo = "0";

    private boolean restrictToProgram = false;
    private String receivedDate = "";
    private String abnormal = "";

    private String extraReviewerId = "";
    private boolean extraReviewDoc = false;

    public String getFunction() {
        return function;
    }

    @StrutsParameter
    public void setFunction(String function) {
        this.function = function;
    }

    public String getFunctionId() {
        return functionId;
    }

    @StrutsParameter
    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public String getDocType() {
        return docType;
    }

    @StrutsParameter
    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDocClass() {
        return docClass;
    }

    @StrutsParameter
    public void setDocClass(String docClass) {
        this.docClass = docClass;
    }

    public String getDocSubClass() {
        return docSubClass;
    }

    @StrutsParameter
    public void setDocSubClass(String docSubClass) {
        this.docSubClass = docSubClass;
    }

    public String getDocDesc() {
        return docDesc;
    }

    @StrutsParameter
    public void setDocDesc(String docDesc) {
        this.docDesc = docDesc;
    }

    public String getDocCreator() {
        return docCreator;
    }

    @StrutsParameter
    public void setDocCreator(String docCreator) {
        this.docCreator = docCreator;
    }

    public String getResponsibleId() {
        return responsibleId;
    }

    @StrutsParameter
    public void setResponsibleId(String responsibleId) {
        this.responsibleId = responsibleId;
    }

    public String getSource() {
        return source;
    }

    @StrutsParameter
    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceFacility() {
        return sourceFacility;
    }

    @StrutsParameter
    public void setSourceFacility(String sourceFacility) {
        this.sourceFacility = sourceFacility;
    }

    public File getDocFile() {
        return docFile;
    }

    public String getMode() {
        return mode;
    }

    @StrutsParameter
    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getDocPublic() {
        return docPublic;
    }

    @StrutsParameter
    public void setDocPublic(String docPublic) {
        this.docPublic = docPublic;
    }

    public String getObservationDate() {
        return observationDate;
    }

    @StrutsParameter
    public void setObservationDate(String observationDate) {
        this.observationDate = observationDate;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    @StrutsParameter
    public void setReviewerId(String reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewDateTime() {
        return reviewDateTime;
    }

    @StrutsParameter
    public void setReviewDateTime(String reviewDateTime) {
        this.reviewDateTime = reviewDateTime;
    }

    public String getContentDateTime() {
        return contentDateTime;
    }

    @StrutsParameter
    public void setContentDateTime(String contentDateTime) {
        this.contentDateTime = contentDateTime;
    }

    public boolean getReviewDoc() {
        return reviewDoc;
    }

    @StrutsParameter
    public void setReviewDoc(boolean reviewDoc) {
        this.reviewDoc = reviewDoc;
    }

    public String getHtml() {
        return html;
    }

    @StrutsParameter
    public void setHtml(String html) {
        this.html = html;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }

    @StrutsParameter
    public void setAppointmentNo(String appointment) {
        this.appointmentNo = appointment;
    }

    public boolean isRestrictToProgram() {
        return restrictToProgram;
    }

    @StrutsParameter
    public void setRestrictToProgram(boolean restrictToProgram) {
        this.restrictToProgram = restrictToProgram;
    }

    public String getReceivedDate() {
        return receivedDate;
    }

    @StrutsParameter
    public void setReceivedDate(String receivedDate) {
        this.receivedDate = receivedDate;
    }

    public String getAbnormal() {
        return abnormal;
    }

    @StrutsParameter
    public void setAbnormal(String abnormal) {
        this.abnormal = abnormal;
    }

    public String getExtraReviewerId() {
        return extraReviewerId;
    }

    @StrutsParameter
    public void setExtraReviewerId(String extraReviewerId) {
        this.extraReviewerId = extraReviewerId;
    }

    public boolean isExtraReviewDoc() {
        return extraReviewDoc;
    }

    @StrutsParameter
    public void setExtraReviewDoc(boolean extraReviewDoc) {
        this.extraReviewDoc = extraReviewDoc;
    }

    private String docFileFileName;
    private String docFileContentType;

    public String getDocFileFileName() {
        return docFileFileName;
    }

    public void setDocFileFileName(String docFileFileName) {
        this.docFileFileName = docFileFileName;
    }

    public String getDocFileContentType() {
        return docFileContentType;
    }

    public void setDocFileContentType(String docFileContentType) {
        this.docFileContentType = docFileContentType;
    }
}
