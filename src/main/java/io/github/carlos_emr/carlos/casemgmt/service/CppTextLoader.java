/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PatientRecordAccess;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/** Loads current CPP text for clinical prefills without consulting retained legacy charts. */
public final class CppTextLoader {
    private final CaseManagementManager manager;
    private final SecurityInfoManager security;

    /**
     * @param manager the authenticated modern-note reader
     * @param security the chart and patient authorization service
     */
    public CppTextLoader(CaseManagementManager manager, SecurityInfoManager security) {
        this.manager = Objects.requireNonNull(manager);
        this.security = Objects.requireNonNull(security);
    }

    /**
     * Reads the latest non-archived notes in the selected CPP section, in chart order.
     * The CPP projection is append-only and can retain superseded text, so it is not a
     * source for a current clinical prefill. Authorization failures propagate to the caller.
     *
     * @param request the requesting chart window, with its optional authenticated program context
     * @param login the requesting clinician
     * @param demographicNo the explicitly authorized patient
     * @param section the modern CPP section
     * @return plain text, or empty when the section is unseeded or has no current notes
     */
    public String load(HttpServletRequest request, LoggedInInfo login, String demographicNo,
                       CaseManagementManager.IssueType section) {
        PatientRecordAccess.require(security, login, demographicNo, "_demographic", "r");
        PatientRecordAccess.require(security, login, demographicNo, "_eChart", "r");
        PatientRecordAccess.require(security, login, demographicNo, "_casemgmt.notes", "r");
        String program = ChartProgramContext.resolveForPrint(patientScopedRequest(request, demographicNo), login);
        Issue issue = manager.getIssueByCode(section);
        if (issue == null) return "";
        StringJoiner text = new StringJoiner("\n");
        List<CaseManagementNote> notes = manager.getActiveNotes(login, demographicNo,
                new String[]{issue.getId().toString()});
        // Match modern chart/print visibility: no-program installations retain their legacy
        // visibility; configured programs apply role and facility policy before clinical text.
        if (program != null && !"0".equals(program)) {
            notes = manager.filterNotes(login, login.getLoggedInProviderNo(), notes, program);
        }
        for (CaseManagementNote note : notes) {
            if (note.getNote() != null && !note.getNote().isBlank()) text.add(note.getNote());
        }
        return text.toString();
    }

    private static HttpServletRequest patientScopedRequest(HttpServletRequest request, String patient) {
        // Consultation pages name their patient with "de". Bind the canonical aliases used
        // by ChartProgramContext so a captured context cannot select another patient's program.
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                return isPatientParameter(name) ? patient : super.getParameter(name);
            }

            @Override
            public String[] getParameterValues(String name) {
                return isPatientParameter(name) ? new String[]{patient} : super.getParameterValues(name);
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                Map<String, String[]> parameters = new HashMap<>(super.getParameterMap());
                parameters.put("demographicNo", new String[]{patient});
                parameters.put("demographic_no", new String[]{patient});
                return Collections.unmodifiableMap(parameters);
            }

            @Override
            public Enumeration<String> getParameterNames() {
                return Collections.enumeration(getParameterMap().keySet());
            }

            private boolean isPatientParameter(String name) {
                return "demographicNo".equals(name) || "demographic_no".equals(name);
            }
        };
    }
}
