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
package ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.WordUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.casemgmt.service.CaseManagementManager.IssueType;
import ca.openosp.openo.commn.model.Allergy;
import ca.openosp.openo.commn.model.Drug;
import ca.openosp.openo.managers.AllergyManager;
import ca.openosp.openo.managers.PrescriptionManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ConsultationClinicalData2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();
    private PrescriptionManager prescriptionManager = SpringUtils.getBean(PrescriptionManager.class);
    private CaseManagementManager caseManagementManager = SpringUtils.getBean(CaseManagementManager.class);
    private AllergyManager allergyManager = SpringUtils.getBean(AllergyManager.class);

    public ConsultationClinicalData2Action() {
        // Default
    }

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() {
        String method = request.getParameter("method");
        if ("fetchLongTermMedications".equals(method)) {
            return fetchLongTermMedications();
        } else if ("fetchAllergies".equals(method)) {
            return fetchAllergies();
        } else if ("fetchRiskFactors".equals(method)) {
            return fetchRiskFactors();
        } else if ("fetchIssueNote".equals(method)) {
            return fetchIssueNote();
        }
        return fetchMedications();
    }

    @SuppressWarnings("unused")
    public String fetchMedications() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = request.getParameter("demographicNo");

        List<Drug> medications = prescriptionManager.getActiveMedications(loggedInInfo, demographicNo);

        if (medications != null) {
            medicationToJson(response, medications, "Medications");
        }

        return null;
    }

    @SuppressWarnings("unused")
    public String fetchLongTermMedications() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = request.getParameter("demographicNo");
        List<Drug> medications = prescriptionManager.getLongTermDrugs(loggedInInfo, Integer.parseInt(demographicNo));

        if (medications != null) {
            medicationToJson(response, medications, "LongTermMedications");
        }

        return null;
    }

    @SuppressWarnings("unused")
    public String fetchAllergies() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = request.getParameter("demographicNo");

        List<Allergy> allergies = allergyManager.getActiveAllergies(loggedInInfo, Integer.parseInt(demographicNo));

        ObjectNode json = objectMapper.createObjectNode();
        json.put("noteType", "Allergies");
        StringBuilder stringBuilder = new StringBuilder();
        String allergin = null;
        String reaction = null;

        for (Allergy allergy : allergies) {

            allergin = allergy.getDescription();
            reaction = allergy.getReaction();

            if (allergin != null) {
                stringBuilder.append(WordUtils.capitalizeFully(allergin));
            }

            if (reaction != null) {
                reaction = reaction.replace("\n", " ").replace("\r", " ");
                stringBuilder.append(" Reaction: ");
                stringBuilder.append(reaction);
            }

            stringBuilder.append("\n");
        }

        json.put("note", stringBuilder.toString());

        response.setContentType("text/javascript");

        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            logger.error("Authentication error: ", e);
        }

        return null;
    }

    @Deprecated
    @SuppressWarnings("unused")
    /**
     * @Deprecated Use the FetchIssueNote method with a RiskFactor issueType parameter
     * @param mapping
     * @param form
     * @param request
     * @param response
     * @return
     */
    public String fetchRiskFactors() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = request.getParameter("demographicNo");
        String noteType = "RiskFactors";

        Issue issue = caseManagementManager.getIssueByCode(noteType);
        List<CaseManagementNote> riskFactors = caseManagementManager.getNotes(loggedInInfo, demographicNo, new String[]{issue.getId() + ""});

        ObjectNode json = objectMapper.createObjectNode();
        json.put("noteType", noteType);
        StringBuilder stringBuilder = new StringBuilder();

        if (riskFactors != null) {
            for (CaseManagementNote riskFactor : riskFactors) {
                stringBuilder.append(riskFactor.getNote());
                stringBuilder.append("\n");
            }
        }

        json.put("note", stringBuilder.toString());

        response.setContentType("text/javascript");
        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            logger.error("Authentication error: ", e);
        }

        return null;
    }

    @SuppressWarnings("unused")
    public String fetchIssueNote() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String issueType = request.getParameter("issueType");
        String demographicNo = request.getParameter("demographicNo");
        IssueType issueTypeEnum = IssueType.valueOf(issueType.toUpperCase());
        Issue issue = caseManagementManager.getIssueByCode(issueTypeEnum);
        List<CaseManagementNote> issueNoteList = caseManagementManager.getActiveNotes(loggedInInfo, demographicNo, new String[]{issue.getId() + ""});

        ObjectNode json = objectMapper.createObjectNode();
        json.put("noteType", issueTypeEnum.name());
        StringBuilder stringBuilder = new StringBuilder();

        if (issueNoteList != null) {
            for (CaseManagementNote issueNote : issueNoteList) {
                stringBuilder.append(issueNote.getNote());
                stringBuilder.append("\n");
            }
        }

        json.put("note", stringBuilder.toString());

        response.setContentType("text/javascript");
        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            logger.error("Authentication error: ", e);
        }

        return null;
    }

    private void medicationToJson(HttpServletResponse response, List<Drug> medications, String notetype) {

        ObjectNode json = objectMapper.createObjectNode();
        json.put("noteType", notetype);
        StringBuilder stringBuilder = new StringBuilder();
        String prescription = null;

        for (Drug medication : medications) {

            if (medication.isCurrent()) {

                prescription = medication.getSpecial();

                if (prescription != null) {
                    prescription = prescription.replace("\n", " ").replace("\r", " ");
                    stringBuilder.append(WordUtils.capitalizeFully(prescription) + "\n");
                }

            }
        }

        json.put("note", stringBuilder.toString());

        response.setContentType("text/javascript");
        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            logger.error("Authentication error: ", e);
        }
    }

}
