/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The per-patient access decision for Hospital Report Manager reports.
 *
 * <p>The module right ({@code _hrm}) says a provider may work with hospital reports. It says
 * nothing about one patient: a provider can be locked out of a chart (a {@code _demographic$N} or
 * {@code _eChart$N} entry with no rights) or hold narrower rights for that patient, and the rest of
 * the chart refuses them. Every HRM route that lists, shows, prints, downloads or changes a report
 * filed to a patient asks here first, so the report is refused exactly when the chart is.</p>
 *
 * <p>A report filed to a patient is readable when, for every patient it is filed to:</p>
 * <ul>
 *   <li>no demographic or chart lock denies the user that patient
 *       ({@link SecurityInfoManager#isAllowedAccessToPatientRecord});</li>
 *   <li>the user may read the patient's demographic ({@code _demographic} read, evaluated for the
 *       patient, the check {@code DemographicManager} makes before it loads one); and</li>
 *   <li>the user holds {@code _hrm} read, evaluated for the patient so a patient-scoped
 *       {@code _hrm$N} entry is honoured.</li>
 * </ul>
 *
 * <p>A report filed to no patient is not decided here: the module right alone governs it, as
 * before, so unmatched reports can still be opened and matched.</p>
 *
 * <p>Routes that open one named report or patient use the ordinary privilege check. Lists that
 * walk many patients use {@link #listFilter}, which evaluates read-only: filtering a list must not
 * mark the user's session as it would when a locked chart is opened.</p>
 *
 * @since 2026-11-12
 */
public final class HRMReportAccess {

    private static final String HRM = "_hrm";
    private static final String DEMOGRAPHIC = "_demographic";

    private HRMReportAccess() {
    }

    /**
     * Reads a report or patient number from a request value, the way the HRM pages and DAOs do.
     *
     * <p>The guard must see the number its caller will act on, so this is {@link Integer#parseInt}
     * (after trimming, which some handlers do), not a stricter pattern: a value such as
     * {@code +7} that a page would read as 7 must be decided as 7 here too.</p>
     *
     * @param value the raw value, such as an {@code id} or {@code demographic_no} parameter
     * @return the number, or {@code null} when the value is missing or not an integer; the caller
     *         then has nothing to decide for and fails as it always did
     */
    public static Integer number(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * Whether the user may read the reports filed to this patient.
     *
     * @param security the privilege service
     * @param user the logged-in user; {@code null} is refused
     * @param demographicNo the patient
     * @return {@code true} when the chart's own rules let the user read this patient's reports
     */
    public static boolean mayReadPatient(SecurityInfoManager security, LoggedInInfo user, int demographicNo) {
        return decide(security, user, demographicNo, true, true);
    }

    /**
     * Whether the user may read a report with these patient links.
     *
     * @param security the privilege service
     * @param user the logged-in user; {@code null} is refused
     * @param links the report's patient links; none means the report is filed to no patient
     * @return {@code true} when every patient the report is filed to is readable, or it is filed to none
     */
    public static boolean mayReadReport(SecurityInfoManager security, LoggedInInfo user,
                                        Collection<HRMDocumentToDemographic> links) {
        if (user == null) {
            return false;
        }
        for (Integer patient : patients(links)) {
            if (!mayReadPatient(security, user, patient)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the user may read the report with this id, looked up before anything else is read.
     *
     * @param security the privilege service
     * @param linkDao the report-to-patient links
     * @param user the logged-in user; {@code null} is refused
     * @param hrmDocumentId the report; {@code null} names no report and is not refused here
     * @return {@code true} when the report is filed to no patient or only to readable ones
     */
    public static boolean mayReadReport(SecurityInfoManager security, HRMDocumentToDemographicDao linkDao,
                                        LoggedInInfo user, Integer hrmDocumentId) {
        if (user == null) {
            return false;
        }
        if (hrmDocumentId == null) {
            return true;
        }
        return mayReadReport(security, user, linkDao.findByHrmDocumentId(hrmDocumentId));
    }

    /**
     * Refuses a report the user may not read.
     *
     * @throws SecurityException when {@link #mayReadReport(SecurityInfoManager,
     *         HRMDocumentToDemographicDao, LoggedInInfo, Integer)} is false; the message names no
     *         patient and no report
     */
    public static void requireReport(SecurityInfoManager security, HRMDocumentToDemographicDao linkDao,
                                     LoggedInInfo user, Integer hrmDocumentId) {
        if (!mayReadReport(security, linkDao, user, hrmDocumentId)) {
            throw denied();
        }
    }

    /**
     * The refusal the HRM actions throw. The document package maps a {@link SecurityException} to
     * the 403 security page; the message carries no identifier.
     *
     * @return the exception to throw
     */
    public static SecurityException denied() {
        return new SecurityException("missing required patient read access");
    }

    /**
     * A filter for a list that walks many reports.
     *
     * <p>The decision is the same as {@link #mayReadReport(SecurityInfoManager, LoggedInInfo,
     * Collection)}, but evaluated read-only and remembered per patient for the life of the
     * filter, so one list costs one evaluation per distinct patient and leaves the session alone.</p>
     *
     * @param security the privilege service
     * @param user the logged-in user; a {@code null} user sees no patient's reports
     * @param moduleRight whether {@code _hrm} read for the patient is part of the decision. The
     *        provider inbox passes {@code false}: it has always listed a routed report without the
     *        module right, and only the patient's own rules are new there
     * @return a predicate over a report's patient links; {@code true} keeps the report in the list
     */
    public static Predicate<Collection<HRMDocumentToDemographic>> listFilter(SecurityInfoManager security,
                                                                              LoggedInInfo user, boolean moduleRight) {
        Map<Integer, Boolean> decided = new HashMap<>();
        return links -> {
            for (Integer patient : patients(links)) {
                if (!decided.computeIfAbsent(patient, id -> decide(security, user, id, false, moduleRight))) {
                    return false;
                }
            }
            return true;
        };
    }

    /** The patients a report is filed to; a link without a patient number names none. */
    private static Set<Integer> patients(Collection<HRMDocumentToDemographic> links) {
        Set<Integer> patients = new LinkedHashSet<>();
        if (links != null) {
            for (HRMDocumentToDemographic link : links) {
                Integer patient = link == null ? null : link.getDemographicNo();
                if (patient != null && patient > 0) {
                    patients.add(patient);
                }
            }
        }
        return patients;
    }

    private static boolean decide(SecurityInfoManager security, LoggedInInfo user, int demographicNo,
                                  boolean stateful, boolean moduleRight) {
        if (user == null || demographicNo <= 0) {
            return false;
        }
        String patient = Integer.toString(demographicNo);
        // The lock first: when it denies the patient the privilege checks below are not reached,
        // so a list never trips the session marking a locked chart open would.
        return security.isAllowedAccessToPatientRecord(user, demographicNo)
                && read(security, user, DEMOGRAPHIC, patient, stateful)
                && (!moduleRight || read(security, user, HRM, patient, stateful));
    }

    private static boolean read(SecurityInfoManager security, LoggedInInfo user, String object, String patient,
                                boolean stateful) {
        return stateful
                ? security.hasPrivilege(user, object, SecurityInfoManager.READ, patient)
                : security.hasPrivilegeReadOnly(user, object, SecurityInfoManager.READ, patient);
    }
}
