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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.CarlosProperties;
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
import io.github.carlos_emr.carlos.utility.FileValidationException;
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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

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
 * <p>PDF page counting is delegated to {@link EDocUtil} to determine the number of pages
 * in uploaded PDF documents.
 *
 * @see ManageDocument2Action
 * @see EDocUtil
 * @see PathValidationUtils
 * @since 2006-07-27
 */
public class AddEditDocument2Action extends ActionSupport implements UploadedFilesAware {
    private static final String EDIT_DOCUMENT_NO_ATTRIBUTE = "editDocumentNo";
    private static final String FAIL_EDIT_RESULT = "failEdit";
    private static final String FUNCTION_PARAMETER = "function";
    private static final String FUNCTION_ID_PARAMETER = "functionid";
    private static final String CURRENT_USER_PARAMETER = "curUser";
    private static final String APPOINTMENT_NO_PARAMETER = "appointmentNo";
    private static final String PARENT_AJAX_ID_PARAMETER = "parentAjaxId";
    private static final String DOCUMENT_REPORT_PATH = "/documentManager/ViewDocumentReport";
    private static final int MAX_REDIRECT_PARAMETER_LENGTH = 128;
    private static final Pattern SAFE_REDIRECT_PARAMETER = Pattern.compile("[A-Za-z0-9_.:-]{0,128}");

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
            response.setHeader("oscar_error", props.getString("dms.addDocument.errorZeroSize"));
            response.sendError(500, props.getString("dms.addDocument.errorZeroSize"));
            return null;
        }

        int numberOfPages = 0;
        String originalFileName = filled(this.docFileFileName) ? this.docFileFileName : uploadedDocFile.getName();
        String fileName;
        try {
            fileName = PathValidationUtils.validateFileName(originalFileName);
        } catch (FileValidationException e) {
            MiscUtils.getLogger().warn("Rejected invalid document upload filename");
            response.setHeader("oscar_error", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return null;
        }

        ValidatedDocumentUpload validatedSource;
        File file;
        try {
            validatedSource = ValidatedDocumentUpload.from(uploadedDocFile);
            if (validatedSource.length() == 0) {
                response.setHeader("oscar_error", props.getString("dms.addDocument.errorZeroSize"));
                response.sendError(500, props.getString("dms.addDocument.errorZeroSize"));
                return null;
            }
            file = writeLocalFile(validatedSource.openStream(), fileName); // write file to local dir
        } catch (FileValidationException e) {
            MiscUtils.getLogger().warn("Rejected invalid document upload source");
            response.setHeader("oscar_error", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return null;
        }

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

        if (!file.exists() || file.length() < validatedSource.length()) {
            response.setHeader("oscar_error", props.getString("dms.addDocument.errorNoWrite"));
            response.sendError(500, props.getString("dms.addDocument.errorNoWrite"));
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
     * Counts the number of pages in a local PDF file using the centralized document utility.
     * The file is located in the configured DOCUMENT_DIR.
     *
     * @param fileName String the PDF filename (relative to DOCUMENT_DIR)
     * @return int the number of pages, or 0 if the file cannot be read
     */
    public static int countNumOfPages(String fileName) {
        return EDocUtil.getPDFPageCount(fileName);
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
            Hashtable<String, String> errors = new Hashtable<>();
            errors.put("uploaderror", "dms.error.uploadError");
            request.setAttribute("docerrors", errors);
            request.setAttribute(EDIT_DOCUMENT_NO_ATTRIBUTE, "");
            return FAIL_EDIT_RESULT;
        } else if (this.getMode().equals("add")) {
            // if add/edit success then send redirect, if failed send a forward (need the formdata and errors hashtables while trying to avoid POSTDATA messages)
            if (addDocument(request)) { // if success
                String redirect = buildDocumentReportRedirect(request);
                try {
                    response.sendRedirect(redirect); // codeql[java/unvalidated-url-redirection] -- fixed same-app target; request values are allowlisted query parameters only
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return NONE;
            } else {
                request.setAttribute(FUNCTION_PARAMETER, request.getParameter(FUNCTION_PARAMETER));
                request.setAttribute(FUNCTION_ID_PARAMETER, request.getParameter(FUNCTION_ID_PARAMETER));
                request.setAttribute(PARENT_AJAX_ID_PARAMETER, request.getParameter(PARENT_AJAX_ID_PARAMETER));
                request.setAttribute(CURRENT_USER_PARAMETER, request.getParameter(CURRENT_USER_PARAMETER));
                request.setAttribute(APPOINTMENT_NO_PARAMETER, request.getParameter(APPOINTMENT_NO_PARAMETER));
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

        Hashtable<String, String> errors = new Hashtable<>();
        try {
            if (isMissingDocumentDescription()) {
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
            ValidatedDocumentUpload docUpload = validateUploadedDocumentSource(docFile, errors);
            if (docUpload.length() == 0) {
                errors.put("uploaderror", "dms.error.uploadError");
                throw new FileNotFoundException();
            }
            // validate the original file name first
            String fileName1 = PathValidationUtils.validateFileName(this.docFileFileName);

            EDoc newDoc = new EDoc(this.getDocDesc(), this.getDocType(), fileName1, "", this.getDocCreator(), this.getResponsibleId(), this.getSource(), 'A', this.getObservationDate(), "", "", this.getFunction(), this.getFunctionId());
            newDoc.setDocPublic(this.getDocPublic());

            newDoc.setAppointmentNo(Integer.parseInt(this.getAppointmentNo()));
            newDoc.setDocClass(this.getDocClass());
            newDoc.setDocSubClass(this.getDocSubClass());
            // get the filename with timestamp prefix from EDoc (after preliminary processing)
            String fileName2 = newDoc.getFileName();

            // save local file
            writeLocalFile(docUpload.openStream(), fileName2);
            newDoc.setContentType(this.docFileContentType);

            if (fileName2.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
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

        } catch (FileValidationException e) {
            MiscUtils.getLogger().warn("Rejected invalid document upload source or filename");
            errors.putIfAbsent("uploaderror", "dms.error.invalidFilename");
            request.setAttribute("docerrors", errors);
            return false;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            // ActionRedirect redirect = new ActionRedirect(mapping.findForward("failAdd"));
            request.setAttribute("docerrors", errors);
            return false;
        }

        return true;
    }

    private boolean isMissingDocumentDescription() {
        return this.getDocDesc().isEmpty() || this.getDocDesc().equals("Enter Title");
    }

    /**
     * Edits an existing document's metadata and optionally replaces its file content.
     * Handles reviewer assignment, extra reviewer tracking, and document type updates.
     *
     * @param request HttpServletRequest the current request for session and parameter access
     * @return String the Struts2 result name ("successEdit" or "failEdit")
     */
    private String editDocument(HttpServletRequest request) {
        Hashtable<String, String> errors = new Hashtable<>();

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
            ValidatedDocumentUpload validatedDocFileForUpdate = null;

            if (CarlosProperties.getInstance().getBooleanProperty("ALLOW_UPDATE_DOCUMENT_CONTENT", "true"))
            {
                File docFile = this.getDocFile();
                if (docFile != null && docFile.exists()) {
                    validatedDocFileForUpdate = validateUploadedDocumentSource(docFile, errors);
                    fileName = PathValidationUtils.validateFileName(this.docFileFileName);
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
                fileName = PathValidationUtils.validateFileName(newDoc.getFileName());
                // save local file
                writeLocalFile(validatedDocFileForUpdate.openStream(), fileName);
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
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

        } catch (FileValidationException e) {
            errors.putIfAbsent("uploaderror", "dms.error.invalidFilename");
            request.setAttribute("docerrors", errors);
            request.setAttribute(EDIT_DOCUMENT_NO_ATTRIBUTE, this.getMode());
            MiscUtils.getLogger().warn("Rejected invalid document upload source or filename");
            return FAIL_EDIT_RESULT;
        } catch (Exception e) {
            request.setAttribute("docerrors", errors);
            request.setAttribute(EDIT_DOCUMENT_NO_ATTRIBUTE, this.getMode());
            MiscUtils.getLogger().error("Failed to edit document", e);
            return FAIL_EDIT_RESULT;
        }
        return "successEdit";
    }

    private ValidatedDocumentUpload validateUploadedDocumentSource(File docFile, Hashtable<String, String> errors) {
        try {
            return ValidatedDocumentUpload.from(docFile);
        } catch (FileValidationException e) {
            errors.put("uploaderror", "dms.error.uploadError");
            throw e;
        }
    }

    private static final class ValidatedDocumentUpload {
        private final File file;

        private ValidatedDocumentUpload(File file) {
            this.file = file;
        }

        private static ValidatedDocumentUpload from(File uploadFile) {
            File validatedUpload = PathValidationUtils.validateUpload(uploadFile);
            return new ValidatedDocumentUpload(validatedUpload);
        }

        private long length() {
            return file.length();
        }

        private InputStream openStream() throws IOException {
            // codeql[java/path-injection] -- validated Struts/Tomcat temp file; see ValidatedDocumentUpload.from.
            return Files.newInputStream(file.toPath());
        }
    }


    /**
     * Writes an uploaded file to the local document storage directory. The destination
     * path is validated using {@link PathValidationUtils} to prevent path traversal.
     *
     * @param is InputStream the input stream of the file content to write
     * @param fileName String the target filename (relative to DOCUMENT_DIR)
     * @return File the written file
     * @throws Exception if path validation or file writing fails
     */
    public static File writeLocalFile(InputStream is, String fileName) throws Exception {
        // Validate file path using PathValidationUtils
        String docDir = CarlosProperties.getInstance().getDocumentDirectory();
        File baseDirFile = new File(docDir);
        File validatedFile = PathValidationUtils.validatePath(fileName, baseDirFile);
        Path savePath = validatedFile.toPath();
        Path parentPath = savePath.getParent();
        if (parentPath == null) {
            throw new FileValidationException("Document storage path has no parent directory");
        }

        // Create the parent directory
        Files.createDirectories(parentPath);

        try (InputStream input = is;
                FileOutputStream fos = new FileOutputStream(savePath.toFile())) {
            byte[] buf = new byte[128 * 1024];
            int i = 0;
            while ((i = input.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
        }
        return savePath.toFile();
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
            MiscUtils.getLogger().error("Error putting file in database", e);
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
            String inputName = uploaded.getInputName();
            if ("docFile".equals(inputName)) {
                selected = uploaded;
                break;
            }
            if (selected == null && "filedata".equals(inputName)) {
                selected = uploaded;
            }
        }

        if (selected != null) {
            this.docFile = Path.of(selected.getAbsolutePath()).toFile();
            this.docFileFileName = selected.getOriginalName();
            this.docFileContentType = selected.getContentType();
        }
    }

    private boolean filled(String s) {
        return (s != null && s.trim().length() > 0);
    }

    static String buildDocumentReportRedirect(HttpServletRequest request) {
        StringBuilder redirect = new StringBuilder(safeContextPath(request.getContextPath()))
                .append(DOCUMENT_REPORT_PATH);
        appendQueryParam(redirect, "docerrors", "docerrors"); // Allows the JSP to check if the document was just submitted
        appendQueryParam(redirect, FUNCTION_PARAMETER, safeRedirectParameter(request.getParameter(FUNCTION_PARAMETER)));
        appendQueryParam(redirect, FUNCTION_ID_PARAMETER, safeRedirectParameter(request.getParameter(FUNCTION_ID_PARAMETER)));
        appendQueryParam(redirect, CURRENT_USER_PARAMETER, safeRedirectParameter(request.getParameter(CURRENT_USER_PARAMETER)));
        appendQueryParam(redirect, APPOINTMENT_NO_PARAMETER, safeRedirectParameter(request.getParameter(APPOINTMENT_NO_PARAMETER)));

        String parentAjaxId = safeRedirectParameter(request.getParameter(PARENT_AJAX_ID_PARAMETER));
        if (!parentAjaxId.isEmpty()) {
            appendQueryParam(redirect, PARENT_AJAX_ID_PARAMETER, parentAjaxId);
            appendQueryParam(redirect, "updateParent", "true");
        }
        return redirect.toString();
    }

    private static String safeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank()) {
            return "";
        }
        if (!contextPath.startsWith("/") || contextPath.startsWith("//")
                || contextPath.contains("\\") || contextPath.contains("\r") || contextPath.contains("\n")) {
            throw new SecurityException("Invalid context path");
        }
        return contextPath;
    }

    static String safeRedirectParameter(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_REDIRECT_PARAMETER_LENGTH
                || !SAFE_REDIRECT_PARAMETER.matcher(trimmed).matches()) {
            return "";
        }
        return trimmed;
    }

    static void appendQueryParam(StringBuilder redirect, String name, String value) {
        redirect.append(redirect.indexOf("?") == -1 ? "?" : "&");
        redirect.append(URLEncoder.encode(name, StandardCharsets.UTF_8));
        redirect.append("=");
        redirect.append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
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

    private File filedata;

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

    public void setDocFile(File docFile) {
        this.docFile = docFile;
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

    public File getFiledata() {
        return filedata;
    }

    public void setFiledata(File Filedata) {
        this.filedata = Filedata;
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
