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


package io.github.carlos_emr.carlos.encounter.pageUtil;

import java.io.IOException;
import java.util.Date;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BcBillStaging;
import io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.EChartRevisionPersister.AppointmentChange;
import io.github.carlos_emr.carlos.encounter.pageUtil.EChartRevisionPersister.Result;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterWindowDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.commn.model.EncounterWindow;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.transaction.PlatformTransactionManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Saves the legacy encounter editor ({@code encounter/Index2.jsp}, rendered only with the CAISI
 * module and {@code casetoEncounter}) as a new {@code eChart} revision of the submitting chart's
 * patient.
 *
 * <p>Issue #4673: this action used to take the patient, the entry metadata and the revision the
 * editor was opened from out of the login-wide {@code EctSessionBean} and the session's
 * {@code eChartID}, both rewritten whenever another chart was entered in any window. With two
 * charts open, saving A's editor appended A's text to B's chart and signed B's appointment. The
 * patient now comes from the request ({@code demographicNo}), the revision from the editor's own
 * {@code eChartId} field and the appointment from its {@code appointment_no} field; the provider
 * and user name come from this patient's own remembered entry. {@link EChartRevisionPersister}
 * checks the revision and the appointment under the patient's row lock and writes nothing unless
 * both belong to the patient and the revision is still the newest. The login-wide bean is neither
 * read nor changed. After a save the editor keeps the visit it submitted: the patient's remembered
 * entry is the latest of any of that patient's windows, so its appointment can be another one's.</p>
 *
 * <p>POST only, with {@code _demographic} write for the patient and access to the record; every
 * button but Exit also needs {@code _eChart} write, and Verify and Sign
 * {@code _eChart.verifyButton} write, as the editor offers them. A stale revision, an appointment
 * that now belongs to another patient, or an editor without its revision field answers 409 with
 * the unsaved text on the concurrency page (412 for the AJAX form of the request); malformed or
 * conflicting identities answer 400 before anything is written.</p>
 */
public class EctSaveEncounter2Action extends ActionSupport {

    static final int DEFAULT_ROW_ONE_SIZE = 60;
    static final int DEFAULT_ROW_TWO_SIZE = 60;
    static final int DEFAULT_ROW_THREE_SIZE = 378;
    static final int DEFAULT_PRES_BOX_SIZE = 30;
    private static final int MIN_LAYOUT_SIZE = 10;

    private static final String SAVE = "Save";
    private static final String AUTO_SAVE = "AutoSave";
    private static final String SIGN_SAVE_EXIT = "Sign,Save and Exit";
    private static final String SIGN_SAVE_BILL = "Sign,Save and Bill";
    private static final String VERIFY_SIGN = "Verify and Sign";
    private static final String SPLIT_CHART = "Split Chart";
    private static final String EXIT = "Exit";
    private static final Set<String> BUTTONS = Set.of(SAVE, AUTO_SAVE, SIGN_SAVE_EXIT, SIGN_SAVE_BILL, VERIFY_SIGN,
            SPLIT_CHART, EXIT);
    private static final Pattern IDENTIFIER = Pattern.compile("\\d{1,9}");

    static Logger log = MiscUtils.getLogger();

    // Struts actions are Serializable; these collaborators are per-request Spring lookups, not state.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient EChartRevisionPersister revisions;
    private final transient EncounterWindowDao encounterWindowDao;

    public EctSaveEncounter2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                new EChartRevisionPersister(SpringUtils.getBean(PlatformTransactionManager.class),
                        SpringUtils.getBean(EntityManagerFactory.class), SpringUtils.getBean(EChartDao.class),
                        SpringUtils.getBean(OscarAppointmentDao.class), SpringUtils.getBean(AppointmentArchiveDao.class)),
                SpringUtils.getBean(EncounterWindowDao.class));
    }

    EctSaveEncounter2Action(SecurityInfoManager securityInfoManager, EChartRevisionPersister revisions,
                            EncounterWindowDao encounterWindowDao) {
        this.securityInfoManager = securityInfoManager;
        this.revisions = revisions;
        this.encounterWindowDao = encounterWindowDao;
    }

    /**
     * Parses a layout size from the given request parameter string.
     * Returns {@code defaultValue} if the parameter is null, non-numeric,
     * or below the minimum layout size threshold ({@value MIN_LAYOUT_SIZE} px).
     *
     * @param param        the raw request parameter string
     * @param defaultValue the field-specific default to apply on invalid input
     * @return the parsed size, or {@code defaultValue} if the input is invalid or too small
     */
    static int parseLayoutSize(String param, int defaultValue) {
        int value = ConversionUtils.fromIntString(param);
        if (value >= MIN_LAYOUT_SIZE) {
            return value;
        }
        log.debug("parseLayoutSize: invalid or missing layout param, using default {}", defaultValue);
        return defaultValue;
    }

    @Override
    public String execute() throws IOException, ServletException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        String demographicNo;
        try {
            demographicNo = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException invalidPatient) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidPatient.getMessage());
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", demographicNo)
                || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, Integer.valueOf(demographicNo))) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        String button = request.getParameter("btnPressed");
        if (button == null || !BUTTONS.contains(button)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown encounter action");
            return NONE;
        }
        // The editor offers its writing buttons only with _eChart write, and Verify and Sign only
        // with _eChart.verifyButton write as well (encounter-row-three.jspf). Exit stores the layout.
        if (!EXIT.equals(button) && !securityInfoManager.hasPrivilege(loggedInInfo, "_eChart", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_eChart)");
        }
        if (VERIFY_SIGN.equals(button)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_eChart.verifyButton", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_eChart.verifyButton)");
        }
        String revisionField = request.getParameter("eChartId");
        Integer baseRevision;
        Integer appointmentNo;
        try {
            baseRevision = optionalIdentifier(revisionField);
            appointmentNo = optionalIdentifier(request.getParameter("appointment_no"));
        } catch (IllegalArgumentException _) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid encounter revision or appointment");
            return NONE;
        }
        // This chart's own entry metadata (provider, user name, visit date), never another window's.
        EctSessionBean entry = EncounterPatientContext.entryOrLoggedIn(request, loggedInInfo, demographicNo);

        Result saved = null;
        if (!EXIT.equals(button)) {
            EChart revision = submittedRevision(request, entry, button);
            if (revisionField == null) {
                // An editor rendered without its revision (before issue #4673, or a hand-built
                // request) cannot show that it is current, so it must not overwrite the chart.
                return conflict(request, response, revision);
            }
            saved = revisions.save(Integer.parseInt(demographicNo), baseRevision, appointmentNo,
                    appointmentChange(button), revision, entry.providerNo);
            switch (saved.outcome()) {
                case SAVED -> {
                    // The revision is written; the button's result below shows or closes it.
                }
                case PATIENT_NOT_FOUND -> {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Patient not found");
                    return NONE;
                }
                case FOREIGN_REVISION -> {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The encounter revision belongs to another chart");
                    return NONE;
                }
                case STALE_REVISION, FOREIGN_APPOINTMENT -> {
                    return conflict(request, response, revision);
                }
                default -> throw new IllegalStateException("Unhandled encounter save outcome " + saved.outcome());
            }
            LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_ECHART, demographicNo,
                    request.getRemoteAddr());
            keepSubmittedVisit(entry, saved, request.getParameter("subject"));
            // TimeOut.jsp refreshes the schedule that opened the chart when a visit status changed.
            request.setAttribute("refreshOpener", saved.appointmentUpdated());
        }

        saveLayout(request, entry.providerNo);

        return switch (button) {
            case SIGN_SAVE_BILL -> bill(request, loggedInInfo, entry, demographicNo, saved);
            case SIGN_SAVE_EXIT, VERIFY_SIGN -> "success";
            case SAVE, AUTO_SAVE -> {
                showSavedRevision(request, loggedInInfo, entry, saved.revision(), false);
                yield "saveAndStay";
            }
            case SPLIT_CHART -> {
                showSavedRevision(request, loggedInInfo, entry, saved.revision(), true);
                yield "splitchart";
            }
            default -> "close";
        };
    }

    /**
     * A positive database identifier, or {@code null} for the editor's "none" values (absent, empty,
     * {@code "null"} or {@code "0"}).
     *
     * @throws IllegalArgumentException for anything else
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive match of the legacy "null" form value; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive match of the legacy \"null\" form value; not a security or authorization decision")
    static Integer optionalIdentifier(String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid identifier");
        }
        int id = Integer.parseInt(value);
        return id == 0 ? null : id;
    }

    private static AppointmentChange appointmentChange(String button) {
        return switch (button) {
            case SIGN_SAVE_EXIT -> AppointmentChange.SIGN;
            case VERIFY_SIGN -> AppointmentChange.VERIFY;
            default -> AppointmentChange.NONE;
        };
    }

    /** The revision this request submits, signed as its button asks, by this chart's entry user. */
    private static EChart submittedRevision(HttpServletRequest request, EctSessionBean entry, String button) {
        Date now = new Date();
        EChart revision = new EChart();
        revision.setTimestamp(now);
        revision.setProviderNo(entry.providerNo);
        revision.setSocialHistory(request.getParameter("shTextarea"));
        revision.setFamilyHistory(request.getParameter("fhTextarea"));
        revision.setMedicalHistory(request.getParameter("mhTextarea"));
        revision.setOngoingConcerns(request.getParameter("ocTextarea"));
        revision.setReminders(request.getParameter("reTextarea"));
        String encounter = request.getParameter("enTextarea");
        String subject = request.getParameter("subject");
        try {
            ResourceBundle labels = ResourceBundle.getBundle("oscarResources", request.getLocale());
            switch (button) {
                case SIGN_SAVE_BILL, SIGN_SAVE_EXIT -> encounter = signed(encounter, labels,
                        "encounter.class.EctSaveEncounterAction.msgSigned", now, entry.userName, request);
                case VERIFY_SIGN -> encounter = signed(encounter, labels,
                        "encounter.class.EctSaveEncounterAction.msgVerAndSig", now, entry.userName, request);
                case SPLIT_CHART -> subject = labels.getString("encounter.class.EctSaveEncounterAction.msgSplitChart");
                default -> {
                    // Save, AutoSave: the text is stored as typed, without a signature line.
                }
            }
        } catch (MissingResourceException missing) {
            log.error("Encounter signature labels are missing; saving without the signature line", missing);
        }
        revision.setEncounter(encounter);
        revision.setSubject(subject);
        return revision;
    }

    private static String signed(String encounter, ResourceBundle labels, String action, Date when, String userName,
                                 HttpServletRequest request) {
        return (encounter == null ? "" : encounter) + "\n[" + labels.getString(action) + " "
                + UtilDateUtilities.DateToString(when, labels.getString("date.yyyyMMddHHmmss"), request.getLocale())
                + " " + labels.getString("encounter.class.EctSaveEncounterAction.msgSigBy") + " " + userName + "]";
    }

    /** Keeps the unsaved text for the user (409), or answers the legacy AJAX precondition (412). */
    private static String conflict(HttpServletRequest request, HttpServletResponse response, EChart unsaved)
            throws IOException {
        if ("ajax".equals(request.getParameter("submitMethod"))) {
            response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                    "Somebody else is currently modifying this encounter");
            return NONE;
        }
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        request.setAttribute("unsavedEncounter", unsaved.getEncounter());
        return "concurrencyError";
    }

    private void saveLayout(HttpServletRequest request, String providerNo) {
        encounterWindowDao.remove(providerNo);
        EncounterWindow ew = new EncounterWindow();
        ew.setProviderNo(providerNo);
        ew.setRowOneSize(parseLayoutSize(request.getParameter("rowOneSize"), DEFAULT_ROW_ONE_SIZE));
        ew.setRowTwoSize(parseLayoutSize(request.getParameter("rowTwoSize"), DEFAULT_ROW_TWO_SIZE));
        ew.setRowThreeSize(parseLayoutSize(request.getParameter("rowThreeSize"), DEFAULT_ROW_THREE_SIZE));
        ew.setPresBoxSize(parseLayoutSize(request.getParameter("presBoxSize"), DEFAULT_PRES_BOX_SIZE));
        encounterWindowDao.persist(ew);
    }

    /**
     * Re-renders the editor for the revision this request wrote: the patient's own header, and the
     * saved text with its revision id for the next save's currency check.
     */
    private static void showSavedRevision(HttpServletRequest request, LoggedInInfo loggedInInfo, EctSessionBean entry,
                                          EChart saved, boolean split) {
        entry.setUpEncounterPage(loggedInInfo);
        entry.socialHistory = saved.getSocialHistory();
        entry.familyHistory = saved.getFamilyHistory();
        entry.medicalHistory = saved.getMedicalHistory();
        entry.ongoingConcerns = saved.getOngoingConcerns();
        entry.reminders = saved.getReminders();
        entry.subject = saved.getSubject();
        entry.encounter = saved.getEncounter();
        entry.eChartId = String.valueOf(saved.getId());
        entry.eChartTimeStamp = saved.getTimestamp();
        entry.template = "";
        request.setAttribute("EctSessionBean", entry);
        EctIncomingEncounter2Action.setEncounterAttributes(request, entry, loggedInInfo, split, true);
    }

    /**
     * Binds the rest of this request to the visit this window submitted. The patient's remembered
     * entry is the latest of any of the patient's windows, so its appointment, date and reason can be
     * another window's: re-rendered as this editor's, the next Sign would sign that other visit, and
     * Sign, Save and Bill would bill on that visit's date.
     *
     * @param entry         this patient's entry, updated in place
     * @param saved         the accepted save, naming the appointment it checked (or none)
     * @param postedSubject the visit reason this editor posted as its subject
     */
    static void keepSubmittedVisit(EctSessionBean entry, Result saved, String postedSubject) {
        entry.appointmentNo = saved.appointmentNo() == null ? null : saved.appointmentNo().toString();
        // The appointment's own date; a visit without one is dated today, as a chart opened
        // without an appointment is.
        entry.appointmentDate = ConversionUtils.toDateString(
                saved.appointmentDate() != null ? saved.appointmentDate() : new Date());
        entry.reason = postedSubject;
    }

    /**
     * The appointment the bill this save hands over is for: the save's own appointment, or
     * {@code "0"} for none. The bill form sends it back as {@code appointment_no} when the physician
     * or the form is changed, and the bill setup accepts digits only; {@code "0"} is what the day
     * sheet sends for a visit without an appointment, and saving the bill reads it as none.
     *
     * @param saved the accepted save
     * @return the appointment number, digits only
     */
    static String billAppointmentNo(Result saved) {
        return saved.appointmentNo() == null ? "0" : saved.appointmentNo().toString();
    }

    private static String bill(HttpServletRequest request, LoggedInInfo loggedInInfo, EctSessionBean entry,
                               String demographicNo, Result saved) {
        // The patient's own name, loaded for this chart rather than taken from another window.
        entry.setUpEncounterPage(loggedInInfo);
        BillingSessionBean bean = new BillingSessionBean();
        bean.setApptProviderNo(entry.providerNo);
        bean.setPatientName(entry.getPatientFirstName() + " " + entry.getPatientLastName());
        bean.setBillRegion(CarlosProperties.getInstance().getProperty("billregion"));
        Properties oscarVariables = (Properties) request.getAttribute("oscarVariables");
        String formBill = "GP";
        if (oscarVariables != null) {
            formBill = oscarVariables.getProperty("default_view", formBill);
        }
        bean.setBillForm(formBill);
        bean.setPatientNo(demographicNo);
        // Only an existing appointment of this patient; the persister refused any other.
        bean.setApptNo(billAppointmentNo(saved));
        bean.setApptDate(entry.appointmentDate);
        // CWE-501: validate status against appointment status pattern before session storage
        String statusParam = request.getParameter("status");
        if (statusParam != null && !statusParam.matches("[a-zA-Z]{1,2}")) {
            log.warn("Rejected invalid appointment status at trust boundary");
            statusParam = null;
        }
        bean.setApptStatus(statusParam);
        request.setAttribute("encounter", "true");
        // Every bean field is validated before it reaches the session: the patient and appointment
        // are numeric and were checked as this chart's own under the patient lock, the provider and
        // name come from this patient's entry and record, the region and form from server
        // configuration, and the status matched the two-letter pattern above. The bill is staged
        // under its own key, which the forward to /billing carries in this request, so another
        // window's bill is never shown or saved in its place (issue #4673).
        BcBillStaging.open(request, bean);
        return "bill";
    }
}
