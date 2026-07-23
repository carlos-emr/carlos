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
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import io.github.carlos_emr.carlos.utility.LogSafe;

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
    public Path renderFaxDocument(LoggedInInfo loggedInInfo, TransactionType transactionType, FormTransportContainer formTransportContainer) throws PDFGenerationException {
        return renderFaxDocument(loggedInInfo, transactionType, 0, 0, formTransportContainer);
    }

    @Override
    public Path renderFaxDocument(LoggedInInfo loggedInInfo, TransactionType transactionType, int transactionId, int demographicNo) throws PDFGenerationException {
        return renderFaxDocument(loggedInInfo, transactionType, transactionId, demographicNo, null);
    }

    /**
     * Renders a fax document for the specified transaction type.
     *
     * @deprecated Move rendering methods into DocumentManager.
     * @return Path to the rendered document, or null if rendering is not implemented for the transaction type
     * @throws PDFGenerationException when an EFORM or FORM document cannot be rendered
     */
    @Override
    @Deprecated
    public Path renderFaxDocument(LoggedInInfo loggedInInfo, TransactionType transactionType, int transactionId, int demographicNo, FormTransportContainer formTransportContainer) throws PDFGenerationException {

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

        logger.info("Rendering consultation request document number {} for fax preview.", requestId);

        return null;
    }

    @Override
    public Path renderDocument(LoggedInInfo loggedInInfo, int documentNo, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_edoc)");
        }

        logger.info("Rendering document number {} for fax preview.", documentNo);
        return null;
    }

    @Override
    public Path renderEform(LoggedInInfo loggedInInfo, int eformId, int demographicNo) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }
        logger.info("Rendering eform number {} for fax preview.", eformId);
        return faxDocumentManager.getEformFaxDocument(loggedInInfo, eformId);
    }

    @Override
    public Path renderPrescription(LoggedInInfo loggedInInfo, int rxId, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }
        logger.info("Rendering prescription number {} for fax preview.", rxId);

        return null;
    }

    @Override
    public Path renderForm(LoggedInInfo loggedInInfo, int formId, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_form)");
        }

        logger.info("Rendering form number {} for fax preview.", formId);

        return null;
    }

    @Override
    public Path renderForm(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.WRITE, formTransportContainer.getDemographicNo())) {
            throw new RuntimeException("missing required sec object (_form)");
        }

        logger.info("Rendering form number {} for fax preview.", LogSafe.sanitize(formTransportContainer.getFormName()));

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
     * <p>
     * This method is {@code @Transactional} so that the per-recipient {@code faxJobDao.persist}
     * calls in the loop below commit atomically: if persisting recipient N throws, recipients
     * 1..N-1 are rolled back rather than left as orphaned WAITING rows that {@code FaxSender}
     * could pick up and transmit while the caller only sees an error page for the batch.
     * <p>
     * <b>Filesystem side effects are NOT covered by this transaction.</b> Temp-file promotion
     * (see {@link #createFaxJob}) and cover-page file creation (see {@link #addCoverPage(byte[],
     * Path)}) write directly to disk and are not undone by a JPA rollback. An aborted queue can
     * therefore leave orphaned files on disk with no corresponding transmittable row. Temp-side
     * orphans are swept by {@code ApplicationTempPurgeJob}; a promoted document, and a
     * {@code Cover_*} file whose concat succeeded before the rollback, land in DOCUMENT_DIR —
     * which no automated sweep covers — so those are bounded only by {@code addCoverPage}'s
     * failed-concat cleanup and manual housekeeping, not by any backstop.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    @Transactional
    @Override
    public List<FaxJob> createAndSaveFaxJob(LoggedInInfo loggedInInfo, Map<String, Object> faxJobMap) {

        // Parse and fail fast BEFORE createFaxJob: the promotion inside createFaxJob deletes
        // the temp preview source, so a recipient-shape failure after it destroys the user's
        // only copy and strands an orphan PDF in the document store.
        String[] copytoRecipients = (String[]) faxJobMap.get("copyToRecipients");
        List<FaxRecipient> parsedRecipients = (copytoRecipients != null && copytoRecipients.length > 0)
                ? parseFaxRecipients(copytoRecipients)
                : List.of();

        FaxJob faxJob = createFaxJob(loggedInInfo, faxJobMap);
        List<FaxJob> faxJobList = new ArrayList<FaxJob>();
        boolean isCoverpage = Boolean.parseBoolean((String) faxJobMap.get("coverpage"));

        // Add the first job that contains the original recipient.
        faxJobList.add(faxJob);

        // A job that failed validation has no file to duplicate, cover, or transmit. Return it
        // un-persisted so the preview screen renders the per-job failure status; FaxSender only
        // picks up WAITING jobs, so an ERROR job can never be transmitted.
        if (STATUS.ERROR.equals(faxJob.getStatus())) {
            return faxJobList;
        }

        // Duplicate the fax job for each copy-to recipient; the original receiver is already in the list.
        if (!parsedRecipients.isEmpty()) {
            List<FaxJob> faxJobRecipients = addRecipients(loggedInInfo, faxJob, parsedRecipients);
            faxJobList.addAll(faxJobRecipients);
        }

        // Create a cover page for each fax job if requested by the user.
        if (isCoverpage) {
            String comments = (String) faxJobMap.get("comments");

            for (FaxJob faxJobObject : faxJobList) {
                // Never touch a job already in ERROR: its file_name may be unset, and there is
                // nothing meaningful to prepend a cover page to.
                if (STATUS.ERROR.equals(faxJobObject.getStatus())) {
                    continue;
                }
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

        // Persist only sendable jobs; ERROR jobs (validation or cover-page failures) stay in the
        // returned list un-persisted so the caller can render their per-job status instead of the
        // pre-fix behavior of silently dropping them or throwing an unmapped RuntimeException.
        List<FaxJob> validJobs = faxJobList.stream()
                .filter(job -> job.getStatus() != STATUS.ERROR)
                .collect(Collectors.toList());

        if (!validJobs.isEmpty()) {
            // saveFaxJob persists in place (assigns ids on the same instances), so faxJobList
            // already reflects the saved state and keeps the original recipient ordering.
            saveFaxJob(loggedInInfo, validJobs);
        }

        return faxJobList;
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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

        recipientFaxNumber = recipientFaxNumber.replaceAll("\\D", "");

        // Build the job shell before any validation so every ERROR return below is display-ready:
        // CoverPage.jsp renders recipient/destination/status/statusString per job on the preview.
        FaxJob faxJob = new FaxJob();
        faxJob.setStamp(new Date());
        faxJob.setOscarUser(loggedInInfo.getLoggedInProviderNo());
        faxJob.setDemographicNo(demographicNo);
        faxJob.setRecipient(recipient);
        faxJob.setDestination(recipientFaxNumber);

        //TODO Possible that this could be multiple accounts using the same return fax line.
        FaxConfig faxConfig = faxConfigDao.getActiveConfigByNumber(senderFaxNumber);

        // No valid account means no fax can be sent.
        if (faxConfig == null) {
            logger.error("Fax account {} is not found, invalid, or inactive", LogSafe.sanitize(senderFaxNumber));
            faxJob.setStatus(STATUS.ERROR);
            faxJob.setStatusString("Fax account " + senderFaxNumber + " is not found, invalid, or inactive");
            return faxJob;
        }

        faxJob.setFax_line(faxConfig.getFaxNumber());
        faxJob.setUser(faxConfig.getFaxUser());

        // Create the sender profile, defaulting to the clinic address.
        FaxAccount faxAccount = new FaxAccount(faxConfig);
        Clinic clinic = clinicDAO.getClinic();
        faxAccount.setSubText(clinic.getClinicName());
        faxAccount.setAddress(clinic.getClinicAddress());
        faxAccount.setFacilityName(clinic.getClinicName());
        faxJob.setFaxAccount(faxAccount);

        // Validate and resolve the file path (traversal + existence) BEFORE the destructive
        // promotion below, so an invalid account or bad path can never strand an orphan copy in
        // the document store after the preview temp source has been deleted.
        Path faxDocument;
        try {
            faxDocument = resolveAndValidateFilePath(faxFilePath);
        } catch (SecurityException | IOException e) {
            logger.error("Invalid or inaccessible fax file path: {}", LogSafe.sanitize(faxFilePath), e);
            faxJob.setStatus(STATUS.ERROR);
            faxJob.setStatusString("File missing on local storage or invalid file path.");
            return faxJob;
        }

        // Promote CARLOS-owned renderer/preview temp files into the permanent document store as the
        // last step before queueing: copyFileToOscarDocuments deletes the temp source on success, so
        // it must only run once every validation above has passed. Scoped to application-owned temp
        // subtrees (not the whole shared temp root) so a caller-supplied faxFilePath cannot promote
        // and fax out an unrelated file another process left in java.io.tmpdir or Tomcat work.
        if (PathValidationUtils.isInApplicationTempDirectory(faxDocument.toFile())) {
            String promoted = nioFileManager.copyFileToOscarDocuments(faxDocument.toString());
            // copyFileToOscarDocuments returns null when promotion fails. Follow the same controlled
            // error path as resolveAndValidateFilePath above — return an ERROR-status FaxJob rather
            // than passing null downstream (uncaught IllegalArgumentException → 500) or throwing.
            // The source file provably existed (validated above), so this is a storage-side
            // failure, not a missing file — say so, or the user checks the wrong thing.
            if (promoted == null || promoted.isBlank()) {
                faxJob.setStatus(STATUS.ERROR);
                faxJob.setStatusString("The fax document could not be stored for sending. Please retry or contact your administrator.");
                return faxJob;
            }
            faxDocument = Paths.get(promoted);
        }

        faxJob.setStatus(FaxJob.STATUS.WAITING);
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
        return addRecipients(loggedInInfo, faxJob, parseFaxRecipients(faxRecipients));
    }

    /**
     * Parses the indexed JSON recipient entries, failing fast when any entry is null, blank, or
     * unparseable — a partial recipient list must never be silently sent. Deliberately free of
     * side effects so {@link #createAndSaveFaxJob} can run it BEFORE the destructive temp-file
     * promotion in {@link #createFaxJob}.
     *
     * @throws IllegalArgumentException naming the failed entry count when any entry cannot be
     *         parsed into a {@link FaxRecipient}
     */
    private List<FaxRecipient> parseFaxRecipients(String[] faxRecipients) {

        List<FaxRecipient> faxRecipientArray = new ArrayList<FaxRecipient>();
        List<String> failedRecipients = new ArrayList<String>();

        for (String copytoRecipient : faxRecipients) {
            // Null/blank entries (e.g. a sparse Struts index array) are shape failures too:
            // silently skipping one is exactly the dropped-recipient bug the fail-fast below
            // exists to prevent.
            if (copytoRecipient == null || copytoRecipient.trim().isEmpty()) {
                failedRecipients.add(String.valueOf(copytoRecipient));
                continue;
            }
            // Assumes that the recipient entry is a JSONObject
            copytoRecipient = "{" + copytoRecipient + "}";
            try {
                ObjectNode copytoRecipientJson = (ObjectNode) objectMapper.readTree(copytoRecipient);
                FaxRecipient faxRecipient = new FaxRecipient(copytoRecipientJson);
                faxRecipientArray.add(faxRecipient);
            } catch (Exception e) {
                logger.error("Failed to parse fax recipient JSON: {} - Recipient will be SKIPPED", LogSafe.sanitize(copytoRecipient), e);
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
        return faxRecipientArray;
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

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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
        } catch (IOException | RuntimeException e) {
            // The cover target lives in the permanent document store; a failed concat must not
            // leave a partial PHI-bearing Cover_* file behind.
            try {
                Files.deleteIfExists(newCurrentDocument);
            } catch (IOException cleanupFailure) {
                logger.warn("Unable to remove partial cover page after failed concat: {}",
                        LogSafe.sanitize(newCurrentDocument.getFileName().toString()), cleanupFailure);
            }
            throw e;
        }
        return newCurrentDocument;
    }

    /**
     * Overload
     * Get preview image by specific page number.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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
        } else {
            // No source PDF on disk means no preview can be generated; surface it rather than returning
            // a silent null the caller may render as a broken image. The basename (a server-generated
            // temp/document name, not PHI) is what lets a busy system correlate this with its request.
            logger.warn("Fax preview source is missing; no preview image generated (file {}, page {})",
                    LogSafe.sanitize(filePath == null ? null : filePath.getFileName().toString()), pageNumber);
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
    // FindSecBugs PATH_TRAVERSAL_IN: the File is only used to test the application-temp boundary via
    // PathValidationUtils.isInApplicationTempDirectory before deletion; nioFileManager.deleteTempFile
    // re-validates the path independently.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    @Override
    public boolean flush(LoggedInInfo loggedInInfo, String filePath) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        // Preview page images are cached per source PDF as "<boundedName>_<sourceKey>_<page>.png", so
        // clearing them requires the same source-scoped prefix, not the raw PDF name — remove every page
        // for this source. With the multi-page CoverPage preview this can be many PNGs. The cached
        // pages are rendered images of the fax document (PHI): a removal failure must fail the
        // flush, not be reported as success with the images still on disk.
        File previewSource = (filePath == null || filePath.isBlank()) ? null : new File(filePath);
        int cachePagesRemoved = 0;
        boolean cacheCleared = true;
        if (previewSource != null && previewSource.getParent() != null) {
            try {
                cachePagesRemoved = nioFileManager.removeCacheVersions(loggedInInfo, previewSource.getParent(), previewSource.getName());
            } catch (IOException e) {
                // Per-page failures were already logged with their exceptions by removeCacheVersions.
                logger.error("Fax preview cache flush left cached page image(s) on disk: {}", e.getMessage());
                cacheCleared = false;
            } catch (IllegalArgumentException e) {
                // The preview source could not be keyed to an allowed preview location, so the
                // source-scoped page prefix is underivable and we cannot confirm the PHI preview
                // pages were removed. Treat an unkeyable source as an uncleared cache, never success.
                logger.error("Fax preview cache flush could not key its source directory: {}", e.getMessage());
                cacheCleared = false;
            }
        }

        // Only a CARLOS-owned temp artifact is eligible for temp deletion here. Guarding on the
        // application temp boundary keeps a non-temp filePath (e.g. a DOCUMENT_DIR path passed by the
        // fax cancel flow) from raising a SecurityException out of deleteTempFile. The validated
        // canonical path is what gets deleted, so the checked file and the deleted file cannot
        // diverge through a symlink.
        File validatedTemp = null;
        boolean tempResolutionFailed = false;
        if (filePath != null && !filePath.isBlank()) {
            File candidateTemp = new File(filePath);
            try {
                validatedTemp = PathValidationUtils.validateApplicationTempPath(candidateTemp);
            } catch (SecurityException e) {
                // validateApplicationTempPath folds two different failures into the same
                // SecurityException type: (a) a path legitimately outside every CARLOS-owned temp
                // subtree (e.g. a DOCUMENT_DIR document — nothing to delete here, safe to report
                // success) versus (b) a canonicalization failure (e.g. a broken/looping symlink) on
                // a path that could be a REAL temp artifact we simply could not verify. Reporting
                // success for (b) would leave PHI on disk. File.exists() cannot distinguish these:
                // a DOCUMENT_DIR fax source is a real, persisted patient document and routinely
                // exists on disk, so "does the file exist" would misclassify the common, benign
                // case (a) as an unverifiable artifact and fail every such flush. Re-resolving the
                // canonical path here — independent of validateApplicationTempPath's internal
                // prefix check — tells us which failure actually occurred: if it succeeds now, the
                // original failure was the boundary check (a); if it also throws, canonicalization
                // itself is the problem (b).
                boolean canonicalizes;
                try {
                    candidateTemp.getCanonicalPath();
                    canonicalizes = true;
                } catch (IOException canonicalizationError) {
                    canonicalizes = false;
                }
                if (!canonicalizes) {
                    tempResolutionFailed = true;
                    logger.warn("Fax flush could not canonicalize a path to verify it as a temp artifact: {}", e.getMessage());
                } else {
                    logger.debug("Fax flush skipped non-temp path: {}", e.getMessage());
                }
            }
        }
        boolean tempExisted = validatedTemp != null && validatedTemp.exists();
        boolean tempDeleted = tempExisted && nioFileManager.deleteTempFile(validatedTemp.getPath());

        if (logger.isDebugEnabled()) {
            logger.debug("Fax preview flush: cachePagesRemoved={} cacheCleared={} tempExisted={} tempDeleted={} tempResolutionFailed={}",
                    cachePagesRemoved, cacheCleared, tempExisted, tempDeleted, tempResolutionFailed);
        }
        // Success means everything that existed was removed — cached PHI preview pages included.
        // "Nothing to clear" — the preview was never rendered, was already flushed, or the path is
        // a DOCUMENT_DIR document with no temp artifact — is success, not an error for the
        // fax-cancel flow to alarm the user about. But a still-existing path we could not verify as
        // a temp artifact must NOT report success: that would leave an unverified PHI preview image
        // on disk while telling the caller the flush succeeded.
        return cacheCleared && !tempResolutionFailed && (!tempExisted || tempDeleted);
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
            logger.error("Cannot resend fax: no fax job found for id {}", LogSafe.sanitize(jobId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    @Override
    public void validateFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path must not be null or empty");
        }

        // Check for path traversal patterns
        if (filePath.contains("..") || filePath.contains("~")) {
            logger.error("Path traversal attempt detected: {}", LogSafe.sanitize(filePath)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            throw new SecurityException("Invalid file path detected: path traversal patterns not allowed");
        }

        File file = new File(filePath);
        // Accept CARLOS-owned temp previews only, not the entire shared temp root, so a caller
        // cannot name an unrelated temp file for preview/fax.
        if (PathValidationUtils.isInApplicationTempDirectory(file)) {
            return;
        }

        // Use PathValidationUtils for document-root validation only after the temp-root fast path.
        File documentDir = new File(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"));
        PathValidationUtils.validateExistingPath(file, documentDir);
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    @Override
    public Path resolveAndValidateFilePath(String filePath) throws IOException {
        // First validate with existing security checks
        validateFilePath(filePath);

        File file = new File(filePath);
        Path resolvedPath;
        // Accept CARLOS-owned temp previews only, not the entire shared temp root.
        if (PathValidationUtils.isInApplicationTempDirectory(file)) {
            resolvedPath = file.getCanonicalFile().toPath();
        } else {
            File documentDir = new File(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"));
            resolvedPath = PathValidationUtils.validateExistingPath(file, documentDir).toPath();
        }

        resolvedPath = resolvedPath.normalize();

        // Ensure the file exists and is a regular file
        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            logger.error("File not found or is not a regular file: {}", LogSafe.sanitize(filePath)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
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
                // faxNumber failed format validation, so it may carry injection/control chars — sanitize.
                logger.error("{} - {}", errorMsg, LogSafe.sanitize(faxNumber));
                throw new SecurityException(errorMsg);
            }
        }
    }


}
