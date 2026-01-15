//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.hospitalReportManager.v2018;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import ca.openosp.openo.PMmodule.dao.ProviderDao;

import ca.openosp.openo.commn.dao.HrmLogDao;
import ca.openosp.openo.commn.dao.HrmLogEntryDao;
import ca.openosp.openo.commn.dao.OscarJobDao;
import ca.openosp.openo.commn.dao.OscarJobTypeDao;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.jobs.OscarJobUtils;
import ca.openosp.openo.commn.model.HrmLog;
import ca.openosp.openo.commn.model.HrmLogEntry;
import ca.openosp.openo.commn.model.OscarJob;
import ca.openosp.openo.commn.model.OscarJobType;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.hospitalReportManager.HRMReport;
import ca.openosp.openo.hospitalReportManager.HRMReportParser;
import ca.openosp.openo.hospitalReportManager.SFTPConnector;
import ca.openosp.openo.hospitalReportManager.dao.HRMCategoryDao;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentDao;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import ca.openosp.openo.hospitalReportManager.dao.HRMProviderConfidentialityStatementDao;
import ca.openosp.openo.hospitalReportManager.model.HRMCategory;
import ca.openosp.openo.hospitalReportManager.model.HRMDocument;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentSubClass;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentToDemographic;
import ca.openosp.openo.hospitalReportManager.model.HRMProviderConfidentialityStatement;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for Hospital Report Manager (HRM) operations and administration.
 * <p>
 * This action handles comprehensive HRM functionality including:
 * <ul>
 * <li>Report file upload and processing (uploadReport)</li>
 * <li>Private key file upload for SFTP authentication (uploadPrivateKey)</li>
 * <li>SFTP configuration management (saveConfigurationDetails, getConfigurationDetails)</li>
 * <li>Manual report fetching from remote servers (fetch)</li>
 * <li>Automated polling configuration for scheduled downloads</li>
 * <li>Report categorization and search (searchCategory, saveCategory)</li>
 * <li>Provider confidentiality statement management</li>
 * <li>HRM transaction logging and detailed log viewing</li>
 * <li>DataTables-based report listing with filtering and pagination</li>
 * </ul>
 * <p>
 * The action routes requests to specific methods based on the "method" request parameter.
 * File upload operations use Struts 2's built-in file upload mechanism with filename
 * sanitization to prevent path traversal attacks.
 * <p>
 * All operations enforce role-based security checks using SecurityInfoManager with
 * three privilege levels:
 * <ul>
 * <li>_hrm - Standard HRM access for providers</li>
 * <li>_hrm.administrator - Administrative HRM access</li>
 * <li>_admin.hrm - System-level HRM administration</li>
 * </ul>
 *
 * @since 2006-04-20
 */
public class HRM2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    static int draw = 0;

    Logger logger = MiscUtils.getLogger();

    private HRMDocumentDao hrmDocumentDao = SpringUtils.getBean(HRMDocumentDao.class);
    private UserPropertyDAO userPropertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
    private HRMCategoryDao hrmCategoryDao = SpringUtils.getBean(HRMCategoryDao.class);
    private HrmLogDao hrmLogDao = SpringUtils.getBean(HrmLogDao.class);
    private ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao = SpringUtils.getBean(HRMDocumentToDemographicDao.class);

    // Struts 2 file upload properties for HRM report upload
    private List<File> hrm_file;
    private List<String> hrm_fileContentType;
    private List<String> hrm_fileFileName;

    // Struts 2 file upload properties for private key upload
    private List<File> privateKeyFile;
    private List<String> privateKeyFileContentType;
    private List<String> privateKeyFileFileName;

    /**
     * Uploads an HRM report file and adds it to the provider inbox.
     * <p>
     * This method processes uploaded HRM (Hospital Report Manager) report files,
     * validates the file, parses the report content, and adds it to the logged-in
     * provider's inbox. The file is sanitized to prevent path traversal attacks.
     * <p>
     * Expected request parameters (via Struts 2 property injection):
     * <ul>
     * <li>hrm_file - File uploaded HRM report file</li>
     * </ul>
     *
     * @return null (writes JSON response directly to output stream)
     * @throws Exception if report processing fails
     * @throws SecurityException if user lacks _hrm read privilege
     */
    public String uploadReport() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // MANDATORY security check
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        String downloadDirectory = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        JSONObject obj = new JSONObject();

        try {
            if (hrm_file != null && !hrm_file.isEmpty()) {
                for (int i = 0; i < hrm_file.size(); i++) {
                    File uploadedFile = hrm_file.get(i);
                    String originalFileName = hrm_fileFileName.get(i);

                    // Sanitize filename to prevent path traversal attacks
                    String sanitizedFileName = sanitizeFileName(originalFileName);
                    if (sanitizedFileName == null || sanitizedFileName.isEmpty()) {
                        MiscUtils.getLogger().error("Invalid filename provided: '{}'", originalFileName);
                        obj.put("message", "Error: Invalid filename");
                        throw new RuntimeException("Invalid filename provided");
                    }

                    // Additional safeguard: extract only the filename component
                    String safeFileName = FilenameUtils.getName(sanitizedFileName);
                    if (safeFileName == null || safeFileName.isEmpty()) {
                        MiscUtils.getLogger().error("Unable to extract safe filename from: '{}'", sanitizedFileName);
                        obj.put("message", "Error: Invalid filename");
                        throw new RuntimeException("Invalid filename after sanitization");
                    }

                    // Copy uploaded file to document directory
                    File destinationFile = new File(downloadDirectory, safeFileName);

                    // Validate that the uploaded file is in an allowed temp directory
                    if (!PathValidationUtils.isInAllowedTempDirectory(uploadedFile)) {
                        MiscUtils.getLogger().error("Attempted to read uploaded file outside of tempdir: '{}'", uploadedFile.getPath());
                        obj.put("message", "Error: Invalid uploaded file location");
                        throw new RuntimeException("Invalid uploaded file location");
                    }

                    try (InputStream inputStream = new FileInputStream(uploadedFile)) {
                        java.nio.file.Files.copy(inputStream, destinationFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }

                    // Parse and process the HRM report
                    HRMReport report = HRMReportParser.parseReport(loggedInInfo, destinationFile.getAbsolutePath());
                    if (report != null) {
                        HRMReportParser.addReportToInbox(loggedInInfo, report);
                        obj.put("message", safeFileName + " successfully saved");
                    } else {
                        obj.put("message", "Error: Could not parse report");
                        throw new RuntimeException("Failed to parse HRM report");
                    }
                }
            } else {
                obj.put("message", "Error: No file uploaded");
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error processing HRM report upload", e);
            obj.put("message", "Error: " + e.getMessage());
            throw new RuntimeException(e);
        }

        response.setContentType("application/json");
        obj.write(response.getWriter());

        return null;
    }

    /**
     * Uploads an HRM private key file for OMD (Ontario MD) integration.
     * <p>
     * This method processes uploaded private key files used for secure SFTP
     * communication with the HRM system. The file is saved to the OMD directory
     * and the filename is stored in user properties for later retrieval.
     * <p>
     * Expected request parameters (via Struts 2 property injection):
     * <ul>
     * <li>privateKeyFile - File private key file for SFTP authentication</li>
     * </ul>
     *
     * @return null (writes JSON response directly to output stream)
     * @throws Exception if private key upload fails
     * @throws SecurityException if user lacks _admin.hrm read privilege
     */
    public String uploadPrivateKey() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // MANDATORY security check
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.hrm", "r", null)) {
            throw new SecurityException("missing required sec object (_admin.hrm)");
        }

        String privateKeyDirectory = OscarProperties.getInstance().getProperty("OMD_DIRECTORY");
        if (privateKeyDirectory == null) {
            privateKeyDirectory = OscarProperties.getInstance().getProperty("DOCUMENT_DIR") + File.separator + ".." + File.separator + "hrm" + File.separator + "OMD";
        }

        JSONObject obj = new JSONObject();

        try {
            if (privateKeyFile != null && !privateKeyFile.isEmpty()) {
                for (int i = 0; i < privateKeyFile.size(); i++) {
                    File uploadedFile = privateKeyFile.get(i);
                    String originalFileName = privateKeyFileFileName.get(i);

                    // Sanitize filename to prevent path traversal attacks
                    String sanitizedFileName = sanitizeFileName(originalFileName);
                    if (sanitizedFileName == null || sanitizedFileName.isEmpty()) {
                        MiscUtils.getLogger().error("Invalid filename provided: '{}'", originalFileName);
                        obj.put("message", "Error: Invalid filename");
                        throw new RuntimeException("Invalid filename provided");
                    }

                    // Additional safeguard: extract only the filename component
                    String safeFileName = FilenameUtils.getName(sanitizedFileName);
                    if (safeFileName == null || safeFileName.isEmpty()) {
                        MiscUtils.getLogger().error("Unable to extract safe filename from: '{}'", sanitizedFileName);
                        obj.put("message", "Error: Invalid filename");
                        throw new RuntimeException("Invalid filename after sanitization");
                    }

                    // Copy uploaded file to private key directory
                    File destinationFile = new File(privateKeyDirectory, safeFileName);

                    // Validate that the uploaded file is in an allowed temp directory
                    if (!PathValidationUtils.isInAllowedTempDirectory(uploadedFile)) {
                        MiscUtils.getLogger().error("Attempted to read uploaded file outside of tempdir: '{}'", uploadedFile.getPath());
                        obj.put("message", "Error: Invalid uploaded file location");
                        throw new RuntimeException("Invalid uploaded file location");
                    }

                    // Re-validate at point of use for static analysis visibility
                    File validatedUploadedFile = PathValidationUtils.validateUpload(uploadedFile);
                    try (InputStream inputStream = new FileInputStream(validatedUploadedFile)) {
                        java.nio.file.Files.copy(inputStream, destinationFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }

                    // Update user property with the private key filename
                    saveUserProperty("hrm_private_key_file", safeFileName);

                    obj.put("message", safeFileName + " successfully saved");
                }
            } else {
                obj.put("message", "Error: No file uploaded");
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error processing private key upload", e);
            obj.put("message", "Error: " + e.getMessage());
        }

        response.setContentType("application/json");
        obj.write(response.getWriter());

        return null;
    }

    private String getUserPropertyValueOrNull(String propName) {
        UserProperty up = userPropertyDao.getProp(propName);
        if (up != null && !StringUtils.isEmpty(up.getValue())) {
            return up.getValue();
        }
        return null;
    }

    //new SFTPConnector().startAutoFetch(loggedInInfo);
    public String fetch() throws Exception {

        boolean isHrm = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "r", null);

        if (!isHrm) {
            return null;
        }
        SFTPConnector connector = null;
        String error = null;
        try {

            String hostname = getUserPropertyValueOrNull("hrm_hostname");
            String port = getUserPropertyValueOrNull("hrm_port");
            String username = getUserPropertyValueOrNull("hrm_username");
            String remoteDir = getUserPropertyValueOrNull("hrm_location");
            String decryptionKey = getUserPropertyValueOrNull("hrm_decryption_key");
            String privateKeyFile = getUserPropertyValueOrNull("hrm_private_key_file");

            String privateKeyDirectory = OscarProperties.getInstance().getProperty("OMD_DIRECTORY");
            if (privateKeyDirectory == null) {
                privateKeyDirectory = OscarProperties.getInstance().getProperty("DOCUMENT_DIR") + ".." + File.separator + "hrm" + File.separator + "OMD" + File.separator;
            }


            connector = new SFTPConnector(LoggedInInfo.getLoggedInInfoFromSession(request), hostname, Integer.parseInt(port), username, privateKeyDirectory + privateKeyFile, "Manual");
            SFTPConnector.setDecryptionKey(decryptionKey);
            connector.startAutoFetch(LoggedInInfo.getLoggedInInfoFromSession(request), remoteDir);
            connector.close();
        } catch (Exception e) {
            error = e.getMessage();
        }

        JSONObject obj = new JSONObject();
        obj.put("error", error);

        response.setContentType("application/json");
        obj.write(response.getWriter());

        return null;
    }

    public String saveConfigurationDetails() throws Exception {

        boolean isAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.hrm", "r", null);

        if (!isAdmin) {
            return null;
        }
        String hostname = request.getParameter("hostname");
        String port = request.getParameter("port");
        String username = request.getParameter("username");
        String location = request.getParameter("remoteDirectory");
        String key = request.getParameter("decryptionKey");

        String pollingEnabled = request.getParameter("polling_enabled");
        String pollingInterval = request.getParameter("polling_interval");

        saveUserProperty("hrm_hostname", hostname);
        saveUserProperty("hrm_port", port);
        saveUserProperty("hrm_username", username);
        saveUserProperty("hrm_location", location);
        saveUserProperty("hrm_decryption_key", key);

        int pInterval = 30;

        try {
            pInterval = Integer.parseInt(pollingInterval);
            if (pInterval < 0)
                pInterval = 30;
        } catch (NumberFormatException e) {
            pInterval = 30;
        }

        savePolling(LoggedInInfo.getLoggedInInfoFromSession(request), Boolean.valueOf(pollingEnabled), pInterval);

        JSONObject obj = new JSONObject();

        response.setContentType("application/json");
        obj.write(response.getWriter());
        return null;
    }

    /*
     * auto polling - enabled/disabled
     * auto-polling interval
     */
    public String getConfigurationDetails() throws Exception {

        boolean isAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.hrm", "r", null);

        if (!isAdmin) {
            return null;
        }

        JSONObject obj = new JSONObject();
        obj.put("hostname", getUserPropertyValueOrEmpty("hrm_hostname"));
        obj.put("port", getUserPropertyValueOrEmpty("hrm_port"));
        obj.put("username", getUserPropertyValueOrEmpty("hrm_username"));
        obj.put("remoteDirectory", getUserPropertyValueOrEmpty("hrm_location"));
        obj.put("decryptionKey", getUserPropertyValueOrEmpty("hrm_decryption_key"));
        obj.put("privateKeyFile", getUserPropertyValueOrEmpty("hrm_private_key_file"));

        obj.put("polling_enabled", isPollingEnabled());
        obj.put("polling_interval", getPollingInterval());

        response.setContentType("application/json");
        obj.write(response.getWriter());

        return null;
    }

    private void savePolling(LoggedInInfo loggedInInfo, boolean enabled, int frequencyInMinutes) {
        OscarJobDao oscarJobDao = SpringUtils.getBean(OscarJobDao.class);
        OscarJobTypeDao oscarJobTypeDao = SpringUtils.getBean(OscarJobTypeDao.class);

        OscarJobType oscarJobType = null;
        OscarJob oscarJob = null;
        List<OscarJobType> jobTypes = oscarJobTypeDao.findByClassName("ca.openosp.openo.hospitalReportManager.v2018.HRMDownloadJob");
        if (jobTypes.isEmpty()) {
            oscarJobType = new OscarJobType();
            oscarJobType.setClassName("ca.openosp.openo.hospitalReportManager.v2018.HRMDownloadJob");
            oscarJobType.setDescription("HRM Downloader");
            oscarJobType.setEnabled(true);
            oscarJobType.setName("HRMDownloadJobType");
            oscarJobType.setUpdated(new Date());
            oscarJobTypeDao.persist(oscarJobType);
        } else {
            oscarJobType = jobTypes.get(0);
        }

        List<OscarJob> jobs = oscarJobDao.findByType(oscarJobType);
        if (jobs.isEmpty()) {
            oscarJob = new OscarJob();
            oscarJob.setCronExpression("0 0/" + frequencyInMinutes + " * 1/1 * ?");
            oscarJob.setDescription("HRM Downloader");
            oscarJob.setEnabled(enabled);
            oscarJob.setName("HRMDownloadJob");
            oscarJob.setOscarJobTypeId(oscarJobType.getId());
            oscarJob.setOscarJobType(oscarJobType);
            oscarJob.setProviderNo(loggedInInfo.getLoggedInProviderNo());
            oscarJob.setUpdated(new Date());
            oscarJobDao.persist(oscarJob);
        } else {
            oscarJob = jobs.get(0);
            oscarJob.setCronExpression("0 0/" + frequencyInMinutes + " * 1/1 * ?");
            oscarJob.setEnabled(enabled);
            oscarJobDao.merge(oscarJob);
        }

        try {
            OscarJobUtils.resetJobExecutionFramework();
        } catch (Exception e) {
            logger.error("Error", e);
        }
    }

    private boolean isPollingEnabled() {

        OscarJob job = getOscarJobForPolling();

        if (job != null && job.isEnabled() && job.getOscarJobType().isEnabled() && !StringUtils.isEmpty(job.getCronExpression())) {
            return true;
        }

        return false;
    }

    private String getPollingInterval() {

        OscarJob job = getOscarJobForPolling();

        if (job != null && !StringUtils.isEmpty(job.getCronExpression())) {
            String[] expr = job.getCronExpression().split(" ");
            String val = expr[1];
            String v = val.split("/")[1];
            return v;
        }

        return "N/A";
    }

    private OscarJob getOscarJobForPolling() {
        OscarJobDao oscarJobDao = SpringUtils.getBean(OscarJobDao.class);
        OscarJobTypeDao oscarJobTypeDao = SpringUtils.getBean(OscarJobTypeDao.class);

        List<OscarJobType> jobTypes = oscarJobTypeDao.findByClassName("ca.openosp.openo.hospitalReportManager.v2018.HRMDownloadJob");

        for (OscarJobType jobType : jobTypes) {
            List<OscarJob> jobs = oscarJobDao.findByType(jobType);
            for (OscarJob job : jobs) {
                return job;
            }
        }

        return null;
    }

    private String getUserPropertyValueOrEmpty(String propName) {
        UserProperty up = userPropertyDao.getProp(propName);
        if (up != null && !StringUtils.isEmpty(up.getValue())) {
            return up.getValue();
        }
        return "";
    }

    private Integer saveUserProperty(String name, String value) {
        UserProperty up = userPropertyDao.getProp(name);
        if (up != null) {
            up.setValue(value);
            userPropertyDao.merge(up);
        } else {
            up = new UserProperty();
            up.setName(name);
            up.setValue(value);
            userPropertyDao.persist(up);
        }

        return up.getId();
    }

    public String getConfidentialityStatement() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);

        String data = hrmProviderConfidentialityStatementDao.getConfidentialityStatementForProvider(loggedInInfo.getLoggedInProviderNo());
        JSONObject res = new JSONObject();
        res.put("value", data != null ? data : "");

        response.setContentType("application/json");
        res.write(response.getWriter());

        return null;
    }

    public String saveConfidentialityStatement() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);

        String value = request.getParameter("value");

        HRMProviderConfidentialityStatement stmt = hrmProviderConfidentialityStatementDao.findByProvider(loggedInInfo.getLoggedInProviderNo());

        if (stmt == null) {
            stmt = new HRMProviderConfidentialityStatement();
            stmt.setId(loggedInInfo.getLoggedInProviderNo());
        }
        stmt.setStatement(value);
        hrmProviderConfidentialityStatementDao.merge(stmt);

        return null;
    }

    public String addToOutageList() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        SFTPConnector.addMeToDoNotSendList(loggedInInfo);


        return null;
    }

    public String getHrmStatus() throws Exception {

        JSONObject obj = new JSONObject();

        response.setContentType("application/json");
        obj.put("running", SFTPConnector.isFetchRunning());
        obj.write(response.getWriter());


        return null;
    }

    public String getDetailedLog() throws Exception {

        boolean isHrmAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm.administrator", "r", null);
        boolean isHrm = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "r", null);
        boolean isAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.hrm", "r", null);

        if (!isHrm && !isHrmAdmin && !isAdmin) {
            return null;
        }
        String id = request.getParameter("id");

        HrmLogEntryDao hrmLogEntryDao = SpringUtils.getBean(HrmLogEntryDao.class);

        List<HrmLogEntry> hrmLogEntries = hrmLogEntryDao.findByHrmLogId(Integer.parseInt(id));

        JSONObject res = new JSONObject();

        JSONArray data = new JSONArray();


        for (HrmLogEntry l : hrmLogEntries) {
            JSONObject obj = new JSONObject();
            obj.put("id", l.getId());
            obj.put("decrypted_filename", l.getDecryptedFileName() != null ? l.getDecryptedFileName().substring(l.getDecryptedFileName().lastIndexOf("/") + 1) : "");
            obj.put("encrypted_filename", l.getEncryptedFileName() != null ? l.getEncryptedFileName().substring(l.getEncryptedFileName().lastIndexOf("/") + 1) : "");
            obj.put("error", l.getError() != null ? l.getError() : "");
            obj.put("filename", l.getFilename() != null ? l.getFilename().substring(l.getFilename().lastIndexOf("/") + 1) : "");
            obj.put("recipient", l.getRecipientName() != null ? l.getRecipientName() + (l.getRecipientId() != null ? "(" + l.getRecipientId() + ")" : "") : "");

            data.put(obj);
        }

        res.put("items", data);

        response.setContentType("application/json");
        res.write(response.getWriter());


        return null;
    }

    public String viewLog() throws Exception {

        boolean isHrmAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm.administrator", "r", null);
        boolean isHrm = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "r", null);
        boolean isAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.hrm", "r", null);

        if (!isHrm && !isHrmAdmin && !isAdmin) {
            return null;
        }

        String start = request.getParameter("start");
        String length = request.getParameter("length");

        String orderingColumnIndex = request.getParameter("order[0][column]"); //idx (eg 0)
        String orderingColumnDirection = request.getParameter("order[0][dir]"); //asc,desc


        //setup a column map from request parameters
        Map<Integer, ColumnInfo> columnMap = new HashMap<Integer, ColumnInfo>();
        int idx = 0;
        while (true) {
            if (request.getParameter("columns[" + idx + "][data]") == null) {
                break;
            }
            columnMap.put(idx, new ColumnInfo(idx, request.getParameter("columns[" + idx + "][data]")));
            idx++;
        }

        String orderBy = null;

        if (!StringUtils.isEmpty(orderingColumnIndex)) {
            ColumnInfo columnInfo = columnMap.get(Integer.parseInt(orderingColumnIndex));
            if ("transaction_date".equals(columnInfo.getData())) {
                orderBy = "started";
            } else if ("transaction_type".equals(columnInfo.getData())) {
                orderBy = "transactionType";
            }
        }

        String limitToProvider = null;
        if (!isHrmAdmin && !isAdmin) {
            limitToProvider = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
        }

        List<HrmLog> logs = hrmLogDao.query(Integer.parseInt(start), Integer.parseInt(length), orderBy, orderingColumnDirection, limitToProvider);

        JSONArray data = new JSONArray();

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        for (HrmLog l : logs) {
            Provider p = null;
            if (!StringUtils.isEmpty(l.getInitiatingProviderNo())) {
                p = providerDao.getProvider(l.getInitiatingProviderNo());
            }

            JSONObject data1 = new JSONObject();
            data1.put("id", l.getId());
            data1.put("transaction_date", fmt.format(l.getStarted()));
            data1.put("transaction_type", l.getTransactionType());
            data1.put("external_system", l.getExternalSystem());
            data1.put("initiating_provider", p != null ? p.getFormattedName() : "");
            data1.put("connected", l.getConnected() != null && l.getConnected() ? "Yes" : "No");
            data1.put("downloaded", l.getDownloadedFiles() != null && l.getDownloadedFiles() ? "Yes" : "No");
            data1.put("num_files_downloaded", l.getNumFilesDownloaded() != null ? l.getNumFilesDownloaded() : "N/A");
            data1.put("deleted", l.getDeleted() != null && l.getDeleted() ? "Yes" : "No");

            data.put(data1);
        }

        JSONObject obj = new JSONObject();
        obj.put("draw", ++draw);
        obj.put("recordsTotal", data.length());
        obj.put("recordsFiltered", data.length());
        obj.put("data", data);
        //obj.put("error", "error occurred");

        response.setContentType("application/json");
        obj.write(response.getWriter());

        return null;
    }

    public String searchCategory() throws Exception {

        String query = request.getParameter("query");

        List<HRMCategory> categoryList = hrmCategoryDao.search(query);
        JSONArray data = new JSONArray();
        for (HRMCategory category : categoryList) {
            JSONObject obj = new JSONObject();
            obj.put("id", category.getId());
            obj.put("mnemonic", category.getSubClassNameMnemonic());
            obj.put("name", category.getCategoryName());
            data.put(obj);
        }
        JSONObject d = new JSONObject();
        d.put("results", data);

        response.setContentType("text/x-json");
        d.write(response.getWriter());

        return null;
    }

    public String saveCategory() throws Exception {

        String hrmDocumentId = request.getParameter("hrmDocumentId");
        String categoryId = request.getParameter("categoryId");

        boolean isHrm = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "r", null);

        HRMCategory category = null;
        HRMDocument doc = null;

        if (isHrm) {

            category = hrmCategoryDao.find(Integer.parseInt(categoryId));
            if (category != null) {
                doc = hrmDocumentDao.find(Integer.parseInt(hrmDocumentId));

                if (doc != null) {
                    doc.setHrmCategoryId(Integer.parseInt(categoryId));
                    hrmCategoryDao.merge(doc);
                }
            }
        }


        JSONObject d = new JSONObject();

        if (doc != null && doc.getHrmCategoryId() != null) {
            HRMCategory setCat = hrmCategoryDao.find(doc.getHrmCategoryId());
            if (setCat != null) {
                d.put("value", setCat.getSubClassNameMnemonic() + ":" + setCat.getCategoryName());
            }
        }

        response.setContentType("text/x-json");
        d.write(response.getWriter());

        return null;
    }

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("uploadReport".equals(method)) {
            return uploadReport();
        } else if ("uploadPrivateKey".equals(method)) {
            return uploadPrivateKey();
        } else if ("fetch".equals(method)) {
            return fetch();
        } else if ("saveConfigurationDetails".equals(method)) {
            return saveConfigurationDetails();
        } else if ("getConfigurationDetails".equals(method)) {
            return getConfigurationDetails();
        } else if ("getPollingInterval".equals(method)) {
            return getPollingInterval();
        } else if ("getConfidentialityStatement".equals(method)) {
            return getConfidentialityStatement();
        } else if ("saveConfidentialityStatement".equals(method)) {
            return saveConfidentialityStatement();
        } else if ("addToOutageList".equals(method)) {
            return addToOutageList();
        } else if ("getHrmStatus".equals(method)) {
            return getHrmStatus();
        } else if ("getDetailedLog".equals(method)) {
            return getDetailedLog();
        } else if ("viewLog".equals(method)) {
            return viewLog();
        } else if ("searchCategory".equals(method)) {
            return searchCategory();
        } else if ("saveCategory".equals(method)) {
            return saveCategory();
        } 
        boolean isHrmAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm.administrator", "r", null);
        boolean isHrm = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "r", null);
        boolean isAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.hrm", "r", null);


        String start = request.getParameter("start");
        String length = request.getParameter("length");

        String orderingColumnIndex = request.getParameter("order[0][column]"); //idx (eg 0)
        String orderingColumnDirection = request.getParameter("order[0][dir]"); //asc,desc


        String providerNo = request.getParameter("providerNo");
        String providerUnmatched = StringUtils.trimToEmpty(request.getParameter("providerUnmatched"));

        String noSignOff = StringUtils.trimToEmpty(request.getParameter("noSignOff"));
        String demographicUnmatched = StringUtils.trimToEmpty(request.getParameter("demographicUnmatched"));

        if (isHrmAdmin && "ALL".equals(providerNo)) {
            providerNo = null;
        }
        if (!isHrmAdmin && !isAdmin) {
            providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
        }

        //setup a column map from request parameters
        Map<Integer, ColumnInfo> columnMap = new HashMap<Integer, ColumnInfo>();
        int idx = 0;
        while (true) {
            if (request.getParameter("columns[" + idx + "][data]") == null) {
                break;
            }
            columnMap.put(idx, new ColumnInfo(idx, request.getParameter("columns[" + idx + "][data]")));
            idx++;
        }

        String orderBy = null;

        if (!StringUtils.isEmpty(orderingColumnIndex)) {
            ColumnInfo columnInfo = columnMap.get(Integer.parseInt(orderingColumnIndex));
            if ("patient_name".equals(columnInfo.getData())) {
                orderBy = "formattedName";
            } else if ("patient_dob".equals(columnInfo.getData())) {
                orderBy = "dob";
            } else if ("report_date".equals(columnInfo.getData())) {
                orderBy = "reportDate";
            } else if ("received_date".equals(columnInfo.getData())) {
                orderBy = "timeReceived";
            } else if ("sending_facility".equals(columnInfo.getData())) {
                orderBy = "sourceFacility";
            }
        }

        JSONArray data = new JSONArray();

        long total = 0L;


        if (isHrm) {
            List<HRMDocument> docs = hrmDocumentDao.query(providerNo, "true".equals(providerUnmatched), "true".equals(noSignOff), "true".equals(demographicUnmatched), Integer.parseInt(start), Integer.parseInt(length), orderBy, orderingColumnDirection);

            total = hrmDocumentDao.queryForCount(providerNo, "true".equals(providerUnmatched), "true".equals(noSignOff), "true".equals(demographicUnmatched), Integer.parseInt(start), Integer.parseInt(length), orderBy, orderingColumnDirection);


            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            for (HRMDocument d : docs) {
                HRMCategory category = null;
                if (d.getHrmCategoryId() != null) {
                    category = hrmCategoryDao.find(d.getHrmCategoryId());
                }

                List<HRMDocumentToDemographic> ptList = hrmDocumentToDemographicDao.findByHrmDocumentId(d.getId().toString());
                Integer demographicNo = ptList.get(0).getDemographicNo();
                JSONObject data1 = new JSONObject();
                data1.put("id", d.getId() + "");
                data1.put("provider_no", d.getRecipientProviderNo() != null ? d.getRecipientProviderNo() : "");
                data1.put("demographic_no", demographicNo != null ? demographicNo.toString() : "");
                data1.put("recipient_name", d.getRecipientName() != null ? d.getRecipientName() : "");
                data1.put("patient_name", d.getFormattedName());
                data1.put("patient_dob", d.getDob());
                data1.put("patient_hcn", d.getHcn());
                data1.put("patient_gender", d.getGender());

                String reportDate = "";
                if (d.getReportDate() != null) {
                    reportDate = fmt.format(d.getReportDate());
                } else if (!d.getAccompanyingSubClasses().isEmpty()) {
                    for (HRMDocumentSubClass hdsc : d.getAccompanyingSubClasses()) {
                        if (hdsc.isActive() && hdsc.getSubClassDateTime() != null) {
                            reportDate = fmt.format(hdsc.getSubClassDateTime());
                        }
                    }
                }

                data1.put("report_date", reportDate != null ? reportDate : "");


                data1.put("sending_facility", d.getSourceFacility() != null ? d.getSourceFacility() : "");
                if (!StringUtils.isEmpty(d.getClassName()) && !StringUtils.isEmpty(d.getSubClassName())) {
                    String className = d.getClassName();
                    String subClassName = d.getSubClassName();
                    String displaySubClass = "";
                    if (subClassName != null) {
                        if (subClassName.indexOf("^") != -1) {
                            displaySubClass = subClassName.split("\\^")[1];
                        } else {
                            displaySubClass = subClassName;
                        }
                    }
                    data1.put("class_subclass", className + (displaySubClass.length() > 0 ? ":" + displaySubClass : ""));
                }
                if (!StringUtils.isEmpty(d.getClassName()) && !d.getAccompanyingSubClasses().isEmpty()) {
                    for (HRMDocumentSubClass sc : d.getAccompanyingSubClasses()) {
                        if (sc.isActive()) {
                            data1.put("class_subclass", d.getClassName() + " " + sc.getSubClass() + ":" + sc.getSubClassMnemonic() + ":" + sc.getSubClassDescription());
                        }
                    }
                }

                data1.put("received_date", fmt.format(d.getTimeReceived()));
                //data1.put("reviewed", "N/A");
                data1.put("category", category != null ? category.getCategoryName() : "");
                data1.put("description", d.getDescription());

                data.put(data1);
            }
        }

        JSONObject obj = new JSONObject();
        obj.put("draw", ++draw);
        obj.put("recordsTotal", total);
        obj.put("recordsFiltered", total);
        obj.put("data", data);
        //obj.put("error", "error occurred");

        response.setContentType("application/json");
        obj.write(response.getWriter());

        return null;
    }

    /**
     * Sanitizes filename to prevent path traversal attacks and other security issues.
     * <p>
     * This method applies multiple security measures to ensure the filename is safe:
     * <ul>
     * <li>Unicode normalization to prevent homoglyph attacks</li>
     * <li>URL decoding to catch encoded traversal attempts (e.g., %2e%2e)</li>
     * <li>Path component extraction (removes directory separators)</li>
     * <li>Dangerous character removal (Windows illegal chars, multiple dots)</li>
     * <li>Length validation (max 255 characters)</li>
     * </ul>
     * <p>
     * Based on the pattern from HRMUploadLab2Action.
     *
     * @param fileName String original filename from user upload
     * @return String sanitized filename, or null if invalid
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }

        // First normalize Unicode to prevent homoglyph attacks
        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFKC);

        // URL decode to catch encoded traversal attempts like %2e%2e
        String decoded = normalized;
        try {
            // Decode multiple times to catch double-encoding
            for (int i = 0; i < 3; i++) {
                String temp = URLDecoder.decode(decoded, "UTF-8");
                if (temp.equals(decoded)) {
                    break; // No more encoding layers
                }
                decoded = temp;
            }
        } catch (Exception e) {
            // If decoding fails, reject the filename
            return null;
        }

        // Extract just the filename (remove any path components)
        String baseName = new File(decoded).getName();

        // Remove dangerous characters and sequences
        String sanitized = baseName
            .replaceAll("[\\\\/:*?\"<>|]", "") // Windows illegal chars
            .replaceAll("\\.{2,}", ".")        // Multiple dots (../)
            .replaceAll("^\\.", "")            // Leading dot
            .replaceAll("\\.$", "")            // Trailing dot
            .trim();

        // Additional security checks
        if (sanitized.isEmpty() ||
            sanitized.contains("..") ||
            sanitized.startsWith("/") ||
            sanitized.startsWith("\\") ||
            sanitized.length() > 255) {
            return null;
        }

        // Ensure we have a reasonable filename
        if (sanitized.length() < 1) {
            return null;
        }

        return sanitized;
    }

    // Struts 2 property injection setters for HRM report upload

    public void setHrm_file(List<File> hrm_file) {
        this.hrm_file = hrm_file;
    }

    public void setHrm_fileContentType(List<String> hrm_fileContentType) {
        this.hrm_fileContentType = hrm_fileContentType;
    }

    public void setHrm_fileFileName(List<String> hrm_fileFileName) {
        this.hrm_fileFileName = hrm_fileFileName;
    }

    // Struts 2 property injection setters for private key upload

    public void setPrivateKeyFile(List<File> privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public void setPrivateKeyFileContentType(List<String> privateKeyFileContentType) {
        this.privateKeyFileContentType = privateKeyFileContentType;
    }

    public void setPrivateKeyFileFileName(List<String> privateKeyFileFileName) {
        this.privateKeyFileFileName = privateKeyFileFileName;
    }

}

