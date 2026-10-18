/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Resolves the patient carried by an encounter request, independently of other chart windows.
 * Callers must check the resolved patient's privileges before reading or writing clinical data.
 *
 * @since 2026-10-15
 */
public final class EncounterPatientContext {
    private static final String ENCOUNTER_PREFIX = EncounterPatientContext.class.getName() + ".";
    private EncounterPatientContext() { }

    /**
     * Returns the explicitly submitted patient ID; conflicting aliases or repeated values fail.
     * The session's last-opened chart is never a source of patient identity.
     *
     * @throws IllegalArgumentException if the patient is missing, invalid, or ambiguous
     */
    public static String requireDemographicNo(HttpServletRequest request) {
        String patient = null;
        for (String name : new String[]{"demographicNo", "demographic_no"}) {
            String[] values = request.getParameterValues(name);
            if (values == null) continue;
            for (String value : values) {
                String candidate = canonicalPatient(value);
                if (patient != null && !patient.equals(candidate)) {
                    throw new IllegalArgumentException("Conflicting demographic numbers");
                }
                patient = candidate;
            }
        }
        if (patient == null) throw new IllegalArgumentException("Missing required demographicNo");
        return patient;
    }

    /** Retains immutable entry metadata separately for each patient opened in this login. */
    public static void rememberEncounter(HttpServletRequest request, EctSessionBean bean) {
        String patient = canonicalPatient(bean.demographicNo);
        request.getSession().setAttribute(ENCOUNTER_PREFIX + patient, new SavedEncounter(
                patient, bean.providerNo, bean.curProviderNo, bean.userName, bean.appointmentNo,
                bean.appointmentDate, bean.startTime, bean.encType, bean.reason,
                bean.patientFirstName, bean.patientLastName, bean.status));
        request.setAttribute("EctSessionBean", bean);
    }

    /**
     * Returns a request-owned copy of this patient's entry metadata, or null if none was stored.
     * Never substitutes the login's last-opened patient when the requested chart is absent.
     */
    public static EctSessionBean findEncounter(HttpServletRequest request, String demographicNo) {
        String patient = canonicalPatient(demographicNo);
        HttpSession session = request.getSession(false);
        SavedEncounter saved = session == null ? null
                : (SavedEncounter) session.getAttribute(ENCOUNTER_PREFIX + patient);
        return saved == null ? null : saved.toBean();
    }

    /**
     * Returns a request-owned copy of this patient's entry metadata or, when this login has not
     * entered that chart (or has closed it), metadata naming only the logged-in provider and user,
     * today's date and no appointment. Never another chart window's entry (issue #4673).
     *
     * @throws IllegalArgumentException if {@code demographicNo} is not a valid patient ID
     */
    public static EctSessionBean entryOrLoggedIn(HttpServletRequest request, LoggedInInfo loggedInInfo,
                                                 String demographicNo) {
        EctSessionBean bean = findEncounter(request, demographicNo);
        if (bean == null) {
            bean = new EctSessionBean();
            bean.demographicNo = canonicalPatient(demographicNo);
            bean.providerNo = loggedInInfo.getLoggedInProviderNo();
            bean.curProviderNo = bean.providerNo;
            HttpSession session = request.getSession(false);
            bean.userName = session == null ? "" : (Objects.toString(session.getAttribute("userfirstname"), "")
                    + " " + Objects.toString(session.getAttribute("userlastname"), "")).trim();
        }
        if (bean.appointmentDate == null) bean.appointmentDate = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");
        if (bean.currentDate == null) bean.currentDate = new Date();
        return bean;
    }

    /**
     * The provider this patient's chart was entered for in this login, or the logged-in provider
     * when this login has not entered it (or {@code demographicNo} is null). The notes pages compare
     * unsigned notes' authors with it. Never the provider of another chart's entry: the login-wide
     * bean's is whichever chart was entered last (issue #4673).
     *
     * @throws IllegalArgumentException if {@code demographicNo} is given but not a valid patient ID
     */
    public static String entryProvider(HttpServletRequest request, LoggedInInfo loggedInInfo, String demographicNo) {
        EctSessionBean entry = demographicNo == null ? null : findEncounter(request, demographicNo);
        return entry != null && entry.providerNo != null && !entry.providerNo.isEmpty()
                ? entry.providerNo
                : loggedInInfo.getLoggedInProviderNo();
    }

    /**
     * The appointment this patient was last opened for from the day sheet in this login, provided
     * that appointment still exists and is still this patient's; otherwise null.
     *
     * <p>Work that belongs to "the current visit" uses this: Health Tracker measurements and
     * notes are linked to it, and a multisite prescription preselects the address of its clinic
     * site. It used to be the day sheet's login-wide {@code cur_appointment_no}, the appointment
     * last clicked for any patient, so once another patient's appointment was opened, work in
     * this patient's window was tied to the other patient's visit (issue #4673). The day sheet
     * now files the appointment under the patient it is booked for
     * ({@code ViewStoreApptInSession2Action}), and the ownership check here also covers an
     * appointment that was moved to another patient after it was opened.</p>
     *
     * @param request the request whose session holds this login's day-sheet appointments
     * @param demographicNo the patient; a missing or invalid value yields null
     * @param appointments the DAO used to confirm the appointment is this patient's
     * @return this patient's own current appointment, or null when there is none
     */
    public static Appointment currentAppointment(HttpServletRequest request, String demographicNo,
                                                 OscarAppointmentDao appointments) {
        String patient;
        try {
            patient = canonicalPatient(demographicNo);
        } catch (IllegalArgumentException invalidPatient) {
            return null;
        }
        HttpSession session = request.getSession(false);
        Object stored = session == null ? null
                : session.getAttribute(ChartSessionKeys.currentAppointment(patient));
        if (!(stored instanceof String appointmentNo) || !appointmentNo.matches("[0-9]{1,9}")) {
            return null;
        }
        int id = Integer.parseInt(appointmentNo);
        if (id <= 0) {
            return null;
        }
        Appointment appointment = appointments.find(id);
        return appointment != null && Integer.toString(appointment.getDemographicNo()).equals(patient)
                ? appointment : null;
    }

    /** Releases only the chart being closed, retaining metadata for other open patients. */
    public static void forgetEncounter(HttpServletRequest request, String demographicNo) {
        String patient = canonicalPatient(demographicNo);
        HttpSession session = request.getSession(false);
        if (session != null) session.removeAttribute(ENCOUNTER_PREFIX + patient);
    }

    private record SavedEncounter(String demographicNo, String providerNo, String curProviderNo,
                                  String userName, String appointmentNo, String appointmentDate,
                                  String startTime, String encType, String reason,
                                  String patientFirstName, String patientLastName,
                                  String status) implements Serializable {
        private EctSessionBean toBean() {
            EctSessionBean bean = new EctSessionBean();
            bean.demographicNo = demographicNo;
            bean.providerNo = providerNo;
            bean.curProviderNo = curProviderNo;
            bean.userName = userName;
            bean.appointmentNo = appointmentNo;
            bean.appointmentDate = appointmentDate;
            bean.startTime = startTime;
            bean.encType = encType;
            bean.reason = reason;
            bean.patientFirstName = patientFirstName;
            bean.patientLastName = patientLastName;
            bean.status = status;
            return bean;
        }
    }

    private static String canonicalPatient(String value) {
        if (value == null || !value.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("Invalid demographicNo");
        }
        try {
            int patient = Integer.parseInt(value);
            if (patient > 0) return Integer.toString(patient);
        } catch (NumberFormatException ignored) {
            // A numeric value outside the database identifier range is still invalid.
        }
        throw new IllegalArgumentException("Invalid demographicNo");
    }
}
