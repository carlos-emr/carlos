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

    private static final String SERVER_URL = "https://www.srfax.com/SRF_SecWebSvc.php";

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

    public static final List<String> validCoverLetterNames = new ArrayList<String>(4) {{
        add("Basic");
        add("Standard");
        add("Company");
        add("Personal");
    }};

    public static final String RESPONSE_STATUS_SENT = "Sent";
    public static final String RESPONSE_STATUS_FAILED = "Failed";
    public static final String RESPONSE_STATUS_PROGRESS = "In Progress";
    public static final List<String> RESPONSE_STATUSES_FINAL = new ArrayList<String>(2) {{
        add(RESPONSE_STATUS_SENT);
        add(RESPONSE_STATUS_FAILED);
    }};

    private final String access_id;
    private final String access_pwd;

    public SRFaxApiConnector(String username, String password) {
        this.access_id = username;
        this.access_pwd = password;
    }

    // -- Queue Fax --

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
     * Queue a fax with all available API options.
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
     * Queue a standard fax with all cover letter options.
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
     * Queue a standard fax with basic cover letter.
     */
    public SingleWrapper<Integer> queueFax(String sCallerID, String sSenderEmail, String sToFaxNumber,
                                           Map<String, String> sFileMap, String sCoverPage) {
        return queueFax(sCallerID, sSenderEmail, FAX_TYPE_SINGLE, sToFaxNumber, sFileMap, RESPONSE_FORMAT_JSON,
                null, null, sCoverPage, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Queue a standard fax with no cover letter.
     */
    public SingleWrapper<Integer> queueFax(String sCallerID, String sSenderEmail, String sToFaxNumber,
                                           Map<String, String> sFileMap) {
        return queueFax(sCallerID, sSenderEmail, FAX_TYPE_SINGLE, sToFaxNumber, sFileMap, RESPONSE_FORMAT_JSON,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    // -- Fax Status --

    public SingleWrapper<GetFaxStatusResult> getFaxStatus(String sFaxDetailsID) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(S_FAX_DETAILS_ID, sFaxDetailsID);
        parameters.put(S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);

        String[] requiredFields = {S_FAX_DETAILS_ID};
        String[] optionalFields = {S_RESPONSE_FORMAT};
        String result = processRequest(ACTION_GET_FAX_STATUS, requiredFields, optionalFields, parameters);
        return processSingleResponse(result, new TypeReference<SingleWrapper<GetFaxStatusResult>>() {});
    }

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

    public SingleWrapper<String> deleteFax(String sDirection, String sFaxFileName, String sFaxDetailsID) {
        Map<String, String> parameters = new HashMap<>();
        putIfPresent(parameters, S_FAX_FILE_NAME, sFaxFileName);
        putIfPresent(parameters, S_FAX_DETAILS_ID, sFaxDetailsID);
        putIfPresent(parameters, S_DIRECTION, sDirection);
        putIfPresent(parameters, S_RESPONSE_FORMAT, RESPONSE_FORMAT_JSON);

        String[] requiredFields = {S_DIRECTION, S_FAX_FILE_NAME + "_*|" + S_FAX_DETAILS_ID + "_*"};
        String[] optionalFields = {S_RESPONSE_FORMAT};
        String result = processRequest(ACTION_DELETE_FAX, requiredFields, optionalFields, parameters);
        return processSingleResponse(result);
    }

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

    private static boolean putIfPresent(Map<String, String> parameterMap, String key, String optionalValue) {
        if (optionalValue != null) {
            parameterMap.put(key, optionalValue);
            return true;
        }
        return false;
    }

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
                    ObjectMapper mapper = new ObjectMapper();
                    result = mapper.readValue(response, typeReference);
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

    @SuppressWarnings("unchecked")
    private static <T> SingleWrapper<T> processSingleResponse(String response) {
        return processSingleResponse(response, new TypeReference<SingleWrapper<T>>() {});
    }

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
                    ObjectMapper mapper = new ObjectMapper();
                    result = mapper.readValue(response, typeReference);
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

    private String processRequest(String action, String[] requiredFields, String[] optionalFields,
                                  Map<String, String> parameters) {
        validateRequiredVariables(requiredFields, parameters);
        Map<String, String> postVariables = preparePostVariables(requiredFields, optionalFields, parameters);

        postVariables.put(ACTION, action);
        postVariables.put(ACCESS_ID, access_id);
        postVariables.put(ACCESS_PW, access_pwd);

        return postRequest(postVariables);
    }

    private String postRequest(Map<String, String> postVariables) {
        String result = "";
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            logger.debug("POST URL: {}", SERVER_URL);
            HttpPost httpPost = new HttpPost(SERVER_URL);

            ArrayList<NameValuePair> postParameters = new ArrayList<>(postVariables.size());
            for (Map.Entry<String, String> entry : postVariables.entrySet()) {
                postParameters.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            UrlEncodedFormEntity urlEntity = new UrlEncodedFormEntity(postParameters);
            httpPost.setEntity(urlEntity);

            HttpResponse httpResponse = httpClient.execute(httpPost);
            logger.debug("RESPONSE INFO: statusCode={}, reason={}",
                    httpResponse.getStatusLine().getStatusCode(),
                    httpResponse.getStatusLine().getReasonPhrase());

            HttpEntity entity = httpResponse.getEntity();
            if (entity != null) {
                try (InputStream inputStream = entity.getContent()) {
                    result = IOUtils.toString(inputStream, "UTF-8");
                }
            }
        } catch (IOException e) {
            logger.error("Error", e);
            throw new FaxApiConnectionException(e, "fax.exception.connectionError.srfax");
        }
        return result;
    }

    private Map<String, String> preparePostVariables(String[] requiredFields, String[] optionalFields,
                                                     Map<String, String> parameters) {
        Map<String, String> postVariables = new HashMap<>();

        List<String> inputVariables = new ArrayList<>(requiredFields.length + optionalFields.length);
        inputVariables.addAll(Arrays.asList(requiredFields));
        inputVariables.addAll(Arrays.asList(optionalFields));

        for (String paramName : inputVariables) {
            if (paramName.endsWith("*") && paramName.indexOf('|') == -1) {
                String fieldPrefix = paramName.replace("*", "");
                postVariables.putAll(getWildcardVariables(fieldPrefix, parameters));
            } else if (paramName.contains("|")) {
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
            } else if (parameters.containsKey(paramName)) {
                postVariables.put(paramName, parameters.get(paramName));
            }
        }
        return postVariables;
    }

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

    private void validateWildcardParam(String paramSpec, Map<String, String> parameters) {
        String prefix = paramSpec.replace("*", "");
        String firstKey = prefix + "1";

        if (!parameters.containsKey(firstKey) || parameters.get(firstKey).isEmpty()) {
            throw new FaxApiValidationException(
                    "Missing required parameter: " + prefix,
                    "fax.exception.validationError");
        }
    }

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

    private void validateSimpleParam(String paramName, Map<String, String> parameters) {
        if (!parameters.containsKey(paramName) || parameters.get(paramName).isEmpty()) {
            throw new FaxApiValidationException(
                    "Missing required parameter: " + paramName,
                    "fax.exception.validationError");
        }
    }
}
