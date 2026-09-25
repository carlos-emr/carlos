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

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.DrugReasonDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.DrugReason;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.CodingSystemManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData.DrugMonograph.DrugComponent;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.owasp.encoder.Encode;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public final class RxWriteScript2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final String PRIVILEGE_READ = "r";
    private static final String PRIVILEGE_WRITE = "w";

    private static final Logger logger = MiscUtils.getLogger();
    private static UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
    private static final String DEFAULT_QUANTITY = "30";
    /** Prefix of every {@code action} that rewrites or saves the staged item. */
    private static final String ACTION_UPDATE_PREFIX = "update";
    private static final String HEADER_ALLOW = "Allow";
    private static final String POST_REQUIRED = "POST required";
    private static final PartialDateDao partialDateDao = (PartialDateDao) SpringUtils.getBean(PartialDateDao.class);

    /** The only {@code action} values {@link #updateReRxDrug()} will act on. */
    private static final Set<String> RE_RX_ACTIONS =
            Set.of("addToReRxDrugIdList", "removeFromReRxDrugIdList", "clearReRxDrugIdList");

    private final DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    private final RxManager rxManager = SpringUtils.getBean(RxManager.class);
    private final PrescriptionSignatureStampService signatureStampService;

    /** Struts-created router: resolves collaborators from the Spring context. */
    public RxWriteScript2Action() {
        this(SpringUtils.getBean(PrescriptionSignatureStampService.class));
    }

    RxWriteScript2Action(PrescriptionSignatureStampService signatureStampService) {
        this.signatureStampService = signatureStampService;
    }

    String removeExtraChars(String s) {
        return s.replace("" + ((char) 130), "").replace("" + ((char) 194), "").replace("" + ((char) 195), "").replace("" + ((char) 172), "");
    }


    /**
     * Dispatches to the Rx write operation named by the {@code parameterValue} request parameter.
     *
     * <p>On the save-and-print path the script is persisted, the patient's stale reprint state
     * ({@link RxReprintWorkspace}) is cleared so a subsequent view is not mistaken for a reprint,
     * and the prescriber's signature stamp is applied when one is configured. (Reuse of an already-persisted script
     * number happens in {@link RxViewScript2Action}, not here.)</p>
     *
     * @return the Struts result for the dispatched operation
     */
    public String execute() throws IOException, ServletException, Exception {
        String method = request.getParameter("parameterValue");

        String dispatchResult = switch (method != null ? method : "") {
            case "updateReRxDrug" -> updateReRxDrug();
            case "saveCustomName" -> saveCustomName();
            case "newCustomNote" -> newCustomNote();
            case "listPreviousInstructions" -> listPreviousInstructions();
            case "newCustomDrug" -> newCustomDrug();
            case "normalDrugSetCustom" -> normalDrugSetCustom();
            case "createNewRx" -> createNewRx();
            case "updateDrug" -> updateDrug();
            case "iterateStash" -> iterateStash();
            case "updateSpecialInstruction" -> updateSpecialInstruction();
            case "updateProperty" -> updateProperty();
            case "updateSaveAllDrugs" -> updateSaveAllDrugs();
            case "updateLongTermStatus" -> updateLongTermStatus();
            case "checkNoStashItem" -> checkNoStashItem();
            case "searchSpecialInstructions" -> {
                searchSpecialInstructions();
                yield null;
            }
            case "getInstructionsAutocomplete" -> {
                getInstructionsAutocomplete();
                yield null;
            }
            default -> null;
        };

        if (dispatchResult != null || (method != null && !method.isEmpty())) {
            return dispatchResult;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        // update* rewrites the staged item and updateAndPrint saves the script, so, like every other
        // Rx write, it is POST-only: CSRFGuard does not check GET, so a cross-origin GET could
        // otherwise drive the write. Checked before the stash is resolved or touched. The staging
        // page (WriteScript.jsp) posts its form.
        if (this.getAction() != null && this.getAction().startsWith(ACTION_UPDATE_PREFIX)
                && !"POST".equals(request.getMethod())) {
            response.setHeader(HEADER_ALLOW, "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, POST_REQUIRED);
            return NONE;
        }

        //RxWriteScriptForm frm = (RxWriteScriptForm) form;
        String fwd;
        // update* actions rewrite the current stash item (and updateAndPrint saves it), so they
        // need the explicitly named patient's bean, never the fallback; other actions only
        // re-render (#3875).
        RxSessionBean bean = this.getAction() != null && this.getAction().startsWith(ACTION_UPDATE_PREFIX)
                ? RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w")
                : RxRequestedPatientAccess.resolveForRead(securityInfoManager, request, "_rx", "r");

        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        RxReprintWorkspace.Entry previousReprint = RxReprintWorkspace.find(request.getSession(), bean.getDemographicNo());
        synchronized (bean) {
            fwd = updateSelectedCardLocked(bean, loggedInInfo);
        }
        if ("viewScript".equals(fwd)) {
            RxReprintWorkspace.pinForRequest(request, null);
            RxReprintWorkspace.clearIfSame(request.getSession(), bean.getDemographicNo(), previousReprint);
        }
        return fwd;
    }

    /** Updates the selected card and optionally saves it while its position is stable. */
    private String updateSelectedCardLocked(RxSessionBean bean, LoggedInInfo loggedInInfo) throws IOException {
        String fwd = "refresh";
        // A request without an action parameter is a plain (re)render, not an update.
        if (this.getAction() != null && this.getAction().startsWith(ACTION_UPDATE_PREFIX)) {

            RxDrugData drugData = new RxDrugData();
            // The cursor selects the item being edited; with nothing (valid) selected there is
            // nothing to update, so refuse rather than fail on an out-of-range index.
            RxPrescriptionData.Prescription rx = bean.getCurrentStashItem();
            if (rx == null) {
                response.sendError(HttpServletResponse.SC_CONFLICT);
                return NONE;
            }

			if (! this.getGCN_SEQNO().equals("0")) { // not custom
				if (this.getBrandName().equals(rx.getBrandName()) == false) {
					rx.setBrandName(this.getBrandName());
                } else {
					rx.setGCN_SEQNO(this.getGCN_SEQNO());
                }
            } else { // custom
                rx.setBrandName(null);
				rx.setGCN_SEQNO("0");
				rx.setCustomName(this.getCustomName());
            }

            rx.setRxDate(RxUtil.StringToDate(this.getRxDate(), "yyyy-MM-dd"));
            rx.setWrittenDate(RxUtil.StringToDate(this.getWrittenDate(), "yyyy-MM-dd"));
            rx.setTakeMin(this.getTakeMinFloat());
            rx.setTakeMax(this.getTakeMaxFloat());
            rx.setFrequencyCode(this.getFrequencyCode());
            rx.setDuration(this.getDuration());
            rx.setDurationUnit(this.getDurationUnit());
            rx.setQuantity(this.getQuantity());
            rx.setRepeat(this.getRepeat());
            rx.setLastRefillDate(RxUtil.StringToDate(this.getLastRefillDate(), "yyyy-MM-dd"));
            rx.setNosubs(this.getNosubs());
            rx.setPrn(this.getPrn());
            rx.setSpecial(removeExtraChars(this.getSpecial()));
            rx.setAtcCode(this.getAtcCode());
            rx.setRegionalIdentifier(this.getRegionalIdentifier());
            rx.setUnit(removeExtraChars(this.getUnit()));
            rx.setUnitName(this.getUnitName());
            rx.setMethod(this.getMethod());
            rx.setRoute(this.getRoute());
            rx.setCustomInstr(this.getCustomInstr());
            rx.setDosage(removeExtraChars(this.getDosage()));
            rx.setOutsideProviderName(this.getOutsideProviderName());
            rx.setOutsideProviderOhip(this.getOutsideProviderOhip());
            rx.setLongTerm(this.getLongTerm());
            rx.setShortTerm(this.getShortTerm());
            rx.setPastMed(this.getPastMed());
            rx.setPatientCompliance(this.getPatientCompliance());

            try {
                rx.setDrugForm(drugData.getDrugForm(String.valueOf(this.getGCN_SEQNO())));
            } catch (Exception _) {
                logger.error("Unable to get DrugForm from drugref");
            }

            if (rx.getSpecial() == null) {
                logger.error("Prescription drug instructions are missing");
            } else if (rx.getSpecial().length() < 6) {
                logger.warn("Prescription drug instructions are empty");
            }

            bean.setStashItem(bean.getStashIndex(), rx);
            rx = null;

            if (this.getAction().equals(ACTION_UPDATE_PREFIX)) {
                fwd = "refresh";
            }
            if (this.getAction().equals("updateAddAnother")) {
                fwd = "addAnother";
            }
            if (this.getAction().equals("updateAndPrint")) {
                if (!RxSessionBeanResolver.isRequestForBeanPatient(request, bean)) {
                    logger.warn("Refused prescription save: request does not name the prescribing window's patient");
                    response.sendError(HttpServletResponse.SC_CONFLICT);
                    return NONE;
                }
                // SAVE THE DRUG through the shared persistence, which records the script on each
                // stash item (ViewScript2 builds the preview URL and the pad's signature-override
                // POST from it) and archives the re-prescribed sources; this path used to save the
                // replacement and leave the source active (#3908).
                String scriptId = persistStash(loggedInInfo, bean);
                fwd = "viewScript";
                // Same stamp-on-write as RxViewScript2Action: a stamp on file signs the freshly
                // written script so it can be faxed without the pad. This action already runs under
                // _rx write (checkPrivilege above). Eligibility is decided inside the service from
                // the persisted row, so no reprint-state guard is needed here.
                if (signatureStampService.applyStampToScript(loggedInInfo, bean, scriptId) != null) {
                    request.setAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED, Boolean.TRUE);
                }
            }
        }
        return fwd;
    }

    /**
     * Adds, removes, or clears the drug ids staged in the Rx session for re-prescribing. Called by
     * the prescription UI as an AJAX POST; nothing is persisted here, and the staged ids are only
     * acted on later by {@link #archiveReRxDrugs}.
     *
     * <p>The {@code action} parameter must be one of {@link #RE_RX_ACTIONS}; anything else is
     * rejected with {@code 403}. Staging additionally requires that {@code reRxDrugId} belongs to
     * the demographic this Rx session is scoped to, so a caller in one chart cannot queue another
     * patient's medication for archival.</p>
     *
     * <p>A request that names a valid action but is a no-op against the current list state (adding
     * an already-staged id, removing one that is not staged) is accepted, so double-submits from
     * the UI stay harmless.</p>
     *
     * <p>POST is required; anything else is rejected with {@code 405} before the Rx session is
     * touched. A missing patient workspace returns {@code 409}, so AJAX clients cannot mistake
     * a followed error-page redirect for a successful mutation.</p>
     *
     * @return {@link #NONE} when the request is rejected and an error status has been written,
     *         otherwise {@code null} so the dispatcher completes without rendering a view
     * @throws IOException if writing the error status fails
     * @since 2010-03-17
     */
    public String updateReRxDrug() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);

        // Staging mutates session state, and CSRFGuard only protects POST/PUT/DELETE/PATCH, so a
        // cross-origin GET could queue a drug for archival. The UI already POSTs. HTTP method
        // names are case-sensitive, so this is an exact match.
        if (!"POST".equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, POST_REQUIRED);
            return NONE;
        }

        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        synchronized (bean) {
            return updateReRxDrugLocked(bean);
        }
    }

    private String updateReRxDrugLocked(RxSessionBean bean) throws IOException {
        List<String> reRxDrugIdList = bean.getReRxDrugIdList();
        String action = request.getParameter("action");
        String drugId = request.getParameter("reRxDrugId");
        // Gate on the action name only: the else below also catches benign list-state no-ops, and
        // 403-ing those would break idempotent double-submits. Set.of(...).contains(null) throws.
        if (action == null || !RE_RX_ACTIONS.contains(action)) {
            // The value is untrusted request input, so it is deliberately not echoed to the log.
            logger.warn("Rejected re-Rx update: missing or unrecognized action");
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }
        if (action.equals("addToReRxDrugIdList") && !reRxDrugIdList.contains(drugId)) {
            // drugId is client-supplied. Nothing is persisted at staging time, so reject outright
            // rather than let saveDrug() archive another patient's medication.
            if (!isDrugOwnedByDemographic(drugId, bean.getDemographicNo())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
            reRxDrugIdList.add(drugId);
        } else if (action.equals("removeFromReRxDrugIdList") && reRxDrugIdList.contains(drugId)) {
            reRxDrugIdList.remove(drugId);
            removeStagedCopy(bean, drugId);
        } else if (action.equals("clearReRxDrugIdList")) {
            bean.clearReRxDrugIdList();
        } else {
            // Valid action, but a no-op against the current list (repeat add, remove of an unstaged
            // id). Harmless once the name is gated above, so it is a debug note, not a warning.
            logger.debug("No change to staged re-Rx drug ids: action is a no-op for the current list");
        }

        return null;

    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise). Renames a staged custom drug card.
     *
     * @return the operation's view result, {@code null} when no view is needed, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String saveCustomName() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);
        if (refuseUnlessPost()) {
            return NONE;
        }

        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        synchronized (bean) {
            return saveCustomNameLocked(bean);
        }
    }

    private String saveCustomNameLocked(RxSessionBean bean) {
        try {
            String randomId = request.getParameter("randomId");
            String customName = request.getParameter("customName");
            RxPrescriptionData.Prescription rx = bean.getStashItem2(Integer.parseInt(randomId));
            if (rx == null) {
                logger.error("prescript is null", new NullPointerException());
                return null;
            }
            rx.setCustomName(customName);
            rx.setBrandName(null);
            rx.setGenericName(null);
            bean.setStashItem(bean.getIndexFromRx(Integer.parseInt(randomId)), rx);

        } catch (Exception e) {
            logger.error("Error ({})", e.getClass().getSimpleName());
        }

        return null;
    }

    private void setDefaultQuantity(final HttpServletRequest request) {
        try {
            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
            String provider = (String) request.getSession().getAttribute("user");
            if (provider != null) {
                userPropertyDAO = (UserPropertyDAO) ctx.getBean(UserPropertyDAO.class);
                UserProperty prop = userPropertyDAO.getProp(provider, UserProperty.RX_DEFAULT_QUANTITY);
                if (prop != null) RxUtil.setDefaultQuantity(prop.getValue());
                else RxUtil.setDefaultQuantity(DEFAULT_QUANTITY);
            } else {
                logger.error("Provider is null", new NullPointerException());
            }
        } catch (Exception e) {
            logger.error("Error ({})", e.getClass().getSimpleName());
        }
    }

    private RxPrescriptionData.Prescription setCustomRxDurationQuantity(RxPrescriptionData.Prescription rx) {
        String quantity = rx.getQuantity();
        if (RxUtil.isMitte(quantity)) {
            String duration = RxUtil.getDurationFromQuantityText(quantity);
            String durationUnit = RxUtil.getDurationUnitFromQuantityText(quantity);
            rx.setDuration(duration);
            rx.setDurationUnit(durationUnit);
            rx.setQuantity(RxUtil.getQuantityFromQuantityText(quantity));
            rx.setUnitName(RxUtil.getUnitNameFromQuantityText(quantity)); // this is actually an indicator for Mitte prescript
        } else rx.setDuration(RxUtil.findDuration(rx));

        return rx;
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise). Stages a free-text note card.
     *
     * @return the operation's view result, {@code null} when no view is needed, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String newCustomNote() throws IOException {
        logger.debug("=============Start newCustomNote RxWriteScript2Action.java===============");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);
        if (refuseUnlessPost()) {
            return NONE;
        }

        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }

        synchronized (bean) {
            return newCustomNoteLocked(bean, loggedInInfo);
        }
    }

    private String newCustomNoteLocked(RxSessionBean bean, LoggedInInfo loggedInInfo) {
        try {
            RxPrescriptionData rxData = new RxPrescriptionData();

            // create Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo());
            // The page names the card's key; it must be unused in this stash (#3908).
            rx.setRandomId(RxStashIds.acceptOrNext(bean, request.getParameter("randomId"), RxStashIds.DEFAULT_BOUND));
            rx.setCustomNote(true);
            rx.setGenericName(null);
            rx.setBrandName(null);
            rx.setDrugForm("");
            rx.setRoute("");
            rx.setDosage("");
            rx.setUnit("");
			rx.setGCN_SEQNO("0");
            rx.setRegionalIdentifier("");
            rx.setAtcCode("");
            RxUtil.setDefaultSpecialQuantityRepeat(rx);
            rx = setCustomRxDurationQuantity(rx);

            List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();

            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listRxDrugs.add(rx);
            }
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);

            String today = null;
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            try {
                today = dateFormat.format(calendar.getTime());
                // p("today's date", today);
            } catch (Exception e) {
                logger.error("Error ({})", e.getClass().getSimpleName());
            }
            Date tod = RxUtil.StringToDate(today, "yyyy-MM-dd");
            rx.setRxDate(tod);
            rx.setWrittenDate(tod);

            request.setAttribute("listRxDrugs", listRxDrugs);
        } catch (Exception e) {
            logger.error("Error ({})", e.getClass().getSimpleName());
        }
        logger.debug("=============END newCustomNote RxWriteScript2Action.java===============");
        return "newRx";
    }

    /**
     * Reads earlier instructions for a staged card ({@code _rx} read); does not change any state.
     *
     * @return {@code NONE}; the result is written directly
     */
    public String listPreviousInstructions() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_READ);

        logger.debug("=============Start listPreviousInstructions RxWriteScript2Action.java===============");
        String randomId = request.getParameter("randomId");
        // get prescript from randomId.
        // if prescript is normal drug, if din is not null, use din to find it
        // if din is null, use BN to find it
        // if prescript is custom drug, use customName to find it.
        // append results to a list.
        RxSessionBean bean = RxRequestedPatientAccess.resolveForRead(securityInfoManager, request, "_rx", "r");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        randomId = randomId != null ? randomId.trim() : null;
        if (randomId == null || !randomId.matches("\\d+")) {
            logger.warn("listPreviousInstructions: invalid randomId");
            bean.setListMedHistory(new ArrayList<>());
            return null;
        }

        final int randomIdInt;
        try {
            randomIdInt = Integer.parseInt(randomId);
        } catch (NumberFormatException _) {
            logger.warn("listPreviousInstructions: randomId is out of range");
            bean.setListMedHistory(new ArrayList<>());
            return null;
        }

        // create Prescription
        RxPrescriptionData.Prescription rx = bean.getStashItem2(randomIdInt);
        if (rx == null) {
            logger.warn("listPreviousInstructions: no stash item found");
            bean.setListMedHistory(new ArrayList<>());
            return null;
        }
        List<HashMap<String, String>> retList = new ArrayList();
        retList = RxUtil.getPreviousInstructions(rx);

        bean.setListMedHistory(retList);
        return null;
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise). Stages a custom drug card.
     *
     * @return the operation's view result, {@code null} when no view is needed, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String newCustomDrug() throws IOException {
        logger.debug("=============Start newCustomDrug RxWriteScript2Action.java===============");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);
        if (refuseUnlessPost()) {
            return NONE;
        }

        // set default quantity;
        setDefaultQuantity(request);

        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }

        synchronized (bean) {
            return newCustomDrugLocked(bean, loggedInInfo);
        }
    }

    private String newCustomDrugLocked(RxSessionBean bean, LoggedInInfo loggedInInfo) {
        String customDrugName = request.getParameter("name");
        try {
            RxPrescriptionData rxData = new RxPrescriptionData();

            // create Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo());
            if (customDrugName != null && !customDrugName.isEmpty()) {
                rx.setCustomName(customDrugName);
            }
            // The page names the card's key; it must be unused in this stash (#3908).
            rx.setRandomId(RxStashIds.acceptOrNext(bean, request.getParameter("randomId"), RxStashIds.DEFAULT_BOUND));
            rx.setGenericName(null);
            rx.setBrandName(null);
            rx.setDrugForm("");
            rx.setRoute("");
            rx.setDosage("");
            rx.setUnit("");
			rx.setGCN_SEQNO("0");
            rx.setRegionalIdentifier("");
            rx.setAtcCode("");
            RxUtil.setDefaultSpecialQuantityRepeat(rx); // 1 OD, 20, 0;
            rx = setCustomRxDurationQuantity(rx);

            List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();

            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listRxDrugs.add(rx);
            }
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);

            String today = null;
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            try {
                today = dateFormat.format(calendar.getTime());
                // p("today's date", today);
            } catch (Exception e) {
                logger.error("Error ({})", e.getClass().getSimpleName());
            }
            Date tod = RxUtil.StringToDate(today, "yyyy-MM-dd");
            rx.setRxDate(tod);
            rx.setWrittenDate(tod);

            request.setAttribute("listRxDrugs", listRxDrugs);
        } catch (Exception e) {
            logger.error("Error ({})", e.getClass().getSimpleName());
        }
        return "newRx";
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise). Turns a staged catalogue drug into a custom one.
     *
     * @return the operation's view result, {@code null} when no view is needed, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String normalDrugSetCustom() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);
        if (refuseUnlessPost()) {
            return NONE;
        }

        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        synchronized (bean) {
            return normalDrugSetCustomLocked(bean);
        }
    }

    private String normalDrugSetCustomLocked(RxSessionBean bean) {
        String randomId = request.getParameter("randomId");
        String customDrugName = request.getParameter("customDrugName");
        logger.debug("radomId=" + randomId);
        if (randomId != null && customDrugName != null) {
            RxPrescriptionData.Prescription normalRx = bean.getStashItem2(Integer.parseInt(randomId));
            if (normalRx != null) {// set other fields same as normal drug, set some fields null like custom drug, remove normal drugfrom stash, add customdrug to stash,
                // forward to prescribe.jsp
                RxPrescriptionData.Prescription customRx = normalRx;
                customRx.setCustomName(customDrugName);
                customRx.setRandomId(Long.parseLong(randomId));
                customRx.setGenericName(null);
                customRx.setBrandName(null);
                customRx.setDrugForm("");
                customRx.setRoute("");
                customRx.setDosage("");
                customRx.setUnit("");
				customRx.setGCN_SEQNO("0");
                customRx.setRegionalIdentifier("");
                customRx.setAtcCode("");
                bean.setStashItem(bean.getIndexFromRx(Integer.parseInt(randomId)), customRx);
                List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();
                if (RxUtil.isRxUniqueInStash(bean, customRx)) {
                    // p("unique");
                    listRxDrugs.add(customRx);
                }
                request.setAttribute("listRxDrugs", listRxDrugs);
                return "newRx";
            } else {

                return null;
            }
        } else {

            return null;
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise). Stages a new card for a catalogue drug.
     *
     * @return the operation's view result, {@code null} when no view is needed, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String createNewRx() throws IOException {
        logger.debug("=============Start createNewRx RxWriteScript2Action.java===============");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);
        if (refuseUnlessPost()) {
            return NONE;
        }
        response.setContentType("application/json");
        // set default quantity
        setDefaultQuantity(request);
        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }

        synchronized (bean) {
            return createNewRxLocked(bean, loggedInInfo);
        }
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of internal domain values")
    private String createNewRxLocked(RxSessionBean bean, LoggedInInfo loggedInInfo) {
        String success = "newRx";
        try {
            RxPrescriptionData rxData = new RxPrescriptionData();
            RxDrugData drugData = new RxDrugData();

            // create Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo());

            // The page names the card's key; it must be unused in this stash (#3908).
            rx.setRandomId(RxStashIds.acceptOrNext(bean, request.getParameter("randomId"), RxStashIds.DEFAULT_BOUND));
            String drugId = request.getParameter("drugId");
            String text = request.getParameter("text");

			if (text != null) {
				text = Encode.forJava(text);
			}

			if (drugId != null) {
				drugId = Encode.forJava(drugId);
			}

            logger.debug("requesting drug from drugref id={}", LogSafe.sanitize(drugId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            RxDrugData.DrugMonograph dmono = drugData.getDrug2(drugId);

            // A DrugRef id that resolves to no product surfaces here as an empty
            // monograph (name/product null): typically an autocomplete row that is a
            // drug class or ingredient rather than a prescribable product, or an id
            // absent from a partially-populated drugref2. Previously this either NPEd
            // (components) or staged a nameless entry, and createNewRx's blanket catch
            // returned an empty pane with no message. Tell the clinician instead.
            if (dmono == null
                    || (dmono.name == null
                        && (dmono.getProduct() == null || dmono.getProduct().isEmpty()))) {
                logger.warn("createNewRx: no prescribable DrugRef product for drug id {}", LogSafe.sanitize(drugId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                request.setAttribute("rxStageError",
                    "The selected item could not be added as a prescription. "
                    + "Please choose a specific drug product (a brand or a strength), "
                    + "not a drug class or ingredient.");
                return success;
            }

			// this is the drug name the user selected from the autocomplete interface
			rx.setDrugPrescribed(text);

            ArrayList<DrugComponent> drugComponents = dmono.getDrugComponentList();
            if (drugComponents != null && drugComponents.size() > 0) {

                StringBuilder stringBuilder = new StringBuilder();
                int count = 0;
                for (RxDrugData.DrugMonograph.DrugComponent drugComponent : drugComponents) {

					String strength = drugComponent.getStrength();
					String unit = drugComponent.getUnit();
					String drugName = drugComponent.getName();
					String drugForm = dmono.drugForm;

					if (strength == null) {
						strength = "";
					}

					if (unit == null) {
						unit = "";
					}

					if (drugName == null) {
						drugName = "";
					}

					if (drugForm == null) {
						drugForm = "";
					}

					if (! strength.contains("/")) {
						strength = strength.toLowerCase().replace(unit.toLowerCase(), "");
						strength += unit.toLowerCase();
					}

					stringBuilder.append(drugName.trim());

					if ( ! stringBuilder.toString().toLowerCase().contains(strength.toLowerCase())) {
						stringBuilder.append( " " + strength.toLowerCase() );
					}

					if (! stringBuilder.toString().toLowerCase().endsWith(drugForm.toLowerCase())) {
						stringBuilder.append(" " + drugForm.toLowerCase());
					}

                    count++;
                    if (count > 0 && count != drugComponents.size()) {
                        stringBuilder.append(" / ");
                    }
                }

				rx.setGenericName(stringBuilder.toString().trim());
            } else {
                rx.setGenericName(dmono.name);
            }

			if (dmono.getProduct() != null && ! dmono.getProduct().isEmpty()) {
				rx.setBrandName(dmono.getProduct());
			} else {
				rx.setBrandName(text);
			}

            //there's a change there's multiple forms. Select the first one by default
            //save the list in a separate variable to make a drop down in the interface.
            if (dmono != null && dmono.drugForm != null && dmono.drugForm.indexOf(",") != -1) {
                String[] forms = dmono.drugForm.split(",");
                rx.setDrugForm(forms[0]);
            } else if (dmono.drugForm != null) {
                rx.setDrugForm(dmono.drugForm);
            } else if (dmono.drugForm == null) {
                rx.setDrugForm("");
            }
            rx.setDrugFormList(dmono.drugForm);

            // TO DO: cache the most used route from the drugs table.
            // for now, check to see if ORAL present, if yes use that, if not use the first one.
            boolean oral = false;
            for (int i = 0; i < dmono.route.size(); i++) {
                if (((String) dmono.route.get(i)).equalsIgnoreCase("ORAL")) {
                    oral = true;
                }
            }
            if (oral) {
                rx.setRoute("ORAL");
            } else {
                if (dmono.route.size() > 0) {
                    rx.setRoute((String) dmono.route.get(0));
                }
            }
            // if user specified route in instructions, it'll be changed to the one specified.
            String dosage = "";
            String unit = "";
            Vector comps = dmono.components;
            for (int i = 0; i < comps.size(); i++) {
                RxDrugData.DrugMonograph.DrugComponent drugComp = (RxDrugData.DrugMonograph.DrugComponent) comps.get(i);
                String strength = drugComp.strength;
                unit = drugComp.unit;

				if (strength == null) {
					strength = "";
				}

				if (unit == null) {
					unit = "";
				}

				// covers all cases when unit is missing -or- included with the strength.
				if (! strength.contains("/")) {
					strength = strength.toLowerCase().trim().replace(unit.toLowerCase().trim(), "");
					strength += unit.toLowerCase().trim();
				}

				dosage += (" " + strength.toLowerCase());
            }
            rx.setDosage(removeExtraChars(dosage));
            rx.setUnit(removeExtraChars(unit));
            rx.setGCN_SEQNO(drugId+"");
            rx.setRegionalIdentifier(dmono.regionalIdentifier);
            String atcCode = dmono.atc;
            rx.setAtcCode(atcCode);
            RxUtil.setSpecialQuantityRepeat(rx);
            rx = setCustomRxDurationQuantity(rx);
            List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();
            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listRxDrugs.add(rx);
            } else {
                // Duplicate of something already staged: the stash de-dupes, but the
                // pane is re-rendered from listRxDrugs, so leaving it empty blanked the
                // pane with no explanation. Surface a notice instead of a silent blank.
                request.setAttribute("rxStageError",
                    "That prescription is already staged.");
            }
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);
            String today = null;
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            try {
                today = dateFormat.format(calendar.getTime());
            } catch (Exception e) {
                logger.error("Error ({})", e.getClass().getSimpleName());
            }
            Date tod = RxUtil.StringToDate(today, "yyyy-MM-dd");
            rx.setRxDate(tod);
            rx.setWrittenDate(tod);
			rx.setDiscontinuedLatest(RxUtil.checkDiscontinuedBefore(rx)); // check and set if rx was discontinued before.
            request.setAttribute("listRxDrugs", listRxDrugs);
        } catch (Exception e) {
            logger.error("Error ({})", e.getClass().getSimpleName());
            // Fail loud: prescribe.jsp renders this notice in the staging pane rather
            // than returning an empty 200 that looks like "nothing happened" to the user.
            request.setAttribute("rxStageError",
                "Could not load the selected drug. Please try again or choose another product.");
        }
        logger.debug("=============END createNewRx RxWriteScript2Action.java===============");

        return success;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise). Rewrites a field of a staged card.
     *
     * @return the operation's view result, {@code null} when no view is needed, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("unused")
    public String updateDrug() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);
        if (refuseUnlessPost()) {
            return NONE;
        }

        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }

        synchronized (bean) {
            return updateDrugLocked(bean);
        }
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of internal domain values")
    private String updateDrugLocked(RxSessionBean bean) throws IOException {
        String action = request.getParameter("action");
        response.setContentType("application/json");

        if ("parseInstructions".equals(action)) {

            try {
                String randomId = request.getParameter("randomId");
                RxPrescriptionData.Prescription rx = bean.getStashItem2(Integer.parseInt(randomId));
                if (rx == null) {
                    logger.error("prescript is null", new NullPointerException());
                }

                String instructions = request.getParameter("instruction");
                rx.setSpecial(instructions);
                RxUtil.instrucParser(rx);
                bean.setStashItem(bean.getIndexFromRx(Integer.parseInt(randomId)), rx);

                HashMap<String, Object> hm = new HashMap<String, Object>();

                if (rx.getRoute() == null || rx.getRoute().equalsIgnoreCase("null")) {
                    rx.setRoute("");
                }

                hm.put("method", rx.getMethod());
                hm.put("takeMin", rx.getTakeMin());
                hm.put("takeMax", rx.getTakeMax());
                hm.put("duration", rx.getDuration());
                hm.put("frequency", rx.getFrequencyCode());
                hm.put("route", rx.getRoute());
                hm.put("durationUnit", rx.getDurationUnit());
                hm.put("prn", rx.getPrn());
                hm.put("calQuantity", rx.getQuantity());
                hm.put("unitName", rx.getUnitName());
                hm.put("policyViolations", rx.getPolicyViolations());
                ObjectNode jsonObject = objectMapper.valueToTree(hm);
                response.getOutputStream().write(jsonObject.toString().getBytes());
            } catch (Exception e) {
                logger.error("Error ({})", e.getClass().getSimpleName());
            }

        } else if ("updateQty".equals(action)) {

            try {
                String quantity = request.getParameter("quantity");
                String randomId = request.getParameter("randomId");
                RxPrescriptionData.Prescription rx = bean.getStashItem2(Integer.parseInt(randomId));
                // get prescript from randomId
                if (quantity == null || quantity.equalsIgnoreCase("null")) {
                    quantity = "";
                }
                // check if quantity is same as prescript.getquantity(), if yes, do nothing.
                if (quantity.equals(rx.getQuantity()) && rx.getUnitName() == null) {
                    // do nothing
                } else {

                    if (RxUtil.isStringToNumber(quantity)) {
                        rx.setQuantity(quantity);
                        rx.setUnitName(null);
                    } else if (RxUtil.isMitte(quantity)) {// set duration for mitte

                        String duration = RxUtil.getDurationFromQuantityText(quantity);
                        String durationUnit = RxUtil.getDurationUnitFromQuantityText(quantity);
                        rx.setDuration(duration);
                        rx.setDurationUnit(durationUnit);
                        rx.setQuantity(RxUtil.getQuantityFromQuantityText(quantity));
                        rx.setUnitName(RxUtil.getUnitNameFromQuantityText(quantity)); // this is actually an indicator for Mitte prescript
                    } else {
                        rx.setQuantity(RxUtil.getQuantityFromQuantityText(quantity));
                        rx.setUnitName(RxUtil.getUnitNameFromQuantityText(quantity));
                    }

                    String frequency = rx.getFrequencyCode();
                    String takeMin = rx.getTakeMinString();
                    String takeMax = rx.getTakeMaxString();
                    String durationUnit = rx.getDurationUnit();
                    double nPerDay = 0d;
                    double nDays = 0d;
                    if (rx.getUnitName() != null || takeMin.equals("0") || takeMax.equals("0") || frequency.equals("")) {
                    } else {
                        if (durationUnit == null || durationUnit.isEmpty()) {
                            durationUnit = "D";
                        }

                        nPerDay = RxUtil.findNPerDay(frequency);
                        nDays = RxUtil.findNDays(durationUnit);
                        if (RxUtil.isStringToNumber(quantity) && !rx.isDurationSpecifiedByUser()) {// don't not caculate duration if it's already specified by the user
                            double qtyD = Double.parseDouble(quantity);
                            // quantity=takeMax * nDays * duration * nPerDay
                            double durD = qtyD / ((Double.parseDouble(takeMax)) * nPerDay * nDays);
                            int durI = (int) durD;
                            rx.setDuration(Integer.toString(durI));
                        } else {
                            // don't calculate duration if quantity can't be parsed to string
                        }
                        rx.setDurationUnit(durationUnit);
                    }
                    // duration=quantity divide by no. of pills per duration period.
                    // if not, recalculate duration based on frequency if frequency is not empty
                    // if there is already a duration uni present, use that duration unit. if not, set duration unit to days, and output duration in days
                }
                bean.setStashItem(bean.getIndexFromRx(Integer.parseInt(randomId)), rx);

                if (rx.getRoute() == null) {
                    rx.setRoute("");
                }
                HashMap<String, Object> hm = new HashMap<String, Object>();
                hm.put("method", rx.getMethod());
                hm.put("takeMin", rx.getTakeMin());
                hm.put("takeMax", rx.getTakeMax());
                hm.put("duration", rx.getDuration());
                hm.put("frequency", rx.getFrequencyCode());
                hm.put("route", rx.getRoute());
                hm.put("durationUnit", rx.getDurationUnit());
                hm.put("prn", rx.getPrn());
                hm.put("calQuantity", rx.getQuantity());
                hm.put("unitName", rx.getUnitName());
                ObjectNode jsonObject = objectMapper.valueToTree(hm);

                response.getOutputStream().write(jsonObject.toString().getBytes());
            } catch (Exception e) {
                logger.error("Error ({})", e.getClass().getSimpleName());
            }
        }

        return null;

    }

    /**
     * Renders the staged cards of the resolved patient (read-only); nothing is staged when the patient has
     * no open Rx.
     *
     * @return the staged-card view, or {@code null} when nothing is staged
     */
    public String iterateStash() {
        // Dispatched before execute()'s common privilege check: check _rx read here, globally and
        // (through resolveForRead) for the patient whose stash is rendered (#3908).
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_READ);
        RxSessionBean bean = RxRequestedPatientAccess.resolveForRead(securityInfoManager, request, "_rx", "r");
        // No Rx session for this request's patient means nothing is staged.
        if (bean == null) {
            return null;
        }
        List<RxPrescriptionData.Prescription> listP = Arrays.asList(bean.getStash());
        if (listP.size() == 0) {
            return null;
        } else {
            request.setAttribute("listRxDrugs", listP);
            return "newRx";
        }

    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise). Sets the special instructions of a staged card; 409 when the request names no open patient.
     *
     * @return the operation's view result, {@code null} when no view is needed, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String updateSpecialInstruction() throws Exception {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);
        if (refuseUnlessPost()) {
            return NONE;
        }

        // get special instruction from parameter
        // get prescript from random Id
        // prescript.setspecialisntruction
        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        synchronized (bean) {
            return updateSpecialInstructionLocked(bean);
        }
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of internal domain values")
    private String updateSpecialInstructionLocked(RxSessionBean bean) throws IOException {
        String randomId = request.getParameter("randomId");
        String specialInstruction = request.getParameter("specialInstruction");
        RxPrescriptionData.Prescription rx = stagedCard(bean, randomId);
        if (rx == null || specialInstruction == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if (specialInstruction.trim().length() > 0 && !specialInstruction.trim().equalsIgnoreCase("Enter Special Instruction")) {
            rx.setSpecialInstruction(specialInstruction.trim());
        } else {
            rx.setSpecialInstruction(null);
        }

        return null;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise). Sets a property of a staged card; 409 when the request names no open patient.
     *
     * @return the operation's view result, {@code null} when no view is needed, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String updateProperty() throws Exception {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);
        if (refuseUnlessPost()) {
            return NONE;
        }

        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875).
        RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        synchronized (bean) {
            return updatePropertyLocked(bean);
        }
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of internal domain values")
    private String updatePropertyLocked(RxSessionBean bean) throws IOException {
        String elem = request.getParameter("elementId");
        String val = request.getParameter("propertyValue");
        if (elem == null || val == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        val = val.trim();
        String[] strArr = elem.split("_");
        if (strArr.length > 1) {
            String num = strArr[1];
            num = num.trim();
            RxPrescriptionData.Prescription rx = stagedCard(bean, num);
            if (rx == null) {
                // A malformed or stale card key names nothing to update: 400, not a 500.
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            if (elem.equals("method_" + num)) {
                if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setMethod(val);
            } else if (elem.equals("route_" + num)) {
                if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setRoute(val);
            } else if (elem.equals("frequency_" + num)) {
                if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setFrequencyCode(val);
            } else if (elem.equals("minimum_" + num)) {
                if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setTakeMin(Float.parseFloat(val));
            } else if (elem.equals("maximum_" + num)) {
                if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setTakeMax(Float.parseFloat(val));
            } else if (elem.equals("duration_" + num)) {
                if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setDuration(val);
            } else if (elem.equals("durationUnit_" + num)) {
                if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setDurationUnit(val);
            } else if (elem.equals("repeats_" + num)) {
                if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setRepeat(Integer.parseInt(val));
            } else if (elem.equals("prnVal_" + num)) {
                if (!val.equals("") && !val.equalsIgnoreCase("null")) {
                    if (val.equalsIgnoreCase("true")) rx.setPrn(true);
                    else rx.setPrn(false);
                } else rx.setPrn(false);
            }
        }
        return null;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    /**
     * Saves the staged prescription. POST-only; the request must name the window's patient, whose bean is
     * saved (409 otherwise), and the caller needs {@code _rx} write globally and for that patient with record
     * access, checked before anything is changed or saved. A save that names no staged card is refused (400);
     * one omitting a current card is stale and refused (409), before changing any draft. Re-prescribed
     * sources are archived only when their replacement is saved.
     *
     * @return {@code NONE} after an error response, otherwise {@code refresh}
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String updateSaveAllDrugs() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);

        // Saves the prescription, its drugs and any ReRx archival: POST-only, checked before the
        // stash is resolved or changed (CSRFGuard does not check GET). SearchDrug3 posts it.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader(HEADER_ALLOW, "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, POST_REQUIRED);
            return NONE;
        }

        RxSessionBean bean = RxSessionBeanResolver.resolve(request);
        // A save must name the patient of the window it came from, and that patient's bean must be
        // the one being saved (#3875). Never save on the no-patient fallback: with two charts open
        // it is whichever patient's Rx page was opened last.
        if (!RxSessionBeanResolver.isRequestForBeanPatient(request, bean)) {
            logger.warn("Refused prescription save: request does not name the prescribing window's patient");
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        // Before changing or saving anything: patient-level _rx write and record access for the
        // patient being saved, not only the global _rx write checked above (#3908).
        RxRequestedPatientAccess.requirePatient(securityInfoManager, LoggedInInfo.getLoggedInInfoFromSession(request),
                bean.getDemographicNo(), "_rx", PRIVILEGE_WRITE);
        RxReprintWorkspace.Entry previousReprint = RxReprintWorkspace.find(request.getSession(), bean.getDemographicNo());
        String result;
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Keep card-set validation, form updates and persistence together. A close from another
        // window must not shift a cached index onto another drug partway through this save.
        synchronized (bean) {
            result = updateSaveAllDrugsLocked(bean, loggedInInfo);
        }
        if ("refresh".equals(result)) {
            // Acquire the session mutex only after releasing the bean monitor: opening Rx takes
            // these locks in the opposite order while removing persisted drafts.
            RxReprintWorkspace.clearIfSame(request.getSession(), bean.getDemographicNo(), previousReprint);
        }
        return result;
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of internal domain values")
    private String updateSaveAllDrugsLocked(RxSessionBean bean, LoggedInInfo loggedInInfo) throws IOException {
        Enumeration em = request.getParameterNames();
        List<String> randNum = new ArrayList<String>();
        while (em.hasMoreElements()) {
            String ele = em.nextElement().toString();
            if (ele.startsWith("drugName_")) {
                String rNum = ele.substring(9);
                if (!randNum.contains(rNum)) {
                    randNum.add(rNum);
                }
            }
        }

        if (!validateSubmittedStash(bean, randNum)) {
            return NONE;
        }

        for (String num : randNum) {
            int randomId;
            try {
                randomId = Integer.parseInt(num);
            } catch (NumberFormatException _) {
                // A malformed card key names no staged item; skip it like a stale one.
                continue;
            }
            int stashIndex = bean.getIndexFromRx(randomId);
            try {
                if (stashIndex == -1) {
                    continue;
                } else {
                    RxPrescriptionData.Prescription rx = bean.getStashItem(stashIndex);

                    Boolean patientCompliance = null;
                    boolean isOutsideProvider = false;
                    Boolean isLongTerm = null;
                    boolean isShortTerm = false;
                    Boolean isPastMed = null;
                    boolean isDispenseInternal = false;
                    boolean isStartDateUnknown = false;
                    boolean isNonAuthoritative = false;
                    boolean nosubs = false;
                    Date pickupDate = null;
                    Date pickupTime = null;

                    em = request.getParameterNames();
                    while (em.hasMoreElements()) {
                        String elem = (String) em.nextElement();
                        String val = request.getParameter(elem);
                        val = val.trim();
                        if (elem.equals("drugName_" + num)) {
                            if (rx.isCustom()) {
                                rx.setCustomName(val);
                                rx.setBrandName(null);
                                rx.setGenericName(null);
                            } else {
                                rx.setBrandName(val);
                            }
                        } else if ("rxPharmacyId".equals(elem)) {
                            if (val != null && !val.isEmpty()) {
                                rx.setPharmacyId(Integer.parseInt(val));
                            }
                        } else if (elem.equals("repeats_" + num)) {
                            if (val.equals("") || val == null) {
                                rx.setRepeat(0);
                            } else {
                                rx.setRepeat(Integer.parseInt(val));
                            }

                        } else if (elem.equals("codingSystem_" + num)) {
                            if (val != null) {
                                rx.setDrugReasonCodeSystem(val);
                            }

                        } else if (elem.equals("reasonCode_" + num)) {
                            if (val != null) {
                                rx.setDrugReasonCode(val);
                            }
                        } else if (elem.equals("instructions_" + num)) {
                            rx.setSpecial(val);
                        } else if (elem.equals("quantity_" + num)) {
                            if (val.equals("") || val == null) {
                                rx.setQuantity("0");
                            } else {
                                if (RxUtil.isStringToNumber(val)) {
                                    rx.setQuantity(val);
                                    rx.setUnitName(null);
                                } else {
                                    rx.setQuantity(RxUtil.getQuantityFromQuantityText(val));
                                    rx.setUnitName(RxUtil.getUnitNameFromQuantityText(val));
                                }
                            }
                        } else if (elem.equals("longTerm_" + num)) {
                            if ("yes".equals(val)) {
                                isLongTerm = true;
                            } else if ("no".equals(val)) {
                                isLongTerm = false;
                            }
                        } else if (elem.equals("shortTerm_" + num)) {
                            if (val.equals("on")) {
                                isShortTerm = true;
                            } else {
                                isShortTerm = false;
                            }
                        } else if (elem.equals("nonAuthoritativeN_" + num)) {
                            if (val.equals("on")) {
                                isNonAuthoritative = true;
                            } else {
                                isNonAuthoritative = false;
                            }
                        } else if (elem.equals("nosubs_" + num)) {
                            nosubs = "on".equals(val);
                        } else if (elem.equals("refillDuration_" + num)) {
                            if (val != null && !val.isEmpty() && !val.equalsIgnoreCase("null")) rx.setRefillDuration(Integer.parseInt(val));
                        } else if (elem.equals("refillQuantity_" + num)) {
                            rx.setRefillQuantity(Integer.parseInt(val));
                        } else if (elem.equals("dispenseInterval_" + num)) {
                            rx.setDispenseInterval(val);
                        } else if (elem.equals("protocol_" + num)) {
                            rx.setProtocol(val);
                        } else if (elem.equals("priorRxProtocol_" + num)) {
                            rx.setPriorRxProtocol(val);
                        } else if (elem.equals("lastRefillDate_" + num)) {
                            rx.setLastRefillDate(RxUtil.StringToDate(val, "yyyy-MM-dd"));
                        } else if (elem.equals("rxDate_" + num)) {
                            if ((val == null) || (val.equals(""))) {
                                rx.setRxDate(RxUtil.StringToDate("0000-00-00", "yyyy-MM-dd"));
                            } else {
                                rx.setRxDateFormat(partialDateDao.getFormat(val));
                                rx.setRxDate(partialDateDao.StringToDate(val));
                            }
                        } else if (elem.equals("pickupDate_" + num)) {
                            if ((val != null) && (!val.equals(""))) {
                                pickupDate = RxUtil.StringToDate(val, "yyyy-MM-dd");
                            }
                        } else if (elem.equals("pickupTime_" + num)) {
                            if ((val != null) && (!val.equals(""))) {
                                pickupTime = RxUtil.StringToDate(val, "hh:mm");
                            }
                        } else if (elem.equals("writtenDate_" + num)) {
                            if (val == null || (val.equals(""))) {
                                rx.setWrittenDate(RxUtil.StringToDate("0000-00-00", "yyyy-MM-dd"));
                            } else {
                                rx.setWrittenDateFormat(partialDateDao.getFormat(val));
                                rx.setWrittenDate(partialDateDao.StringToDate(val));
                            }

                        } else if (elem.equals("outsideProviderName_" + num)) {
                            rx.setOutsideProviderName(val);
                        } else if (elem.equals("outsideProviderOhip_" + num)) {
                            if (val.equals("") || val == null) {
                                rx.setOutsideProviderOhip("0");
                            } else {
                                rx.setOutsideProviderOhip(val);
                            }
                        } else if (elem.equals("ocheck_" + num)) {
                            if (val.equals("on")) {
                                isOutsideProvider = true;
                            } else {
                                isOutsideProvider = false;
                            }
                        } else if (elem.equals("pastMed_" + num)) {
                            if ("yes".equals(val)) {
                                isPastMed = true;
                            } else if ("no".equals(val)) {
                                isPastMed = false;
                            }
                        } else if (elem.equals("dispenseInternal_" + num)) {
                            if (val.equals("on")) {
                                isDispenseInternal = true;
                            } else {
                                isDispenseInternal = false;
                            }
                        } else if (elem.equals("startDateUnknown_" + num)) {
                            if (val.equals("on")) {
                                isStartDateUnknown = true;
                            } else {
                                isStartDateUnknown = false;
                            }
                        } else if (elem.equals("comment_" + num)) {
                            rx.setComment(val);
                        } else if (elem.equals("patientCompliance_" + num)) {
                            if ("yes".equals(val)) {
                                patientCompliance = true;
                            } else if ("no".equals(val)) {
                                patientCompliance = false;
                            }
                        } else if (elem.equals("eTreatmentType_" + num)) {
                            if ("--".equals(val)) {
                                rx.setETreatmentType(null);
                            } else {
                                rx.setETreatmentType(val);
                            }
                        } else if (elem.equals("rxStatus_" + num)) {
                            if ("--".equals(val)) {
                                rx.setRxStatus(null);
                            } else {
                                rx.setRxStatus(val);
                            }
                        } else if (elem.equals("drugForm_" + num)) {
                            rx.setDrugForm(val);
                        }

                    }

                    if (!isOutsideProvider) {
                        rx.setOutsideProviderName("");
                        rx.setOutsideProviderOhip("");
                    }
                    rx.setPastMed(isPastMed);
                    rx.setDispenseInternal(isDispenseInternal);
                    rx.setPatientCompliance(patientCompliance);
                    rx.setStartDateUnknown(isStartDateUnknown);
                    rx.setLongTerm(isLongTerm);
                    rx.setShortTerm(isShortTerm);
                    rx.setNonAuthoritative(isNonAuthoritative);
                    rx.setNosubs(nosubs);
                    String newline = System.getProperty("line.separator");

                    if (pickupDate != null && pickupTime != null) {
                        rx.setPickupDate(RxUtil.combineDateTime(pickupDate, pickupTime));
                    } else if (pickupTime != null) {
                        rx.setPickupDate(RxUtil.combineDateTime(new Date(), pickupTime));
                    } else {
                        rx.setPickupDate(pickupDate);
                    }

                    String special;
                    if (rx.isCustomNote()) {
                        rx.setQuantity(null);
                        rx.setUnitName(null);
                        rx.setRepeat(0);
                        special = rx.getCustomName() + newline + rx.getSpecial();
                        if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                            special += newline + rx.getSpecialInstruction();
                    } else if (rx.isCustom()) {// custom drug
                        if (rx.getUnitName() == null) {
                            special = rx.getCustomName() + newline + rx.getSpecial();
                            if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                                special += newline + rx.getSpecialInstruction();
                            special += newline + "Qty:" + rx.getQuantity() + " Repeats:" + "" + rx.getRepeat();
                        } else {
                            special = rx.getCustomName() + newline + rx.getSpecial();
                            if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                                special += newline + rx.getSpecialInstruction();
                            special += newline + "Qty:" + rx.getQuantity() + " " + rx.getUnitName() + " Repeats:" + "" + rx.getRepeat();
                        }
                    } else {// non-custom drug
                        if (rx.getUnitName() == null) {
                            special = rx.getBrandName() + newline + rx.getSpecial();
                            if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                                special += newline + rx.getSpecialInstruction();

                            special += newline + "Qty:" + rx.getQuantity() + " Repeats:" + "" + rx.getRepeat();
                        } else {
                            special = rx.getBrandName() + newline + rx.getSpecial();
                            if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                                special += newline + rx.getSpecialInstruction();
                            special += newline + "Qty:" + rx.getQuantity() + " " + rx.getUnitName() + " Repeats:" + "" + rx.getRepeat();
                        }
                    }

                    if (!rx.isCustomNote() && rx.isMitte()) {
                        special = special.replace("Qty", "Mitte");
                    }

                    rx.setSpecial(special.trim());

                    bean.setStashItem(stashIndex, rx);
                }
            } catch (Exception e) {
                logger.error("Error ({})", e.getClass().getSimpleName());
                continue;
            }
        }
        // The patient and permission were checked before taking the bean monitor. Re-resolving
        // through saveDrug here would acquire the session mutex while holding the bean lock.
        persistStash(loggedInInfo, bean);
        return "refresh";
    }

    /**
     * Checks the whole submitted card set before any draft is changed. A missing form field is
     * not a close: another window may have staged that card after this form was rendered.
     * Explicit close requests remove cards from the shared stash before acknowledging the UI.
     * Called while holding the bean monitor, together with the subsequent updates and save.
     */
    private boolean validateSubmittedStash(RxSessionBean bean, List<String> submittedKeys) throws IOException {
        Set<Integer> submittedIndexes = new HashSet<>();
        for (String key : submittedKeys) {
            try {
                int index = bean.getIndexFromRx(Integer.parseInt(key));
                if (index >= 0) submittedIndexes.add(index);
            } catch (NumberFormatException _) {
                // Malformed or stale keys cannot name a current card.
            }
        }
        if (submittedIndexes.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        if (submittedIndexes.size() != bean.getStashSize()) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"STALE_RX_STASH\"}");
            return false;
        }
        return true;
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * POST-only (405 + {@code Allow: POST} otherwise). Toggles a saved drug's long-term flag by saving a copy and
     * archiving the original; the drug must belong to the patient.
     *
     * @return {@code NONE}; the JSON result is written directly
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String updateLongTermStatus() throws IOException, Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);
        if (refuseUnlessPost()) {
            return NONE;
        }

        HashMap<String, Object> hm = new HashMap<>();
        String strId = request.getParameter("ltDrugId");
        boolean isLongTerm = Boolean.parseBoolean(request.getParameter("isLongTerm"));

        // A malformed id is answered like a missing one instead of a 500 from parseInt.
        if (Objects.isNull(strId) || !strId.matches("\\d{1,9}")) {
	        hm.put("success", false);
		} else {
            int drugId = Integer.parseInt(strId);
            // Saves and archives a chart drug: only the named patient's bean, never the fallback (#3875).
            RxSessionBean bean = RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
            if (bean == null) {
                response.sendError(HttpServletResponse.SC_CONFLICT);
                return NONE;
            }

            RxPrescriptionData rxData = new RxPrescriptionData();
            RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);
            if (oldRx == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return NONE;
            }
            if (oldRx.getDemographicNo() != bean.getDemographicNo()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
            oldRx.setLongTerm(isLongTerm);
            oldRx.setShortTerm(false);
            boolean saveStatus = oldRx.Save(oldRx.getScript_no());

            if (saveStatus) {
                saveStatus = this.rxManager.archiveDrug(loggedInInfo, drugId, bean.getDemographicNo(),
                        isLongTerm ? Drug.ARCHIVED_REASON_LT_ENABLED : Drug.ARCHIVED_REASON_LT_DISABLED);
            }

            hm.put("success", saveStatus);
        }
        response.setContentType("application/json");
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        response.getOutputStream().write(jsonObject.toString().getBytes());
        return NONE;
    }
  
    /**
     * Persists the staged cards of the request's patient and archives their re-prescribed sources. Skips
     * (without writing) a request that does not name the bean's patient or has nothing staged, and requires
     * {@code _rx} write for that patient with record access.
     *
     * @param request the current request
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public void saveDrug(final HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        RxSessionBean bean = RxSessionBeanResolver.resolve(request);
        if (!RxSessionBeanResolver.isRequestForBeanPatient(request, bean)) {
            logger.warn("Skipped prescription save: request does not name the prescribing window's patient");
            return;
        }
        RxRequestedPatientAccess.requirePatient(securityInfoManager, loggedInInfo,
                bean.getDemographicNo(), "_rx", PRIVILEGE_WRITE);

        // Nothing staged: do not write an empty script, and above all do not reach the re-Rx
        // archival below. A ReRx box that was ticked but never staged would otherwise archive
        // the patient's active medication with no replacement written (#3869). The prescribing
        // page blocks this too, but the server must not rely on it.
        if (bean.getStashSize() == 0) {
            logger.info("Skipped prescription save: no staged medications");
            return;
        }

        persistStash(loggedInInfo, bean);
    }

    /**
     * Persists the stash as one prescription and settles its re-prescriptions: every staged item
     * is written under the new script, the sources whose replacement was just written are
     * archived as REPRESCRIBED, and the ReRx list is cleared so a later save cannot archive them
     * again. Every path that writes the stash (updateSaveAllDrugs, updateAndPrint and the
     * write-script fallback in RxViewScript2Action) goes through here, so the ReRx invariant -- a
     * source is archived exactly when its replacement is saved -- holds on all of them (#3908).
     * Runs under the bean's monitor: a card closed from another window of the same patient
     * mid-save can neither shift the indexes being written nor be half-saved.
     *
     * @param loggedInInfo the prescriber
     * @param bean         the patient's Rx bean, already authorised for write and non-empty
     * @return the new script id, or null when no staged cards remain
     */
    // The shared session bean is the lock used by all synchronized stash accessors. A separate
    // action-local monitor would not serialize operations from two windows of the same patient.
    @SuppressWarnings("java:S2445")
    String persistStash(LoggedInInfo loggedInInfo, RxSessionBean bean) {
        synchronized (bean) {
            if (bean.getStashSize() == 0) {
                return null;
            }
            return persistStashLocked(loggedInInfo, bean);
        }
    }

    private String persistStashLocked(LoggedInInfo loggedInInfo, RxSessionBean bean) {
        RxPrescriptionData.Prescription rx = null;
        RxPrescriptionData prescription = new RxPrescriptionData();
        String scriptId = prescription.saveScript(loggedInInfo, bean);
        StringBuilder auditStr = new StringBuilder();
        // Source drug ids actually re-prescribed by this save. A staged re-prescription carries
        // its source drug id in drugReferenceId, set by the re-prescribe newPrescription overload,
        // and only those sources may be archived as REPRESCRIBED.
        Set<Integer> represcribedSourceIds = new HashSet<>();
        for (int i = 0; i < bean.getStashSize(); i++) {
            try {
                rx = bean.getStashItem(i);
                rx.Save(scriptId); // new drug id available after this line
                rx.setScript_no(scriptId);
                if (rx.getDrugReferenceId() > 0) {
                    represcribedSourceIds.add(rx.getDrugReferenceId());
                }
                bean.addRandomIdDrugIdPair(rx.getRandomId(), rx.getDrugId());
                auditStr.append(rx.getAuditString());
                auditStr.append("\n");

                // save drug reason. Method borrowed from
                // RxReason2Action.
                if (!StringUtils.isNullOrEmpty(rx.getDrugReasonCode())) {
                    addDrugReason(rx.getDrugReasonCodeSystem(),
                            "false", "", rx.getDrugReasonCode(),
                            rx.getDrugId() + "", rx.getDemographicNo() + "",
                            rx.getProviderNo(), request);
                }

                //write partial date
                if (StringUtils.filled(rx.getWrittenDateFormat()))
                    partialDateDao.setPartialDate(PartialDate.DRUGS, rx.getDrugId(), PartialDate.DRUGS_WRITTENDATE, rx.getWrittenDateFormat());

                if (StringUtils.filled(rx.getRxDateFormat()))
                    partialDateDao.setPartialDate(PartialDate.DRUGS, rx.getDrugId(), PartialDate.DRUGS_STARTDATE, rx.getRxDateFormat());
            } catch (Exception e) {
                logger.error("Error ({})", e.getClass().getSimpleName());
            }

            rx = null;
        }

        String ip = request.getRemoteAddr();
        request.setAttribute("scriptId", scriptId);

        // The stamp is NOT applied here. saveDrug is a separate AJAX request whose response is JSON,
        // so the RX_STAMP_SIGNATURE_APPLIED signal it would set could not reach the ViewScript2
        // render that follows (opened by popForm2 -> RxViewScript2Action), and the pad would be
        // hidden. The stamp is applied by the CSRF-protected POST to RxViewScript2Action,
        // which reuses this same script row and renders the page — keeping the pad available
        // to override the stamp. Ordinary GET/HEAD preview navigation never stamps or saves.

        archiveReRxDrugs(loggedInInfo, bean, represcribedSourceIds, ip, auditStr.toString());
        // The sources are settled: a later save from this window (Edit Rx after Save & Print)
        // must not archive them again with a second audit trail.
        bean.clearReRxDrugIdList();

        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_PRESCRIPTION, scriptId, ip, "" + bean.getDemographicNo(), auditStr.toString());

        return scriptId;
    }

    /**
     * Returns a JSON list of instruction suggestions for the autocomplete on the instructions field.
     * Uses the same data source as displayMedHistory (RxUtil.getPreviousInstructions), filtered by
     * the typed term.
     *
     * @return null (writes JSON directly to response)
     * @throws IOException if response writing fails
     * @since 2026-03-22
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String getInstructionsAutocomplete() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_READ);

        String randomId = request.getParameter("randomId");
        String term = request.getParameter("term");

        // Reject excessively long term values to prevent potential abuse
        if (term != null && term.length() > 100) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"results\":[]}");
            return NONE;
        }

        RxSessionBean bean = RxRequestedPatientAccess.resolveForRead(securityInfoManager, request, "_rx", "r");
        if (bean == null || randomId == null || randomId.isBlank()) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"results\":[]}");
            return NONE;
        }

        int randomIdInt;
        try {
            randomIdInt = Integer.parseInt(randomId.trim());
        } catch (NumberFormatException _) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"results\":[]}");
            return NONE;
        }
        RxPrescriptionData.Prescription rx = bean.getStashItem2(randomIdInt);
        if (rx == null) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"results\":[]}");
            return NONE;
        }
        List<HashMap<String, String>> history = RxUtil.getPreviousInstructions(rx);
        if (history == null) {
            history = new ArrayList<>();
        }

        List<String> instructions = new ArrayList<>();
        for (HashMap<String, String> hm : history) {
            String ins = hm.get("instruction");
            if (ins != null && !ins.equalsIgnoreCase("null") && !ins.trim().isEmpty()) {
                String trimmed = ins.trim();
                // filter by typed term (case-insensitive), or include all if term is empty
                if (term == null || term.isEmpty() || trimmed.toLowerCase().contains(term.toLowerCase())) {
                    if (!instructions.contains(trimmed)) {
                        instructions.add(trimmed);
                    }
                }
            }
        }

        Map<String, Object> json = new HashMap<>();
        json.put("results", instructions);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), json);
        return NONE;
    }

    /**
     * Autocomplete for stored special instructions ({@code _rx} read); reads only.
     *
     * @return {@code NONE}; the JSON is written directly
     */
    public String searchSpecialInstructions() throws IOException {
        // Dispatched before execute()'s common privilege check; stored instructions are Rx data.
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_READ);
		String str = request.getParameter("query");
		Set<String> set = this.rxManager.getStoredInstructionsMatching(str);

		Map<String, Object> json = new HashMap<>();
        json.put("results", set);

		response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

		ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getWriter(), json);

        // JSON is written directly: a named result would forward prescribe.jsp over it.
        return NONE;
	}

    /**
     * Reports how many cards the resolved patient has staged (0 without an open Rx); reads only.
     *
     * @return {@code NONE}; the JSON is written directly
     */
    public String checkNoStashItem() throws IOException, Exception {
        // Dispatched before execute()'s common privilege check: the staged count is Rx state, so
        // require global _rx read here and, through resolveForRead, for the patient (#3908).
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_READ);
        RxSessionBean bean = RxRequestedPatientAccess.resolveForRead(securityInfoManager, request, "_rx", "r");
        // No Rx session for this request's patient: report an empty stash rather than a 500.
        int n = bean == null ? 0 : bean.getStashSize();
        HashMap hm = new HashMap();
        hm.put("NoStashItem", n);
        ObjectNode jsonObject = objectMapper.valueToTree(hm);

        response.setContentType("application/json");

        response.getOutputStream().write(jsonObject.toString().getBytes());
        return NONE;
    }


    /**
     * Refuses a non-POST request to a dispatch that changes staged Rx state or the chart (stash
     * edits, custom drugs, new staged items, property and long-term updates): CSRFGuard does not
     * check GET, so a cross-origin GET could otherwise drive the write. Every UI caller POSTs.
     * Callers must return {@link #NONE} when this answers {@code true}; it runs after the privilege
     * check and before any state is resolved or changed.
     *
     * @return {@code true} when the request was refused with 405
     */
    private boolean refuseUnlessPost() throws IOException {
        if ("POST".equals(request.getMethod())) {
            return false;
        }
        response.setHeader(HEADER_ALLOW, "POST");
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, POST_REQUIRED);
        return true;
    }

    private void checkPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", privilege, null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
    }

    /**
     * Archives the source drugs of the re-prescriptions this save actually wrote. More than one
     * entry point appends to the staged list, so ownership is re-checked here rather than trusted
     * from staging time.
     *
     * <p>A staged re-Rx id is archived only when a saved stash item was re-prescribed from it
     * ({@code savedSourceIds}). Ticking a ReRx box records the id before any card is staged, and
     * the card can then be closed or never staged at all; archiving on the staged id alone
     * removed the patient's active medication with nothing written to replace it (#3869).</p>
     *
     * <p>A rejected drug is skipped, not fatal: the new prescription is already persisted by this
     * point and no transaction spans the loop, so aborting would leave a half-written script.
     * That includes an unchecked exception from {@link RxManager#archiveDrug} (a demographic-scoped
     * authorization failure, or a row with no demographic).</p>
     *
     * <p>A malformed, null, unsaved, or refused entry is skipped individually so one bad id cannot
     * stop the remaining staged drugs from being archived.</p>
     *
     * <p>Package-private for the re-prescribe regression tests.</p>
     *
     * @param loggedInInfo   the provider performing the re-prescribe, used for the archival
     *                       authorization check and the audit entries
     * @param bean           the Rx session supplying both the staged drug ids and the demographic
     *                       they are validated against
     * @param savedSourceIds source drug ids of the re-prescriptions persisted by this save
     * @param ip             caller address recorded on the re-prescribe audit entry
     * @param auditStr       audit detail string shared with the enclosing save
     * @since 2026-08-16
     */
    void archiveReRxDrugs(LoggedInInfo loggedInInfo, RxSessionBean bean, Set<Integer> savedSourceIds,
                          String ip, String auditStr) {
        // Drug ids and demographic numbers correlate to patient records (CLAUDE.md PHI policy), so
        // skipped entries are only counted by reason and summarised once below.
        int malformed = 0;
        int notReprescribed = 0;
        int failed = 0;
        int refused = 0;
        for (String item : bean.getReRxDrugIdList()) {
            switch (archiveReRxSource(item, loggedInInfo, bean, savedSourceIds, ip, auditStr)) {
                case MALFORMED -> malformed++;
                case NOT_REPRESCRIBED -> notReprescribed++;
                case FAILED -> failed++;
                case REFUSED -> refused++;
                default -> { /* ARCHIVED: nothing to count */ }
            }
        }
        if (malformed + failed + refused > 0) {
            logger.warn("Skipped re-Rx archival: {} malformed staged id(s), {} archive failure(s), "
                    + "{} not found or not owned by the Rx patient", malformed, failed, refused);
        }
        if (notReprescribed > 0) {
            logger.info("Skipped re-Rx archival: {} staged source(s) not re-prescribed in this save", notReprescribed);
        }
    }

    /** The staged card carrying the request's key, or {@code null} for a malformed or unknown key. */
    private static RxPrescriptionData.Prescription stagedCard(RxSessionBean bean, String rawKey) {
        if (rawKey == null || !rawKey.trim().matches("\\d{1,9}")) {
            return null;
        }
        return bean.getStashItem2(Integer.parseInt(rawKey.trim()));
    }

    /** Why one staged re-Rx source was, or was not, archived by {@link #archiveReRxDrugs}. */
    private enum ArchiveOutcome { ARCHIVED, MALFORMED, NOT_REPRESCRIBED, FAILED, REFUSED }

    /**
     * Archives one staged re-Rx source as REPRESCRIBED and writes its audit entries, or says why
     * it was left alone. The caller counts the outcomes: ids correlate to patient records, so they
     * are not logged individually.
     */
    private ArchiveOutcome archiveReRxSource(String item, LoggedInInfo loggedInInfo, RxSessionBean bean,
                                             Set<Integer> savedSourceIds, String ip, String auditStr) {
        // The list permits nulls, and item.trim() would throw past the catch below, stranding
        // every later entry after the new script is already persisted.
        if (item == null) {
            return ArchiveOutcome.MALFORMED;
        }
        int sourceId;
        try {
            sourceId = Integer.parseInt(item.trim());
        } catch (NumberFormatException _) {
            return ArchiveOutcome.MALFORMED;
        }
        if (!savedSourceIds.contains(sourceId)) {
            // Ticked but never staged (or its card was closed): the source medication stays
            // active because no replacement was written.
            return ArchiveOutcome.NOT_REPRESCRIBED;
        }
        boolean archived;
        try {
            archived = this.rxManager.archiveDrug(loggedInInfo, sourceId, bean.getDemographicNo(), Drug.REPRESCRIBED);
        } catch (RuntimeException _) {
            return ArchiveOutcome.FAILED;
        }
        if (!archived) {
            // archiveDrug() cannot distinguish a missing row from a cross-patient one.
            return ArchiveOutcome.REFUSED;
        }
        //log that this med is being re-prescribed
        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.REPRESCRIBE, LogConst.CON_MEDICATION, "drugid=" + sourceId, ip, "" + bean.getDemographicNo(), auditStr);
        //log that the med is being discontinued buy the system
        LogAction.addLog("-1", LogConst.DISCONTINUE, LogConst.CON_MEDICATION, "drugid=" + sourceId, "", "" + bean.getDemographicNo(), auditStr);
        return ArchiveOutcome.ARCHIVED;
    }

    /**
     * Removes every staged card re-prescribed from {@code drugId}, if any. Through removeStashItem,
     * which keeps the cursor on the same card; an iterator removal left it pointing one card too
     * far (#3908).
     */
    // The shared session bean is the lock used by all synchronized stash accessors. A separate
    // action-local monitor would not serialize operations from two windows of the same patient.
    @SuppressWarnings("java:S2445")
    private static void removeStagedCopy(RxSessionBean bean, String drugId) {
        int sourceId;
        try {
            sourceId = Integer.parseInt(drugId);
        } catch (NumberFormatException e) {
            logger.error("Prescription update failed ({})", e.getClass().getSimpleName());
            return;
        }
        synchronized (bean) {
            for (int i = bean.getStashSize() - 1; i >= 0; i--) {
                if (bean.getStashItem(i).getDrugReferenceId() == sourceId) {
                    bean.removeStashItem(i);
                }
            }
        }
    }

    /**
     * Confirms a client-supplied drug id belongs to the demographic the Rx session is scoped to.
     *
     * <p>The {@code _rx} write privilege says the caller may prescribe, not that this drug row is
     * theirs to touch. Without this, a caller in one chart can stage another patient's drug id and
     * have it archived.</p>
     */
    private boolean isDrugOwnedByDemographic(String drugIdParam, int sessionDemographicNo) {
        if (drugIdParam == null || drugIdParam.trim().isEmpty()) {
            logger.warn("Blocked re-Rx staging: no drug id supplied");
            return false;
        }

        int parsedDrugId;
        try {
            parsedDrugId = Integer.parseInt(drugIdParam.trim());
        } catch (NumberFormatException _) {
            logger.warn("Blocked re-Rx staging: malformed drug id");
            return false;
        }

        DrugDao drugDao = SpringUtils.getBean(DrugDao.class);
        Drug drug = drugDao.find(parsedDrugId);
        if (drug == null) {
            logger.warn("Blocked re-Rx staging: drug not found");
            return false;
        }

        // getDemographicId() is a nullable Integer -- '!=' would unbox to an NPE on a null demographic_no.
        if (!Objects.equals(drug.getDemographicId(), sessionDemographicNo)) {
            // No ids: drug and demographic numbers correlate to patient records.
            logger.warn("Blocked cross-patient re-Rx staging: drug does not belong to the Rx session's patient");
            return false;
        }

        return true;
    }

    private void addDrugReason(String codingSystem,
                               String primaryReasonFlagStr, String comments,
                               String code, String drugIdStr, String demographicNo,
                               String providerNo, HttpServletRequest request) {
        DrugReasonDao drugReasonDao = (DrugReasonDao) SpringUtils.getBean(DrugReasonDao.class);
        Integer drugId = Integer.parseInt(drugIdStr);

        // should this be instantiated with the Spring Utilities?
        CodingSystemManager codingSystemManager = new CodingSystemManager();

        if (!codingSystemManager.isCodeAvailable(codingSystem, code)) {
            request.setAttribute("message", getText("SelectReason.error.codeValid"));
            return;
        }

        if (drugReasonDao.hasReason(drugId, codingSystem, code, true)) {
            request.setAttribute("message", getText("SelectReason.error.duplicateCode"));
            return;
        }

        boolean primaryReasonFlag = true;
        if (!"true".equals(primaryReasonFlagStr)) {
            primaryReasonFlag = false;
        }

        DrugReason dr = new DrugReason();

        dr.setDrugId(drugId);
        dr.setProviderNo(providerNo);
        dr.setDemographicNo(Integer.parseInt(demographicNo));

        dr.setCodingSystem(codingSystem);
        dr.setCode(code);
        dr.setComments(comments);
        dr.setPrimaryReasonFlag(primaryReasonFlag);
        dr.setArchivedFlag(false);
        dr.setDateCoded(new Date());

        drugReasonDao.addNewDrugReason(dr);

        String ip = request.getRemoteAddr();
        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_DRUGREASON, "" + dr.getId(), ip, demographicNo, dr.getAuditString());

    }

    String action = "";
    int drugId = 0;
    int demographicNo = 0;
    String rxDate = null;       //cnv to Date
    String endDate = null;      //cnv to Date
    String writtenDate = null;    //cnv to Date
    String GN = null;
    String BN = null;
    String GCN_SEQNO = "0";
    String customName = null;
    String takeMin = null;
    String takeMax = null;
    String frequencyCode = null;
    String duration = null;
    String durationUnit = null;
    String quantity = null;
    int repeat = 0;
    String lastRefillDate = null;
    boolean nosubs = false;
    boolean prn = false;
    boolean customInstr = false;
    Boolean longTerm = null;
    boolean shortTerm = false;
    Boolean pastMed = null;
    boolean dispenseInternal = false;
    Boolean patientCompliance = null;
    String special = null;
    String atcCode = null;
    String regionalIdentifier = null;
    String method = null;
    String unit = null;
    String unitName = null;
    String route = null;
    String dosage = null;
    String outsideProviderName = null;
    String outsideProviderOhip = null;


    public String getAction() {
        return this.action;
    }

    @StrutsParameter
    public void setAction(String RHS) {
        this.action = RHS;
    }

    public int getDrugId() {
        return this.drugId;
    }

    @StrutsParameter
    public void setDrugID(int RHS) {
        this.drugId = RHS;
    }

    public int getDemographicNo() {
        return this.demographicNo;
    }

    /**
     * Not a Struts parameter. The patient is resolved from the request by
     * {@link RxSessionBeanResolver}, which accepts the same demographicNo repeated in the URL and
     * the form body. Binding it here turned that repeat into an int conversion error, and the
     * workflow interceptor answered every such save with the unmapped {@code input} result
     * (HTTP 404, the prescription was not saved).
     */
    public void setDemographicNo(int RHS) {
        this.demographicNo = RHS;
    }

    public String getRxDate() {
        return this.rxDate;
    }

    @StrutsParameter
    public void setRxDate(String RHS) {
        this.rxDate = RHS;
    }

    public String getEndDate() {
        return this.endDate;
    }

    @StrutsParameter
    public void setEndDate(String RHS) {
        this.endDate = RHS;
    }

    public String getWrittenDate() {
        return this.writtenDate;
    }

    @StrutsParameter
    public void setWrittenDate(String RHS) {
        this.writtenDate = RHS;
    }

    public String getGenericName() {
        return this.GN;
    }

    @StrutsParameter
    public void setGenericName(String RHS) {
        this.GN = RHS;
    }

    public String getBrandName() {
        return this.BN;
    }

    @StrutsParameter
    public void setBrandName(String RHS) {
        this.BN = RHS;
    }

    public String getGCN_SEQNO() {
        return this.GCN_SEQNO;
    }

    @StrutsParameter
    public void setGCN_SEQNO(String RHS) {
        this.GCN_SEQNO = RHS;
    }

    public String getCustomName() {
        return this.customName;
    }

    @StrutsParameter
    public void setCustomName(String RHS) {
        this.customName = RHS;
    }

    public String getTakeMin() {
        return this.takeMin;
    }

    @StrutsParameter
    public void setTakeMin(String RHS) {
        this.takeMin = RHS;
    }

    public float getTakeMinFloat() {
        try {
            return Float.parseFloat(this.takeMin);
        } catch (NumberFormatException | NullPointerException _) {
            // -1 is the legacy "unspecified" sentinel. Absent/empty input maps to it as intended; a
            // non-empty value that fails to parse is a data-quality problem, so surface it (previously
            // swallowed silently) rather than corrupting the dose to -1 with no signal.
            if (this.takeMin != null && !this.takeMin.trim().isEmpty()) {
                logger.warn("Unparseable takeMin dose value; defaulting to the -1 unspecified sentinel");
            }
            return -1;
        }
    }

    public String getTakeMax() {
        return this.takeMax;
    }

    @StrutsParameter
    public void setTakeMax(String RHS) {
        this.takeMax = RHS;
    }

    public float getTakeMaxFloat() {
        try {
            return Float.parseFloat(this.takeMax);
        } catch (NumberFormatException | NullPointerException _) {
            if (this.takeMax != null && !this.takeMax.trim().isEmpty()) {
                logger.warn("Unparseable takeMax dose value; defaulting to the -1 unspecified sentinel");
            }
            return -1;
        }
    }

    public String getFrequencyCode() {
        return this.frequencyCode;
    }

    @StrutsParameter
    public void setFrequencyCode(String RHS) {
        this.frequencyCode = RHS;
    }

    public String getDuration() {
        return this.duration;
    }

    @StrutsParameter
    public void setDuration(String RHS) {
        this.duration = RHS;
    }

    public String getDurationUnit() {
        return this.durationUnit;
    }

    @StrutsParameter
    public void setDurationUnit(String RHS) {
        this.durationUnit = RHS;
    }

    public String getQuantity() {
        return this.quantity;
    }

    @StrutsParameter
    public void setQuantity(String RHS) {
        this.quantity = RHS;
    }

    public int getRepeat() {
        return this.repeat;
    }

    @StrutsParameter
    public void setRepeat(int RHS) {
        this.repeat = RHS;
    }

    public String getLastRefillDate() {
        return this.lastRefillDate;
    }

    @StrutsParameter
    public void setLastRefillDate(String RHS) {
        this.lastRefillDate = RHS;
    }

    public boolean getNosubs() {
        return this.nosubs;
    }

    @StrutsParameter
    public void setNosubs(boolean RHS) {
        this.nosubs = RHS;
    }

    public boolean getPrn() {
        return this.prn;
    }

    @StrutsParameter
    public void setPrn(boolean RHS) {
        this.prn = RHS;
    }

    public String getSpecial() {
        return this.special;
    }

    @StrutsParameter
    public void setSpecial(String RHS) {

        if (RHS == null || RHS.length() < 6)
            MiscUtils.getLogger().error("Prescription drug instructions are missing or empty");

        this.special = RHS;
    }

    public boolean getCustomInstr() {
        return this.customInstr;
    }

    @StrutsParameter
    public void setCustomInstr(boolean c) {
        this.customInstr = c;
    }

    public Boolean getLongTerm() {
        return this.longTerm;
    }

    @StrutsParameter
    public void setLongTerm(Boolean trueFalseNull) {
        this.longTerm = trueFalseNull;
    }

    public boolean getShortTerm() {
        return this.shortTerm;
    }

    @StrutsParameter
    public void setShortTerm(boolean st) {
        this.shortTerm = st;
    }

    public Boolean getPastMed() {
        return this.pastMed;
    }

    @StrutsParameter
    public void setPastMed(Boolean trueFalseNull) {
        this.pastMed = trueFalseNull;
    }

    public boolean getDispenseInternal() {
        return dispenseInternal;
    }

    public boolean isDispenseInternal() {
        return dispenseInternal;
    }

    @StrutsParameter
    public void setDispenseInternal(boolean dispenseInternal) {
        this.dispenseInternal = dispenseInternal;
    }

    public Boolean getPatientCompliance() {
        return this.patientCompliance;
    }

    @StrutsParameter
    public void setPatientCompliance(Boolean trueFalseNull) {
        this.patientCompliance = trueFalseNull;
    }

    /**
     * Setter accepts String to handle both numeric IDs and composite IDs.
     * Only sets the int property if the input is a pure integer.
     */
    @StrutsParameter
    public void setDrugId(String drugId) {
        if (drugId != null) {
            try {
                this.drugId = Integer.parseInt(drugId);
            } catch (NumberFormatException _) {
                // Non-integer drugIds (like Vigilance composite IDs) are handled via request.getParameter()
            }
        }
    }

    public String getGN() {
        return GN;
    }

    @StrutsParameter
    public void setGN(String GN) {
        this.GN = GN;
    }

    public String getBN() {
        return BN;
    }

    @StrutsParameter
    public void setBN(String BN) {
        this.BN = BN;
    }

    public boolean isNosubs() {
        return nosubs;
    }

    public boolean isPrn() {
        return prn;
    }

    public boolean isCustomInstr() {
        return customInstr;
    }

    public boolean isShortTerm() {
        return shortTerm;
    }

    public String getAtcCode() {
        return atcCode;
    }

    @StrutsParameter
    public void setAtcCode(String atcCode) {
        this.atcCode = atcCode;
    }

    public String getRegionalIdentifier() {
        return regionalIdentifier;
    }

    @StrutsParameter
    public void setRegionalIdentifier(String regionalIdentifier) {
        this.regionalIdentifier = regionalIdentifier;
    }

    public String getMethod() {
        return method;
    }

    @StrutsParameter
    public void setMethod(String method) {
        this.method = method;
    }

    public String getUnit() {
        return unit;
    }

    @StrutsParameter
    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getUnitName() {
        return unitName;
    }

    @StrutsParameter
    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getRoute() {
        return route;
    }

    @StrutsParameter
    public void setRoute(String route) {
        this.route = route;
    }

    public String getDosage() {
        return dosage;
    }

    @StrutsParameter
    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getOutsideProviderName() {
        return outsideProviderName;
    }

    @StrutsParameter
    public void setOutsideProviderName(String outsideProviderName) {
        this.outsideProviderName = outsideProviderName;
    }

    public String getOutsideProviderOhip() {
        return outsideProviderOhip;
    }

    @StrutsParameter
    public void setOutsideProviderOhip(String outsideProviderOhip) {
        this.outsideProviderOhip = outsideProviderOhip;
    }
}
