/**
 * Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
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
 * This software was written for
 * CloudPractice Inc.
 * Victoria, British Columbia
 * Canada
 *
 * Ported to CARLOS EMR from JunoEMR (2026).
 */
package io.github.carlos_emr.carlos.fax.connector.srfax;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.fax.connector.srfax.result.GetFaxInboxResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.result.GetFaxOutboxResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.result.GetFaxStatusResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.result.GetUsageResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.resultWrapper.ListWrapper;
import io.github.carlos_emr.carlos.fax.connector.srfax.resultWrapper.SingleWrapper;
import io.github.carlos_emr.carlos.fax.exception.FaxApiConnectionException;
import io.github.carlos_emr.carlos.fax.exception.FaxApiValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Low-level SRFax API client that communicates directly with the SRFax cloud service
 * at {@code https://www.srfax.com/SRF_SecWebSvc.php}.
 * <p>
 * Supports all SRFax API actions: Queue_Fax, Get_FaxStatus, Get_MultiFaxStatus,
 * Get_Fax_Inbox, Get_Fax_Outbox, Retrieve_Fax, Update_Viewed_Status, Delete_Fax,
 * Stop_Fax, and Get_Fax_Usage.
 *
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
public class SRFaxApiConnector {

    private static final Logger logger = MiscUtils.getLogger();
    /** Shared Jackson ObjectMapper instance for JSON serialization/deserialization. Thread-safe. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** SRFax API endpoint URL. All API calls are HTTP POST to this URL. */
    private static final String SERVER_URL = "https://www.srfax.com/SRF_SecWebSvc.php";

    // -- SRFax API parameter names --
    private static final String ACCESS_ID = "access_id";
    private static final String ACCESS_PW = "access_pwd";
    private static final String ACTION = "action";
    private static final String ACTION_QUEUE_FAX = "Queue_Fax";
    private static final String ACTION_GET_FAX_STATUS = "Get_FaxStatus";
    private static final String ACTION_GET_MULTI_FAX_STATUS = "Get_MultiFaxStatus";
    private static final String ACTION_GET_FAX_INBOX = "Get_Fax_Inbox";
    private static final String ACTION_GET_FAX_OUTBOX = "Get_Fax_Outbox";
    private static final String ACTION_RETRIEVE_FAX = "Retrieve_Fax";
    private static final String ACTION_UPDATE_VIEWED_STATUS = "Update_Viewed_Status";
    private static final String ACTION_DELETE_FAX = "Delete_Fax";
    private static final String ACTION_STOP_FAX = "Stop_Fax";
    private static final String ACTION_GET_FAX_USAGE = "Get_Fax_Usage";

    private static final String S_CALLER_ID = "sCallerID";
    private static final String S_SENDER_EMAIL = "sSenderEmail";
    private static final String S_FAX_TYPE = "sFaxType";
    private static final String S_TO_FAX_NUMBER = "sToFaxNumber";
    private static final String S_RESPONSE_FORMAT = "sResponseFormat";
    private static final String S_ACCOUNT_CODE = "sAccountCode";
    private static final String S_RETRIES = "sRetries";
    private static final String S_COVER_PAGE = "sCoverPage";
    private static final String S_CP_FROM_NAME = "sCPFromName";
    private static final String S_CP_TO_NAME = "sCPToName";
    private static final String S_CP_ORGANIZATION = "sCPOrganization";
    private static final String S_CP_SUBJECT = "sCPSubject";
    private static final String S_CP_COMMENTS = "sCPComments";
    private static final String S_FILE_NAME_BASE = "sFileName_";
    private static final String S_FILE_NAME_WILDCARD = S_FILE_NAME_BASE + "*";
    private static final String S_FILE_CONTENT_BASE = "sFileContent_";
    private static final String S_FILE_CONTENT_WILDCARD = S_FILE_CONTENT_BASE + "*";
    private static final String S_NOTIFY_URL = "sNotifyURL";
    private static final String S_FAX_FROM_HEADER = "sFaxFromHeader";
    private static final String S_QUEUE_FAX_DATE = "sQueueFaxDate";
    private static final String S_QUEUE_FAX_TIME = "sQueueFaxTime";
    private static final String S_PERIOD = "sPeriod";
    private static final String S_START_DATE = "sStartDate";
    private static final String S_END_DATE = "sEndDate";
    private static final String S_INCLUDE_SUB_USERS = "sIncludeSubUsers";
    private static final String S_FAX_DETAILS_ID = "sFaxDetailsID";
    private static final String S_FAX_FILE_NAME = "sFaxFileName";
    private static final String S_VIEWED_STATUS = "sViewedStatus";
    private static final String S_DIRECTION = "sDirection";
    private static final String S_MARKAS_VIEWED = "sMarkasViewed";
    private static final String S_FAX_FORMAT = "sFaxFormat";
    private static final String S_SUB_USER_ID = "sSubUserID";

    public static final String DATE_FORMAT = "yyyyMMdd";

    public static final String RESPONSE_FORMAT_JSON = "JSON";
    public static final String PERIOD_ALL = "ALL";
    public static final String PERIOD_RANGE = "RANGE";
    public static final String FAX_TYPE_SINGLE = "SINGLE";
    public static final String RETRIEVE_DIRECTION_IN = "IN";
    public static final String RETRIEVE_DOC_FORMAT = "PDF";
    public static final String RETRIEVE_DONT_CHANGE_STATUS = "N";
    public static final String MARK_AS_READ = "Y";
    public static final String VIEWED_STATUS_UNREAD = "UNREAD";

    /** Immutable list of valid SRFax cover letter template names. */
    public static final List<String> validCoverLetterNames = Collections.unmodifiableList(
            Arrays.asList("Basic", "Standard", "Company", "Personal"));

    public static final String RESPONSE_STATUS_SENT = "Sent";
    public static final String RESPONSE_STATUS_FAILED = "Failed";
    public static final String RESPONSE_STATUS_PROGRESS = "In Progress";
    /** Immutable list of terminal fax delivery statuses. */
    public static final List<String> RESPONSE_STATUSES_FINAL = Collections.unmodifiableList(
            Arrays.asList(RESPONSE_STATUS_SENT, RESPONSE_STATUS_FAILED));

    /** SRFax account login ID (typically the fax number or email). */
    private final String access_id;
    /** SRFax account password. */
    private final String access_pwd;

    /**
     * Constructs a new SRFaxApiConnector with the given credentials.
     *
     * @param username String the SRFax account login ID
     * @param password String the SRFax account password
     */
    public SRFaxApiConnector(String username, String password) {
        this.access_id = username;
        this.access_pwd = password;
    }

    // -- Queue Fax --

    /**
     * Internal queue fax method that sends the raw parameter map to the SRFax Queue_Fax API.
     *
     * @param parameters Map containing all SRFax API parameters for the Queue_Fax action
     * @return SingleWrapper containing the fax details ID (Integer) on success
     */
    private SingleWrapper<Integer> queueFax(Map<String, String> parameters) {
        String[] requiredFields = {S_CALLER_ID, S_SENDER_EMAIL, S_FAX_TYPE, S_TO_FAX_NUMBER};
        String[] optionalFields = {
                S_RESPONSE_FORMAT, S_ACCOUNT_CODE, S_RETRIES, S_COVER_PAGE,
                S_CP_FROM_NAME, S_CP_TO_NAME, S_CP_ORGANIZATION,
                S_CP_SUBJECT, S_CP_COMMENTS, S_FILE_NAME_WILDCARD, S_FILE_CONTENT_WILDCARD,
                S_NOTIFY_URL, S_FAX_FROM_HEADER, S_QUEUE_FAX_DATE, S_QUEUE_FAX_TIME
        };
        String result = processRequest(ACTION_QUEUE_FAX, requiredFields, optionalFields, parameters);
        return processSingleResponse(result);
    }

    /**
     * Queue a fax with all available SRFax API options.
     *
     * @param sCallerID String the sender's caller ID (fax number)
     * @param sSenderEmail String the sender's email address for notifications
     * @param sFaxType String the fax type (use {@link #FAX_TYPE_SINGLE} for standard faxes)
     * @param sToFaxNumber String the destination fax number
     * @param sFileMap Map of filename to base64-encoded content pairs to attach
     * @param sResponseFormat String response format (use {@link #RESPONSE_FORMAT_JSON})
     * @param sAccountCode String optional account code for billing
     * @param sRetries String optional number of retry attempts
     * @param sCoverPage String optional cover page template name (see {@link #validCoverLetterNames})
     * @param sCPFromName String optional cover page "From" name
     * @param sCPToName String optional cover page "To" name
     * @param sCPOrganization String optional cover page organization name
     * @param sCPSubject String optional cover page subject line
     * @param sCPComments String optional cover page comments
     * @param sNotifyURL String optional webhook URL for delivery notifications
     * @param sFaxFromHeader String optional fax header override
     * @param sQueueFaxDate String optional scheduled send date (yyyyMMdd)
     * @param sQueueFaxTime String optional scheduled send time (HH:mm)
     * @return SingleWrapper containing the fax details ID (Integer) on success
     * @throws FaxApiValidationException if required parameters are missing
     * @throws FaxApiConnectionException if the SRFax API cannot be reached
     */
    public SingleWrapper<Integer> queueFax(String sCallerID, String sSenderEmail, String sFaxType, String sToFaxNumber,
                                           Map<String, String> sFileMap,
                                           String sResponseFormat, String sAccountCode, String sRetries,
                                           String sCoverPage, String sCPFromName, String sCPToName,
                                           String sCPOrganization, String sCPSubject, String sCPComments,
                                           String sNotifyURL, String sFaxFromHeader,
                                           String sQueueFaxDate, String sQueueFaxTime) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(S_CALLER_ID, sCallerID);
        parameters.put(S_SENDER_EMAIL, sSenderEmail);
        parameters.put(S_FAX_TYPE, sFaxType);
        parameters.put(S_TO_FAX_NUMBER, sToFaxNumber);

        if (sFileMap != null) {
            int counter = 1;
            for (Map.Entry<String, String> entry : sFileMap.entrySet()) {
                parameters.put(S_FILE_NAME_BASE + counter, entry.getKey());
                parameters.put(S_FILE_CONTENT_BASE + counter, entry.getValue());
                counter++;
            }
        }
        putIfPresent(parameters, S_RESPONSE_FORMAT, sResponseFormat);
        putIfPresent(parameters, S_ACCOUNT_CODE, sAccountCode);
        putIfPresent(parameters, S_RETRIES, sRetries);
        putIfPresent(parameters, S_COVER_PAGE, sCoverPage);
        putIfPresent(parameters, S_CP_FROM_NAME, sCPFromName);
        putIfPresent(parameters, S_CP_TO_NAME, sCPToName);
        putIfPresent(parameters, S_CP_ORGANIZATION, sCPOrganization);
        putIfPresent(parameters, S_CP_SUBJECT, sCPSubject);
        putIfPresent(parameters, S_CP_COMMENTS, sCPComments);
        putIfPresent(parameters, S_NOTIFY_URL, sNotifyURL);
        putIfPresent(parameters, S_FAX_FROM_HEADER, sFaxFromHeader);
        putIfPresent(parameters, S_QUEUE_FAX_DATE, sQueueFaxDate);
        putIfPresent(parameters, S_QUEUE_FAX_TIME, sQueueFaxTime);

        return queueFax(parameters);
    }

    /**
     * Queue a standard single fax with full cover letter customization.
     * <p>
     * Convenience overload that defaults to {@link #FAX_TYPE_SINGLE} and {@link #RESPONSE_FORMAT_JSON},
     * while allowing all cover letter fields (from name, to name, organization, subject, comments)
     * and a custom fax-from header.
     *
     * @param sCallerID String the sender's caller ID (fax number)
     * @param sSenderEmail String the sender's email address for notifications
     * @param sToFaxNumber String the destination fax number
     * @param sFileMap Map of filename to base64-encoded content pairs to attach
     * @param sCoverPage String cover page template name (see {@link #validCoverLetterNames}), or null
     * @param sCPFromName String cover page "From" name, or null
     * @param sCPToName String cover page "To" name, or null
     * @param sCPOrganization String cover page organization name, or null
     * @param sCPSubject String cover page subject line, or null
     * @param sCPComments String cover page comments, or null
     * @param sFaxFromHeader String custom fax-from header override, or null
     * @return SingleWrapper containing the fax details ID (Integer) on success
     */
    public SingleWrapper<Integer> queueFax(String sCallerID, String sSenderEmail, String sToFaxNumber,
                                           Map<String, String> sFileMap,
                                           String sCoverPage, String sCPFromName, String sCPToName,
                                           String sCPOrganization, String sCPSubject, String sCPComments,
                                           String sFaxFromHeader) {
        return queueFax(sCallerID, sSenderEmail, FAX_TYPE_SINGLE, sToFaxNumber, sFileMap, RESPONSE_FORMAT_JSON,
                null, null, sCoverPage, sCPFromName, sCPToName, sCPOrganization, sCPSubject, sCPComments,
                null, sFaxFromHeader, null, null);
    }

    /**
     * Queue a standard single fax with a basic cover letter template.
     * <p>
     * Convenience overload that defaults to {@link #FAX_TYPE_SINGLE} and {@link #RESPONSE_FORMAT_JSON}
     * with only a cover page template name. No cover letter field customization is available.
     *
     * @param sCallerID String the sender's caller ID (fax number)
     * @param sSenderEmail String the sender's email address for notifications
     * @param sToFaxNumber String the destination fax number
     * @param sFileMap Map of filename to base64-encoded content pairs to attach
     * @param sCoverPage String cover page template name (see {@link #validCoverLetterNames}), or null for none
     * @return SingleWrapper containing the fax details ID (Integer) on success
     */
    public SingleWrapper<Integer> queueFax(String sCallerID, String sSenderEmail, String sToFaxNumber,
                                           Map<String, String> sFileMap, String sCoverPage) {
        return queueFax(sCallerID, sSenderEmail, FAX_TYPE_SINGLE, sToFaxNumber, sFileMap, RESPONSE_FORMAT_JSON,
                null, null, sCoverPage, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Queue a standard single fax with no cover letter.
     * <p>
     * Simplest convenience overload: defaults to {@link #FAX_TYPE_SINGLE} and
     * {@link #RESPONSE_FORMAT_JSON} with no cover page or optional parameters.
     * This is the overload used by {@link SRFaxConnector#sendFax} when no cover letter is configured.
     *
     * @param sCallerID String the sender's caller ID (fax number)
     * @param sSenderEmail String the sender's email address for notifications
     * @param sToFaxNumber String the destination fax number
     * @param sFileMap Map of filename to base64-encoded content pairs to attach
     * @return SingleWrapper containing the fax details ID (Integer) on success
     */
    public SingleWrapper<Integer> queueFax(String sCallerID, String sSenderEmail, String sToFaxNumber,
                                           Map<String, String> sFileMap) {
        return queueFax(sCallerID, sSenderEmail, FAX_TYPE_SINGLE, sToFaxNumber, sFileMap, RESPONSE_FORMAT_JSON,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    // -- Fax Status --

    /**
     * Query the delivery status of a single fax by its details ID.
     *
     * @param sFaxDetailsID String the SRFax details ID of the fax to check
     * @return SingleWrapper containing the {@link GetFaxStatusResult} on success
     */
    public SingleWrapper<GetFaxStatusResult> getFaxStatus(String sFaxDetailsID) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(S_FAX_DETAILS_ID, sFaxDetailsID);
        parameters.put(S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);

        String[] requiredFields = {S_FAX_DETAILS_ID};
        String[] optionalFields = {S_RESPONSE_FORMAT};
        String result = processRequest(ACTION_GET_FAX_STATUS, requiredFields, optionalFields, parameters);
        return processSingleResponse(result, new TypeReference<SingleWrapper<GetFaxStatusResult>>() {});
    }

    /**
     * Query the delivery status of multiple faxes in a single API call.
     *
     * @param sFaxDetailsIDList List of SRFax details IDs to check
     * @return ListWrapper containing {@link GetFaxStatusResult} entries
     */
    public ListWrapper<GetFaxStatusResult> getMultiFaxStatus(List<String> sFaxDetailsIDList) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(S_FAX_DETAILS_ID, String.join("|", sFaxDetailsIDList));
        parameters.put(S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);

        String[] requiredFields = {S_FAX_DETAILS_ID};
        String[] optionalFields = {S_RESPONSE_FORMAT};
        String result = processRequest(ACTION_GET_MULTI_FAX_STATUS, requiredFields, optionalFields, parameters);
        return processListResponse(result, new TypeReference<ListWrapper<GetFaxStatusResult>>() {});
    }

    // -- Inbox / Outbox --

    /**
     * Retrieve the list of incoming faxes from the SRFax inbox.
     *
     * @param sPeriod String the time period filter ({@link #PERIOD_ALL} or {@link #PERIOD_RANGE})
     * @param sStartDate String start date in yyyyMMdd format (required for RANGE period)
     * @param sEndDate String end date in yyyyMMdd format (required for RANGE period)
     * @param sViewedStatus String filter by viewed status ({@link #VIEWED_STATUS_UNREAD} or null for all)
     * @param sIncludeSubUsers String whether to include sub-user faxes ("Y" or null)
     * @return ListWrapper containing {@link GetFaxInboxResult} entries
     */
    public ListWrapper<GetFaxInboxResult> getFaxInbox(String sPeriod, String sStartDate, String sEndDate,
                                                      String sViewedStatus, String sIncludeSubUsers) {
        Map<String, String> parameters = new HashMap<>();
        putIfPresent(parameters, S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);
        putIfPresent(parameters, S_PERIOD, sPeriod);
        putIfPresent(parameters, S_START_DATE, sStartDate);
        putIfPresent(parameters, S_END_DATE, sEndDate);
        putIfPresent(parameters, S_VIEWED_STATUS, sViewedStatus);
        putIfPresent(parameters, S_INCLUDE_SUB_USERS, sIncludeSubUsers);

        String[] requiredFields = {};
        String[] optionalFields = {S_RESPONSE_FORMAT, S_PERIOD, S_START_DATE, S_END_DATE, S_VIEWED_STATUS, S_INCLUDE_SUB_USERS};
        String result = processRequest(ACTION_GET_FAX_INBOX, requiredFields, optionalFields, parameters);
        return processListResponse(result, new TypeReference<ListWrapper<GetFaxInboxResult>>() {});
    }

    /**
     * Retrieve the list of outgoing faxes from the SRFax outbox.
     *
     * @param sPeriod String the time period filter ({@link #PERIOD_ALL} or {@link #PERIOD_RANGE})
     * @param sStartDate String start date in yyyyMMdd format (required for RANGE period)
     * @param sEndDate String end date in yyyyMMdd format (required for RANGE period)
     * @param sIncludeSubUsers String whether to include sub-user faxes ("Y" or null)
     * @return ListWrapper containing {@link GetFaxOutboxResult} entries
     */
    public ListWrapper<GetFaxOutboxResult> getFaxOutbox(String sPeriod, String sStartDate, String sEndDate,
                                                        String sIncludeSubUsers) {
        Map<String, String> parameters = new HashMap<>();
        putIfPresent(parameters, S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);
        putIfPresent(parameters, S_PERIOD, sPeriod);
        putIfPresent(parameters, S_START_DATE, sStartDate);
        putIfPresent(parameters, S_END_DATE, sEndDate);
        putIfPresent(parameters, S_INCLUDE_SUB_USERS, sIncludeSubUsers);

        String[] requiredFields = {};
        String[] optionalFields = {S_RESPONSE_FORMAT, S_PERIOD, S_START_DATE, S_END_DATE, S_INCLUDE_SUB_USERS};
        String result = processRequest(ACTION_GET_FAX_OUTBOX, requiredFields, optionalFields, parameters);
        return processListResponse(result, new TypeReference<ListWrapper<GetFaxOutboxResult>>() {});
    }

    // -- Retrieve / Update / Delete --

    /**
     * Download the content of a specific fax as a base64-encoded PDF.
     *
     * @param sFaxFileName String the fax filename (or null if using details ID)
     * @param sFaxDetailsID String the SRFax details ID (or null if using filename)
     * @param sDirection String the direction filter ({@link #RETRIEVE_DIRECTION_IN} for inbound)
     * @return SingleWrapper containing the base64-encoded fax content on success
     */
    public SingleWrapper<String> retrieveFax(String sFaxFileName, String sFaxDetailsID, String sDirection) {
        Map<String, String> parameters = new HashMap<>();
        putIfPresent(parameters, S_FAX_FILE_NAME, sFaxFileName);
        putIfPresent(parameters, S_FAX_DETAILS_ID, sFaxDetailsID);
        putIfPresent(parameters, S_DIRECTION, sDirection);
        putIfPresent(parameters, S_FAX_FORMAT, RETRIEVE_DOC_FORMAT);
        putIfPresent(parameters, S_MARKAS_VIEWED, RETRIEVE_DONT_CHANGE_STATUS);
        putIfPresent(parameters, S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);

        String[] requiredFields = {S_FAX_FILE_NAME + "|" + S_FAX_DETAILS_ID, S_DIRECTION};
        String[] optionalFields = {S_FAX_FORMAT, S_MARKAS_VIEWED, S_RESPONSE_FORMAT, S_SUB_USER_ID};
        String result = processRequest(ACTION_RETRIEVE_FAX, requiredFields, optionalFields, parameters);
        return processSingleResponse(result);
    }

    /**
     * Update the viewed/read status of a fax on the SRFax server.
     *
     * @param sFaxFileName String the fax filename (or null if using details ID)
     * @param sFaxDetailsID String the SRFax details ID (or null if using filename)
     * @param sDirection String the direction filter (IN or OUT)
     * @param sMarkasViewed String mark as viewed flag ({@link #MARK_AS_READ} for "Y")
     * @return SingleWrapper containing a confirmation string on success
     */
    public SingleWrapper<String> updateViewedStatus(String sFaxFileName, String sFaxDetailsID,
                                                    String sDirection, String sMarkasViewed) {
        Map<String, String> parameters = new HashMap<>();
        putIfPresent(parameters, S_FAX_FILE_NAME, sFaxFileName);
        putIfPresent(parameters, S_FAX_DETAILS_ID, sFaxDetailsID);
        putIfPresent(parameters, S_DIRECTION, sDirection);
        putIfPresent(parameters, S_MARKAS_VIEWED, sMarkasViewed);
        putIfPresent(parameters, S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);

        String[] requiredFields = {S_FAX_FILE_NAME + "|" + S_FAX_DETAILS_ID, S_DIRECTION, S_MARKAS_VIEWED};
        String[] optionalFields = {S_RESPONSE_FORMAT};
        String result = processRequest(ACTION_UPDATE_VIEWED_STATUS, requiredFields, optionalFields, parameters);
        return processSingleResponse(result);
    }

    /**
     * Delete a fax from the SRFax server.
     *
     * @param sDirection String the direction (IN or OUT)
     * @param sFaxFileName String the fax filename (or null if using details ID)
     * @param sFaxDetailsID String the SRFax details ID (or null if using filename)
     * @return SingleWrapper containing a confirmation string on success
     */
    public SingleWrapper<String> deleteFax(String sDirection, String sFaxFileName, String sFaxDetailsID) {
        Map<String, String> parameters = new HashMap<>();
        putIfPresent(parameters, S_FAX_FILE_NAME, sFaxFileName);
        putIfPresent(parameters, S_FAX_DETAILS_ID, sFaxDetailsID);
        putIfPresent(parameters, S_DIRECTION, sDirection);
        putIfPresent(parameters, S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);

        String[] requiredFields = {S_DIRECTION, S_FAX_FILE_NAME + "|" + S_FAX_DETAILS_ID};
        String[] optionalFields = {S_RESPONSE_FORMAT};
        String result = processRequest(ACTION_DELETE_FAX, requiredFields, optionalFields, parameters);
        return processSingleResponse(result);
    }

    /**
     * Cancel a queued fax that has not yet been sent.
     *
     * @param sFaxDetailsID String the SRFax details ID of the fax to cancel
     * @return SingleWrapper containing a confirmation string on success
     */
    public SingleWrapper<String> stopFax(String sFaxDetailsID) {
        Map<String, String> parameters = new HashMap<>();
        putIfPresent(parameters, S_FAX_DETAILS_ID, sFaxDetailsID);
        putIfPresent(parameters, S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);

        String[] requiredFields = {S_FAX_DETAILS_ID};
        String[] optionalFields = {S_RESPONSE_FORMAT};
        String result = processRequest(ACTION_STOP_FAX, requiredFields, optionalFields, parameters);
        return processSingleResponse(result);
    }

    // -- Usage --

    /**
     * Retrieve fax usage statistics for a date range.
     *
     * @param sStartDate String start date in yyyyMMdd format
     * @param sEndDate String end date in yyyyMMdd format
     * @param sIncludeSubUsers String whether to include sub-user usage ("Y" or null)
     * @return ListWrapper containing {@link GetUsageResult} entries
     */
    public ListWrapper<GetUsageResult> getFaxUsageByRange(String sStartDate, String sEndDate, String sIncludeSubUsers) {
        Map<String, String> parameters = new HashMap<>();
        putIfPresent(parameters, S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);
        putIfPresent(parameters, S_PERIOD, PERIOD_RANGE);
        putIfPresent(parameters, S_START_DATE, sStartDate);
        putIfPresent(parameters, S_END_DATE, sEndDate);
        putIfPresent(parameters, S_INCLUDE_SUB_USERS, sIncludeSubUsers);

        String[] requiredFields = {};
        String[] optionalFields = {S_RESPONSE_FORMAT, S_PERIOD, S_START_DATE, S_END_DATE, S_INCLUDE_SUB_USERS};
        String result = processRequest(ACTION_GET_FAX_USAGE, requiredFields, optionalFields, parameters);
        return processListResponse(result, new TypeReference<ListWrapper<GetUsageResult>>() {});
    }

    // ---- Internal Methods ----

    /**
     * Adds a key-value pair to the parameter map only if the value is non-null.
     *
     * @param parameterMap Map the target parameter map
     * @param key String the parameter name
     * @param optionalValue String the parameter value (may be null)
     * @return boolean true if the value was added
     */
    private static boolean putIfPresent(Map<String, String> parameterMap, String key, String optionalValue) {
        if (optionalValue != null) {
            parameterMap.put(key, optionalValue);
            return true;
        }
        return false;
    }

    /**
     * Deserializes a JSON response string into a {@link ListWrapper}.
     * Handles empty responses (IP block), failure statuses, and success with list results.
     */
    @SuppressWarnings("unchecked")
    private static <T> ListWrapper<T> processListResponse(String response, TypeReference<?> typeReference) {
        ListWrapper<T> result = null;
        try {
            if (response.trim().isEmpty()) {
                logger.warn("API Response Error: Account is blocked at this IP");
                result = new ListWrapper<>();
                result.setStatus("Error");
                result.setError("Account is Blocked at this IP");
            } else {
                JSONObject json = new JSONObject(response);
                String status = json.getString("Status");
                if (ListWrapper.STATUS_SUCCESS.equals(status)) {
                    result = OBJECT_MAPPER.readValue(response, typeReference);
                } else {
                    logger.warn("API Response Failure: {}", response);
                    result = new ListWrapper<>();
                    result.setStatus(status);
                    result.setError(json.getString("Result"));
                }
            }
        } catch (IOException e) {
            logger.error("Error", e);
        }
        return result;
    }

    /**
     * Deserializes a JSON response string into a {@link SingleWrapper} using generic type inference.
     * Note: due to type erasure, this overload works reliably for simple types (Integer, String).
     * For complex result types, use the overload that accepts an explicit TypeReference.
     */
    @SuppressWarnings("unchecked")
    private static <T> SingleWrapper<T> processSingleResponse(String response) {
        return processSingleResponse(response, new TypeReference<SingleWrapper<T>>() {});
    }

    /**
     * Deserializes a JSON response string into a {@link SingleWrapper} with an explicit TypeReference.
     * Handles empty responses (IP block), failure statuses, and success with single results.
     */
    @SuppressWarnings("unchecked")
    private static <T> SingleWrapper<T> processSingleResponse(String response, TypeReference<?> typeReference) {
        SingleWrapper<T> result = null;
        if (response.trim().isEmpty()) {
            logger.warn("API Response Error: Account is blocked at this IP");
            result = new SingleWrapper<>();
            result.setStatus("Error");
            result.setError("Account is Blocked at this IP");
        } else {
            JSONObject json = new JSONObject(response);
            String status = json.getString("Status");
            try {
                if (SingleWrapper.STATUS_SUCCESS.equals(status)) {
                    result = OBJECT_MAPPER.readValue(response, typeReference);
                } else {
                    logger.warn("API Response Failure: {}", response);
                    result = new SingleWrapper<>();
                    result.setStatus(status);
                    result.setError(json.getString("Result"));
                }
            } catch (IOException e) {
                logger.error("Error", e);
            }
        }
        return result;
    }

    /**
     * Validates required parameters, builds the POST body with credentials, and executes
     * the HTTP request against the SRFax API.
     *
     * @param action String the SRFax API action name (e.g. "Queue_Fax", "Get_FaxStatus")
     * @param requiredFields String[] parameter names that must be present
     * @param optionalFields String[] parameter names that are included if present
     * @param parameters Map the raw parameter values
     * @return String the raw JSON response body
     * @throws FaxApiValidationException if required parameters are missing
     * @throws FaxApiConnectionException if the HTTP request fails
     */
    private String processRequest(String action, String[] requiredFields, String[] optionalFields,
                                  Map<String, String> parameters) {
        validateRequiredVariables(requiredFields, parameters);
        Map<String, String> postVariables = preparePostVariables(requiredFields, optionalFields, parameters);

        postVariables.put(ACTION, action);
        postVariables.put(ACCESS_ID, access_id);
        postVariables.put(ACCESS_PW, access_pwd);

        return postRequest(postVariables);
    }

    /**
     * Executes an HTTP POST request to the SRFax API with the given parameters.
     * Uses a try-with-resources block for the HTTP client to ensure proper cleanup.
     *
     * @param postVariables Map the complete set of POST parameters including credentials
     * @return String the raw JSON response body
     * @throws FaxApiConnectionException if the HTTP request fails due to network issues
     */
    private String postRequest(Map<String, String> postVariables) {
        String result = "";
        // Auto-close the HTTP client after the request completes
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            logger.debug("POST URL: {}", SERVER_URL);
            HttpPost httpPost = new HttpPost(SERVER_URL);

            // Convert the parameter map to form-encoded name-value pairs
            ArrayList<NameValuePair> postParameters = new ArrayList<>(postVariables.size());
            for (Map.Entry<String, String> entry : postVariables.entrySet()) {
                postParameters.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            UrlEncodedFormEntity urlEntity = new UrlEncodedFormEntity(postParameters, "UTF-8");
            httpPost.setEntity(urlEntity);

            HttpResponse httpResponse = httpClient.execute(httpPost);
            logger.debug("RESPONSE INFO: statusCode={}, reason={}",
                    httpResponse.getStatusLine().getStatusCode(),
                    httpResponse.getStatusLine().getReasonPhrase());

            // Read the response body as a UTF-8 string (expected to be JSON)
            HttpEntity entity = httpResponse.getEntity();
            if (entity != null) {
                try (InputStream inputStream = entity.getContent()) {
                    result = IOUtils.toString(inputStream, "UTF-8");
                }
            }
        } catch (IOException e) {
            logger.error("Error", e);
            // Wrap as FaxApiConnectionException so callers can distinguish transient errors
            throw new FaxApiConnectionException(e, "fax.exception.connectionError.srfax");
        }
        return result;
    }

    /**
     * Builds the final POST parameter map from required and optional field specifications.
     * Handles wildcard fields (e.g. sFileName_*) and piped alternatives (e.g. sFaxFileName|sFaxDetailsID).
     */
    private Map<String, String> preparePostVariables(String[] requiredFields, String[] optionalFields,
                                                     Map<String, String> parameters) {
        Map<String, String> postVariables = new HashMap<>();

        List<String> inputVariables = new ArrayList<>(requiredFields.length + optionalFields.length);
        inputVariables.addAll(Arrays.asList(requiredFields));
        inputVariables.addAll(Arrays.asList(optionalFields));

        for (String paramName : inputVariables) {
            // Handle wildcard parameters like sFileName_* (expand to sFileName_1, sFileName_2, etc.)
            if (paramName.endsWith("*") && paramName.indexOf('|') == -1) {
                String fieldPrefix = paramName.replace("*", "");
                postVariables.putAll(getWildcardVariables(fieldPrefix, parameters));
            }
            // Handle piped alternatives like "sFaxFileName|sFaxDetailsID" (include whichever is present)
            else if (paramName.contains("|")) {
                for (String pipedPart : paramName.split("\\|")) {
                    if (pipedPart.endsWith("*")) {
                        String fieldPrefix = pipedPart.replace("*", "");
                        postVariables.putAll(getWildcardVariables(fieldPrefix, parameters));
                    } else if (parameters.containsKey(pipedPart)) {
                        String val = parameters.get(pipedPart);
                        if (!val.isEmpty()) {
                            postVariables.put(pipedPart, val);
                        }
                    }
                }
            }
            // Simple parameter: include if present in the input map
            else if (parameters.containsKey(paramName)) {
                postVariables.put(paramName, parameters.get(paramName));
            }
        }
        return postVariables;
    }

    /**
     * Collects numbered wildcard parameters (e.g. sFileName_1, sFileName_2, ...) from the parameter map.
     * Stops at the first missing or empty numbered entry.
     */
    private Map<String, String> getWildcardVariables(String fieldPrefix, Map<String, String> parameters) {
        Map<String, String> wildCards = new HashMap<>();
        int suffix = 1;

        while (suffix <= 1000) {
            String paramKey = fieldPrefix + suffix;
            if (!parameters.containsKey(paramKey)) {
                break;
            }
            String val = parameters.get(paramKey);
            if (val.isEmpty()) {
                break;
            }
            wildCards.put(paramKey, val);
            suffix++;
        }
        return wildCards;
    }

    /**
     * Validates that all required parameters are present in the parameter map.
     * Supports three field specification formats: simple, wildcard (*), and piped alternatives (|).
     *
     * @throws FaxApiValidationException if any required parameter is missing
     */
    private void validateRequiredVariables(String[] requiredVariables, Map<String, String> parameters) {
        for (String paramSpec : requiredVariables) {
            if (paramSpec.endsWith("*") && !paramSpec.contains("|")) {
                validateWildcardParam(paramSpec, parameters);
            } else if (paramSpec.contains("|")) {
                validatePipedParam(paramSpec, parameters);
            } else {
                validateSimpleParam(paramSpec, parameters);
            }
        }
    }

    /** Validates that at least the first numbered instance of a wildcard parameter exists. */
    private void validateWildcardParam(String paramSpec, Map<String, String> parameters) {
        String prefix = paramSpec.replace("*", "");
        String firstKey = prefix + "1";

        if (!parameters.containsKey(firstKey) || parameters.get(firstKey).isEmpty()) {
            throw new FaxApiValidationException(
                    "Missing required parameter: " + prefix,
                    "fax.exception.validationError");
        }
    }

    /** Validates that at least one of the pipe-delimited alternative parameters is present. */
    private void validatePipedParam(String paramSpec, Map<String, String> parameters) {
        String[] alternatives = paramSpec.split("\\|");
        boolean found = false;

        for (String alt : alternatives) {
            String trimmed = alt.trim();
            if (trimmed.endsWith("*")) {
                String prefix = trimmed.replace("*", "");
                String firstKey = prefix + "1";
                if (parameters.containsKey(firstKey) && !parameters.get(firstKey).isEmpty()) {
                    found = true;
                }
            } else if (parameters.containsKey(trimmed) && !parameters.get(trimmed).isEmpty()) {
                found = true;
            }
        }
        if (!found) {
            throw new FaxApiValidationException(
                    "Missing required parameter. Provide at least one: " + String.join(",", alternatives),
                    "fax.exception.validationError");
        }
    }

    /** Validates that a simple (non-wildcard, non-piped) required parameter is present and non-empty. */
    private void validateSimpleParam(String paramName, Map<String, String> parameters) {
        if (!parameters.containsKey(paramName) || parameters.get(paramName).isEmpty()) {
            throw new FaxApiValidationException(
                    "Missing required parameter: " + paramName,
                    "fax.exception.validationError");
        }
    }
}
