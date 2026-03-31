/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * Originally written for The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia.
 * Portions contributed by Magenta Health.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.managers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.FaxClientLog;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.fax.core.FaxAccount;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.fax.core.FaxSchedulerJob;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.util.ConcatPDF;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import io.github.carlos_emr.CarlosProperties;

@Service
public class FaxManagerImpl implements FaxManager {

    @Autowired
    private FaxConfigDao faxConfigDao;

    @Autowired
    private FaxClientLogDao faxClientLogDao;

    @Autowired
    private FaxJobDao faxJobDao;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    @Autowired
    private FaxDocumentManager faxDocumentManager;

    @Autowired
    private NioFileManager nioFileManager;

    @Autowired
    private ClinicDAO clinicDAO;

    @Autowired
    private FaxSchedulerJob faxSchedulerJob;

    private Logger logger = MiscUtils.getLogger();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Path renderFaxDocument(LoggedInInfo loggedInInfo, TransactionType transactionType, FormTransportContainer formTransportContainer) {
        return renderFaxDocument(loggedInInfo, transactionType, 0, 0, formTransportContainer);
    }

    @Override
    public Path renderFaxDocument(LoggedInInfo loggedInInfo, TransactionType transactionType, int transactionId, int demographicNo) {
        return renderFaxDocument(loggedInInfo, transactionType, transactionId, demographicNo, null);
    }

    /**
     * Renders a fax document for the specified transaction type.
     *
     * @deprecated Move rendering methods into DocumentManager.
     * @return Path to the rendered document, or null if rendering is not implemented for the transaction type
     */
    @Override
    @Deprecated
    public Path renderFaxDocument(LoggedInInfo loggedInInfo, TransactionType transactionType, int transactionId, int demographicNo, FormTransportContainer formTransportContainer) {

        Path renderedDocument;

        switch (transactionType) {
            case CONSULTATION:
                renderedDocument = renderConsultationRequest(loggedInInfo, transactionId, demographicNo);
                break;
            case DOCUMENT:
                renderedDocument = renderDocument(loggedInInfo, transactionId, demographicNo);
                break;
            case EFORM:
                renderedDocument = renderEform(loggedInInfo, transactionId, demographicNo);
                break;
            case FORM:
                renderedDocument = renderForm(loggedInInfo, formTransportContainer);
                break;
            case RX:
                renderedDocument = renderPrescription(loggedInInfo, transactionId, demographicNo);
                break;
            default:
                renderedDocument = null;
                break;
        }

        return renderedDocument;
    }

    @Override
    public Path renderConsultationRequest(LoggedInInfo loggedInInfo, int requestId, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_con)");
        }

        logger.info("Rendering consultation request document number " + requestId + " for fax preview.");

        return null;
    }

    @Override
    public Path renderDocument(LoggedInInfo loggedInInfo, int documentNo, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_edoc)");
        }

        logger.info("Rendering document number " + documentNo + " for fax preview.");
        return null;
    }

    @Override
    public Path renderEform(LoggedInInfo loggedInInfo, int eformId, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }
        logger.info("Rendering eform number " + eformId + " for fax preview.");
        return faxDocumentManager.getEformFaxDocument(loggedInInfo, eformId);
    }

    @Override
    public Path renderPrescription(LoggedInInfo loggedInInfo, int rxId, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }
        logger.info("Rendering prescription number " + rxId + " for fax preview.");

        return null;
    }

    @Override
    public Path renderForm(LoggedInInfo loggedInInfo, int formId, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_form)");
        }

        logger.info("Rendering form number " + formId + " for fax preview.");

        return null;
    }

    @Override
    public Path renderForm(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.WRITE, formTransportContainer.getDemographicNo())) {
            throw new RuntimeException("missing required sec object (_form)");
        }

        logger.info("Rendering form number " + formTransportContainer.getFormName() + " for fax preview.");

        return faxDocumentManager.getFormFaxDocument(loggedInInfo, formTransportContainer);
    }

    /**
     * 1.) Creates the faxJob
     * 2.) duplicates the faxJob for each recipient
     * 3.) saves all the faxJobs to be sent.
     * Map should contain values for:
     * faxFilePath
     * recipient
     * recipientFaxNumber
     * comments (for cover page)
     * coverpage
     * senderFaxNumber
     * demographicNo
     * copyToRecipients (as String[])
     * <p>
     * The FaxJob list that is returned contains persisted FaxJob Objects
     */
    @Override
    public List<FaxJob> createAndSaveFaxJob(LoggedInInfo loggedInInfo, Map<String, Object> faxJobMap) {

        FaxJob faxJob = createFaxJob(loggedInInfo, faxJobMap);
        List<FaxJob> faxJobList = new ArrayList<FaxJob>();
        boolean isCoverpage = Boolean.parseBoolean((String) faxJobMap.get("coverpage"));

        // Add the first job that contains the original recipient.
        faxJobList.add(faxJob);

        // Duplicate the fax job for each copy-to recipient; the original receiver is already in the list.
        String[] copytoRecipients = (String[]) faxJobMap.get("copyToRecipients");
        if (copytoRecipients != null && copytoRecipients.length > 0) {
            List<FaxJob> faxJobRecipients = addRecipients(loggedInInfo, faxJob, copytoRecipients);
            faxJobList.addAll(faxJobRecipients);
        }

        // Create a cover page for each fax job if requested by the user.
        if (isCoverpage) {
            String comments = (String) faxJobMap.get("comments");

            for (FaxJob faxJobObject : faxJobList) {
                Path faxDocument = Paths.get(faxJobObject.getFile_name());
                try {
                    faxDocument = addCoverPage(loggedInInfo, comments, faxJobObject.getFaxRecipient(), faxJobObject.getFaxAccount(), faxDocument);
                    faxJobObject.setNumPages(faxJobObject.getNumPages() + 1);
                    faxJobObject.setFile_name(faxDocument.getFileName().toString());
                } catch (IOException e) {
                    logger.error("CRITICAL: Failed to add cover page for fax job to {} - Fax will NOT be sent without cover page",
                            faxJobObject.getRecipient(), e);
                    faxJobObject.setStatus(STATUS.ERROR);
                    faxJobObject.setStatusString("Cover page creation failed. Fax not sent. Check disk space and logs.");
                    // Do NOT set file_name - leave job in ERROR state and do not transmit
                }
            }
        }

        // Filter out ERROR jobs before saving (they won't be transmitted)
        List<FaxJob> validJobs = faxJobList.stream()
                .filter(job -> job.getStatus() != STATUS.ERROR)
                .collect(Collectors.toList());

        if (validJobs.isEmpty()) {
            throw new RuntimeException("All fax jobs failed validation. No faxes will be sent. Check logs for details.");
        }

        return saveFaxJob(loggedInInfo, validJobs);
    }

    /**
     * The beginning of a new Fax job from the parameters in the given Map.
     * Map should contain values for:
     * faxFilePath
     * recipient
     * recipientFaxNumber
     * comments (for cover page)
     * coverpage
     * senderFaxNumber
     * demographicNo
     * copyToRecipients (as String[])
     * The FaxJob returned is NEW UN-PERSISTED FaxJob Object with a single recipient
     */
    @Override
    public FaxJob createFaxJob(LoggedInInfo loggedInInfo, Map<String, Object> faxJobMap) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        String faxFilePath = (String) faxJobMap.get("faxFilePath");
        String recipient = (String) faxJobMap.get("recipient");
        String recipientFaxNumber = (String) faxJobMap.get("recipientFaxNumber");
        String senderFaxNumber = (String) faxJobMap.get("senderFaxNumber");
        Integer demographicNo = (Integer) faxJobMap.get("demographicNo");

        // If file is in a temporary directory, copy to the permanent document storage (DOCUMENT_DIR).
        if (faxFilePath.contains("/temp/")) {
            faxFilePath = nioFileManager.copyFileToOscarDocuments(faxFilePath);
        }
        recipientFaxNumber = recipientFaxNumber.replaceAll("\\D", "");

        FaxJob faxJob = new FaxJob();

        //TODO Possible that this could be multiple accounts using the same return fax line.
        FaxConfig faxConfig = faxConfigDao.getActiveConfigByNumber(senderFaxNumber);
        faxJob.setStamp(new Date());
        faxJob.setOscarUser(loggedInInfo.getLoggedInProviderNo());
        faxJob.setDemographicNo(demographicNo);
        faxJob.setRecipient(recipient);
        faxJob.setDestination(recipientFaxNumber);

        // No valid account means no fax can be sent.
        if (faxConfig == null) {
            logger.error("Fax account " + faxJob.getFax_line() + " is not found, invalid, or inactive");
            faxJob.setStatus(STATUS.ERROR);
            faxJob.setStatusString("Fax account " + faxJob.getFax_line() + " is not found, invalid, or inactive");
            return faxJob;
        }

        faxJob.setFax_line(faxConfig.getFaxNumber());
        faxJob.setUser(faxConfig.getFaxUser());
        faxJob.setStatus(FaxJob.STATUS.WAITING);

        // Create the sender profile, defaulting to the clinic address.
        FaxAccount faxAccount = new FaxAccount(faxConfig);
        Clinic clinic = clinicDAO.getClinic();
        faxAccount.setSubText(clinic.getClinicName());
        faxAccount.setAddress(clinic.getClinicAddress());
        faxAccount.setFacilityName(clinic.getClinicName());
        faxJob.setFaxAccount(faxAccount);

        // Validate and resolve the file path to prevent path traversal attacks.
        Path faxDocument;
        try {
            faxDocument = resolveAndValidateFilePath(faxFilePath);
        } catch (SecurityException | IOException e) {
            logger.error("Invalid or inaccessible fax file path: {}", faxFilePath, e);
            faxJob.setStatus(STATUS.ERROR);
            faxJob.setStatusString("File missing on local storage or invalid file path.");
            return faxJob;
        }

        faxJob.setFile_name(faxDocument.getFileName().toString());
        faxJob.setNumPages(EDocUtil.getPDFPageCount(faxDocument.toString()));

        return faxJob;

    }

    /**
     * Add recipients from an indexed array of JSON formatted strings
     * name:<recipient>
     * fax:<recipient fax number>
     */
    @Override
    public List<FaxJob> addRecipients(LoggedInInfo loggedInInfo, FaxJob faxJob, String[] faxRecipients) {

        List<FaxRecipient> faxRecipientArray = new ArrayList<FaxRecipient>();
        List<String> failedRecipients = new ArrayList<String>();

        for (String copytoRecipient : faxRecipients) {
            // Assumes that the recipient entry is a JSONObject
            copytoRecipient = "{" + copytoRecipient + "}";
            try {
                ObjectNode copytoRecipientJson = (ObjectNode) objectMapper.readTree(copytoRecipient);
                FaxRecipient faxRecipient = new FaxRecipient(copytoRecipientJson);
                faxRecipientArray.add(faxRecipient);
            } catch (Exception e) {
                logger.error("Failed to parse fax recipient JSON: {} - Recipient will be SKIPPED", copytoRecipient, e);
                failedRecipients.add(copytoRecipient);
            }
        }

        // Fail fast if any recipients couldn't be parsed - don't send partial fax
        if (!failedRecipients.isEmpty()) {
            int displayCount = Math.min(3, failedRecipients.size());
            String preview = String.join(", ", failedRecipients.subList(0, displayCount));
            if (failedRecipients.size() > 3) {
                preview += " (and " + (failedRecipients.size() - 3) + " more)";
            }
            throw new IllegalArgumentException(
                    String.format("Failed to parse %d recipient(s). Fax not sent. Contact support if this persists. Failed entries: %s",
                            failedRecipients.size(), preview)
            );
        }
        return addRecipients(loggedInInfo, faxJob, faxRecipientArray);
    }

    /**
     * Create 1 faxJob copy for each fax recipient. Status is inherited from the original faxJob.
     */
    @Override
    public List<FaxJob> addRecipients(LoggedInInfo loggedInInfo, FaxJob faxJob, List<FaxRecipient> faxRecipients) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        List<FaxJob> faxJobList = new ArrayList<FaxJob>();

        outer:
        for (FaxRecipient faxRecipient : faxRecipients) {
            // Avoid duplicate fax numbers.
            if (Objects.equals(faxJob.getDestination(), faxRecipient.getFax())) {
                continue;
            }

            for (FaxJob faxJobItem : faxJobList) {
                if (Objects.equals(faxJobItem.getDestination(), faxRecipient.getFax())) {
                    continue outer;
                }
            }

            FaxJob faxJobCopy = new FaxJob(faxJob);
            faxJobCopy.setDestination(faxRecipient.getFax());
            faxJobCopy.setRecipient(faxRecipient.getName());

            faxJobList.add(faxJobCopy);
        }
        return faxJobList;
    }

    /**
     * Persist to the database for transmission later if the fax account is valid.
     * <p>
     * The given faxjob must contain a valid sender fax number and username.
     */
    @Override
    public List<FaxJob> saveFaxJob(LoggedInInfo loggedInInfo, List<FaxJob> faxJobList) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        List<FaxJob> savedFaxJobs = new ArrayList<FaxJob>();

        for (FaxJob faxJob : faxJobList) {
            saveFaxJob(loggedInInfo, faxJob);
            savedFaxJobs.add(faxJob);
        }
        return savedFaxJobs;
    }

    /**
     * Create new or update fax job.
     */
    @Override
    public FaxJob saveFaxJob(LoggedInInfo loggedInInfo, FaxJob faxJob) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        Integer faxJobId = faxJob.getId();

        if (faxJobId == null) {
            faxJobDao.persist(faxJob);
        } else {
            faxJobDao.merge(faxJob);
        }

        if (faxJob.getId() == null || faxJob.getId() < 1) {
            throw new RuntimeException("Failed to persist fax job - database did not generate an ID. "
                    + "Check database connectivity and constraints.");
        }

        return faxJob;
    }

    /**
     * prepend a fax cover page to the given existing PDF document.
     */
    @Override
    public Path addCoverPage(LoggedInInfo loggedInInfo, String note, Path currentDocument) throws IOException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }
        int numberpages = EDocUtil.getPDFPageCount(currentDocument.getFileName().toString());
        byte[] coverPage = faxDocumentManager.createCoverPage(loggedInInfo, note, numberpages);
        return addCoverPage(coverPage, currentDocument);
    }

    @Override
    public Path addCoverPage(LoggedInInfo loggedInInfo, String note, FaxRecipient recipient, FaxAccount sender, Path currentDocument) throws IOException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }
        // Resolve to full path before getting page count to avoid security validation errors
        currentDocument = nioFileManager.getOscarDocument(currentDocument);
        int numberpages = EDocUtil.getPDFPageCount(currentDocument.toString());
        byte[] coverPage = faxDocumentManager.createCoverPage(loggedInInfo, note, recipient, sender, numberpages);
        return addCoverPage(coverPage, currentDocument);
    }

    private Path addCoverPage(byte[] coverPage, Path currentDocument) throws IOException {
        currentDocument = nioFileManager.getOscarDocument(currentDocument);
        Path newCurrentDocument = Paths.get(currentDocument.getParent().toString(), "Cover_" + UUID.randomUUID() + "_" + currentDocument.getFileName());
        Files.createFile(newCurrentDocument);
        try (ByteArrayInputStream currentDocumentStream = new ByteArrayInputStream(Files.readAllBytes(currentDocument));
             OutputStream newDocumentStream = Files.newOutputStream(newCurrentDocument);
             ByteArrayInputStream coverPageStream = new ByteArrayInputStream(coverPage)) {
            List<Object> documentList = new ArrayList<>();
            documentList.add(coverPageStream);
            documentList.add(currentDocumentStream);
            ConcatPDF.concat(documentList, newDocumentStream);
        }
        return newCurrentDocument;
    }

    /**
     * Overload
     * Get preview image by specific page number.
     */
    @Override
    public Path getFaxPreviewImage(LoggedInInfo loggedInInfo, String filePath, int pageNumber) {
        String file = EDocUtil.resolvePath(filePath);
        return getFaxPreviewImage(loggedInInfo, Paths.get(file), pageNumber);
    }

    /**
     * Overload
     * Get a preview image of the documents being faxed.  Default is
     * the first page only
     */
    @Override
    public Path getFaxPreviewImage(LoggedInInfo loggedInInfo, String filePath) {
        String file = EDocUtil.resolvePath(filePath);
        return getFaxPreviewImage(loggedInInfo, Paths.get(file), 1);
    }

    /**
     * Get a preview image of the documents being faxed.  Default is
     * the first page only
     */
    @Override
    public Path getFaxPreviewImage(LoggedInInfo loggedInInfo, Path filePath) {
        return getFaxPreviewImage(loggedInInfo, filePath, 1);
    }

    /**
     * Get preview image by specific page number.
     */
    @Override
    public Path getFaxPreviewImage(LoggedInInfo loggedInInfo, Path filePath, int pageNumber) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        Path outfile = null;

        if (filePath != null && Files.exists(filePath)) {
            outfile = nioFileManager.createCacheVersion2(loggedInInfo, filePath.getParent().toString(), filePath.getFileName().toString(), pageNumber);
        }
        return outfile;
    }

    /**
     * Sets both the global user log and the fax job log.
     */
    @Override
    public void logFaxJob(LoggedInInfo loggedInInfo, FaxJob faxJob, TransactionType transactionType, int transactionId) {

        FaxClientLog faxClientLog = new FaxClientLog();
        faxClientLog.setFaxId(faxJob.getId());
        faxClientLog.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        faxClientLog.setStartTime(new Date(System.currentTimeMillis()));
        faxClientLog.setRequestId(transactionId);
        faxClientLog.setTransactionType(transactionType.name());

        faxClientLogDao.persist(faxClientLog);
    }

    /**
     * Update the transaction logs with a new status.
     */
    @Override
    public void updateFaxLog(LoggedInInfo loggedInInfo, FaxJob faxJob) {

        FaxClientLog faxClientLog = faxClientLogDao.findClientLogbyFaxId(faxJob.getId());
        if (faxClientLog == null) {
            logger.warn("No FaxClientLog found for fax id {} - cannot update fax log entry", faxJob.getId());
            return;
        }
        LogAction.addLogSynchronous(loggedInInfo, faxJob.getStatus().name(), faxClientLog.getTransactionType() + ":" + faxClientLog.getRequestId());
        faxClientLog.setResult(faxJob.getStatus().name());
        faxClientLog.setEndTime(new Date(System.currentTimeMillis()));
        faxClientLogDao.merge(faxClientLog);

    }

    /**
     * Returns all the active sender accounts in this system.
     */
    @Override
    public List<FaxConfig> getFaxGatewayAccounts(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        List<FaxConfig> accounts = faxConfigDao.findAll(0, null);
        List<FaxConfig> sanitizedAccounts = new ArrayList<FaxConfig>();
        for (FaxConfig account : accounts) {
            if (account.isActive()) {
                FaxConfig sanitized = new FaxConfig();
                sanitized.setId(account.getId());
                sanitized.setFaxNumber(account.getFaxNumber());
                sanitized.setAccountName(account.getAccountName());
                sanitized.setSenderEmail(account.getSenderEmail());
                sanitized.setFaxUser(account.getFaxUser());
                sanitized.setActive(account.isActive());
                sanitized.setProviderType(account.getProviderType());
                sanitized.setQueue(account.getQueue());
                sanitized.setDownload(account.isDownload());
                sanitized.setUrl(account.getUrl());
                sanitized.setSiteUser(account.getSiteUser());
                // Passwords deliberately omitted to avoid exposing credentials
                sanitizedAccounts.add(sanitized);
            }
        }

        return sanitizedAccounts;
    }

    @Override
    public List<FaxConfig> getFaxConfigurationAccounts(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        return faxConfigDao.findAll(0, null);
    }

    /**
     * Get all fax jobs with a waiting to be sent status by
     * sender fax number.
     */
    @Override
    public List<FaxJob> getOutGoingFaxes(LoggedInInfo loggedInInfo, String senderFaxNumber) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        return faxJobDao.getReadyToSendFaxes(senderFaxNumber);
    }

    /**
     * Clear the preview cache and temp directory.
     */
    @Override
    public boolean flush(LoggedInInfo loggedInInfo, String filePath) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        boolean cache = nioFileManager.removeCacheVersion(loggedInInfo, filePath);
        boolean temp = nioFileManager.deleteTempFile(filePath);

        return (cache && temp);
    }


    @Override
    public FaxJob getFaxJob(LoggedInInfo loggedInInfo, int jobId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        return faxJobDao.find(jobId);
    }

    /**
     * Returns the actual page count of this PDF document instead of
     * depending on the value that is placed in the database table.
     * Important for when faxes are merged or when a cover page is added.
     */
    @Override
    public int getPageCount(LoggedInInfo loggedInInfo, int jobId) {
        FaxJob faxJob = getFaxJob(loggedInInfo, jobId);
        if (faxJob != null) {
            return EDocUtil.getPDFPageCount(faxJob.getFile_name());
        }
        return 0;
    }

    /**
     * Faxes can be resent by the user if the fax contains a status of
     * ERROR or COMPLETE.  The fax status of the original fax will be changed to
     * RESENT and cannot be resent again.
     */
    @Override
    public boolean resendFax(LoggedInInfo loggedInInfo, String jobId, String destination) {

        boolean success = false;
        FaxJob faxJob = null;

        if (jobId != null && !jobId.isEmpty()) {
            try {
                faxJob = getFaxJob(loggedInInfo, Integer.parseInt(jobId));
            } catch (NumberFormatException e) {
                logger.error("Invalid fax job ID format: {}", jobId);
                return false;
            }
        }

        if (faxJob != null) {

            FaxJob reSentFaxJob = new FaxJob(faxJob);

            // Destination can be replaced with new user input.
            if (destination != null && !destination.isEmpty()) {
                destination = destination.replaceAll("\\D", "");
                reSentFaxJob.setDestination(destination);
            }

            reSentFaxJob.setStamp(new Date());
            reSentFaxJob.setJobId(null);
            reSentFaxJob.setOscarUser(loggedInInfo.getLoggedInProviderNo());
            reSentFaxJob.setStatus(STATUS.WAITING);
            reSentFaxJob.setStatusString("Fax RE-SENT by provider " + loggedInInfo.getLoggedInProviderNo());

            FaxJob reSent = saveFaxJob(loggedInInfo, reSentFaxJob);

            // Update the status of the source re-sent fax job.
            if (reSent != null) {
                faxJob.setStatus(STATUS.RESENT);
                faxJob.setStatusString("Fax RE-SENT as fax id " + reSent.getId() + " by provider " + loggedInInfo.getLoggedInProviderNo());
                saveFaxJob(loggedInInfo, faxJob);
                success = true;
            }

            success = success && !reSentFaxJob.getStatus().equals(STATUS.ERROR);

        } else {
            logger.error("Cannot resend fax: no fax job found for id {}", jobId);
        }

        return success;
    }

    public void restartFaxScheduler(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax.restart", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_admin.fax.restart)");
        }
        faxSchedulerJob.restartTask();
    }

    @Override
    public void startFaxSchedulerIfNotRunning(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax.restart", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_admin.fax.restart)");
        }
        faxSchedulerJob.startIfNotRunning();
    }

    public ObjectNode getFaxSchedularStatus(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax.restart", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_admin.fax.restart)");
        }
        boolean running = faxSchedulerJob.isRunning();
        long lastRun = faxSchedulerJob.getLastSuccessfulRunEpochMs();
        String lastError = faxSchedulerJob.getLastError();

        String status = "Scheduler Stopped (Fatal Error)";
        if (running) {
            status = "Scheduler Running";
        }

        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("faxSchedularStatus", status);
        jsonObject.put("isRunning", running);
        jsonObject.put("lastSuccessfulRunEpochMs", lastRun);
        jsonObject.put("lastError", lastError == null ? "" : lastError);
        return jsonObject;
    }

    /**
     * Validates that a file path is safe and within allowed directories.
     * Prevents path traversal attacks by checking for malicious patterns and
     * validating the path is within whitelisted directories.
     *
     * @param filePath the file path to validate
     * @throws SecurityException if the path is invalid or outside allowed directories
     */
    @Override
    public void validateFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path must not be null or empty");
        }

        // Check for path traversal patterns
        if (filePath.contains("..") || filePath.contains("~")) {
            logger.error("Path traversal attempt detected: {}", filePath);
            throw new SecurityException("Invalid file path detected: path traversal patterns not allowed");
        }

        // Use PathValidationUtils for validation
        File file = new File(filePath);
        File documentDir = new File(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"));

        try {
            file = PathValidationUtils.validateExistingPath(file, documentDir);
        } catch (SecurityException e) {
            // File not in document dir, check if it's in allowed temp directories
            if (!PathValidationUtils.isInAllowedTempDirectory(file)) {
                logger.error("File path outside allowed directories: {}", filePath);
                throw new SecurityException("File path must be within allowed directories");
            }
        }
    }

    /**
     * Resolves and validates a file path with robust path containment checking.
     * This method performs comprehensive security validation including:
     * - Path traversal pattern detection
     * - Path normalization
     * - Containment verification within allowed base directories
     * - File existence and type validation
     *
     * @param filePath the file path to resolve and validate
     * @return the resolved and validated Path object
     * @throws SecurityException if the path is invalid, outside allowed directories, or fails security checks
     * @throws IOException if the file does not exist or is not a regular file
     */
    @Override
    public Path resolveAndValidateFilePath(String filePath) throws IOException {
        // First validate with existing security checks
        validateFilePath(filePath);

        // Use PathValidationUtils for robust path containment validation
        File file = new File(filePath);
        File documentDir = new File(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"));

        try {
            file = PathValidationUtils.validateExistingPath(file, documentDir);
        } catch (SecurityException e) {
            // File not in document dir, check if it's in allowed temp directories
            if (!PathValidationUtils.isInAllowedTempDirectory(file)) {
                logger.error("Path containment check failed - file path outside allowed directories: {}", filePath);
                throw new SecurityException("File path must be within allowed directories");
            }
        }

        Path resolvedPath = file.toPath().normalize();

        // Ensure the file exists and is a regular file
        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            logger.error("File not found or is not a regular file: {}", filePath);
            throw new IOException("File not found or is not a regular file");
        }

        return resolvedPath;
    }

    /**
     * Validates a fax number format.
     * Ensures the fax number contains only valid characters: digits, spaces, hyphens, plus sign, and parentheses.
     *
     * @param faxNumber the fax number to validate
     * @param fieldName the name of the field being validated (for error messages)
     * @throws SecurityException if the fax number format is invalid
     */
    @Override
    public void validateFaxNumber(String faxNumber, String fieldName) {
        // Regex pattern for fax number validation: allows digits, spaces, hyphens, plus sign, and parentheses
        final String FAX_NUMBER_PATTERN = "^[0-9\\-\\+\\(\\)\\s]+$";

        // This method validates FORMAT only for non-empty numbers.
        // Callers are responsible for checking required/non-empty fax numbers separately.
        if (faxNumber != null && !faxNumber.trim().isEmpty()) {
            if (!faxNumber.matches(FAX_NUMBER_PATTERN)) {
                String errorMsg = "Invalid " + fieldName + " format: contains illegal characters";
                logger.error(errorMsg + " - " + faxNumber);
                throw new SecurityException(errorMsg);
            }
        }
    }


}
