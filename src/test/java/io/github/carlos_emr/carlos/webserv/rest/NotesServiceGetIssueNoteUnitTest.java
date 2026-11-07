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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NoteIssueTo1;

/**
 * Unit tests for {@link NotesService#getIssueNote(Integer)}.
 *
 * <p>Regression coverage for issue #2839: the endpoint fetched a clinical note
 * by bare id with no privilege check and no verification that the note
 * belonged to a patient the caller may access, letting any authenticated
 * caller enumerate note ids and read any patient's encounter notes.</p>
 *
 * @since 2026-07-06
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotesService.getIssueNote unit tests")
@Tag("unit")
@Tag("fast")
class NotesServiceGetIssueNoteUnitTest extends CarlosUnitTestBase {

    private static final Integer NOTE_ID = 42;
    private static final String OWNING_DEMOGRAPHIC_NO = "100";
    private static final String PROVIDER_NO = "provider1";

    @Mock
    private CaseManagementManager caseManagementMgr;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private CaseManagementNote casemgmtNote;

    private NotesService service;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER_NO);
        loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);

        service = new NotesService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        injectDependency(service, "caseManagementMgr", caseManagementMgr);
        injectDependency(service, "securityInfoManager", securityInfoManager);

        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), any()))
                .thenReturn(true);
        lenient().when(caseManagementMgr.getNote(String.valueOf(NOTE_ID))).thenReturn(casemgmtNote);
        lenient().when(casemgmtNote.getDemographic_no()).thenReturn(OWNING_DEMOGRAPHIC_NO);
        lenient().when(casemgmtNote.getIssues()).thenReturn(issuesWithOneCppAndOneRegular());
        lenient().when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(true);
        lenient().when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any()))
                .thenReturn(true);
    }

    /**
     * One CPP-code issue (must be filtered out of the returned assigned issues)
     * plus one regular issue (must survive the filter), matching the shape
     * NotesService.isCppCode/getIssueNote actually filters over.
     */
    private Set<CaseManagementIssue> issuesWithOneCppAndOneRegular() {
        Issue cppIssueCode = new Issue();
        cppIssueCode.setCode("OMeds");
        CaseManagementIssue cppIssue = new CaseManagementIssue();
        cppIssue.setIssue(cppIssueCode);

        Issue regularIssueCode = new Issue();
        regularIssueCode.setCode("diabetes");
        CaseManagementIssue regularIssue = new CaseManagementIssue();
        regularIssue.setIssue(regularIssueCode);

        Set<CaseManagementIssue> issues = new LinkedHashSet<>();
        issues.add(cppIssue);
        issues.add(regularIssue);
        return issues;
    }

    @Test
    @DisplayName("should return the note with CPP issues filtered out when caller is in the patient's program domain")
    void shouldReturnNote_whenCallerInProgramDomain() {
        NoteIssueTo1 result = service.getIssueNote(NOTE_ID);

        assertThat(result).isNotNull();
        verify(caseManagementMgr).getNote(String.valueOf(NOTE_ID));
        assertThat(result.getAssignedCMIssues())
                .extracting(cmi -> cmi.getIssue().getCode())
                .containsExactly("diabetes");
    }

    @Test
    @DisplayName("should return the note when caller was only referred into the patient's program domain")
    void shouldReturnNote_whenCallerOnlyReferredIntoProgramDomain() {
        when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(false);
        when(caseManagementMgr.isClientReferredInProgramDomain(any(List.class), eq(OWNING_DEMOGRAPHIC_NO))).thenReturn(true);

        NoteIssueTo1 result = service.getIssueNote(NOTE_ID);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should deny access when caller lacks _eChart read privilege")
    void shouldDenyAccess_whenCallerLacksEChartPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getIssueNote(NOTE_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(caseManagementMgr, never()).getNote(any());
    }

    @Test
    @DisplayName("should deny access when the note does not belong to any of the caller's program domains")
    void shouldDenyAccess_whenNoteNotInCallerProgramDomain() {
        when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(false);
        when(caseManagementMgr.isClientReferredInProgramDomain(any(List.class), eq(OWNING_DEMOGRAPHIC_NO))).thenReturn(false);

        assertThatThrownBy(() -> service.getIssueNote(NOTE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when the patient has an eChart access opt-out override")
    void shouldDenyAccess_whenPatientHasOptOutOverride() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getIssueNote(NOTE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access identically when the note id does not exist")
    void shouldDenyAccess_whenNoteDoesNotExist() {
        when(caseManagementMgr.getNote(String.valueOf(NOTE_ID))).thenReturn(null);

        assertThatThrownBy(() -> service.getIssueNote(NOTE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when the note's demographic_no is not numeric")
    void shouldDenyAccess_whenDemographicNoIsNotNumeric() {
        // A non-numeric demographic_no shouldn't be reachable in practice (the column
        // is an int), but isNoteAccessAllowed must fail closed rather than let
        // Integer.valueOf's NumberFormatException propagate unmapped.
        when(casemgmtNote.getDemographic_no()).thenReturn("not-a-number");

        assertThatThrownBy(() -> service.getIssueNote(NOTE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when the caller has no assigned provider number")
    void shouldDenyAccess_whenCallerHasNoProviderNo() {
        // A LoggedInInfo with no provider set means getLoggedInProviderNo() returns
        // null; this must fail closed rather than reach the domain-membership DAO
        // calls with a null providerNo.
        loggedInInfo.setLoggedInProvider(null);

        assertThatThrownBy(() -> service.getIssueNote(NOTE_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(caseManagementMgr, never()).getProgramProviders(any());
        verify(caseManagementMgr, never()).getAdmission(any());
    }
}
