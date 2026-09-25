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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public final class RxRePrescribe2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final String PRIVILEGE_READ = "r";
    private static final String PRIVILEGE_WRITE = "w";

    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Dispatches on {@code method}: reprints read the named patient's Rx; every re-prescribe method stages
     * copies into the explicitly named patient's stash and needs {@code _rx} write for that patient;
     * {@code saveDigitalSignature} is POST-only.
     *
     * @return the dispatched method's result
     */
    public String execute() throws IOException {
        String method = request.getParameter("method");
        if ("reprint2".equals(method)) {
            return reprint2();
        } else if ("represcribe".equals(method)) {
            return represcribe();
        } else if ("saveReRxDrugIdToStash".equals(method)) {
            return saveReRxDrugIdToStash();
        } else if ("represcribe2".equals(method)) {
            return represcribe2();
        } else if ("repcbAllLongTerm".equals(method)) {
            return repcbAllLongTerm();
        } else if ("represcribeMultiple".equals(method)) {
            return represcribeMultiple();
        }
        return reprint();
    }

    /**
     * Loads a saved script for reprinting, for the patient the request resolves to ({@code _rx} read).
     *
     * @return the reprint result, or {@code null} after a redirect
     */
    public String reprint() throws IOException {
        // Reprinting records a print on the script and puts the patient into reprint mode: POST-only
        // (#3908), and the script number is validated before any lookup instead of failing in
        // parseInt. The legacy rx/rePrescribe form posts it.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }
        if (this.getDrugList() == null || !this.getDrugList().matches("\\d{1,9}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        RxSessionBean sessionBeanRX = RxRequestedPatientAccess.resolveForRead(securityInfoManager, request, "_rx", "r");
        if (sessionBeanRX == null) {
            response.sendRedirect("error.html");
            return null;
        }

        RxSessionBean beanRX = new RxSessionBean();
        beanRX.setDemographicNo(sessionBeanRX.getDemographicNo());
        beanRX.setProviderNo(sessionBeanRX.getProviderNo());

        String script_no = this.getDrugList();

        String ip = request.getRemoteAddr();

        RxPrescriptionData rxData = new RxPrescriptionData();
        List<Prescription> list = rxData.getPrescriptionsByScriptNo(Integer.parseInt(script_no), sessionBeanRX.getDemographicNo());
        if (list.isEmpty()) {
            // Not this patient's script (or none): nothing to reprint, and neither the script's
            // comment nor an empty reprint entry may be exposed under this patient (#3908).
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        RxPrescriptionData.Prescription p = null;
        StringBuilder auditStr = new StringBuilder();
        for (int idx = 0; idx < list.size(); ++idx) {
            p = list.get(idx);
            beanRX.setStashIndex(beanRX.addStashItem(loggedInInfo, p));
            auditStr.append(p.getAuditString() + "\n");
        }

        // save print date/time to prescription table
        if (p != null) {
            p.Print(loggedInInfo);
        }

        String comment = rxData.getScriptComment(script_no);

        // The reprint is kept per patient, never session-wide, so another patient's window cannot
        // render it (#3908). beanRX holds database prescriptions of the resolved patient only.
        RxReprintWorkspace.store(request.getSession(), beanRX, comment);
        request.setAttribute("rePrint", "true");
        request.setAttribute("comment", comment);

        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.REPRINT, LogConst.CON_PRESCRIPTION, script_no, ip, "" + beanRX.getDemographicNo(), auditStr.toString());

        return "reprint";
    }

    /**
     * Loads a saved script of the resolved patient into that patient's {@link RxReprintWorkspace}
     * entry; the script is looked up for that patient only ({@code _rx} read).
     *
     * @return {@code null}; ViewScript2 renders the reprint
     */
    public String reprint2() throws IOException {
        // Records a print on the script and puts the patient into reprint mode: POST-only, like
        // reprint(), because CSRFGuard does not check GET (#3908). SearchDrug3's reprint2() posts.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        RxSessionBean sessionBeanRX = RxRequestedPatientAccess.resolveForRead(securityInfoManager, request, "_rx", "r");
        if (sessionBeanRX == null) {
            // An AJAX caller follows a redirect to a 200 error page and would treat it as staged:
            // answer 409 instead (#3908).
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }

        RxSessionBean beanRX = new RxSessionBean();
        beanRX.setDemographicNo(sessionBeanRX.getDemographicNo());
        beanRX.setProviderNo(sessionBeanRX.getProviderNo());

        String script_no = request.getParameter("scriptNo");
        if (script_no == null || !script_no.matches("\\d{1,9}")) {
            logger.warn("Invalid scriptNo in reprint2");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        long parsedScriptNo = Long.parseLong(script_no);
        if (parsedScriptNo > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid scriptNo");
        }
        int scriptNo = (int) parsedScriptNo;
        String ip = request.getRemoteAddr();
        RxPrescriptionData rxData = new RxPrescriptionData();
        List<Prescription> list = rxData.getPrescriptionsByScriptNo(scriptNo, sessionBeanRX.getDemographicNo());
        if (list.isEmpty()) {
            // Not this patient's script (or none): nothing to reprint, and neither the script's
            // comment nor an empty reprint entry may be exposed under this patient (#3908).
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        RxPrescriptionData.Prescription p = null;
        StringBuilder auditStr = new StringBuilder();
        for (int idx = 0; idx < list.size(); ++idx) {
            p = list.get(idx);
            beanRX.setStashIndex(beanRX.addStashItem(loggedInInfo, p));
            auditStr.append(p.getAuditString() + "\n");
        }
        // p("auditStr "+auditStr.toString());
        // save print date/time
        if (p != null) {
            p.Print(loggedInInfo);
        }

        String comment = rxData.getScriptComment(script_no);
        // The reprinted script, its comment and the "reprinting" state are kept per patient: the
        // old session-wide tmpBeanRX / rePrint / comment put every other open Rx window into
        // reprint mode showing this patient's script (#3908). beanRX and comment come from the
        // database for the resolved patient only.
        RxReprintWorkspace.store(request.getSession(), beanRX, comment);
        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.REPRINT, LogConst.CON_PRESCRIPTION, script_no, ip, "" + beanRX.getDemographicNo(), auditStr.toString());

        return null;
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * Legacy form path: stages copies of the drugs in {@code drugList}.
     *
     * Each source drug must belong to the patient (a drug of another patient is skipped or refused), and each
     * staged source id is recorded once on the ReRx list so {@code saveDrug()} archives it when its
     * replacement is saved.
     *
     * @return {@code represcribe}, or {@code null} after a redirect
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String represcribe() throws IOException {
        // Staging changes the patient's stash: POST-only, refused before anything else (#3908).
        if (refuseUnlessPost()) {
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        // Staging must name its window's patient; never stage on the no-patient fallback (#3875).
        RxSessionBean beanRX = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (beanRX == null) {
            response.sendRedirect("error.html");
            return null;
        }
        StringBuilder auditStr = new StringBuilder();
        try {
            RxPrescriptionData rxData = new RxPrescriptionData();

            for (String rawDrugId : drugList.split(",")) {
                int drugId = parseLegacyDrugId(rawDrugId);
                if (drugId < 0) {
                    break;
                }
                // get original drug
                RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);
                if (isOwnedByBeanPatient(oldRx, beanRX)) {
                    auditStr.append(stageLegacyCopy(loggedInInfo, beanRX, rxData, drugId, oldRx));
                } else {
                    logger.warn("Skipped re-prescribe of a drug that does not belong to the Rx window's patient");
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error occurred. ({})", e.getClass().getSimpleName());
        }

        return SUCCESS;
    }

    /** The ids of the patient's long-term drugs: every prescription with {@code showall}, else the current ones. */
    private static List<Integer> longTermDrugIds(CaseManagementManager caseManagementManager, LoggedInInfo loggedInInfo,
                                                 Integer demoNo, boolean showall) {
        List<Drug> prescriptDrugs = showall
                ? caseManagementManager.getPrescriptions(loggedInInfo, demoNo, true)
                : caseManagementManager.getCurrentPrescriptions(demoNo);
        List<Integer> listLongTermMed = new ArrayList<>();
        for (Drug prescriptDrug : prescriptDrugs) {
            if (prescriptDrug.isLongTerm()) {
                listLongTermMed.add(prescriptDrug.getId());
            }
        }
        return listLongTermMed;
    }

    /** The legacy drug list's next id, or -1 for a malformed one, which ends the list as it always did. */
    private static int parseLegacyDrugId(String rawDrugId) {
        try {
            return Integer.parseInt(rawDrugId);
        } catch (NumberFormatException e) {
            logger.error("Unexpected error. ({})", e.getClass().getSimpleName());
            return -1;
        }
    }

    /** Stages a copy of {@code oldRx} for the legacy re-prescribe and returns its audit line. */
    private String stageLegacyCopy(LoggedInInfo loggedInInfo, RxSessionBean beanRX, RxPrescriptionData rxData,
                                   int drugId, RxPrescriptionData.Prescription oldRx) {
        // Record the source for ReRx archival, as the other staging paths do: without it
        // saveDrug() saved the replacement and left the source active (#3908).
        recordReRxSource(beanRX, drugId);
        // create copy of Prescription
        RxPrescriptionData.Prescription rx = rxData.newPrescription(beanRX.getProviderNo(), beanRX.getDemographicNo(), oldRx);
        beanRX.setStashIndex(beanRX.addStashItem(loggedInInfo, rx));
        request.setAttribute("BoxNoFillFirstLoad", "true");
        return rx.getAuditString() + "\n";
    }

/**
 * Saves or updates a digital signature association with a prescription.
 * 
 * This method associates a digital signature with an existing prescription script,
 * allowing prescriptions to be digitally signed by providers. The signature ID
 * can be null to remove an existing signature association.
 * 
 * @return NONE after a handled response; null only when the missing RxSessionBean
 *         branch redirects to error.html
 * @throws IOException if there's an error redirecting to the error page
 * @throws RuntimeException if the user lacks write privileges for prescriptions
 * 
 * Expected request parameters:
 * - digitalSignatureId: Integer ID of the digital signature (optional, can be null)
 * - scriptId: String ID of the prescription script (required)
 */
public String saveDigitalSignature() throws IOException {
    if (!"POST".equals(request.getMethod())) {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        return NONE;
    }

    // Validate user session and privileges
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

    // The window's patient, named explicitly (ViewScript2 posts demographicNo) and authorised at
    // patient level; never the active-patient fallback, which with two charts open is the other
    // patient (#3908). The script below must belong to this patient.
    RxSessionBean sessionBeanRX =
        RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", PRIVILEGE_WRITE);
    if (sessionBeanRX == null) {
        response.sendError(HttpServletResponse.SC_CONFLICT);
        return NONE;
    }

    // Extract and validate digital signature ID from request (can be null to remove signature)
    String digitalSignatureIdParam = request.getParameter("digitalSignatureId");
    // A null parameter is legitimate: it CLEARS the link. A present one must name a real signature,
    // which means positive — "0" matches the digit pattern but is not an id. Storing 0 would leave
    // the row and the fax path permanently disagreeing: digital_signature_id is then non-null, so
    // ViewScript2's faxTargetSigned lights the Fax button, while resolveSignatureImage looks up
    // signature 0, finds no metadata, and refuses the fax as unsigned — after having overwritten
    // whatever stamp the row carried.
    int parsedDigitalSignatureId = digitalSignatureIdParam == null
            ? 0 : parsePositiveInt(digitalSignatureIdParam);
    if (digitalSignatureIdParam != null && parsedDigitalSignatureId <= 0) {
        logger.warn("Invalid digitalSignatureId rejected");
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return NONE;
    }
    Integer digitalSignatureId = digitalSignatureIdParam == null ? null : parsedDigitalSignatureId;

    // Extract and validate required script ID parameter.
    //
    // Accept the same range the callers emit. ViewScript2's firstValidScriptId admits 1-10 digits
    // that parse to a POSITIVE int, so a 9-digit cap here would reject a legitimate high script
    // number and leave the drawn signature unlinked while the page believed it was saved. Parse
    // defensively even so: 10 digits can still overflow an int (9999999999), and that must be a
    // 400 like any other malformed id, never a 500.
    String scriptId = request.getParameter("scriptId");
    int scriptNo = parsePositiveInt(scriptId);
    if (scriptNo <= 0) {
        logger.warn("Invalid scriptId rejected");
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return NONE;
    }
    
    // Capture client IP for audit logging
    String ip = request.getRemoteAddr();
    
    // Update the prescription with the digital signature
    PrescriptionManager prescriptionManager = SpringUtils.getBean(PrescriptionManager.class);

    // scriptId is request-supplied, and the check above is a GLOBAL _rx write check (null target).
    // Without this, any user holding _rx write could attach or clear a signature on any patient's
    // prescription by walking script ids. Resolve the row first and re-check the right against the
    // patient it actually belongs to. The signature itself is bound to that patient and prescriber
    // below before its id may be attached.
    // Fully qualified: this file's unqualified `Prescription` is RxPrescriptionData.Prescription,
    // while the manager returns the persisted model type.
    io.github.carlos_emr.carlos.commn.model.Prescription targetPrescription =
        prescriptionManager.getPrescription(loggedInInfo, Integer.valueOf(scriptNo));
    if (targetPrescription == null || targetPrescription.getDemographicId() == null) {
        logger.warn("Digital signature not linked: prescription not found");
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return NONE;
    }
    if (targetPrescription.getDemographicId() != sessionBeanRX.getDemographicNo()) {
        // A script of another patient than the window's: refuse before any change.
        logger.warn("Digital signature not linked: prescription does not belong to the window's patient");
        response.sendError(HttpServletResponse.SC_CONFLICT);
        return NONE;
    }
    RxRequestedPatientAccess.requirePatient(securityInfoManager, loggedInInfo,
            targetPrescription.getDemographicId(), "_rx", PRIVILEGE_WRITE);
    // Signing and clearing are prescriber acts, not merely patient-chart mutations. A covering
    // provider with patient Rx write must not replay this prescriber's existing signature onto a
    // different script, or clear the prescriber's signed link, even when the patient is the same.
    if (!Objects.equals(loggedInInfo.getLoggedInProviderNo(), targetPrescription.getProviderNo())) {
        // Paren form, as every failed security-object check reports (the reason is in the comment above).
        throw new SecurityException("missing required sec object (_rx)");
    }
    if (digitalSignatureId != null) {
        DigitalSignature signature = SpringUtils.getBean(DigitalSignatureManager.class)
                .getDigitalSignatureMetadata(digitalSignatureId);
        if (signature == null || signature.getModuleType() != ModuleType.PRESCRIPTION
                || !Objects.equals(signature.getDemographicId(), targetPrescription.getDemographicId())
                || !Objects.equals(signature.getProviderNo(), targetPrescription.getProviderNo())) {
            logger.warn("Digital signature not linked: it does not belong to the prescription's patient and prescriber");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
    }
    // The link is what makes the script "signed" for the fax gate. If the row does not exist the
    // manager returns false; report that as a failure rather than a 200, otherwise the page would
    // treat the script as stored-signed (and enable Fax) for a signature that was never linked.
    if (!prescriptionManager.setPrescriptionSignature(loggedInInfo, scriptNo, digitalSignatureId)) {
        logger.warn("Digital signature not linked: prescription not found");
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return NONE;
    }
    
    // Log the action for audit trail
    // Note: Using REPRINT constant as this is related to prescription printing/signing workflow
    // The patient logged is the PERSISTED prescription's, never the session bean's. scriptId is
    // request-supplied and is authorized above against the row it actually resolves to, so the two
    // can differ; recording the bean's patient would file this signature event under whichever
    // chart happens to be open rather than the one that was signed.
    LogAction.addLog(loggedInInfo.getLoggedInProviderNo(),
                      LogConst.REPRINT,
                      LogConst.CON_PRESCRIPTION, 
                      scriptId, 
                      ip, 
                      String.valueOf(targetPrescription.getDemographicId()));
    
    // A successful HTTP response alone could be a followed login/error redirect. Let the
    // browser recognize this exact completed write before enabling outbound fax controls.
    response.setHeader("X-Carlos-Signature-Write", "written");
    return NONE;
}

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * Stages a copy of one saved drug ({@code drugId}) in the same request that records it for ReRx
     * archival.
     *
     * Each source drug must belong to the patient (a drug of another patient is skipped or refused), and each
     * staged source id is recorded once on the ReRx list so {@code saveDrug()} archives it when its
     * replacement is saved.
     *
     * @return {@code null} (AJAX) or after a redirect
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String saveReRxDrugIdToStash() throws IOException {
        // Staging changes the patient's stash: POST-only, refused before anything else (#3908).
        if (refuseUnlessPost()) {
            return NONE;
        }
        MiscUtils.getLogger().debug("================in saveReRxDrugIdToStash  of RxRePrescribe2Action.java=================");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Staging a copy of a saved drug is an Rx write; this entry point had no privilege check.
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        // Staging must name its window's patient; never stage on the no-patient fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            // An AJAX caller follows a redirect to a 200 error page and would treat it as staged:
            // answer 409 instead (#3908).
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        StringBuilder auditStr = new StringBuilder();

        RxPrescriptionData rxData = new RxPrescriptionData();

        // String strId = (request.getParameter("drugId").split("_"))[1];
        String strId = request.getParameter("drugId");
        try {
            int drugId = Integer.parseInt(strId);
            // get original drug
            RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);
            if (!isOwnedByBeanPatient(oldRx, bean)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
            // Record the source for ReRx archival in this same request, after the ownership check.
            // The callers used to send addToReRxDrugIdList as a separate, un-awaited request; when
            // it lost the race or failed, the replacement was saved and the source stayed active.
            recordReRxSource(bean, drugId);
            // create copy of Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo(), oldRx); // set writtendate, rxdate,enddate=null.
            Long rand = RxStashIds.nextUnique(bean, RxStashIds.DEFAULT_BOUND);
            rx.setRandomId(rand);

            request.setAttribute("BoxNoFillFirstLoad", "true");
            String qText = rx.getQuantity();
            if (qText == null || !RxUtil.isStringToNumber(qText)) {
                rx.setQuantity(RxUtil.getQuantityFromQuantityText(qText));
                rx.setUnitName(RxUtil.getUnitNameFromQuantityText(qText));
            }
            // trim Special
            String spec = RxUtil.trimSpecial(rx);
            rx.setSpecial(spec);

            List<RxPrescriptionData.Prescription> listReRx = new ArrayList<Prescription>();
            rx.setDiscontinuedLatest(RxUtil.checkDiscontinuedBefore(rx));
            // add prescript to prescript list
            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listReRx.add(rx);
            }
            // save prescript to stash
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);

            auditStr.append(rx.getAuditString() + "\n");

            // RxUtil.printStashContent(beanRX);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error ({})", e.getClass().getSimpleName());
        }
        MiscUtils.getLogger().debug("================end saveReRxDrugIdToStash of RxRePrescribe2Action.java=================");
        return null;
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * Stages a copy of one saved drug ({@code drugId}).
     *
     * Each source drug must belong to the patient (a drug of another patient is skipped or refused), and each
     * staged source id is recorded once on the ReRx list so {@code saveDrug()} archives it when its
     * replacement is saved.
     *
     * @return the staged-card result, or {@code null} after a redirect
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String represcribe2() throws IOException {
        // Staging changes the patient's stash: POST-only, refused before anything else (#3908).
        if (refuseUnlessPost()) {
            return NONE;
        }
        MiscUtils.getLogger().debug("================in represcribe2 of RxRePrescribe2Action.java=================");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        // Staging must name its window's patient; never stage on the no-patient fallback (#3875).
        RxSessionBean beanRX = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (beanRX == null) {
            // An AJAX caller follows a redirect to a 200 error page and would treat it as staged:
            // answer 409 instead (#3908).
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }

        StringBuilder auditStr = new StringBuilder();
        RxPrescriptionData rxData = new RxPrescriptionData();

        String strId = request.getParameter("drugId");
        try {
            int drugId = Integer.parseInt(strId);
            // get original drug
            RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);
            if (!isOwnedByBeanPatient(oldRx, beanRX)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
            // Same as saveReRxDrugIdToStash: the source is recorded for archival atomically here.
            recordReRxSource(beanRX, drugId);
            // create copy of Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(beanRX.getProviderNo(), beanRX.getDemographicNo(), oldRx); // set writtendate, rxdate,enddate=null.

            // The page names this card's key (rand = its UI ref id) before the reply renders it; keep
            // it only when it is well formed and unused in this stash, else draw a unique one (#3908).
            long rand = RxStashIds.acceptOrNext(beanRX, request.getParameter("rand"), 10_001);
            rx.setRandomId(rand);

            request.setAttribute("BoxNoFillFirstLoad", "true");
            String qText = rx.getQuantity();
            if (qText == null || !RxUtil.isStringToNumber(qText)) {
                rx.setQuantity(RxUtil.getQuantityFromQuantityText(qText));
                rx.setUnitName(RxUtil.getUnitNameFromQuantityText(qText));
            }
            // trim Special
            String spec = RxUtil.trimSpecial(rx);
            rx.setSpecial(spec);

            List<RxPrescriptionData.Prescription> listReRx = new ArrayList<Prescription>();
            rx.setDiscontinuedLatest(RxUtil.checkDiscontinuedBefore(rx));
            // add prescript to prescript list
            if (RxUtil.isRxUniqueInStash(beanRX, rx)) {
                listReRx.add(rx);
            }
            // save prescript to stash
            int rxStashIndex = beanRX.addStashItem(loggedInInfo, rx);
            beanRX.setStashIndex(rxStashIndex);

            auditStr.append(rx.getAuditString() + "\n");

            // RxUtil.printStashContent(beanRX);
            request.setAttribute("listRxDrugs", listReRx);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error ({})", e.getClass().getSimpleName());
        }

        return "represcribe";
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * Stages copies of all of the patient's long-term medications.
     *
     * Each source drug must belong to the patient (a drug of another patient is skipped or refused), and each
     * staged source id is recorded once on the ReRx list so {@code saveDrug()} archives it when its
     * replacement is saved.
     *
     * @return the staged-card result, or {@code null} after a redirect
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String repcbAllLongTerm() throws IOException {
        // Staging changes the patient's stash: POST-only, refused before anything else (#3908).
        if (refuseUnlessPost()) {
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);
        CaseManagementManager caseManagementManager = SpringUtils.getBean(CaseManagementManager.class);

        // Staging must name its window's patient; never stage on the no-patient fallback (#3875).
        RxSessionBean beanRX = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (beanRX == null) {
            // An AJAX caller follows a redirect to a 200 error page and would treat it as staged:
            // answer 409 instead (#3908).
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        StringBuilder auditStr = new StringBuilder();
        // String idList = request.getParameter("drugIdList");

        Integer demoNo = Integer.parseInt(request.getParameter("demoNo"));
        // Only stage the long-term drugs of the patient this Rx window belongs to (#3875; also the
        // follow-up noted on PR #3369): demoNo is request input.
        if (demoNo.intValue() != beanRX.getDemographicNo()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }
        // showall is a flag value, not a security decision (IMPROPER_UNICODE is suppressed above).
        boolean showall = "true".equalsIgnoreCase(request.getParameter("showall"));
        List<Integer> listLongTermMed = longTermDrugIds(caseManagementManager, loggedInInfo, demoNo, showall);


        // The same bean as beanRX: this request's explicitly named patient.
        RxSessionBean bean = beanRX;

        List<RxPrescriptionData.Prescription> listLongTerm = new ArrayList<Prescription>();
        for (int i = 0; i < listLongTermMed.size(); i++) {
            Long rand = RxStashIds.nextUnique(beanRX, RxStashIds.DEFAULT_BOUND);

            // loop this
            int drugId = listLongTermMed.get(i);

            //add drug to re-prescribe drug list, once (a repeat would be archived twice)
            recordReRxSource(bean, drugId);

            // get original drug
            RxPrescriptionData rxData = new RxPrescriptionData();
            RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);

            // create copy of Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(beanRX.getProviderNo(), beanRX.getDemographicNo(), oldRx);

            request.setAttribute("BoxNoFillFirstLoad", "true");

            // give prescript a random id.
            rx.setRandomId(rand);
            String qText = rx.getQuantity();
            if (qText == null || !RxUtil.isStringToNumber(qText)) {
                rx.setQuantity(RxUtil.getQuantityFromQuantityText(qText));
                rx.setUnitName(RxUtil.getUnitNameFromQuantityText(qText));
            }
            String spec = RxUtil.trimSpecial(rx);
            rx.setSpecial(spec);

            if (RxUtil.isRxUniqueInStash(beanRX, rx)) {
                listLongTerm.add(rx);
            }
            int rxStashIndex = beanRX.addStashItem(loggedInInfo, rx);
            beanRX.setStashIndex(rxStashIndex);
            auditStr.append(rx.getAuditString() + "\n");

        }
        // RxUtil.printStashContent(beanRX);
        request.setAttribute("listRxDrugs", listLongTerm);

        return "repcbLongTerm";
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * Stages copies of the drugs in {@code drugIds} (or, without it, of the ReRx list). Staged sources are
     * added to the ReRx list, never replacing it, so a source staged by an earlier batch is still archived.
     *
     * Each source drug must belong to the patient (a drug of another patient is skipped or refused), and each
     * staged source id is recorded once on the ReRx list so {@code saveDrug()} archives it when its
     * replacement is saved.
     *
     * @return {@code represcribe}, or {@code null} after a redirect
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String represcribeMultiple() throws IOException {
        // Staging changes the patient's stash: POST-only, refused before anything else (#3908).
        if (refuseUnlessPost()) {
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        // Staging must name its window's patient; never stage on the no-patient fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            // An AJAX caller follows a redirect to a 200 error page and would treat it as staged:
            // answer 409 instead (#3908).
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        // Accept drug IDs passed directly in request to avoid race condition with
        // the async session-update call from the checkbox handler.
        // When present, treat them as the source of truth for this request rather
        // than merging into the session-backed list (which a late async call could repopulate).
        String drugIdsParam = request.getParameter("drugIds");
        List<String> reRxDrugList;
        if (drugIdsParam != null && !drugIdsParam.isBlank()) {
            reRxDrugList = new ArrayList<>();
            int malformedIds = 0;
            for (String id : drugIdsParam.split(",")) {
                String trimmed = id.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    int parsedId = Integer.parseInt(trimmed);
                    if (parsedId > 0) {
                        String normalizedId = Integer.toString(parsedId);
                        if (!reRxDrugList.contains(normalizedId)) {
                            reRxDrugList.add(normalizedId);
                        }
                    }
                } catch (NumberFormatException e) {
                    malformedIds++;
                }
            }
            if (malformedIds > 0) {
                logger.warn("represcribeMultiple: skipped {} malformed drug id(s)", malformedIds);
            }
        } else {
            reRxDrugList = new ArrayList<>(bean.getReRxDrugIdList());
        }
        CopyOnWriteArrayList<RxPrescriptionData.Prescription> listReRxDrug = new CopyOnWriteArrayList<Prescription>();
        // Source ids staged below. Each must be on the bean's ReRx list until the save: saveDrug()
        // archives a re-prescribed source only when its id is in that list (archiveReRxDrugs).
        int staged = 0;
        for (String drugId : reRxDrugList) {
            Long rand = RxStashIds.nextUnique(bean, RxStashIds.DEFAULT_BOUND);
            RxPrescriptionData rxData = new RxPrescriptionData();
            RxPrescriptionData.Prescription oldRx;
            try {
                oldRx = rxData.getPrescription(Integer.parseInt(drugId));
            } catch (RuntimeException _) {
                // getPrescription throws for a missing row; one bad id must not drop the rest.
                oldRx = null;
            }
            if (!isOwnedByBeanPatient(oldRx, bean)) {
                logger.warn("Skipped re-prescribe of a drug that does not belong to the Rx window's patient");
                continue;
            }
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo(), oldRx);
            rx.setRandomId(rand);
            String qText = rx.getQuantity();
            if (qText == null || !RxUtil.isStringToNumber(qText)) {
                rx.setQuantity(RxUtil.getQuantityFromQuantityText(qText));
                rx.setUnitName(RxUtil.getUnitNameFromQuantityText(qText));
            }
            String spec = RxUtil.trimSpecial(rx);
            rx.setSpecial(spec);
            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listReRxDrug.add(rx);
            }
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);
            // Add, never replace: a source staged by an earlier batch is still on its card and
            // must stay listed, or saving that card would leave the source active (#3908).
            // Clearing the list (older behaviour) had the same effect for this batch. A repeated
            // id is recorded once; archiveReRxDrugs archives only sources whose replacement saved.
            recordReRxSource(bean, Integer.parseInt(drugId));
            staged++;
        }
        // Counts only: drug ids and prescriptions correlate to the patient's chart.
        logger.debug("represcribeMultiple: {} requested, {} staged", reRxDrugList.size(), staged);
        request.setAttribute("listRxDrugs", listReRxDrug);
        return "represcribe";
    }

    public void p(String s) {
        MiscUtils.getLogger().debug(s);
    }

    public void p(String s, String s1) {
        MiscUtils.getLogger().debug(s + "=" + s1);
    }


    /**
     * Records an ownership-checked source drug on the bean's ReRx list, once. {@code saveDrug()}
     * archives a re-prescribed source only when its id is listed and its replacement was saved, so
     * every staging path records its source here; a repeated id is not added twice, and ids staged
     * by earlier requests are kept (#3908).
     *
     * @param bean         the Rx window's bean, whose patient owns the source drug
     * @param sourceDrugId the saved drug being re-prescribed
     */
    static void recordReRxSource(RxSessionBean bean, int sourceDrugId) {
        String id = String.valueOf(sourceDrugId);
        if (!bean.getReRxDrugIdList().contains(id)) {
            bean.addReRxDrugIdList(id);
        }
    }

    /**
     * Answers a non-POST request with 405 and {@code Allow: POST}. CSRFGuard does not check GET, so
     * a link or image tag could otherwise stage drugs into a patient's stash.
     *
     * @return {@code true} when the request was refused and the caller must return {@code NONE}
     * @throws IOException when the error cannot be sent
     */
    private boolean refuseUnlessPost() throws IOException {
        if ("POST".equals(request.getMethod())) {
            return false;
        }
        response.setHeader("Allow", "POST");
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        return true;
    }

    /**
     * Whether a saved drug belongs to the Rx window's patient. The drug ids the staging calls take
     * are request input: without this a drug id from another chart was copied, with its dosing and
     * instructions, into this patient's stash and could be saved for them (#3875).
     *
     * @param source the saved drug being copied, or {@code null} when it was not found
     * @param bean   the Rx window's bean
     * @return {@code true} only when both are present and belong to the same patient
     */
    static boolean isOwnedByBeanPatient(RxPrescriptionData.Prescription source, RxSessionBean bean) {
        return source != null && bean != null && bean.getDemographicNo() > 0
                && source.getDemographicNo() == bean.getDemographicNo();
    }

    private void checkPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", privilege, null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
    }

    /** Parses a positive signed database identifier, returning -1 for malformed or overflow input. */
    private static int parsePositiveInt(String value) {
        if (value == null || !value.matches("\\d{1,10}")) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    private String drugList = null;

    public String getDrugList() {
        return this.drugList;
    }

    @StrutsParameter
    public void setDrugList(String RHS) {
        this.drugList = RHS;
    }
}
