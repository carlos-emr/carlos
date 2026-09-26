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


package io.github.carlos_emr.carlos.lab.ca.bc.PathNet.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.Connection;
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.HL7.Message;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class LabUpload2Action extends ActionSupport implements UploadedFilesAware {
    private static final String REQUEST_ATTRIBUTE_OUTCOME = "outcome";
    private static final String OUTCOME_EXCEPTION = "exception";

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    Logger _logger = MiscUtils.getLogger();

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }
        if (uploadValidationError != null) {
            addActionError(uploadValidationError);
            request.setAttribute(REQUEST_ATTRIBUTE_OUTCOME, OUTCOME_EXCEPTION);
            return SUCCESS;
        }

        String filename = "";
        String proNo = (String) request.getSession().getAttribute("user");
        String outcome = "";

        try {
            // Validate the uploaded file to prevent path traversal attacks
            if (importFile == null) {
                _logger.error("No file provided for upload");
                outcome = OUTCOME_EXCEPTION;
                request.setAttribute(REQUEST_ATTRIBUTE_OUTCOME, outcome);
                return SUCCESS;
            }

            // Validate file is from an allowed temp directory
            try {
                importFile = PathValidationUtils.validateUpload(importFile);
            } catch (SecurityException e) {
                _logger.error("Invalid upload source - potential path traversal: " + importFile.getPath());
                outcome = OUTCOME_EXCEPTION;
                request.setAttribute(REQUEST_ATTRIBUTE_OUTCOME, outcome);
                return SUCCESS;
            }

            MiscUtils.getLogger().debug("Lab Upload content type = " + importFile.getName());
            // Re-validate at point of use for static analysis visibility
            File validatedImportFile = PathValidationUtils.validateUpload(importFile);
            filename = uploadedFileName == null ? importFile.getName() : uploadedFileName;

            // Snapshot the upload once. The duplicate check, parser, and archive writer each consume
            // a stream, and the file stream cannot be reset; reopening the path per reader could also
            // hash one version of the file while importing or archiving another. Struts caps
            // multipart uploads (struts.multipart.maxSize), which bounds this buffer.
            byte[] uploadContent;
            try (InputStream uploadStream = PathValidationUtils.openValidatedUploadInputStream(validatedImportFile)) {
                uploadContent = uploadStream.readAllBytes();
            }

            // storeIfNew records the checksum in the same transaction as the messages' rows, so a
            // failure leaves neither and a retry stores the batch; a real duplicate is refused. It
            // holds the checksum lock throughout, so a concurrent upload of the same file is never
            // told uploadedPreviously for a batch that then rolls back.
            FileUploadCheck.StoreOutcome stored;
            // The archive written in the store step, cleared only if a confirmed rollback removed it.
            AtomicReference<File> keptArchive = new AtomicReference<>();
            try {
                String archiveName = filename;
                stored = FileUploadCheck.storeIfNew(filename, () -> new ByteArrayInputStream(uploadContent), proNo,
                        checksumId -> storeMessages(uploadContent)
                                && archiveInTransaction(uploadContent, archiveName, keptArchive));
            } catch (FileUploadCheck.LookupFailedException lookupEx) {
                _logger.error("Could not check a PathNet upload's checksum: {}", LogSafe.exceptionTrace(lookupEx.getCause()));
                // Preserve failed bytes through the diagnostic archive path below.
                // A failed lookup has neither claimed the checksum nor parsed the lab.
                stored = null;
            } catch (Exception ex) {
                _logger.error("PathNet upload could not be stored: {}", LogSafe.exceptionTrace(ex));
                stored = null;
            }
            if (stored == FileUploadCheck.StoreOutcome.ALREADY_RECORDED) {
                outcome = "uploadedPreviously";
            } else if (stored == FileUploadCheck.StoreOutcome.STORED) {
                // Archived inside the committed transaction; nothing may fail after the commit.
                outcome = "success";
            } else {
                outcome = OUTCOME_EXCEPTION;
                // A batch that was not stored is still archived for diagnosis, as before; the
                // outcome is a failure either way, so a failed write here changes nothing. Skipped
                // when the store step's archive survived: the commit outcome is then unknown and
                // the batch may be stored, so a second copy would duplicate it.
                if (keptArchive.get() == null) {
                    saveFile(new ByteArrayInputStream(uploadContent), filename);
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            outcome = OUTCOME_EXCEPTION;
        }
        request.setAttribute(REQUEST_ATTRIBUTE_OUTCOME, outcome);
        return SUCCESS;
    }


    public LabUpload2Action() {
    }

    /**
     * Parses every message in the upload and writes it to the database. Runs inside
     * {@link FileUploadCheck#storeIfNew}'s transaction, so the rows of every message and the
     * checksum commit together; a failing message rolls back the ones before it.
     *
     * @param uploadContent the upload's bytes
     * @return {@code false} when the upload holds no messages, which rejects it
     * @throws Exception when a message cannot be parsed or stored
     */
    private static boolean storeMessages(byte[] uploadContent) throws Exception {
        ArrayList<String> messages = new Connection().Retrieve(new ByteArrayInputStream(uploadContent));
        // Retrieve answers null for an unreadable batch and an empty list for one declaring zero
        // messages; neither stored anything, so neither may commit a checksum that refuses a retry.
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        for (int i = 0; i < messages.size(); i++) {
            Message message = new Message(now);
            message.Parse(messages.get(i));
            message.ToDatabase();
        }
        return true;
    }


    /**
     * Archives a stored batch inside {@link FileUploadCheck#storeIfNew}'s transaction.
     *
     * <p>Written after the messages, before the commit: a failed write rejects the batch, rolling
     * back its messages and checksum so the client's retry stores it, instead of committing a lab
     * with no archive and answering a retryable {@code exception} that the retry then refuses as
     * {@code uploadedPreviously}. If the transaction later rolls back, the archive is removed with
     * the rows; after a commit, or a commit whose outcome is unknown, it is kept.</p>
     *
     * @param kept receives the archive written; cleared again when a rollback removes it
     * @return {@code false} when the archive could not be written, which rejects the batch
     */
    private static boolean archiveInTransaction(byte[] uploadContent, String filename, AtomicReference<File> kept) {
        File archived = saveFile(new ByteArrayInputStream(uploadContent), filename, kept);
        if (archived == null) {
            return false;
        }
        kept.set(archived);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    // Cleared only once the file is really gone: if the delete fails the archive is
                    // still on disk, and the failure path must not write a second copy beside it.
                    if (status == STATUS_ROLLED_BACK && deletePartialOutput(archived)) {
                        kept.set(null);
                    }
                }
            });
        }
        return true;
    }

    /**
     * Writes the upload to {@code DOCUMENT_DIR} under a new, generated name.
     *
     * @return the complete file, or {@code null} on failure (a partial file may remain if cleanup fails)
     */
    private static File saveFile(InputStream stream, String filename) {
        return saveFile(stream, filename, new AtomicReference<>());
    }

    /** Also retains an owned partial file when cleanup fails, preventing a second archive. */
    private static File saveFile(InputStream stream, String filename, AtomicReference<File> kept) {
        File outputFile = null;
        boolean created = false;

        try (InputStream uploadStream = stream) {
            //retrieve the file data
            // ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //InputStream stream = file.getInputStream();
            CarlosProperties props = CarlosProperties.getInstance();

            //properties must exist
            String place = props.getProperty("DOCUMENT_DIR");

            File baseDir = PathValidationUtils.resolveConfiguredDirectory(place, "PathNet lab upload directory");
            outputFile = PathValidationUtils.validateGeneratedChildPath(
                    PathValidationUtils.validateGeneratedFileName("LabUpload." + filename + "." + (new Date()).getTime()),
                    baseDir);
            // CREATE_NEW: the generated name is only millisecond-unique and a truncating open
            // destroyed the colliding upload's lab.
            try (OutputStream bos = Files.newOutputStream(outputFile.toPath(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                kept.set(outputFile);
                uploadStream.transferTo(bos);
            }
        } catch (FileAlreadyExistsException nameCollision) {
            MiscUtils.getLogger().error("Generated lab upload name is already in use; upload not written");
            return null;
        } catch (IOException | SecurityException ioe) {
            // Only a successful CREATE_NEW establishes ownership. An open failure may name
            // somebody else's file. Retain an undeletable partial output to suppress a second copy.
            if (created && deletePartialOutput(outputFile)) {
                kept.set(null);
            }
            // exceptionTrace: the message of a filesystem exception here is the generated path,
            // whose basename embeds the caller-supplied lab filename.
            MiscUtils.getLogger().error("Error writing PathNet lab upload: {}", LogSafe.exceptionTrace(ioe));
            return null;
        }

        return outputFile;
    }

    /**
     * Removes a partially written or rolled-back upload. Only ever called for a destination this
     * invocation created exclusively via {@code CREATE_NEW}, so it cannot discard another upload's output.
     *
     * @param outputFile the destination to remove, or {@code null} if none was created
     * @return {@code true} when no such file remains
     */
    private static boolean deletePartialOutput(File outputFile) {
        if (outputFile == null) {
            return true;
        }
        try {
            Files.deleteIfExists(outputFile.toPath());
            return true;
        } catch (IOException | SecurityException deleteException) {
            MiscUtils.getLogger().error("Error deleting partial lab upload output ({})",
                    deleteException.getClass().getSimpleName());
            return false;
        }
    }

    private File importFile;
    private String uploadedFileName;
    private String uploadValidationError;

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.importFile = PathValidationUtils.validateUploadContent(uploaded.getContent());
            try {
                PathValidationUtils.validateStrictFileName(uploaded.getOriginalName());
                this.uploadedFileName = uploaded.getOriginalName();
            } catch (FileValidationException e) {
                this.uploadValidationError = PathValidationUtils.INVALID_FILENAME_MESSAGE;
            }
        }
    }

    public File getImportFile() {
        return importFile;
    }

    public void setImportFile(File importFile) {
        this.importFile = importFile;
    }
}
