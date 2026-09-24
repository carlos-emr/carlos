/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Patient-scoped authorisation for the Rx view gates whose JSPs pick their patient from the
 * request's {@code demographicNo} / {@code demographic_no} (per-patient Rx state, #3875).
 *
 * <p>A gate's global check ({@code _rx} or {@code _allergy} read) says the caller may use the Rx
 * module, not that they may see the patient the URL names. When the request names a patient, the
 * caller must also hold the same privilege for that patient (patient-level
 * {@code _object$demographicNo} restrictions) and be allowed to open that patient's record. A
 * request that names no patient falls back, in the page, to the session's active Rx patient; that
 * patient is authorised the same way, and a session with no active patient is left to the page,
 * which refuses to render.</p>
 *
 * <p>It is also the one patient-level check for every Rx write (stash edits, saves, stamps,
 * re-prescribing, deletes, allergies, pharmacies, encounter text). Those actions check the
 * global privilege first, which only says the caller may use the Rx module; before any side
 * effect they must also pass {@link #requirePatient} for the patient whose data changes, usually
 * through {@link #resolveForWrite}. A patient-level {@code _rx$demographicNo} restriction or a
 * record the caller may not open (program/facility access) otherwise did not stop a write
 * (#3908).</p>
 *
 * @since 2026-09-24
 */
public final class RxRequestedPatientAccess {

    private RxRequestedPatientAccess() {
    }

    /**
     * Refuses the request unless the caller may access the patient it names.
     *
     * @param securityInfoManager the authorisation service
     * @param loggedInInfo        the logged-in provider
     * @param request             the current request
     * @param objectName          the gate's security object, e.g. {@code _rx}
     * @param privilege           the gate's privilege level, e.g. {@code r}
     * @throws SecurityException when the named patient is not accessible to the caller
     */
    public static void require(SecurityInfoManager securityInfoManager, LoggedInInfo loggedInInfo,
                               HttpServletRequest request, String objectName, String privilege) {
        int demographicNo = RxSessionBeanResolver.requestedDemographicNo(request);
        // A malformed, non-positive or conflicting patient (demographicNo=1&demographic_no=2) is
        // refused outright rather than treated as "no patient named", which would let the page
        // fall back to the session's Rx patient without any patient-level check.
        if (demographicNo == RxSessionBeanResolver.INVALID) {
            throw new SecurityException("missing required sec object (" + objectName + ")");
        }
        if (demographicNo > 0) {
            requirePatient(securityInfoManager, loggedInInfo, demographicNo, objectName, privilege);
            return;
        }
        // No patient named: the page falls back to the session's active Rx patient. Authorise
        // that patient too, so a patient-less URL cannot read a chart the caller may not open
        // (for instance after the caller's access to it changed) (#3908).
        RxSessionBean active = RxSessionBeanResolver.resolve(request);
        if (active != null && active.getDemographicNo() > 0) {
            requirePatient(securityInfoManager, loggedInInfo, active.getDemographicNo(), objectName, privilege);
        }
    }

    /**
     * Whether the caller holds {@code privilege} on {@code objectName} for this specific patient
     * and may open the patient's record.
     *
     * @param securityInfoManager the authorisation service
     * @param loggedInInfo        the logged-in provider; {@code null} is never allowed
     * @param demographicNo       the patient whose data is read or changed; non-positive is never allowed
     * @param objectName          the security object, e.g. {@code _rx} or {@code _allergy}
     * @param privilege           the privilege level, e.g. {@code w}
     * @return {@code true} only when both the patient-level privilege and record access hold
     */
    public static boolean mayAccessPatient(SecurityInfoManager securityInfoManager, LoggedInInfo loggedInInfo,
                                           int demographicNo, String objectName, String privilege) {
        return loggedInInfo != null && demographicNo > 0
                && securityInfoManager.hasPrivilege(loggedInInfo, objectName, privilege, demographicNo)
                && securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo);
    }

    /**
     * Refuses the request unless {@link #mayAccessPatient} holds for this patient.
     *
     * @throws SecurityException when the caller may not access this patient at this privilege
     */
    public static void requirePatient(SecurityInfoManager securityInfoManager, LoggedInInfo loggedInInfo,
                                      int demographicNo, String objectName, String privilege) {
        if (!mayAccessPatient(securityInfoManager, loggedInInfo, demographicNo, objectName, privilege)) {
            throw new SecurityException("missing required sec object (" + objectName + ")");
        }
    }

    /**
     * The explicitly named patient's Rx bean for a write ({@link RxSessionBeanResolver#resolveForWrite}),
     * after authorising the caller for that patient.
     *
     * @param securityInfoManager the authorisation service
     * @param request             the current request
     * @param objectName          the security object the write changes, e.g. {@code _rx}
     * @param privilege           the privilege the write needs, e.g. {@code w}
     * @return the bean, or {@code null} when the request names no open Rx patient (callers keep
     *         their existing refusal for that case)
     * @throws SecurityException when the caller may not change that patient's data
     */
    public static RxSessionBean resolveForWrite(SecurityInfoManager securityInfoManager, HttpServletRequest request,
                                                String objectName, String privilege) {
        RxSessionBean bean = RxSessionBeanResolver.resolveForWrite(request);
        if (bean != null) {
            requirePatient(securityInfoManager, LoggedInInfo.getLoggedInInfoFromSession(request),
                    bean.getDemographicNo(), objectName, privilege);
        }
        return bean;
    }
}
