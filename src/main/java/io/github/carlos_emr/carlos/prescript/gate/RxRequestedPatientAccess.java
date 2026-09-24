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
 * request that names no patient is left to the page, which falls back to the Rx patient already
 * opened (and authorised) in this session or refuses to render.</p>
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
        if (demographicNo > 0
                && (!securityInfoManager.hasPrivilege(loggedInInfo, objectName, privilege, demographicNo)
                    || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo))) {
            throw new SecurityException("missing required sec object (" + objectName + ")");
        }
    }
}
