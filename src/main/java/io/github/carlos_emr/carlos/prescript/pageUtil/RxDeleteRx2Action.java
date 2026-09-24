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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts 2 action for managing prescription deletion and discontinuation operations.
 * <p>
 * This action handles multiple prescription management operations including:
 * <ul>
 * <li>Deleting single or multiple prescriptions</li>
 * <li>Discontinuing prescriptions with reason tracking</li>
 * <li>Clearing prescription stash and re-prescription lists</li>
 * </ul>
 * <p>
 * Every operation that archives a saved drug first confirms the drug belongs to the patient of
 * the caller's Rx session, so a drug id from another chart is refused with 403.
 * <p>
 * All deletion operations archive the drug record rather than performing hard deletes,
 * maintaining audit trail compliance for healthcare data.
 *
 * @since 2006-04-20
 */
public final class RxDeleteRx2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DrugDao drugDao = SpringUtils.getBean(DrugDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final String PRIVILEGE_UPDATE = "u";

    /**
     * Main execution method that routes to appropriate sub-methods based on parameterValue parameter.
     * <p>
     * Routes to:
     * <ul>
     * <li>Delete2() - Single prescription deletion by ID</li>
     * <li>clearStash() - Clear prescription stash</li>
     * <li>clearReRxDrugList() - Clear re-prescription list</li>
     * <li>Discontinue() - Discontinue prescription with reason</li>
     * </ul>
     * <p>
     * If no method parameter is provided, performs bulk deletion using the drugList parameter.
     *
     * Expected request parameters:
     * <ul>
     * <li>parameterValue - String method name to execute (optional)</li>
     * <li>drugList - Comma-separated drug IDs for bulk deletion (used when no method specified)</li>
     * </ul>
     *
     * @return String action result ("success", "close", etc.) or null for AJAX responses
     * @throws IOException if response writing fails
     * @throws ServletException if servlet processing fails
     */
    @Override
    public String execute()
            throws IOException, ServletException {
        String method = request.getParameter("parameterValue");
        if ("Delete2".equals(method)) {
            return Delete2();
        } else if ("clearStash".equals(method)) {
            return clearStash();
        } else if ("clearReRxDrugList".equals(method)) {
            return clearReRxDrugList();
        } else if ("Discontinue".equals(method)) {
            return Discontinue();
        }
        checkPrivilege(request, PRIVILEGE_UPDATE);

        // Setup variables
        // Archives drugs or clears staged Rx state: only the named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", PRIVILEGE_UPDATE);
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        String ip = request.getRemoteAddr();
        try {

            String[] drugArr = drugList == null ? new String[0] : drugList.split(",");
            int drugId;
            int i;

            // Validate every requested drug before archiving any, so a list that mixes in another
            // patient's drug is refused as a whole rather than half-applied.
            List<Drug> drugsToDelete = new ArrayList<>();
            for (i = 0; i < drugArr.length; i++) {
                try {
                    drugId = Integer.parseInt(drugArr[i]);

                } catch (Exception e) {
                    break;
                }
                // get original drug
                Drug drug = drugDao.find(drugId);
                if (!isOwnedBySessionPatient(drug, bean)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return NONE;
                }
                drugsToDelete.add(drug);
            }
            for (Drug drug : drugsToDelete) {
                setDrugDelete(drug);
                drugDao.merge(drug);
                LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.DELETE, LogConst.CON_PRESCRIPTION, String.valueOf(drug.getId()), ip, "" + bean.getDemographicNo(), drug.getAuditString());
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;
    }

    /**
     * Sets the deletion flags on a drug record for archiving.
     * <p>
     * Marks the drug as archived rather than performing a hard delete,
     * maintaining compliance with healthcare audit trail requirements.
     *
     * @param drug Drug the drug entity to mark as deleted
     */
    private void setDrugDelete(Drug drug) {
        drug.setArchived(true);
        drug.setArchivedDate(new Date());
        drug.setArchivedReason(Drug.DELETED);
    }

    /**
     * Deletes a single prescription by ID.
     * <p>
     * Archives the specified prescription and logs the deletion action.
     * This method is typically called via AJAX from the prescription interface.
     *
     * Expected request parameters:
     * <ul>
     * <li>deleteRxId - String in format "prefix_drugId" where drugId is the prescription ID</li>
     * </ul>
     *
     * @return null (AJAX response, no page navigation)
     * @throws IOException if response redirect fails
     */
    public String Delete2()
            throws IOException {

        MiscUtils.getLogger().debug("===========================Delete2 RxDeleteRx2Action========================");
        checkPrivilege(request, PRIVILEGE_UPDATE);

        // Setup variables
        // Archives drugs or clears staged Rx state: only the named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", PRIVILEGE_UPDATE);
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        String ip = request.getRemoteAddr();
        try {
            String deleteRxId = (request.getParameter("deleteRxId").split("_"))[1];

            Drug drug = drugDao.find(Integer.parseInt(deleteRxId));
            if (!isOwnedBySessionPatient(drug, bean)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
            setDrugDelete(drug);
            drugDao.merge(drug);
            LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.DELETE, LogConst.CON_PRESCRIPTION, deleteRxId, ip, "" + bean.getDemographicNo(), drug.getAuditString());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        MiscUtils.getLogger().debug("===========================END Delete2 RxDeleteRx2Action========================");
        return null;
    }

    /**
     * Clears the prescription stash stored in the session.
     * <p>
     * The stash is a temporary storage area for prescriptions that are being
     * worked on but not yet finalized.
     *
     * @return String "successClearStash" action result
     * @throws IOException if response redirect fails
     */
    public String clearStash()
            throws IOException {
        // The other write paths of this action check _rx update; clearing the stash is a write too.
        checkPrivilege(request, PRIVILEGE_UPDATE);
        // Archives drugs or clears staged Rx state: only the named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", PRIVILEGE_UPDATE);
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        bean.clearStash();
        return "successClearStash";
    }

    /**
     * Clears the re-prescription drug list stored in the session.
     * <p>
     * The re-prescription list tracks drugs that are being re-prescribed
     * (renewed) for the patient. This method clears that list.
     *
     * @return null (AJAX response, no page navigation)
     * @throws IOException if response redirect fails
     */
    public String clearReRxDrugList()
            throws IOException {
        checkPrivilege(request, PRIVILEGE_UPDATE);

        // Archives drugs or clears staged Rx state: only the named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", PRIVILEGE_UPDATE);
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        bean.clearReRxDrugIdList();
        //return "successClearStash";
        return null;
    }


    /**
     * Discontinues a drug by setting archived status and creating a case management note.
     * <p>
     * The method performs the following operations:
     * <ul>
     * <li>Sets the drug's archived flag to true</li>
     * <li>Sets the archived date to current date</li>
     * <li>Records the discontinuation reason</li>
     * <li>Creates a case management note documenting the discontinuation</li>
     * <li>Logs the action for audit purposes</li>
     * <li>Returns JSON response with drug ID and reason</li>
     * </ul>
     *
     * Expected request parameters:
     * <ul>
     * <li>drugId - Integer ID of the drug to discontinue</li>
     * <li>reason - String reason for discontinuation</li>
     * <li>comment - String additional comments (optional)</li>
     * <li>drugSpecial - String drug special instructions</li>
     * </ul>
     * The note is filed against the Rx session's patient, never a request-supplied demographic.
     *
     * @return {@link #NONE} (writes JSON response directly to output stream)
     * @throws IOException if response writing fails
     */
    //STILL NEED TO SAVE REASON AND COMMENT "would like to create a summary note in the echart"
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} update, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * Discontinues one of the patient's saved drugs (refused when the drug is another patient's) and files
     * the discontinue note.
     *
     * @return the discontinue result, or {@code null} after a redirect
     * @throws SecurityException when the caller may not update Rx for the patient
     */
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String Discontinue() throws IOException {
        checkPrivilege(request, PRIVILEGE_UPDATE);

        // Archives drugs or clears staged Rx state: only the named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", PRIVILEGE_UPDATE);
        if (bean == null) {
            response.sendRedirect("error.html");
            return NONE;
        }

        String idStr = request.getParameter("drugId");
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        String reason = request.getParameter("reason");
        //String comment = request.getParameter("comment"); //TODO: PUT this in a note

        String ip = request.getRemoteAddr();

        Drug drug = drugDao.find(id);
        if (!isOwnedBySessionPatient(drug, bean)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        Date date = new Date();
        String logStatement = drug + " Changing end date to :" + date;
        drug.setArchivedDate(date);
        //drug.setEndDate(drug.getArchivedDate());
        drug.setArchived(true);
        drug.setArchivedReason(reason);

        drugDao.merge(drug);
      /*  Enumeration em=request.getParameterNames();
        while (em.hasMoreElements()){
            String s=em.nextElement().toString();
            MiscUtils.getLogger().debug("request.parameterName="+s);
            MiscUtils.getLogger().debug("value="+request.getParameter(s));
        }
        em=request.getAttributeNames();
        while (em.hasMoreElements()){
            String s=em.nextElement().toString();
            MiscUtils.getLogger().debug("request.attributeName="+s);
            MiscUtils.getLogger().debug("value="+request.getAttribute(s));
        }
        em=request.getSession().getAttributeNames();
        while (em.hasMoreElements()){
            String s=em.nextElement().toString();
            MiscUtils.getLogger().debug("request.attributeName in session="+s);
            MiscUtils.getLogger().debug("value="+request.getSession().getAttribute(s));
        }*/
        try {
            createDiscontinueNote(request, bean.getDemographicNo());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.DISCONTINUE, LogConst.CON_PRESCRIPTION, "" + drug.getId(), ip, "" + drug.getDemographicId(), logStatement);

        Hashtable d = new Hashtable();
        d.put("id", "" + id);
        d.put("reason", reason);
        response.setContentType("application/json");
        ObjectNode jsonArray = (ObjectNode) objectMapper.valueToTree(d);
        response.getWriter().write(jsonArray.toString()); // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep -- JSON API response with application/json content-type

        return NONE;
    }

    /**
     * Creates a case management note documenting the discontinuation of a prescription.
     * <p>
     * This method creates a comprehensive clinical note that includes:
     * <ul>
     * <li>Drug special instructions</li>
     * <li>Discontinuation reason</li>
     * <li>Discontinuation comment</li>
     * <li>Provider and program information</li>
     * </ul>
     * <p>
     * The note is linked to the drug record via CaseManagementNoteLink for
     * complete audit trail and clinical documentation.
     *
     * Expected request parameters:
     * <ul>
     * <li>drugId - String ID of the discontinued drug</li>
     * <li>drugSpecial - String special instructions for the drug</li>
     * <li>reason - String reason for discontinuation</li>
     * <li>comment - String additional comments about discontinuation</li>
     * </ul>
     *
     * @param request HttpServletRequest containing discontinuation details
     * @param sessionDemographicNo the Rx session's patient, already verified to own the drug
     */
    private void createDiscontinueNote(HttpServletRequest request, int sessionDemographicNo) {
        //create a note and store this info in casemanagement_note table
        //note_id, update_date, observation_date, demographic_no, provider_no, note:, signed, include_issue_innote, archived, position, uuid
        //signing_provider_no, encounter_type:  billing_code:  program_no, reporter_caisi_role, reporter_program_team, history, password, locked
        CaseManagementNote cmn = new CaseManagementNote();
        //get parameter values
        Date now = EDocUtil.getDmsDateTimeAsDate();
        String demoNo = String.valueOf(sessionDemographicNo);
        String idStr = request.getParameter("drugId");
        String user = request.getSession().getAttribute("user").toString();
        String strNote = request.getParameter("drugSpecial") + "\nDiscontinued reason: " + request.getParameter("reason") + "\nDiscontinued comment: " + request.getParameter("comment");
        HttpSession se = request.getSession();
        String prog_no = new EctProgram(se).getProgram(user);

        //set parameter values
        cmn.setUpdate_date(now);
        cmn.setObservation_date(now);
        cmn.setDemographic_no(demoNo);
        cmn.setProviderNo(user);
        cmn.setNote(strNote);
        cmn.setSigned(true);
        cmn.setSigning_provider_no(user);
        cmn.setProgram_no(prog_no);

        SecRoleDao secRoleDao = (SecRoleDao) SpringUtils.getBean(SecRoleDao.class);
        SecRole doctorRole = secRoleDao.findByName("doctor");
        cmn.setReporter_caisi_role(doctorRole.getId().toString());

        cmn.setReporter_program_team("0");
        cmn.setLocked(false);
        cmn.setHistory(strNote);
        //cmn.setPosition(0);
        //save note
        WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(se.getServletContext());
        CaseManagementManager cmm = (CaseManagementManager) ctx.getBean(CaseManagementManager.class);

        Long note_id = cmm.saveNoteSimpleReturnID(cmn);

        //create an entry in casemgmt note link
        CaseManagementNoteLink cmnl = new CaseManagementNoteLink();
        cmnl.setTableName(CaseManagementNoteLink.DRUGS);
        cmnl.setTableId(Long.parseLong(idStr)); //drug id
        cmnl.setNoteId(note_id);


        EDocUtil.addCaseMgmtNoteLink(cmnl);
    }

    /**
     * Whether {@code drug} exists and belongs to the patient of the caller's Rx session. The
     * {@code _rx} privilege says the caller may prescribe, not that a request-supplied drug id is
     * theirs to archive; without this a drug id from another chart could be deleted or
     * discontinued. {@code Drug.getDemographicId()} is a nullable {@code Integer}.
     */
    private static boolean isOwnedBySessionPatient(Drug drug, RxSessionBean bean) {
        return drug != null && Objects.equals(drug.getDemographicId(), bean.getDemographicNo());
    }

    /**
     * Checks if the current user has the required privilege for prescription operations.
     * <p>
     * Validates that the logged-in user has the specified privilege level (read, update, delete)
     * for the "_rx" security object. Throws RuntimeException if privilege check fails.
     *
     * @param request HttpServletRequest containing the logged-in user session
     * @param privilege String privilege level to check ("r" for read, "u" for update, "d" for delete)
     * @throws RuntimeException if the user lacks the required privilege
     */
    private void checkPrivilege(HttpServletRequest request, String privilege) {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", privilege, null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }
    }

    /**
     * Comma-separated list of drug IDs for bulk deletion operations.
     * <p>
     * This property is set by Struts 2 when the drugList parameter is present
     * in the request. Used by the default execute() method for bulk deletions.
     */
    private String drugList = null;

    /**
     * Gets the comma-separated list of drug IDs for bulk deletion.
     *
     * @return String comma-separated drug IDs, or null if not set
     */
    public String getDrugList() {
        return this.drugList;
    }

    /**
     * Sets the comma-separated list of drug IDs for bulk deletion.
     * <p>
     * This setter is called automatically by Struts 2 when the drugList
     * parameter is present in the request.
     *
     * @param RHS String comma-separated drug IDs
     */
    @StrutsParameter
    public void setDrugList(String RHS) {
        this.drugList = RHS;
    }
}
