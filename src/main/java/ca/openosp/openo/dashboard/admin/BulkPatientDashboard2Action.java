//CHECKSTYLE:OFF
/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
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
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 */

package ca.openosp.openo.dashboard.admin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;

import ca.openosp.openo.dashboard.handler.DemographicPatientStatusRosterStatusHandler;
import ca.openosp.openo.dashboard.handler.DiseaseRegistryHandler;
import ca.openosp.openo.dashboard.handler.ExcludeDemographicHandler;
import ca.openosp.openo.dashboard.handler.MessageHandler;
import ca.openosp.openo.managers.DashboardManager;
import ca.openosp.openo.managers.SecurityInfoManager;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class BulkPatientDashboard2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);

    private ExcludeDemographicHandler excludeDemographicHandler = new ExcludeDemographicHandler();

    private DiseaseRegistryHandler diseaseRegistryHandler = new DiseaseRegistryHandler();

    private DemographicPatientStatusRosterStatusHandler demographicPatientStatusRosterStatusHandler = new DemographicPatientStatusRosterStatusHandler();

    private MessageHandler messageHandler = new MessageHandler();

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() {
        String method = request.getParameter("method");
        if ("addToDiseaseRegistry".equals(method)) {
            return addToDiseaseRegistry();
        } else if ("getICD9Description".equals(method)) {
            return getICD9Description();
        } else if ("setPatientsInactive".equals(method)) {
            return setPatientsInactive();
        }
        return excludePatients();
    }

    public String excludePatients() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        excludeDemographicHandler.setLoggedinInfo(loggedInInfo);

        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String patientIdsJson = request.getParameter("patientIds");
        String indicatorIdString = request.getParameter("indicatorId");

        int indicatorId;
        try {
            indicatorId = Integer.parseInt(indicatorIdString);
        } catch (NumberFormatException exception) {
            logger.error("Could not parse indicator id from: " + indicatorIdString);
            return null;
        }

        String indicatorName = excludeDemographicHandler.getDrilldownIdentifier(
                indicatorId);

        excludeDemographicHandler.excludeDemoIds(patientIdsJson, indicatorName);

        String subject = "Patient exclusion report.";
        String message = "Excluded patient demographic_no {" + patientIdsJson +
                "} from indicator {" + indicatorName + "}";

        messageHandler.notifyProvider(
                subject,
                message,
                providerNo,
                null //parseIntegers(patientIdsJson)
        );
        String mrp = getMRP(loggedInInfo);
        if (mrp != null && !providerNo.equals(mrp)) {
            messageHandler.notifyProvider(subject, message, mrp, null); //parseIntegers(patientIdsJson));
        }

        logger.info(message);

        return null;
    }

    private String getICD9Code(HttpServletRequest request) {
        return request.getParameter("dxUpdateICD9Code");
    }

    public String addToDiseaseRegistry() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo,
                "_dxresearch", SecurityInfoManager.WRITE, null)) {
            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                logger.info("Provider " + loggedInInfo.getLoggedInProvider().getProviderNo() + " does not have write permission on _dxresearch sec object");
            }
            return "unauthorized";
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String icd9code = getICD9Code(request);

        String patientIdsJson = request.getParameter("patientIds");
        ArrayNode patientIds = asJsonArray(patientIdsJson);
        List<Integer> patientIdList = new ArrayList<Integer>();

        String ip = request.getRemoteAddr();
        for (int i = 0; i < patientIds.size(); ++i) {
            int patientId = patientIds.get(i).asInt();
            patientIdList.add(patientId);

            Integer drId = diseaseRegistryHandler.addToDiseaseRegistry(
                    patientId,
                    icd9code,
                    providerNo
            );
            LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, "DX", "" + drId, ip, "");
        }

        String subject = "Bulk addition to disease registry report.";
        String message = "Added ICD9 code {" + icd9code +
                "} to disease registry for patient demographic_no {" + patientIdsJson + "}" +
                " with provider no {" + providerNo + "}";

        messageHandler.notifyProvider(subject, message, providerNo, null); //patientIdList);
        String mrp = getMRP(loggedInInfo);
        if (mrp != null && !providerNo.equals(mrp)) { // operation done by MOA for doctor
            messageHandler.notifyProvider(subject, message, mrp, null); //patientIdList);
        }

        logger.info(message);

        return null;
    }

    public String getICD9Description() {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String icd9code = getICD9Code(request);
        String description = diseaseRegistryHandler.getDescription(icd9code);

        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("icd9code", icd9code);
        jsonObject.put("description", description);

        try {
            objectMapper.writeValue(response.getWriter(), jsonObject);
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (IOException e) {
            logger.error("Error generating JSON response", e);
            return "error";
        }

        return null;
    }

    public String setPatientsInactive() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = getProviderNo(loggedInInfo);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.WRITE, null)) {
            logger.info("Provider " + providerNo + " does not have write permission on _demographic sec object");
            return "unauthorized";
        }

        demographicPatientStatusRosterStatusHandler.setLoggedinInfo(loggedInInfo);

        String patientIdsJson = request.getParameter("patientIds");
        ArrayNode patientIds = asJsonArray(patientIdsJson);
        List<Integer> patientIdList = new ArrayList<Integer>();

        String ip = request.getRemoteAddr();
        for (int i = 0; i < patientIds.size(); ++i) {
            int patientId = patientIds.get(i).asInt();
            patientIdList.add(patientId);
            demographicPatientStatusRosterStatusHandler.setPatientStatusInactive("" + patientId);
            LogAction.addLog(providerNo, LogConst.UPDATE, LogConst.CON_DEMOGRAPHIC, "" + patientId, ip, "" + patientId, "patient_status: IN");
        }

        String subject = "Report on bulk setting of patients to inactive.";
        String message = "Patient charts with demographic_no(s) {" + patientIdsJson +
                "} have been set inactive by: " + loggedInInfo.getLoggedInProvider().getFormattedName();

        messageHandler.notifyProvider(subject, message, providerNo);
        String mrp = getMRP(loggedInInfo);
        if (mrp != null && !providerNo.equals(mrp)) {  // operation done by MOA for doctor
            messageHandler.notifyProvider(subject, message, mrp);
        }

        logger.info(message);

        return null;
    }

    private static ArrayNode asJsonArray(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return objectMapper.createArrayNode();
        }

        if (!jsonString.startsWith("[")) {
            jsonString = "[" + jsonString;
        }
        if (!jsonString.endsWith("]")) {
            jsonString = jsonString + "]";
        }

        try {
            ArrayNode jsonArray = (ArrayNode) objectMapper.readTree(jsonString);
            return jsonArray;
        } catch (Exception e) {
            logger.error("Error parsing JSON array: " + jsonString, e);
            return objectMapper.createArrayNode();
        }
    }

/*	private List<Integer> parseIntegers(String jsonString) {
		List<Integer> ints = new ArrayList<Integer>();

		ArrayNode jsonArray = asJsonArray(jsonString);
		for (int i = 0; i < jsonArray.size(); i++) {
			ints.add(jsonArray.get(i).asInt());
		}

		return ints;
	}*/

    private String getProviderNo(LoggedInInfo loggedInInfo) {
        String providerNo = null;
        if (loggedInInfo != null) {
            providerNo = loggedInInfo.getLoggedInProviderNo();
        }
        return providerNo;
    }

    private String getMRP(LoggedInInfo loggedInInfo) {
        return dashboardManager.getRequestedProviderNo(loggedInInfo);
    }

}
