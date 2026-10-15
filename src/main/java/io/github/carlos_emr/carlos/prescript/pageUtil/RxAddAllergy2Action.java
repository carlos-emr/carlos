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

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.prescript.util.RxUtil;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;


import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class RxAddAllergy2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Handles allergy mutations for users with {@code _allergy} write privilege.
     * Requests must use {@code POST}; other methods return HTTP 405 with
     * {@code Allow: POST} and {@link #NONE}. Missing, malformed, or
     * mismatched rendered patient context returns HTTP 403 and {@link #NONE}.
     * A missing, blank, or non-numeric {@code type} returns HTTP 400 and
     * {@link #NONE} before any allergy is persisted, so malformed requests
     * cannot surface as a 500.
     * An amendment ({@code allergyToArchive}) of an allergy that is no longer active returns
     * HTTP 409 and {@link #NONE} without writing anything (issue #4410).
     * Valid add and amendment requests return {@link #SUCCESS}.
     */
    public String execute() throws IOException, ServletException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_allergy", "w", null)) {
            throw new SecurityException("missing required sec object (_allergy)");
        }

        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // The write target is the patient the request explicitly names (demographicNo), never the
        // session's last-opened Rx patient (per-patient state, #3875). The rendered form's own
        // formDemographicNo must name that same patient: a request naming one patient while the
        // form carries another is refused rather than written to either. This replaces the old
        // equality check against the shared "Patient" session attribute.
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_allergy", "w");
        RxPatientData.Patient patient = null;
        if (bean != null && isSamePatient(request.getParameter("formDemographicNo"), bean.getDemographicNo())) {
            patient = RxSessionBeanResolver.resolvePatient(request, bean.getDemographicNo());
        }
        if (patient == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        String id = request.getParameter("ID");

            if (id == null || "null".equals(id)) {
            id = "";
        }

        String name = request.getParameter("name");
        String type = request.getParameter("type");
        if (type == null || type.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty type parameter");
            return NONE;
        }
        int typeCode;
        try {
            typeCode = Integer.parseInt(type.trim());
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid type parameter");
            return NONE;
        }
        String description = request.getParameter("reactionDescription");

        String startDate = request.getParameter("startDate");
        if (startDate == null) {
            // startDate is optional; normalise to empty so the partial-date
            // length/separator checks below stay null-safe and simply fall
            // through, leaving the allergy start date unset.
            startDate = "";
        }
        String ageOfOnset = request.getParameter("ageOfOnset");
        String severityOfReaction = request.getParameter("severityOfReaction");
        String onSetOfReaction = request.getParameter("onSetOfReaction");
        String lifeStage = request.getParameter("lifeStage");
        String allergyToArchive = request.getParameter("allergyToArchive");

        // An edit must name an existing allergy of this patient before its replacement is
        // persisted. Otherwise a stale or cross-patient form silently becomes a new allergy.
        Integer archiveId = null;
        Allergy original = null;
        if (allergyToArchive != null && !allergyToArchive.isEmpty() && !"null".equals(allergyToArchive)) {
            try {
                archiveId = Integer.valueOf(allergyToArchive);
                if (archiveId <= 0) {
                    throw new NumberFormatException("Non-positive allergy id");
                }
            } catch (NumberFormatException _) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            original = patient.getAllergy(archiveId);
            if (original == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
        }

        String nonDrug = request.getParameter("nonDrug");

        // Retry safety (#3488): the dialogue keeps its entries after a failed save and resubmits
        // the same saveToken, so a retry whose first attempt actually persisted must not add a
        // second allergy. Claimed only after every validation above so a rejected request
        // never burns the token.
        String saveToken = request.getParameter("saveToken");
        if (!AllergySaveTokens.isAcceptable(saveToken)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        jakarta.servlet.http.HttpSession session = request.getSession();
        String fingerprint = AllergySaveTokens.fingerprint(id, name, type, description, startDate,
                ageOfOnset, severityOfReaction, onSetOfReaction, lifeStage, allergyToArchive, nonDrug,
                String.valueOf(patient.getDemographicNo()));
        AllergySaveTokens.Claim claim = AllergySaveTokens.claim(session, saveToken, fingerprint);
        if (claim == AllergySaveTokens.Claim.ALREADY_SAVED) {
            demographicNo = patient.getDemographicNo();
            return succeed(patient.getDemographicNo());
        }
        if (claim == AllergySaveTokens.Claim.IN_PROGRESS || claim == AllergySaveTokens.Claim.PAYLOAD_MISMATCH) {
            // A mismatch means an earlier attempt with this token may already have been saved with
            // different values; refuse rather than report values as saved that were never written.
            // The header tells the dialogue this 409 is not the stale-amendment refusal (#4410):
            // the allergy may already be recorded, so it must not be reported as NOT saved.
            response.setHeader("X-Allergy-Save-Token", "conflict");
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        try {
            // An amendment of an allergy that is no longer active comes from a form opened before
            // someone else amended or inactivated it. Saving it would add a second active version
            // beside theirs (issue #4410), so it is refused and the clinician reviews the list.
            // Checked after the claim: a retry of this dialogue's own amendment that did persist
            // finds the original archived too, and must be answered by the token as saved.
            if (original != null && original.getArchived()) {
                response.sendError(HttpServletResponse.SC_CONFLICT);
                return NONE;
            }
            return saveAllergy(patient, id, name, typeCode, description, startDate, ageOfOnset,
                    severityOfReaction, onSetOfReaction, lifeStage, archiveId, nonDrug, saveToken);
        } finally {
            AllergySaveTokens.release(session, saveToken);
        }
    }

    private String saveAllergy(RxPatientData.Patient patient, String id, String name, int typeCode,
                               String description, String startDate, String ageOfOnset,
                               String severityOfReaction, String onSetOfReaction, String lifeStage,
                               Integer archiveId, String nonDrug, String saveToken) throws IOException {
        Allergy allergy = new Allergy();
            allergy.setDrugrefId(id);
        // regionalIdentifier is exported as a DIN (CDS export, REST), so it is set only from a resolved
        // brand lookup below; the DrugRef search id stays in drugrefId and is never passed off as a DIN.
        allergy.setDescription(name);
        allergy.setTypeCode(typeCode);
        allergy.setReaction(description);

        if (startDate.length() >= 8 && getCharOccur(startDate, '-') == 2) {
            allergy.setStartDate(RxUtil.StringToDate(startDate, "yyyy-MM-dd"));
        } else if (startDate.length() >= 6 && getCharOccur(startDate, '-') >= 1) {
            allergy.setStartDate(RxUtil.StringToDate(startDate, "yyyy-MM"));
            allergy.setStartDateFormat(PartialDate.YEARMONTH);
        } else if (startDate.length() >= 4) {
            allergy.setStartDate(RxUtil.StringToDate(startDate, "yyyy"));
            allergy.setStartDateFormat(PartialDate.YEARONLY);
        }
        allergy.setAgeOfOnset(ageOfOnset);
        allergy.setSeverityOfReaction(severityOfReaction);
        allergy.setOnsetOfReaction(onSetOfReaction);
        allergy.setLifeStage(lifeStage);

        if (nonDrug != null && "on".equals(nonDrug)) {
            allergy.setNonDrug(true);

        } else if (nonDrug != null && "off".equals(nonDrug)) {
            allergy.setNonDrug(false);
        }


        // DrugRef's getDrug resolves only a brand-product search id (category 13) to a DIN and ATC
        // code. The ids of ingredient (14), generic (11/12) and class (8/10) results are not drug
        // codes, so asking for them always fails (#4435). Those allergens are checked by DrugRef
        // from their type and name, and need no stored identifier, so they are not looked up.
        if (typeCode == BRAND_TYPE) {
            if (!id.isEmpty() && !"0".equals(id)) {
                try {
                    RxDrugData.DrugMonograph f = new RxDrugData().getDrug(id);
                    if (StringUtils.isNotBlank(f.regionalIdentifier)) {
                        allergy.setRegionalIdentifier(f.regionalIdentifier);
                    }
                    allergy.setAtc(f.getAtc());
                } catch (Exception e) {
                    MiscUtils.getLogger().warn("Allergy saved without DrugRef identifiers: lookup failed ({})", e.getClass().getSimpleName());
                }
            }
            // The allergy is still saved (the clinician's record must not be lost), but the user
            // is told whenever a brand allergen ends up without an ATC code, including when no id
            // was submitted so no lookup could be tried (e.g. editing a legacy allergy that has
            // no drugref_id): it may not be checkable against prescriptions.
            identifiersUnresolved = StringUtils.isBlank(allergy.getAtc());
        }

        allergy.setDemographicNo(patient.getDemographicNo());
        demographicNo = patient.getDemographicNo();
        allergy.setArchived(false);

        if (archiveId == null) {
            patient.addAllergy(RxUtil.Today(), allergy);
        } else if (!patient.amendActiveAllergy(RxUtil.Today(), allergy, archiveId)) {
            // The check above passed, but another session archived the original before this
            // request could: the conditional archive is the arbiter, and nothing was written.
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        // Add (and, for an edit, archive) committed in one transaction: a retry with this token
        // must not write again.
        AllergySaveTokens.markSaved(request.getSession(), saveToken);

        String ip = request.getRemoteAddr();
        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_ALLERGY, "" + allergy.getAllergyId(), ip, "" + patient.getDemographicNo(), allergy.getAuditString());
        if (archiveId != null) {
            LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.ARCHIVE, LogConst.CON_ALLERGY, String.valueOf(archiveId), ip, "" + patient.getDemographicNo(), null);
        }

        return succeed(patient.getDemographicNo());
    }

    /** Ends a successful save: the redirect back to this patient's allergy list. */
    private String succeed(int patientDemographicNo) {
        demographicNo = patientDemographicNo;
        return SUCCESS;
    }

    /**
     * The patient this request added the allergy for, used by the success redirect back to that
     * patient's allergy page. Read-only: it is set from the resolved patient, never bound from
     * request parameters.
     *
     * @return the patient's demographic number, or 0 before a successful write
     */
    public int getDemographicNo() {
        return demographicNo;
    }

    private int demographicNo;

    /** DrugRef search category of a branded product, the only allergen type with a drug-code id. */
    private static final int BRAND_TYPE = 13;

    private boolean identifiersUnresolved;

    /**
     * Whether a brand allergen was saved without its DrugRef identifiers, so the redirect target
     * can warn that it may not be checked against prescriptions. Read-only; never request-bound.
     */
    public boolean isIdentifiersUnresolved() {
        return identifiersUnresolved;
    }

    /** Whether {@code formValue} is a well-formed demographic number equal to {@code demographicNo}. */
    private static boolean isSamePatient(String formValue, int demographicNo) {
        if (formValue == null || !formValue.trim().matches("\\d{1,9}")) {
            return false;
        }
        return Integer.parseInt(formValue.trim()) == demographicNo;
    }

    private int getCharOccur(String str, char ch) {
        int occurence = 0, from = 0;
        while (str.indexOf(ch, from) >= 0) {
            occurence++;
            from = str.indexOf(ch, from) + 1;
        }
        return occurence;
    }
}
