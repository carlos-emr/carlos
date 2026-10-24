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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for the ownership check added to
 * {@link NotesService#getCurrentNote(Integer, ObjectNode)}.
 *
 * <p>Regression coverage for issue #2839's IDOR class: this endpoint took
 * demographicNo directly from the caller with no privilege or ownership
 * check at all.</p>
 *
 * @since 2026-07-06
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotesService.getCurrentNote unit tests")
@Tag("unit")
@Tag("fast")
class NotesServiceGetCurrentNoteUnitTest extends CarlosUnitTestBase {

    private static final Integer DEMOGRAPHIC_NO = 100;
    private static final String PROVIDER_NO = "provider1";

    @Mock
    private CaseManagementManager caseManagementMgr;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private ProgramManager2 programManager2;

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
        injectDependency(service, "programManager2", programManager2);

        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), any()))
                .thenReturn(true);
        lenient().when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class)))
                .thenReturn(true);
        lenient().when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any()))
                .thenReturn(true);
    }

    private ObjectNode emptyJson() {
        return new ObjectMapper().createObjectNode();
    }

    @Test
    @DisplayName("should deny access when caller lacks _eChart read privilege")
    void shouldDenyAccess_whenCallerLacksEChartPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), any())).thenReturn(false);

        ObjectNode json = emptyJson();

        assertThatThrownBy(() -> service.getCurrentNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when demographicNo is outside the caller's program domain")
    void shouldDenyAccess_whenDemographicNoNotInCallerProgramDomain() {
        when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(false);
        when(caseManagementMgr.isClientReferredInProgramDomain(any(List.class), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(false);

        ObjectNode json = emptyJson();

        assertThatThrownBy(() -> service.getCurrentNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when the patient has an eChart access opt-out override")
    void shouldDenyAccess_whenPatientHasOptOutOverride() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(false);

        ObjectNode json = emptyJson();

        assertThatThrownBy(() -> service.getCurrentNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when a restored autosave draft points at another patient's note")
    void shouldDenyAccess_whenRestoredDraftNoteBelongsToAnotherPatient() {
        // A draft can be seeded through /{demographicNo}/tmpSave with any noteId, so the note
        // the draft restores must be re-checked against this patient before it is returned.
        ProgramProvider programProvider = new ProgramProvider();
        programProvider.setProgramId(5L);
        when(programManager2.getCurrentProgramInDomain(any(), eq(PROVIDER_NO))).thenReturn(programProvider);

        CaseManagementTmpSave draft = new CaseManagementTmpSave();
        draft.setNoteId(4242);
        draft.setNote("draft text");
        when(caseManagementMgr.restoreTmpSave(eq(PROVIDER_NO), eq(String.valueOf(DEMOGRAPHIC_NO)), eq("5")))
                .thenReturn(draft);

        CaseManagementNote otherPatientsNote = new CaseManagementNote();
        otherPatientsNote.setDemographic_no("999");
        when(caseManagementMgr.getNote("4242")).thenReturn(otherPatientsNote);

        ObjectNode json = emptyJson();

        assertThatThrownBy(() -> service.getCurrentNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);
    }
}
