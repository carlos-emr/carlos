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
import java.util.ArrayList;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.daos.DefaultIssueDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.model.security.Secrole;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.encounter.data.EctProviderData;
import io.github.carlos_emr.carlos.encounter.data.EctSplitChart;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import org.owasp.encoder.Encode;

import java.util.Properties;
import java.util.Vector;
import java.util.regex.Pattern;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Opens one patient's legacy encounter page ({@code /encounter/IncomingEncounter}) and remembers
 * that chart's entry metadata (provider, appointment, reason, encounter type) for this login.
 *
 * <p>The request names its own patient ({@code demographicNo}) and, optionally, an appointment of
 * that patient ({@code appointmentNo}; {@code 0} or absent for none). The appointment is checked
 * against its row before any chart is loaded or remembered: one that does not exist or is booked
 * for another patient is refused (400). The entry used to accept any appointment number, so a
 * hand-built request could make another patient's visit a chart's remembered appointment; and the
 * legacy {@code appointmentList} branch, which no page called, loaded the named appointment's
 * patient into the bean and overwrote that patient's remembered entry before the page refused to
 * render it (issue #4676). A request that still carries {@code appointmentList} is an ordinary
 * entry for its own patient.</p>
 */
public class EctIncomingEncounter2Action extends ActionSupport {

    private static Logger log = MiscUtils.getLogger();

    // CWE-501 trust boundary validation patterns
    private static final Pattern SAFE_STATUS = Pattern.compile("[a-zA-Z]{1,2}");
    private static final Pattern SAFE_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern SAFE_TIME = Pattern.compile("[0-9]{1,2}:[0-9]{2}(:[0-9]{2})?");
    // Any character except ASCII control chars (allows Unicode for bilingual Canadian EMR)
    private static final Pattern SAFE_TEXT = Pattern.compile("[^\\p{Cntrl}]*");
    private static final Set<String> VALID_SOURCES = Set.of("encounter", "messenger");
    private final CaseManagementNoteDAO caseManagementNoteDao;
    private final CaseManagementManager caseManagementMgr;
    private final SecurityInfoManager securityInfoManager;
    private final OscarAppointmentDao appointmentDao;

    /** Struts object-factory constructor. */
    public EctIncomingEncounter2Action() {
        this(SpringUtils.getBean(CaseManagementNoteDAO.class), SpringUtils.getBean(CaseManagementManager.class),
                SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(OscarAppointmentDao.class));
    }

    EctIncomingEncounter2Action(CaseManagementNoteDAO caseManagementNoteDao, CaseManagementManager caseManagementMgr,
                                SecurityInfoManager securityInfoManager, OscarAppointmentDao appointmentDao) {
        this.caseManagementNoteDao = caseManagementNoteDao;
        this.caseManagementMgr = caseManagementMgr;
        this.securityInfoManager = securityInfoManager;
        this.appointmentDao = appointmentDao;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() throws IOException, ServletException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        final String demoNo;
        try {
            demoNo = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException invalidPatient) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidPatient.getMessage());
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", demoNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (!"true".equals(CarlosProperties.getInstance().getProperty("program_domain.show_echart", "false"))) {
            if (!caseManagementMgr.isClientInProgramDomain(loggedInInfo.getLoggedInProviderNo(), demoNo)
                    && !caseManagementMgr.isClientReferredInProgramDomain(loggedInInfo.getLoggedInProviderNo(),
                    demoNo)) {
                return "domain-error";
            }
        }

        EctSessionBean bean;
        // The legacy appointmentList switch is gone (#4676): such a request is an ordinary entry
        // for its own demographicNo, and its appointmentNo must be that patient's.
        if (request.getParameter("demographicSearch") != null) {
            // Coming in from the demographicSearch page
            bean = newEntryBean(request, loggedInInfo);
            // Use pre-validated demoNo (validated at method entry)
            bean.demographicNo = demoNo;
            // no curProviderNo when viewing eCharts from search screen
            // bean.curProviderNo="";
            // no reason when viewing eChart from search screen
            bean.reason = "";
            // userName is already set
            // bean.userName=request.getParameter("userName");
            // no appointmentDate from search screen keep old date
            // bean.appointmentDate="";
            // no startTime from search screen
            bean.startTime = "";
            // no status from search screen
            bean.status = "";
            // no date from search screen-keep old date
            // bean.date="";
            bean.appointmentNo = "0";
            bean.check = "myCheck";
            bean.setUpEncounterPage(LoggedInInfo.getLoggedInInfoFromSession(request));
            // demographicNo validated as numeric at method entry; other bean fields are unsanitized — consuming JSPs MUST use OWASP encoding
            request.getSession().setAttribute("EctSessionBean", bean); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        } else {
            if ("yes".equals(request.getParameter("PEAttach"))) {
                return attachNoteToClient(request, response, loggedInInfo, demoNo);
            }
            bean = new EctSessionBean();
            bean.currentDate = UtilDateUtilities.StringToDate(request.getParameter("curDate"));

            if (bean.currentDate == null) {
                bean.currentDate = new Date();
            }

            bean.providerNo = request.getParameter("providerNo");
            if (bean.providerNo != null && !bean.providerNo.isEmpty() && !bean.providerNo.matches("[a-zA-Z0-9]{1,6}")) {
                log.warn("Invalid providerNo");
                return "failure";
            }
            if (bean.providerNo == null || bean.providerNo.isEmpty()) {
                bean.providerNo = (String) request.getSession().getAttribute("user"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): fallback to authenticated provider from own session
            }

            bean.demographicNo = demoNo;
            bean.appointmentNo = request.getParameter("appointmentNo");
            if (bean.appointmentNo == null || "null".equalsIgnoreCase(bean.appointmentNo) || bean.appointmentNo.isEmpty()) {
                bean.appointmentNo = null;
            } else if (!bean.appointmentNo.matches("\\d{1,9}")) {
                log.warn("Invalid appointmentNo");
                return "failure";
            } else {
                // Before the chart is loaded or remembered: the chart's eForm and billing links and
                // the legacy encounter save start from the remembered appointment (#4676).
                bean.appointmentNo = ownAppointment(demoNo, bean.appointmentNo);
                if (bean.appointmentNo == null) {
                    log.warn("Refused encounter entry: the requested appointment is not this patient's");
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The appointment is not this patient's");
                    return NONE;
                }
            }
            bean.curProviderNo = request.getParameter("curProviderNo");
            if (bean.curProviderNo != null && !bean.curProviderNo.isEmpty() && !bean.curProviderNo.matches("[a-zA-Z0-9]{1,6}")) {
                log.warn("Invalid curProviderNo");
                return "failure";
            }
            Provider provider = loggedInInfo.getLoggedInProvider();
            if (bean.curProviderNo == null || bean.curProviderNo.trim().length() == 0)
                bean.curProviderNo = provider.getProviderNo();
            // Reject oversized free-text parameters to prevent session storage abuse
            for (String paramName : new String[]{"reason", "reasonCode", "encType", "userName",
                    "appointmentDate", "startTime", "status", "date", "source"}) {
                String val = request.getParameter(paramName);
                if (val != null && val.length() > 255) {
                    log.warn("Parameter too long: {}", paramName);
                    return "failure";
                }
            }

            // CWE-501 trust boundary: validate structured fields, sanitize free-text
            String reasonParam = request.getParameter("reason");
            bean.reason = (reasonParam != null && SAFE_TEXT.matcher(reasonParam).matches()) ? reasonParam.substring(0, Math.min(reasonParam.length(), 255)) : null;
            String reasonCodeParam = request.getParameter("reasonCode");
            bean.reasonCode = (reasonCodeParam != null && reasonCodeParam.matches("[a-zA-Z0-9]{1,20}")) ? reasonCodeParam : null;
            String encTypeParam = request.getParameter("encType");
            bean.encType = (encTypeParam != null && encTypeParam.matches("[a-zA-Z0-9_ ]{1,50}")) ? encTypeParam : null;
            bean.userName = request.getParameter("userName");
            if (bean.userName == null) {
                // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): fallback to authenticated user's name from own session (set post-auth)
                bean.userName = ((String) request.getSession().getAttribute("userfirstname")) + " "
                        + ((String) request.getSession().getAttribute("userlastname"));
            } else if (!SAFE_TEXT.matcher(bean.userName).matches() || bean.userName.length() > 100) {
                log.warn("Rejected invalid userName at trust boundary, falling back to session-derived name");
                // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): fallback to authenticated user's name from own session after rejecting invalid param
                bean.userName = ((String) request.getSession().getAttribute("userfirstname")) + " "
                        + ((String) request.getSession().getAttribute("userlastname"));
            }

            String apptDateParam = request.getParameter("appointmentDate");
            bean.appointmentDate = (apptDateParam != null && SAFE_DATE.matcher(apptDateParam).matches()) ? apptDateParam : null;
            String startTimeParam = request.getParameter("startTime");
            bean.startTime = (startTimeParam != null && SAFE_TIME.matcher(startTimeParam).matches()) ? startTimeParam : null;
            String statusParam = request.getParameter("status");
            bean.status = (statusParam != null && SAFE_STATUS.matcher(statusParam).matches()) ? statusParam : null;
            String dateParam = request.getParameter("date");
            bean.date = (dateParam != null && SAFE_DATE.matcher(dateParam).matches()) ? dateParam : null;
            bean.check = "myCheck";
            bean.oscarMsgID = request.getParameter("msgId");
            if (bean.oscarMsgID != null && !bean.oscarMsgID.matches("\\d+")) {
                log.warn("Invalid msgId");
                return "failure";
            }
            String sourceParam = request.getParameter("source");
            if (sourceParam != null) {
                bean.source = VALID_SOURCES.contains(sourceParam) ? sourceParam : null;
            }
            bean.setUpEncounterPage(LoggedInInfo.getLoggedInInfoFromSession(request));
            // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- demographicNo/appointmentNo/curProviderNo validated as numeric/alphanumeric;
            // status validated [a-zA-Z]{1,2}; dates validated YYYY-MM-DD; times validated HH:MM; encType validated alphanumeric;
            // source whitelisted to {"encounter","messenger"}; reason/userName sanitized for control chars and length-capped;
            // eChartId is server-generated from setUpEncounterPage()
            request.getSession().setAttribute("EctSessionBean", bean);
            // No login-wide eChartID: the legacy editor posts the revision it was opened from
            // (Index2.jsp's eChartId field), so another window's entry cannot change it (#4673).

            long notesCount = caseManagementNoteDao.getNotesCountByDemographicId(bean.getDemographicNo());
            if (notesCount == 0
                    && CarlosProperties.getInstance().getProperty("wl_default_issue", "false").equals("true")) {
                // assign default issues for a feature: WL: default issues assignment
                String wlProgramId = (String) request.getSession().getAttribute(SessionConstants.CURRENT_PROGRAM_ID);
                assignDefaultIssues(bean, wlProgramId);
            }

        }

        EncounterPatientContext.rememberEncounter(request, bean);
        setEncounterAttributes(request, bean, loggedInInfo, request.getParameter("splitchart") != null, false);
        return "success2";
    }

    /**
     * The PMM "attach note to client" popup's result (casemgmt/attachClient.jsp): copies the note
     * it was opened for into the client's chart, then opens that chart. The client is this
     * request's own patient ({@code selectId} must equal the validated {@code demographicNo}).
     *
     * <p>It used to copy on a GET, which CSRFGuard does not check, any note id into any chart with
     * only {@code _demographic} read, and the copy kept the source note's uuid, appointment and
     * issue links. The uuid made the copy the newest revision of the source note, so the source
     * chart could no longer save or sign that note, and the issue links tied the client's note to
     * the other patient's issues. It now takes the popup's POST only, needs {@code _casemgmt.notes}
     * read and record access for the note's own patient and write and record access for the
     * client, and writes the copy as a new note of the client's: a new uuid, no appointment, no
     * issues, and only its current text as history. A successful copy redirects to the chart, so
     * reloading the chart does not copy the note again.</p>
     *
     * @return {@link #NONE} after the redirect or a refusal
     */
    // FindSecBugs UNVALIDATED_REDIRECT: the redirect goes to this application's own chart route for the
    // validated numeric patient number, never to a URL taken from the request.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "same-origin redirect to this application's chart route for a validated numeric patient number, never to a URL taken from the request")
    private String attachNoteToClient(HttpServletRequest request, HttpServletResponse response,
            LoggedInInfo loggedInInfo, String client) throws IOException {
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        String noteId = request.getParameter("noteId");
        if (!client.equals(request.getParameter("selectId")) || noteId == null || !noteId.matches("\\d{1,9}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        CaseManagementNote note = caseManagementNoteDao.getNote(Long.parseLong(noteId));
        String source = note == null ? null : note.getDemographic_no();
        if (source == null || !source.matches("[1-9]\\d{0,9}")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_casemgmt.notes", "r", source)
                || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, Integer.valueOf(source))
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_casemgmt.notes", "w", client)
                || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, Integer.valueOf(client))) {
            throw new SecurityException("missing required sec object (_casemgmt.notes)");
        }
        note.setId(null);
        note.setUuid(null);
        note.setDemographic_no(client);
        note.setAppointmentNo(0);
        note.setIssues(new HashSet<CaseManagementIssue>());
        note.setHistory(note.getNote());
        caseManagementNoteDao.saveNote(note);
        // demographicNo is the validated, canonical patient number.
        response.sendRedirect(request.getContextPath() + "/encounter/IncomingEncounter?demographicNo=" + client
                + "&appointmentNo=0");
        return NONE;
    }

    /**
     * The appointment an entry for {@code demographicNo} may remember, read from the appointment
     * row rather than echoed from the request.
     *
     * @param demographicNo the request's canonical patient number
     * @param appointmentNo the requested appointment number, already checked to be 1 to 9 digits
     * @return {@code "0"} when the request names no appointment, the appointment's own number when
     *         it is booked for this patient, or null when it does not exist or is another patient's
     */
    private String ownAppointment(String demographicNo, String appointmentNo) {
        int id = Integer.parseInt(appointmentNo);
        if (id == 0) {
            return "0";
        }
        Appointment appointment = appointmentDao.find(id);
        return appointment != null && Integer.toString(appointment.getDemographicNo()).equals(demographicNo)
                ? Integer.toString(appointment.getId()) : null;
    }

    /** Starts a legacy entry without borrowing another chart window's mutable bean. */
    private EctSessionBean newEntryBean(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        EctSessionBean bean = new EctSessionBean();
        bean.currentDate = new Date();
        bean.providerNo = loggedInInfo.getLoggedInProviderNo();
        bean.curProviderNo = bean.providerNo;
        bean.userName = request.getSession().getAttribute("userfirstname") + " "
                + request.getSession().getAttribute("userlastname");
        bean.appointmentDate = UtilDateUtilities.DateToString(bean.currentDate, "yyyy-MM-dd");
        return bean;
    }

    /**
     * Sets request attributes needed by Index2.jsp and its includes.
     * Moves data-fetching logic from JSP scriptlets into the Action layer.
     *
     * @param split         render the split-chart continuation of the encounter text
     * @param savedRevision the bean holds the revision a save just wrote ({@link EctSaveEncounter2Action}):
     *                      the visit continues, so no new visit header is added to the text
     */
    static void setEncounterAttributes(HttpServletRequest request, EctSessionBean bean, LoggedInInfo loggedInInfo,
                                       boolean split, boolean savedRevision) {
        CarlosProperties oscarProps = CarlosProperties.getInstance();

        // Patient age as integer string
        String pAge = Integer.toString(new UtilDateUtilities().calcAge(
                bean.yearOfBirth, bean.monthOfBirth, bean.dateOfBirth));
        request.setAttribute("pAge", pAge);

        // Family doctor info
        if (bean.familyDoctorNo != null && !bean.familyDoctorNo.isEmpty()) {
            EctProviderData.Provider prov = new EctProviderData().getProvider(bean.familyDoctorNo);
            if (prov != null) {
                request.setAttribute("famDocName", prov.getFirstName());
                request.setAttribute("famDocSurname", prov.getSurname());
            } else {
                request.setAttribute("famDocName", "");
                request.setAttribute("famDocSurname", "");
            }
        } else {
            request.setAttribute("famDocName", "");
            request.setAttribute("famDocSurname", "");
        }

        // Window sizes (individual values for EL access)
        Properties windowSizes = EctWindowSizes.getWindowSizes(bean.providerNo);
        request.setAttribute("rowOneSize", windowSizes.getProperty("rowOneSize"));
        request.setAttribute("rowTwoSize", windowSizes.getProperty("rowTwoSize"));
        request.setAttribute("rowThreeSize", windowSizes.getProperty("rowThreeSize"));
        request.setAttribute("presBoxSize", windowSizes.getProperty("presBoxSize"));

        // Province
        request.setAttribute("province", oscarProps.getProperty("billregion", "").trim().toUpperCase());

        // Split chart data
        Vector splitChart = new EctSplitChart().getSplitCharts(bean.demographicNo);
        request.setAttribute("splitChart", splitChart);
        request.setAttribute("hasSplitChart", splitChart != null && !splitChart.isEmpty());

        // Allergies and Prescriptions — guard against non-numeric demographicNo and null patient
        int demoNoInt = -1;
        try {
            demoNoInt = Integer.parseInt(bean.demographicNo);
        } catch (NumberFormatException e) {
            log.error("Non-numeric demographicNo in session bean; skipping allergies/prescriptions");
        }

        Allergy[] allergies = new Allergy[0];
        RxPrescriptionData.Prescription[] prescriptions = new RxPrescriptionData.Prescription[0];
        if (demoNoInt >= 0) {
            RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, demoNoInt);
            if (patient != null) {
                allergies = patient.getAllergies(loggedInInfo);
            }
            prescriptions = new RxPrescriptionData().getUniquePrescriptionsByPatient(demoNoInt);
        }
        request.setAttribute("allergies", allergies);
        request.setAttribute("prescriptions", prescriptions);

        // Encounter text and consumption
        boolean bSplit = split;
        int nEctLen = bean.encounter != null ? bean.encounter.length() : 0;
        boolean bTruncate = bSplit && nEctLen > 5120;
        int consumption = (int) ((bTruncate ? 5120 : nEctLen) / (10.24 * 32));
        consumption = consumption == 0 ? 1 : consumption;
        String ccolor = consumption >= 70 ? "red" : (consumption >= 50 ? "orange" : "green");
        request.setAttribute("consumption", consumption);
        request.setAttribute("consumptionColor", ccolor);
        request.setAttribute("bSplit", bSplit);

        // Build encounter text
        String encounterText = buildEncounterText(bean, bSplit, bTruncate, nEctLen, savedRevision);
        request.setAttribute("encounterText", encounterText);

        // CarlosProperties labels
        request.setAttribute("otherMedLabel", oscarProps.getProperty("otherMedications", ""));
        request.setAttribute("medHistLabel", oscarProps.getProperty("medicalHistory", ""));
        request.setAttribute("ongoingConcernsLabel", oscarProps.getProperty("ongoingConcerns", ""));

        // Popup URL — only allow http/https to prevent javascript: URI injection
        String rawPopUrl = request.getParameter("popupUrl");
        if (rawPopUrl != null && (rawPopUrl.startsWith("http://") || rawPopUrl.startsWith("https://"))) {
            request.setAttribute("popUrl", rawPopUrl);
        } else {
            request.setAttribute("popUrl", "");
        }

        // Template names (escaped for JS)
        int maxLen = 25;
        int truncLen = 22;
        String ellipses = "...";
        List<String> escapedTemplates = new ArrayList<>();
        if (bean.templateNames != null) {
            for (String tmpl : bean.templateNames) {
                String truncated = StringUtils.maxLenString(tmpl, maxLen, truncLen, ellipses);
                escapedTemplates.add(Encode.forJavaScript(truncated));
            }
        }
        request.setAttribute("templateNames", escapedTemplates);
    }

    /**
     * Assembles the encounter text from the session bean, handling split chart
     * truncation and date stamp insertion.
     *
     * @param savedRevision the text is the revision a save just wrote: no visit header is added,
     *                      and the entry message is already part of the saved text
     */
    private static String buildEncounterText(EctSessionBean bean, boolean bSplit, boolean bTruncate, int nEctLen,
                                             boolean savedRevision) {
        StringBuilder sb = new StringBuilder();
        try {
            if (!bSplit) {
                sb.append(bean.encounter != null ? bean.encounter : "");
            } else if (bTruncate) {
                sb.append(bean.encounter.substring(nEctLen - 5120));
                sb.append("\n--------------------------------------------------\n$$SPLIT CHART$$\n");
            } else {
                sb.append(bean.encounter != null ? bean.encounter : "");
                sb.append("\n--------------------------------------------------\n$$SPLIT CHART$$\n");
            }

            if (savedRevision) {
                return sb.toString();
            }
            UtilDateUtilities dateConvert = new UtilDateUtilities();
            if (bean.eChartTimeStamp == null) {
                sb.append("\n[").append(dateConvert.DateToString(bean.currentDate))
                        .append(" .: ").append(bean.reason != null ? bean.reason : "").append("] \n");
            } else if (bean.currentDate.compareTo(bean.eChartTimeStamp) > 0) {
                String apptDate = (bean.appointmentDate == null || bean.appointmentDate.isEmpty())
                        ? UtilDateUtilities.getToday("yyyy-MM-dd") : bean.appointmentDate;
                sb.append("\n__________________________________________________\n[")
                        .append(apptDate).append(" .: ").append(bean.reason != null ? bean.reason : "").append("]\n");
            } else if ((bean.currentDate.compareTo(bean.eChartTimeStamp) == 0)
                    && (bean.reason != null || bean.subject != null)
                    && !java.util.Objects.equals(bean.reason, bean.subject)) {
                sb.append("\n__________________________________________________\n[")
                        .append(bean.appointmentDate != null ? bean.appointmentDate : "")
                        .append(" .: ").append(bean.reason != null ? bean.reason : "").append("]\n");
            }
            if (bean.oscarMsg != null && !bean.oscarMsg.isEmpty()) {
                sb.append("\n\n").append(bean.oscarMsg);
            }
        } catch (Exception e) {
            log.error("Error building encounter text", e);
        }
        return sb.toString();
    }

    /**
     * WL default issues (wl_default_issue=true): gives a patient's first encounter the clinic's
     * default issues (Administration ▸ CAISI ▸ Default Encounter Issue), limited to the issues
     * the login's role in the session's CAISI program may write.
     *
     * <p>The program comes only from the schedule's CAISI program list (or the login location
     * page), which many logins never use or cannot use: one without a bed program is offered
     * only "-No assigned program-". Without a usable program, or with one the login has no role
     * in, nothing is assigned and the encounter opens as usual, with one warning so a clinic can
     * see why. Before #4838 both cases threw, and the encounter page failed with HTTP 500 for
     * every patient who had no notes yet.
     *
     * @param bean             the encounter being opened
     * @param sessionProgramId the session's {@link SessionConstants#CURRENT_PROGRAM_ID}, or null
     */
    void assignDefaultIssues(EctSessionBean bean, String sessionProgramId) {
        Integer programId = usableProgramId(sessionProgramId);
        if (programId == null) {
            log.warn("Default encounter issues were not assigned: wl_default_issue is on, but this session has no CAISI program chosen");
            return;
        }
        Set<Long> issueIdSet = getIssueIdSet(bean.getCurProviderNo(), programId);
        if (issueIdSet == null) {
            log.warn("Default encounter issues were not assigned: wl_default_issue is on, but the login has no role in the session's CAISI program");
            return;
        }
        Integer demographicNo;
        try {
            demographicNo = Integer.valueOf(bean.getDemographicNo());
        } catch (NumberFormatException e) {
            log.warn("Default encounter issues were not assigned: the encounter has no numeric patient number");
            return;
        }
        DefaultIssueDao defaultIssueDao = SpringUtils.getBean(DefaultIssueDao.class);
        IssueDAO issueDao = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
        CaseManagementIssueDAO cmiDao = (CaseManagementIssueDAO) SpringUtils.getBean(CaseManagementIssueDAO.class);
        String[] issueIds = defaultIssueDao.findAllDefaultIssueIds();
        for (String rawId : issueIds) {
            // The admin page stores resolved ids, but a hand-edited or imported list may not be clean;
            // one bad entry must not fail the encounter page (#4838), so skip it.
            Long issueId;
            try {
                issueId = Long.valueOf(rawId.trim());
            } catch (NumberFormatException e) {
                log.warn("A default encounter issue was skipped: the stored default issue list holds a non-numeric id");
                continue;
            }
            // judge current provider can access this issue
            if (!issueIdSet.contains(issueId)) {
                continue;
            }

            // judge this issue exists or not
            CaseManagementIssue cmi = cmiDao.getIssuebyId(bean.getDemographicNo(), issueId.toString());
            if (cmi != null) { // this issue exists
                continue;
            }
            Issue issue = issueDao.getIssue(issueId);
            cmi = new CaseManagementIssue();
            cmi.setAcute(false);
            cmi.setCertain(false);
            cmi.setDemographic_no(demographicNo);
            cmi.setIssue_id(issueId);
            cmi.setMajor(false);
            cmi.setProgram_id(programId);
            cmi.setResolved(false);
            cmi.setType(issue.getRole());
            cmi.setUpdate_date(new Date());
            cmiDao.saveIssue(cmi);
        }
    }

    /**
     * The session's CAISI program id when it names a program, else null: absent, blank, not a
     * number, or not positive ("-No assigned program-" is 0).
     */
    static Integer usableProgramId(String sessionProgramId) {
        if (sessionProgramId == null) {
            return null;
        }
        try {
            int programId = Integer.parseInt(sessionProgramId.trim());
            return programId > 0 ? programId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The issues the login's role in {@code programId} may write, or null when the login has no
     * role in that program.
     */
    private Set<Long> getIssueIdSet(String providerNo, Integer programId) {
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        List<ProgramProvider> ppList = programProviderDao.getProgramProviderByProviderProgramId(providerNo,
                Long.valueOf(programId));
        if (ppList == null || ppList.isEmpty() || ppList.get(0).getRole() == null) {
            return null;
        }
        ProgramProvider pp = ppList.get(0);
        Secrole role = pp.getRole();

        // get program accesses... program allows either all roles or not all roles
        // (does this mean no roles?)
        ProgramAccessDAO programAccessDAO = (ProgramAccessDAO) SpringUtils.getBean(ProgramAccessDAO.class);
        List<ProgramAccess> paList = programAccessDAO.getAccessListByProgramId(Long.valueOf(programId));
        Map<String, ProgramAccess> paMap = new HashMap<String, ProgramAccess>();
        for (Iterator<ProgramAccess> iter = paList.iterator(); iter.hasNext(); ) {
            ProgramAccess pa = iter.next();
            paMap.put(pa.getAccessType().getName().toLowerCase(), pa);
        }

        // get all roles
        CaseManagementManager cmm = new CaseManagementManagerImpl();
        SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
        List<Secrole> allRoles = secroleDao.getRoles();

        RoleProgramAccessDAO roleProgramAccessDAO = (RoleProgramAccessDAO) SpringUtils.getBean(RoleProgramAccessDAO.class);

        List<Secrole> allowableSearchRoles = new ArrayList<Secrole>();
        for (Iterator<Secrole> iter = allRoles.iterator(); iter.hasNext(); ) {
            Secrole r = iter.next();
            String key = "write " + r.getName().toLowerCase() + " issues";
            ProgramAccess pa = paMap.get(key);
            if (pa != null) {
                if (pa.isAllRoles() || cmm.isRoleIncludedInAccess(pa, role)) {
                    allowableSearchRoles.add(r);
                }
            }
            if (pa == null && r.getId().intValue() == role.getId().intValue()) {
                allowableSearchRoles.add(r);
            }

            // global default role access
            if (roleProgramAccessDAO.hasAccess(key, role.getId())) {
                allowableSearchRoles.add(r);
            }
        }
        IssueDAO issueDAO = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
        List<Long> issIdList = issueDAO.getIssueCodeListByRoles(allowableSearchRoles);
        Set<Long> issueSet = new HashSet<Long>();
        for (Long id : issIdList) {
            issueSet.add(id);
        }
        return issueSet;
    }
}
