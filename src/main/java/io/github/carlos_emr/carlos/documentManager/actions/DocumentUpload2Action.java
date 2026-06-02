/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.documentManager.actions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.IncomingDocUtil;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import java.util.List;
import io.github.carlos_emr.carlos.utility.LogSafe;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class DocumentUpload2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        return executeUpload();
    }

    // FindSecBugs IMPROPER_UNICODE: case-fold in a trust path; locale-safe hardening tracked in #2496. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold in a trust path; locale-safe hardening tracked in #2496")
    public String executeUpload() throws Exception {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        HashMap<String, Object> map = new HashMap<String, Object>();
        File docFile = this.getFiledata();
        String destination = request.getParameter("destination");
        ResourceBundle props = ResourceBundle.getBundle("oscarResources");
        if (docFile == null) {
            map.put("error", 4);
        } else {
            // Validate uploaded file is from temp directory for all destinations
            try {
                docFile = PathValidationUtils.validateUpload(docFile);
            } catch (SecurityException e) {
                logger.error("Invalid upload source - potential path traversal: {}", LogSafe.sanitize(docFile.getPath()));
                map.put("error", "Invalid file upload");
                docFile = null; // Treat as if no file was uploaded
            }
        }

        if (docFile != null && destination != null && destination.equals("incomingDocs")) {
            String fileName = this.filedataFileName;
            String sanitizedFileName = sanitizeFileNameForIncomingDocs(fileName);
            if (sanitizedFileName == null) {
                map.put("error", props.getString("dms.error.invalidFilename"));
            } else if (!sanitizedFileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                map.put("error", props.getString("dms.documentUpload.onlyPdf"));
            } else if (docFile.length() == 0) {
                map.put("error", 4);
                throw new FileNotFoundException();
            } else {
                String queueId = request.getParameter("queue");
                String destFolder = request.getParameter("destFolder");

                File incomingDir = PathValidationUtils.resolveConfiguredDirectory(IncomingDocUtil.getAndCreateIncomingDocumentFilePath(queueId, destFolder), "incoming document directory");
                File destinationFile = PathValidationUtils.validateGeneratedChildPath(sanitizedFileName, incomingDir);
                WriteToIncomingDocsResult writeResult = writeToIncomingDocs(docFile, destinationFile);
                if (writeResult == WriteToIncomingDocsResult.ALREADY_EXISTS) {
                    map.put("error", sanitizedFileName + " " + props.getString("dms.documentUpload.alreadyExists"));
                } else if (writeResult == WriteToIncomingDocsResult.FAILED) {
                    map.put("error", "Failed to write file. Please contact administrator");
                    MiscUtils.getLogger().error("Failed to write file to {}", LogSafe.sanitize(destFolder)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                } else {
                    map.put("name", docFile.getName());
                    map.put("size", docFile.length());
                }

                if (queueId != null) {
                    try {
                        request.getSession().setAttribute("preferredQueue", String.valueOf(Integer.parseInt(queueId.trim()))); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
                    } catch (NumberFormatException e) {
                        // Do not store an invalid (non-integer) queue ID in the session (trust boundary protection)
                        logger.warn("Invalid queue ID format — skipping session attribute update");
                    }
                }
                if (docFile != null) {
                    docFile.delete();
                    docFile = null;
                }

            }
        } else if (docFile != null) {
            int numberOfPages = 0;
            String fileName;
            try {
                fileName = PathValidationUtils.validateFileName(this.filedataFileName);
            } catch (FileValidationException e) {
                logger.warn("Rejected invalid document upload filename");
                map.put("error", props.getString("dms.error.invalidFilename"));
                writeUploadResponse(map);
                return null;
            }
            String user = (String) request.getSession().getAttribute("user");
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            EDoc newDoc = new EDoc("", "", fileName, "", user, user, this.getSource(), 'A',
                    simpleDateFormat.format(Calendar.getInstance().getTime()),
                    "", "", "demographic", "-1", 0);
            newDoc.setDocPublic("0");

            // if the document was added in the context of a program
            ProgramManager2 programManager = SpringUtils.getBean(ProgramManager2.class);
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            ProgramProvider pp = programManager.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
            if (pp != null && pp.getProgramId() != null) {
                newDoc.setProgramId(pp.getProgramId().intValue());
            }

            fileName = newDoc.getFileName();
            String filePath = newDoc.getFilePath();
            // save local file;
            if (docFile.length() == 0) {
                map.put("error", 4);
                throw new FileNotFoundException();
            }

            // write file to local dir
            writeLocalFile(docFile, fileName);
            newDoc.setContentType(this.filedataContentType);
            if (fileName.endsWith(".PDF") || fileName.endsWith(".pdf")) {
                newDoc.setContentType("application/pdf");
                // get number of pages when document is a PDF
                numberOfPages = countNumOfPages(filePath);
            }
            newDoc.setNumberOfPages(numberOfPages);
            String doc_no = EDocUtil.addDocumentSQL(newDoc);
            LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_DOCUMENT, doc_no, request.getRemoteAddr());

            String providerId = request.getParameter("providers");
            if (providerId != null) {
                WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
                ProviderInboxRoutingDao providerInboxRoutingDao = (ProviderInboxRoutingDao) ctx.getBean(ProviderInboxRoutingDao.class);
                providerInboxRoutingDao.addToProviderInbox(providerId, Integer.parseInt(doc_no), "DOC");
            }

            String queueId = request.getParameter("queue");
            if (queueId != null && !queueId.equals("-1")) {
                if (!queueId.trim().matches("\\d+")) {
                    logger.warn("Invalid queue ID format — skipping queue link");
                    request.getSession().removeAttribute("preferredQueue");
                } else {
                    WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
                    QueueDocumentLinkDao queueDocumentLinkDAO = (QueueDocumentLinkDao) ctx.getBean(QueueDocumentLinkDao.class);
                    Integer qid = Integer.parseInt(queueId.trim());
                    Integer did = Integer.parseInt(doc_no.trim());
                    queueDocumentLinkDAO.addActiveQueueDocumentLink(qid, did);
                    request.getSession().setAttribute("preferredQueue", String.valueOf(qid)); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
                }
            }

            map.put("name", docFile.getName());
            map.put("size", docFile.length());

            if (docFile != null) {
                docFile.delete();
                docFile = null;
            }
        }
        writeUploadResponse(map);
        return null;
    }

    private void writeUploadResponse(HashMap<String, Object> map) throws IOException {
        ArrayNode jsonArray = objectMapper.createArrayNode();
        ObjectNode jsonObject = objectMapper.valueToTree(map);
        jsonArray.add(jsonObject);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getOutputStream(), jsonArray);
    }

    /**
     * Counts the number of pages in a local pdf file.
     *
     * @param fileName the name of the file
     * @return the number of pages in the file
     */
    public int countNumOfPages(String fileName) {
        return EDocUtil.getPDFPageCount(fileName);
    }

    /**
     * Writes an uploaded file to disk with canonical path validation
     *
     * @param docFile  the uploaded file
     * @param fileName the name for the file on disk
     * @throws Exception when an error occurs
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private void writeLocalFile(File docFile, String fileName) throws Exception {
        InputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = Files.newInputStream(docFile.toPath());
            String documentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
            if (!documentDir.endsWith(File.separator)) {
                documentDir += File.separator;
            }
            // Validate the destination path using PathValidationUtils
            File baseDir = new File(documentDir);
            File destinationFile = PathValidationUtils.validatePath(fileName, baseDir);

            fos = new FileOutputStream(destinationFile);
            byte[] buf = new byte[128 * 1024];
            int i = 0;
            while ((i = fis.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
        } catch (Exception e) {
            logger.error("Error writing local file", e);
            throw e;
        } finally {
            if (fis != null)
                fis.close();
            if (fos != null)
                fos.close();
        }
    }

    private WriteToIncomingDocsResult writeToIncomingDocs(File docFile, File destinationFile) {
        if (docFile == null || destinationFile == null) {
            logger.error("Invalid parameters provided for writeToIncomingDocs");
            return WriteToIncomingDocsResult.FAILED;
        }

        try {
            // validateUpload throws SecurityException if the source escaped the allowed temp dir; keep it
            // inside the handled block so this returns FAILED rather than escaping as an unhandled 500.
            File validatedDocFile = PathValidationUtils.validateUpload(docFile);
            try (InputStream fis = Files.newInputStream(validatedDocFile.toPath());
                    OutputStream fos = Files.newOutputStream(destinationFile.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                IOUtils.copy(fis, fos);
            }
        } catch (FileAlreadyExistsException e) {
            return WriteToIncomingDocsResult.ALREADY_EXISTS;
        } catch (IOException | SecurityException e) {
            logger.error("Error writing file to incoming docs", e);
            return WriteToIncomingDocsResult.FAILED;
        }

        return WriteToIncomingDocsResult.SUCCESS;
    }

    private enum WriteToIncomingDocsResult {
        SUCCESS,
        ALREADY_EXISTS,
        FAILED
    }

    /**
     * Sanitizes a filename for use in incoming documents to prevent path traversal attacks.
     * Uses Apache Commons IO FilenameUtils for robust path traversal prevention.
     *
     * @param fileName the original filename
     * @return sanitized filename safe for filesystem operations
     */
    private String sanitizeFileNameForIncomingDocs(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }

        String baseName;
        try {
            baseName = FilenameUtils.getName(fileName);
        } catch (IllegalArgumentException e) {
            return null;
        }

        // Ensure baseName doesn't contain any path separators
        if (baseName.contains("/") || baseName.contains("\\") || baseName.contains("..")) {
            return null;
        }

        // Reject filenames starting with . (hidden files)
        if (baseName.startsWith(".")) {
            return null;
        }

        // Ensure baseName is not empty
        if (baseName.trim().isEmpty()) {
            return null;
        }

        return baseName;
    }

    public String setUploadDestination() {

        String user_no = (String) request.getSession().getAttribute("user");
        String destination = request.getParameter("destination");
        UserPropertyDAO pref = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty up = pref.getProp(user_no, UserProperty.UPLOAD_DOCUMENT_DESTINATION);

        if (up == null) {
            up = new UserProperty();
            up.setName(UserProperty.UPLOAD_DOCUMENT_DESTINATION);
            up.setProviderNo(user_no);
        }

        if (up.getValue() == null || !(up.getValue().equals(destination))) {
            up.setValue(destination);
            pref.saveProp(up);
        }
        return null;
    }

    public String setUploadIncomingDocumentFolder() {


        String user_no = (String) request.getSession().getAttribute("user");
        String destFolder = request.getParameter("destFolder");
        UserPropertyDAO pref = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty up = pref.getProp(user_no, UserProperty.UPLOAD_INCOMING_DOCUMENT_FOLDER);

        if (up == null) {
            up = new UserProperty();
            up.setName(UserProperty.UPLOAD_INCOMING_DOCUMENT_FOLDER);
            up.setProviderNo(user_no);
        }

        if (up.getValue() == null || !(up.getValue().equals(destFolder))) {
            up.setValue(destFolder);
            pref.saveProp(up);
        }
        return null;
    }

    private String function = "";
    private String functionId = "";
    private String docType = "";
    private String docDesc = "";
    private String docCreator = "";
    private String responsibleId = "";
    private String source = "";
    private File docFile;

    private File filedata;
    private String filedataFileName;
    private String filedataContentType;

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null) {
            for (UploadedFile uploaded : uploadedFiles) {
                String inputName = uploaded.getInputName();
                if ("filedata".equals(inputName)) {
                    // Validation runs during Struts binding (before execute()). On rejection leave the
                    // field null instead of throwing: executeUpload() already maps a null upload to a
                    // graceful error, so a raw SecurityException here would otherwise bypass it as an
                    // unhandled 500 to the AJAX uploader.
                    try {
                        this.filedata = PathValidationUtils.validateUploadContent(uploaded.getContent());
                    } catch (SecurityException e) {
                        logger.warn("Rejected invalid upload content for field filedata");
                        this.filedata = null;
                    }
                    this.filedataContentType = uploaded.getContentType();
                    this.filedataFileName = uploaded.getOriginalName();
                } else if ("docFile".equals(inputName)) {
                    try {
                        this.docFile = PathValidationUtils.validateUploadContent(uploaded.getContent());
                    } catch (SecurityException e) {
                        logger.warn("Rejected invalid upload content for field docFile");
                        this.docFile = null;
                    }
                }
            }
        }
    }

    private String docPublic = "";
    private String mode = "";
    private String observationDate = "";
    private String reviewerId = "";
    private String reviewDateTime = "";
    private boolean reviewDoc = false;
    private String html = "";

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

    public File getDocFile() {
        return docFile;
    }

    @StrutsParameter
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

    @StrutsParameter
    public void setFiledata(File Filedata) {
        this.filedata = Filedata;
    }

    public String getFiledataFileName() {
        return filedataFileName;
    }

    @StrutsParameter
    public void setFiledataFileName(String filedataFileName) {
        this.filedataFileName = filedataFileName;
    }

    public String getFiledataContentType() {
        return filedataContentType;
    }

    @StrutsParameter
    public void setFiledataContentType(String filedataContentType) {
        this.filedataContentType = filedataContentType;
    }
}
