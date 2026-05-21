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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.UploadedFileUtils;
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

public class DocumentUpload2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Set<String> DEFAULT_INCOMING_DOC_FOLDERS = Set.of("Fax", "Mail", "File", "Refile");
    private static final Set<String> ALLOWED_INCOMING_DOC_FOLDERS = getAllowedIncomingDocFolders();
    private static final String INVALID_INCOMING_DESTINATION_MESSAGE = "Invalid incoming document destination.";
    private static final String PREFERRED_QUEUE_SESSION_KEY = "preferredQueue";
    private static final String UNIQUE_DOCUMENT_FILENAME_ERROR =
            "Unable to create a unique document filename. Please try again.";
    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static Set<String> getAllowedIncomingDocFolders() {
        String configuredFolders = CarlosProperties.getInstance().getProperty("ALLOWED_INCOMING_DOC_FOLDERS");
        if (configuredFolders == null || configuredFolders.trim().isEmpty()) {
            return DEFAULT_INCOMING_DOC_FOLDERS;
        }

        Set<String> parsedFolders = new LinkedHashSet<>();
        Arrays.stream(configuredFolders.split(","))
                .map(String::trim)
                .filter(folder -> !folder.isEmpty())
                .forEach(parsedFolders::add);

        if (parsedFolders.isEmpty()) {
            return DEFAULT_INCOMING_DOC_FOLDERS;
        }
        return Collections.unmodifiableSet(parsedFolders);
    }

    public String execute() throws Exception {
        return executeUpload();
    }

    public String executeUpload() throws Exception {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        HashMap<String, Object> map = new HashMap<String, Object>();
        UploadedFile uploadedFile = this.filedataUpload;
        ValidatedUpload docFile = null;
        String destination = request.getParameter("destination");
        String queueId = normalizeIncomingParam(request.getParameter("queue"));
        String destFolder = normalizeIncomingParam(request.getParameter("destFolder"));
        ResourceBundle props = ResourceBundle.getBundle("oscarResources");
        if (uploadedFile == null) {
            map.put("error", 4);
        } else {
            try {
                docFile = ValidatedUpload.from(uploadedFile);
            } catch (FileValidationException e) {
                logger.error("Invalid upload source - potential path traversal: {}", LogSanitizer.sanitize(uploadedFile.getOriginalName()));
                map.put("error", "Invalid file upload");
            } catch (SecurityException e) {
                logger.error("Invalid upload source - potential path traversal: {}", LogSanitizer.sanitize(uploadedFile.getOriginalName()));
                map.put("error", "Invalid file upload");
            }
        }

        ValidatedUpload uploadedTempFile = docFile;
        try {
            if (docFile != null && "incomingDocs".equals(destination)) {
                String fileName = this.filedataFileName;
                if (fileName == null || fileName.trim().isEmpty()) {
                    map.put("error", PathValidationUtils.INVALID_FILENAME_MESSAGE);
                } else if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    map.put("error", props.getString("dms.documentUpload.onlyPdf"));
                } else if (docFile.length() == 0) {
                    map.put("error", 4);
                } else if (!isValidIncomingDestination(queueId, destFolder)) {
                    map.put("error", INVALID_INCOMING_DESTINATION_MESSAGE);
                } else {
                    try {
                        String sanitizedFileName = validateIncomingDocsFileName(fileName);
                        boolean success = writeToIncomingDocs(docFile, queueId, destFolder, sanitizedFileName);
                        if (!success) {
                            map.put("error", "Failed to write file. Please contact administrator");
                            MiscUtils.getLogger().error("Failed to write file to {}", LogSanitizer.sanitize(destFolder)); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
                        } else {
                            map.put("name", sanitizedFileName);
                            map.put("size", docFile.length());
                            storePreferredQueue(queueId);
                        }
                    } catch (FileValidationException e) {
                        map.put("error", e.getMessage());
                    } catch (IllegalArgumentException | SecurityException e) {
                        logger.warn("Rejected invalid incoming document destination");
                        map.put("error", INVALID_INCOMING_DESTINATION_MESSAGE);
                    }
                }
            } else if (docFile != null) {
                File copiedDocumentFile = null;
                boolean documentRecordCreated = false;
                try {
                    int numberOfPages = 0;
                    String fileName = PathValidationUtils.validateFileName(this.filedataFileName);
                    String user = (String) request.getSession().getAttribute("user");
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    EDoc newDoc = new EDoc("", "", fileName, "", user, user, this.getSource(), 'A',
                            simpleDateFormat.format(Calendar.getInstance().getTime()),
                            "", "", "demographic", "-1", 0);
                    newDoc.setDocPublic("0");

                    ProgramManager2 programManager = SpringUtils.getBean(ProgramManager2.class);
                    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
                    ProgramProvider pp = programManager.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
                    if (pp != null && pp.getProgramId() != null) {
                        newDoc.setProgramId(pp.getProgramId().intValue());
                    }

                    String fileNameInDoc = newDoc.getFileName();
                    if (docFile.length() == 0) {
                        map.put("error", 4);
                    } else {
                        copiedDocumentFile = writeLocalFile(docFile, fileNameInDoc);
                        fileNameInDoc = copiedDocumentFile.getName();
                        newDoc.setFileName(fileNameInDoc);
                        newDoc.setFilePath(copiedDocumentFile.getPath());
                        newDoc.setContentType(this.filedataContentType);
                        if (fileNameInDoc.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                            newDoc.setContentType("application/pdf");
                            numberOfPages = countNumOfPages(copiedDocumentFile.getPath());
                        }
                        newDoc.setNumberOfPages(numberOfPages);
                        String docNo = EDocUtil.addDocumentSQL(newDoc);
                        documentRecordCreated = true;
                        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_DOCUMENT, docNo, request.getRemoteAddr());

                        String providerId = request.getParameter("providers");
                        if (providerId != null) {
                            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
                            ProviderInboxRoutingDao providerInboxRoutingDao = (ProviderInboxRoutingDao) ctx.getBean(ProviderInboxRoutingDao.class);
                            providerInboxRoutingDao.addToProviderInbox(providerId, Integer.parseInt(docNo), "DOC");
                        }

                        String queueIdParam = request.getParameter("queue");
                        if (queueIdParam != null && !queueIdParam.equals("-1")) {
                            if (!queueIdParam.trim().matches("\\d+")) {
                                logger.warn("Invalid queue ID format — skipping queue link");
                                request.getSession().removeAttribute(PREFERRED_QUEUE_SESSION_KEY);
                            } else {
                                WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
                                QueueDocumentLinkDao queueDocumentLinkDAO = (QueueDocumentLinkDao) ctx.getBean(QueueDocumentLinkDao.class);
                                int qid = Integer.parseInt(queueIdParam.trim());
                                Integer did = Integer.parseInt(docNo.trim());
                                queueDocumentLinkDAO.addActiveQueueDocumentLink(qid, did);
                                storePreferredQueue(qid);
                            }
                        }

                        map.put("name", fileNameInDoc);
                        map.put("size", docFile.length());
                    }
                } catch (FileValidationException e) {
                    deleteCopiedDocumentIfUnpersisted(copiedDocumentFile, documentRecordCreated);
                    logger.warn("Rejected invalid document upload filename");
                    map.put("error", e.getMessage());
                } catch (UniqueDocumentFilenameException e) {
                    deleteCopiedDocumentIfUnpersisted(copiedDocumentFile, documentRecordCreated);
                    logger.warn("Unable to create unique document upload filename", e);
                    map.put("error", UNIQUE_DOCUMENT_FILENAME_ERROR);
                } catch (Exception e) {
                    deleteCopiedDocumentIfUnpersisted(copiedDocumentFile, documentRecordCreated);
                    throw e;
                }
            }
        } finally {
            deleteUploadedTempFile(uploadedTempFile);
        }
        ArrayNode jsonArray = objectMapper.createArrayNode();
        ObjectNode jsonObject = objectMapper.valueToTree(map);
        jsonArray.add(jsonObject);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getOutputStream(), jsonArray);
        return null;
    }

    private void deleteUploadedTempFile(ValidatedUpload uploadedFile) {
        if (uploadedFile == null) {
            return;
        }

        if (!uploadedFile.delete()) {
            logger.warn("Uploaded temp file was already removed or could not be deleted");
        }
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
    private File writeLocalFile(ValidatedUpload docFile, String fileName) throws Exception {
        String documentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (!documentDir.endsWith(File.separator)) {
            documentDir += File.separator;
        }
        File baseDir = new File(documentDir);
        IOException lastCollision = null;

        for (int attempt = 0; attempt < PathValidationUtils.MAX_UPLOAD_FILENAME_COLLISION_RETRIES; attempt++) {
            File destinationFile = PathValidationUtils.validatePath(
                    fileNameWithCollisionSuffix(fileName, attempt), baseDir);
            boolean fileCreatedByRequest = false;
            boolean writeSucceeded = false;

            try {
                // codeql[java/path-injection] -- destinationFile is validated under DOCUMENT_DIR.
                try (OutputStream fos = Files.newOutputStream(destinationFile.toPath(),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    fileCreatedByRequest = true;
                    try (InputStream fis = docFile.openStream()) {
                        byte[] buf = new byte[128 * 1024];
                        int i = 0;
                        while ((i = fis.read(buf)) != -1) {
                            fos.write(buf, 0, i);
                        }
                    }
                }
                writeSucceeded = true;
                return destinationFile;
            } catch (FileAlreadyExistsException e) {
                lastCollision = e;
            } catch (Exception e) {
                logger.error("Error writing local file", e);
                throw e;
            } finally {
                if (!writeSucceeded && fileCreatedByRequest) {
                    deleteCopiedDocumentIfUnpersisted(destinationFile, false);
                }
            }
        }

        throw new UniqueDocumentFilenameException(UNIQUE_DOCUMENT_FILENAME_ERROR, lastCollision);
    }

    private String fileNameWithCollisionSuffix(String fileName, int attempt) {
        if (attempt == 0) {
            return fileName;
        }

        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            return fileName.substring(0, extensionIndex) + "_" + attempt + fileName.substring(extensionIndex);
        }
        return fileName + "_" + attempt;
    }

    private static final class UniqueDocumentFilenameException extends IOException {
        private UniqueDocumentFilenameException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ValidatedUpload {
        private final UploadedFile upload;
        private final File file;

        private ValidatedUpload(UploadedFile upload, File file) {
            this.upload = upload;
            this.file = file;
        }

        private static ValidatedUpload from(UploadedFile upload) {
            File validatedUpload = PathValidationUtils.validateUpload(UploadedFileUtils.getUploadedFile(upload));
            return new ValidatedUpload(upload, validatedUpload);
        }

        private long length() {
            return file.length();
        }

        private InputStream openStream() throws IOException {
            return Files.newInputStream(file.toPath());
        }

        private boolean delete() {
            return upload.delete();
        }
    }

    private void deleteCopiedDocumentIfUnpersisted(File copiedDocumentFile, boolean documentRecordCreated) {
        if (documentRecordCreated || copiedDocumentFile == null || !copiedDocumentFile.exists()) {
            return;
        }

        try {
            Files.delete(copiedDocumentFile.toPath());
        } catch (IOException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Unable to delete unpersisted document file: {}", LogSanitizer.sanitize(copiedDocumentFile.getPath()), e);
            }
        }
    }

    private boolean writeToIncomingDocs(ValidatedUpload docFile, String queueId, String PdfDir, String fileName) {
        if (queueId == null || PdfDir == null || fileName == null) {
            logger.error("Invalid parameters provided for writeToIncomingDocs");
            return false;
        }

        // Check direct path separators; PathValidationUtils handles dot-only and hidden names.
        if (fileName.contains("/") || fileName.contains("\\")) {
            logger.error("Filename contains invalid path characters");
            return false;
        }

        // Create directory structure and get validated parent path
        String parentPath = IncomingDocUtil.getAndCreateIncomingDocumentFilePath(queueId, PdfDir);
        File parentDir = new File(parentPath);
        if (!parentDir.exists()) {
            return false;
        }

        // Use PathValidationUtils to construct and validate the destination file path
        // (sanitizes fileName, rejects traversal, ensures result is within parentDir).
        File destinationFile;
        try {
            destinationFile = PathValidationUtils.validatePath(fileName, parentDir);
        } catch (SecurityException e) {
            logger.error("Destination file is outside allowed directory: {}", LogSafe.sanitize(fileName));
            return false;
        }

        try (InputStream fis = docFile.openStream();
                // codeql[java/path-injection] -- destinationFile is validated under the incoming-docs directory before this sink.
                OutputStream fos = Files.newOutputStream(destinationFile.toPath(),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            IOUtils.copy(fis, fos);
        } catch (IOException e) {
            logger.error("Error writing file to incoming docs", e);
            return false;
        }

        return true;
    }

    /**
     * Validates a filename for use in incoming documents to prevent path traversal attacks.
     *
     * @param fileName the original filename
     * @return sanitized filename safe for filesystem operations
     */
    private String validateIncomingDocsFileName(String fileName) {
        String baseName = PathValidationUtils.validateFileName(fileName);

        if (!baseName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new FileValidationException(PathValidationUtils.INVALID_FILENAME_MESSAGE);
        }
        return baseName;
    }

    private boolean isValidIncomingDestination(String queueId, String destFolder) {
        return queueId != null
                && queueId.trim().matches("\\d+")
                && ALLOWED_INCOMING_DOC_FOLDERS.contains(destFolder);
    }

    private String normalizeIncomingParam(String value) {
        return value == null ? null : value.trim();
    }

    private void storePreferredQueue(String queueId) {
        if (queueId == null) {
            return;
        }
        try {
            storePreferredQueue(Integer.parseInt(queueId.trim()));
        } catch (NumberFormatException e) {
            // Do not store an invalid queue ID in the session.
            logger.warn("Invalid queue ID format - skipping session attribute update");
        }
    }

    private void storePreferredQueue(int queueId) {
        PreferredQueueId preferredQueueId = PreferredQueueId.from(queueId);
        request.getSession().setAttribute(PREFERRED_QUEUE_SESSION_KEY, preferredQueueId.sessionValue());
    }

    private static final class PreferredQueueId {
        private final int value;

        private PreferredQueueId(int value) {
            this.value = value;
        }

        private static PreferredQueueId from(int queueId) {
            return new PreferredQueueId(queueId);
        }

        private String sessionValue() {
            return String.valueOf(value);
        }
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
        String destFolder = normalizeIncomingParam(request.getParameter("destFolder"));
        if (destFolder == null || !ALLOWED_INCOMING_DOC_FOLDERS.contains(destFolder)) {
            logger.warn("Rejected invalid incoming document folder update");
            return null;
        }
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
    private UploadedFile filedataUpload;
    private String filedataFileName;
    private String filedataContentType;

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null) {
            for (UploadedFile uploaded : uploadedFiles) {
                String inputName = uploaded.getInputName();
                if ("filedata".equals(inputName)) {
                    this.filedataUpload = uploaded;
                    this.filedataContentType = uploaded.getContentType();
                    this.filedataFileName = uploaded.getOriginalName();
                    return;
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
        return UploadedFileUtils.getUploadedFileOrNull(filedataUpload);
    }

    public String getFiledataFileName() {
        return filedataFileName;
    }

    public void setFiledataFileName(String filedataFileName) {
        this.filedataFileName = filedataFileName;
    }

    public String getFiledataContentType() {
        return filedataContentType;
    }

    public void setFiledataContentType(String filedataContentType) {
        this.filedataContentType = filedataContentType;
    }
}
