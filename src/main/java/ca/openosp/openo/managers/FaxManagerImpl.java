/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */

package ca.openosp.openo.managers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.ClinicDAO;
import ca.openosp.openo.commn.dao.FaxClientLogDao;
import ca.openosp.openo.commn.dao.FaxConfigDao;
import ca.openosp.openo.commn.dao.FaxJobDao;
import ca.openosp.openo.commn.model.Clinic;
import ca.openosp.openo.commn.model.FaxClientLog;
import ca.openosp.openo.commn.model.FaxConfig;
import ca.openosp.openo.commn.model.FaxJob;
import ca.openosp.openo.commn.model.FaxJob.STATUS;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.fax.core.FaxAccount;
import ca.openosp.openo.fax.core.FaxRecipient;
import ca.openosp.openo.fax.core.FaxSchedulerJob;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ca.openosp.openo.form.util.FormTransportContainer;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.util.ConcatPDF;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import ca.openosp.OscarProperties;

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

    // public enum TransactionType {CONSULTATION, EFORM, FORM, RX, DOCUMENT}

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
     * @return
     * @Deprecated Move these rendering methods into a more generic class like the DocumentManager
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
     * isCoverpage
     * senderFaxNumber
     * demographicNo
     * copytoRecipients (as String[])
     * <p>
     * The FaxJob list that is returned contains persisted FaxJob Objects
     */
    @Override
    public List<FaxJob> createAndSaveFaxJob(LoggedInInfo loggedInInfo, Map<String, Object> faxJobMap) {

        FaxJob faxJob = createFaxJob(loggedInInfo, faxJobMap);
        List<FaxJob> faxJobList = new ArrayList<FaxJob>();
        boolean isCoverpage = Boolean.parseBoolean((String) faxJobMap.get("coverpage"));

        /*
         * add the first job that contains the original recipient.
         */
        faxJobList.add(faxJob);

        /*
         * duplicate the fax job for each copy to recipient
         * The original receiver is already retained in the list.
         */
        String[] copytoRecipients = (String[]) faxJobMap.get("copyToRecipients");
        if (copytoRecipients != null && copytoRecipients.length > 0) {
            List<FaxJob> faxJobRecipients = addRecipients(loggedInInfo, faxJob, copytoRecipients);
            faxJobList.addAll(faxJobRecipients);
        }

        /*
         * Create a cover page for each of the fax jobs if requested by the user.
         */
        if (isCoverpage) {
            String comments = (String) faxJobMap.get("comments");

            for (FaxJob faxJobObject : faxJobList) {
                Path faxDocument = Paths.get(faxJobObject.getFile_name());
                try {
                    faxDocument = addCoverPage(loggedInInfo, comments, faxJobObject.getFaxRecipient(), faxJobObject.getFaxAccount(), faxDocument);
                    faxJob.setNumPages(faxJobObject.getNumPages() + 1);
                } catch (IOException e) {
                    logger.error("failed to add cover page ", e);
                    faxJobObject.setStatus(STATUS.ERROR);
                } finally {
                    /*
                     * set the final document retrieval path.
                     */
                    faxJobObject.setFile_name(faxDocument.getFileName().toString());
                }
            }
        }

        return saveFaxJob(loggedInInfo, faxJobList);
    }

    /**
     * The beginning of a new Fax job from the parameters in the given Map.
     * Map should contain values for:
     * faxFilePath
     * recipient
     * recipientFaxNumber
     * comments (for cover page)
     * isCoverpage
     * senderFaxNumber
     * demographicNo
     * copytoRecipients (as String[])
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

        //Checking if a file is saved in a temporary directory, then copying it to a permanent directory (/var/lib/OscarDocument/...).
        if (faxFilePath.contains("/temp/")) {
            faxFilePath = nioFileManager.copyFileToOscarDocuments(faxFilePath);
        }
        recipientFaxNumber = recipientFaxNumber.replaceAll("\\D", "");

        FaxJob faxJob = new FaxJob();

        //TODO Possible that this could be multiple accounts using the same return fax line.
        FaxConfig faxConfig = faxConfigDao.getActiveConfigByNumber(senderFaxNumber);
        /*
         * Build the foundation of a faxJob
         */
        faxJob.setStamp(new Date());
        faxJob.setOscarUser(loggedInInfo.getLoggedInProviderNo());
        faxJob.setDemographicNo(demographicNo);
        faxJob.setRecipient(recipient);
        faxJob.setDestination(recipientFaxNumber);

        /*
         * This will be null if the fax number is invalid.
         * No valid account - No fax
         */
        if (faxConfig == null) {
            logger.error("Fax account " + faxJob.getFax_line() + " is not found, invalid, or inactive");
            faxJob.setStatus(STATUS.ERROR);
            faxJob.setStatusString("Fax account " + faxJob.getFax_line() + " is not found, invalid, or inactive");
            return faxJob;
        }

        faxJob.setFax_line(faxConfig.getFaxNumber());
        faxJob.setUser(faxConfig.getFaxUser());
        faxJob.setStatus(FaxJob.STATUS.WAITING);

        /*
         * Create the sender profile
         * This is set with the clinic address by default.
         */
        FaxAccount faxAccount = new FaxAccount(faxConfig);
        Clinic clinic = clinicDAO.getClinic();
        faxAccount.setSubText(clinic.getClinicName());
        faxAccount.setAddress(clinic.getClinicAddress());
        faxAccount.setFacilityName(clinic.getClinicName());
        faxJob.setFaxAccount(faxAccount);

        /*
         * No document - No Fax. Return an error.
         * Validate and resolve the file path to prevent path traversal attacks
         */
        Path faxDocument;
        try {
            faxDocument = resolveAndValidateFilePath(faxFilePath);
        } catch (SecurityException | IOException e) {
            logger.error("Invalid or inaccessible fax file path: " + faxFilePath, e);
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
        for (String copytoRecipient : faxRecipients) {
            /*
             *  assumes that the recipient entry is a JSONObject
             */
            copytoRecipient = "{" + copytoRecipient + "}";
            try {
                ObjectNode copytoRecipientJson = (ObjectNode) objectMapper.readTree(copytoRecipient);
                FaxRecipient faxRecipient = new FaxRecipient(copytoRecipientJson);
                faxRecipientArray.add(faxRecipient);
            } catch (Exception e) {
                logger.error("failed to parse fax recipient json: " + copytoRecipient, e);
            }
        }
        return addRecipients(loggedInInfo, faxJob, faxRecipientArray);
    }

    /**
     * Create 1 faxJob for each fax recipient. Sets each faxJob to the
     * default status of WAITNG.
     */
    @Override
    public List<FaxJob> addRecipients(LoggedInInfo loggedInInfo, FaxJob faxJob, List<FaxRecipient> faxRecipients) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        List<FaxJob> faxJobList = new ArrayList<FaxJob>();

        outer:
        for (FaxRecipient faxRecipient : faxRecipients) {
            /*
             * Avoid duplicate fax numbers.
             */
            if (faxJob.getDestination() == faxRecipient.getFax()) {
                continue;
            }

            for (FaxJob faxJobItem : faxJobList) {
                if (faxJobItem.getDestination() == faxRecipient.getFax()) {
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
            logger.error("Unable to save fax job. Contact support. " + faxJob);
            return null;
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
                account.setFaxPasswd(null);
                account.setPasswd(null);
                sanitizedAccounts.add(account);
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

        /*
         * Remove the preview cache.
         */
        boolean cache = nioFileManager.removeCacheVersion(loggedInInfo, filePath);

        /*
         * Delete the temp file
         */
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
            faxJob = getFaxJob(loggedInInfo, Integer.parseInt(jobId));
        }

        if (faxJob != null) {

            /*
             * make a copy
             */
            FaxJob reSentFaxJob = new FaxJob(faxJob);

            /*
             * Destination can be replaced with new user input
             */
            if (destination != null && !destination.isEmpty()) {
                destination = destination.replaceAll("\\D", "");
                reSentFaxJob.setDestination(destination);
            }

            reSentFaxJob.setStamp(new Date());
            reSentFaxJob.setJobId(null);
            reSentFaxJob.setOscarUser(loggedInInfo.getLoggedInProviderNo());
            reSentFaxJob.setStatus(STATUS.WAITING);
            reSentFaxJob.setStatusString("Fax RE-SENT by provider " + loggedInInfo.getLoggedInProviderNo());

            /*
             * adapt the Fax queue and logs accordingly.
             */
            FaxJob reSent = saveFaxJob(loggedInInfo, reSentFaxJob);

            /*
             *  Update the status of the source re-sent fax job.
             */
            if (reSent != null) {
                faxJob.setStatus(STATUS.RESENT);
                faxJob.setStatusString("Fax RE-SENT as fax id " + reSent.getId() + " by provider " + loggedInInfo.getLoggedInProviderNo());
                saveFaxJob(loggedInInfo, faxJob);
                success = true;
            }

            // TODO: update fax and oscar logs.

            success = success && !reSentFaxJob.getStatus().equals(STATUS.ERROR);

        } else {
            // something went horribly wrong
            logger.error("Something went horribly wrong while attempting to resend fax id: " + jobId);
        }

        return success;
    }

    public void restartFaxScheduler(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax.restart", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_admin.fax.restart)");
        }
        faxSchedulerJob.restartTask();
    }

    public ObjectNode getFaxSchedularStatus(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax.restart", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_admin.fax.restart)");
        }
        String status = "Uncaught Exception - connection is likely down";
        if (FaxSchedulerJob.isRunning()) {
            status = "No uncaught exception - connection is likely up";
        }
        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("faxSchedularStatus", status);
        jsonObject.put("isRunning", FaxSchedulerJob.isRunning());
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
            return;
        }

        // Check for path traversal patterns
        if (filePath.contains("..") || filePath.contains("~")) {
            logger.error("Path traversal attempt detected: " + filePath);
            throw new SecurityException("Invalid file path detected: path traversal patterns not allowed");
        }

        // Use PathValidationUtils for validation
        File file = new File(filePath);
        File documentDir = new File(OscarProperties.getInstance().getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"));

        try {
            PathValidationUtils.validateExistingPath(file, documentDir);
        } catch (SecurityException e) {
            // File not in document dir, check if it's in allowed temp directories
            if (!PathValidationUtils.isInAllowedTempDirectory(file)) {
                logger.error("File path outside allowed directories: " + filePath);
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
        File documentDir = new File(OscarProperties.getInstance().getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"));

        try {
            PathValidationUtils.validateExistingPath(file, documentDir);
        } catch (SecurityException e) {
            // File not in document dir, check if it's in allowed temp directories
            if (!PathValidationUtils.isInAllowedTempDirectory(file)) {
                logger.error("Path containment check failed - file path outside allowed directories: " + filePath);
                throw new SecurityException("File path must be within allowed directories");
            }
        }

        Path resolvedPath = file.toPath().normalize();

        // Ensure the file exists and is a regular file
        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            logger.error("File not found or is not a regular file: " + filePath);
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

        if (faxNumber != null && !faxNumber.trim().isEmpty()) {
            if (!faxNumber.matches(FAX_NUMBER_PATTERN)) {
                String errorMsg = "Invalid " + fieldName + " format: contains illegal characters";
                logger.error(errorMsg + " - " + faxNumber);
                throw new SecurityException(errorMsg);
            }
        }
    }


}
