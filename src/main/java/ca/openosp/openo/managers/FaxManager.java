//CHECKSTYLE:OFF
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

import com.fasterxml.jackson.databind.node.ObjectNode;
import ca.openosp.openo.commn.dao.FaxConfigDao;
import ca.openosp.openo.commn.model.FaxConfig;
import ca.openosp.openo.commn.model.FaxJob;
import ca.openosp.openo.fax.core.FaxAccount;
import ca.openosp.openo.fax.core.FaxRecipient;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.form.util.FormTransportContainer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface FaxManager {

    enum TransactionType {CONSULTATION, EFORM, FORM, RX, DOCUMENT}

    Path renderFaxDocument(LoggedInInfo loggedInInfo, TransactionType transactionType, FormTransportContainer formTransportContainer);

    Path renderFaxDocument(LoggedInInfo loggedInInfo, TransactionType transactionType, int transactionId, int demographicNo);

    /**
     * @return
     * @Deprecated Move these rendering methods into a more generic class like the DocumentManager
     */
    Path renderFaxDocument(LoggedInInfo loggedInInfo, TransactionType transactionType, int transactionId, int demographicNo, FormTransportContainer formTransportContainer);

    Path renderConsultationRequest(LoggedInInfo loggedInInfo, int requestId, int demographicNo);

    Path renderDocument(LoggedInInfo loggedInInfo, int documentNo, int demographicNo);

    Path renderEform(LoggedInInfo loggedInInfo, int eformId, int demographicNo);

    Path renderPrescription(LoggedInInfo loggedInInfo, int rxId, int demographicNo);

    Path renderForm(LoggedInInfo loggedInInfo, int formId, int demographicNo);

    Path renderForm(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer);

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
    List<FaxJob> createAndSaveFaxJob(LoggedInInfo loggedInInfo, Map<String, Object> faxJobMap);

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
    FaxJob createFaxJob(LoggedInInfo loggedInInfo, Map<String, Object> faxJobMap);

    /**
     * Add recipients from an indexed array of JSON formatted strings
     * name:<recipient>
     * fax:<recipient fax number>
     */
    List<FaxJob> addRecipients(LoggedInInfo loggedInInfo, FaxJob faxJob, String[] faxRecipients);

    /**
     * Create 1 faxJob for each fax recipient. Sets each faxJob to the
     * default status of WAITNG.
     */
    List<FaxJob> addRecipients(LoggedInInfo loggedInInfo, FaxJob faxJob, List<FaxRecipient> faxRecipients);

    /**
     * Persist to the database for transmission later if the fax account is valid.
     * <p>
     * The given faxjob must contain a valid sender fax number and username.
     */
    List<FaxJob> saveFaxJob(LoggedInInfo loggedInInfo, List<FaxJob> faxJobList);

    /**
     * Create new or update fax job.
     */
    FaxJob saveFaxJob(LoggedInInfo loggedInInfo, FaxJob faxJob);

    /**
     * prepend a fax cover page to the given existing PDF document.
     */
    Path addCoverPage(LoggedInInfo loggedInInfo, String note, Path currentDocument) throws IOException;

    Path addCoverPage(LoggedInInfo loggedInInfo, String note, FaxRecipient recipient, FaxAccount sender, Path currentDocument) throws IOException;

    /**
     * Overload
     * Get preview image by specific page number.
     */
    Path getFaxPreviewImage(LoggedInInfo loggedInInfo, String filePath, int pageNumber);

    /**
     * Overload
     * Get a preview image of the documents being faxed.  Default is
     * the first page only
     */
    Path getFaxPreviewImage(LoggedInInfo loggedInInfo, String filePath);

    /**
     * Get a preview image of the documents being faxed.  Default is
     * the first page only
     */
    Path getFaxPreviewImage(LoggedInInfo loggedInInfo, Path filePath);

    /**
     * Get preview image by specific page number.
     */
    Path getFaxPreviewImage(LoggedInInfo loggedInInfo, Path filePath, int pageNumber);

    /**
     * Sets both the global user log and the fax job log.
     */
    void logFaxJob(LoggedInInfo loggedInInfo, FaxJob faxJob, TransactionType transactionType, int transactionId);

    /**
     * Update the transaction logs with a new status.
     */
    void updateFaxLog(LoggedInInfo loggedInInfo, FaxJob faxJob);

    /**
     * Returns all the active sender accounts in this system.
     */
    List<FaxConfig> getFaxGatewayAccounts(LoggedInInfo loggedInInfo);

    List<FaxConfig> getFaxConfigurationAccounts(LoggedInInfo loggedInInfo);

    /**
     * Get all fax jobs with a waiting to be sent status by
     * sender fax number.
     */
    List<FaxJob> getOutGoingFaxes(LoggedInInfo loggedInInfo, String senderFaxNumber);

    /**
     * Clear the preview cache and temp directory.
     */
    boolean flush(LoggedInInfo loggedInInfo, String filePath);

    /**
     * Check if fax services are enabled.
     */

    FaxJob getFaxJob(LoggedInInfo loggedInInfo, int jobId);

    /**
     * Returns the actual page count of this PDF document instead of
     * depending on the value that is placed in the database table.
     * Important for when faxes are merged or when a cover page is added.
     */
    int getPageCount(LoggedInInfo loggedInInfo, int jobId);

    /**
     * Faxes can be resent by the user if the fax contains a status of
     * ERROR or COMPLETE.  The fax status of the original fax will be changed to
     * RESENT and cannot be resent again.
     */
    boolean resendFax(LoggedInInfo loggedInInfo, String jobId, String destination);

    public void restartFaxScheduler(LoggedInInfo loggedInInfo);

    public ObjectNode getFaxSchedularStatus(LoggedInInfo loggedInInfo);

    /**
     * Validates that a file path is safe and within allowed directories.
     * Prevents path traversal attacks by checking for malicious patterns and
     * validating the path is within whitelisted directories.
     *
     * @param filePath the file path to validate
     * @throws SecurityException if the path is invalid or outside allowed directories
     */
    void validateFilePath(String filePath);

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
    Path resolveAndValidateFilePath(String filePath) throws IOException;

    /**
     * Validates a fax number format.
     * Ensures the fax number contains only valid characters: digits, spaces, hyphens, plus sign, and parentheses.
     *
     * @param faxNumber the fax number to validate
     * @param fieldName the name of the field being validated (for error messages)
     * @throws SecurityException if the fax number format is invalid
     */
    void validateFaxNumber(String faxNumber, String fieldName);

    /**
     * Check if fax services are enabled.
     */
    public static boolean isEnabled() {

        FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
        List<FaxConfig> accounts = faxConfigDao.findAll(0, null);
        for (FaxConfig account : accounts) {
            if (account.isActive()) {
                return Boolean.TRUE;
            }

        }
        return Boolean.FALSE;
    }
}
