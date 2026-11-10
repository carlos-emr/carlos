/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;

/** Patient-specific module rights and record restrictions are separate required boundaries. */
public final class PatientApiAccess {
    private PatientApiAccess() { }
    public static void require(LoggedInInfo actor, SecurityInfoManager permissions,
                               String module, String right, Integer patient) {
        requireId(patient);
        if (actor == null || !permissions.hasPrivilege(actor, module, right, patient.toString())) {
            throw new ForbiddenException("Patient module access denied");
        }
        requireChart(actor, permissions, patient);
    }
    public static void requireChart(LoggedInInfo actor, SecurityInfoManager permissions, Integer patient) {
        requireId(patient);
        if (actor == null || !permissions.isAllowedAccessToPatientRecord(actor, patient)) {
            throw new ForbiddenException("Patient record access denied");
        }
    }
    private static void requireId(Integer patient) {
        if (patient == null || patient <= 0) throw new BadRequestException("Invalid patient identifier");
    }
}
