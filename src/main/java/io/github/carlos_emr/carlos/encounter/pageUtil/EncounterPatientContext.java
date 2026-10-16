/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

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
