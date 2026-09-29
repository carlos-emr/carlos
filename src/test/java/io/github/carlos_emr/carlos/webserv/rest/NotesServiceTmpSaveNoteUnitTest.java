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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NoteTo1;

/**
 * Unit tests for the ownership checks added to
 * {@link NotesService#tmpSaveNote(Integer, NoteTo1)}.
 *
 * <p>Regression coverage for issue #2839's IDOR class. The autosave endpoint wrote a draft
 * row for whatever demographicNo the caller put in the path, with no privilege or ownership
 * check, and stored a caller-supplied noteId alongside it. {@code getCurrentNote} later
 * restores that noteId, so an unchecked draft was a way to stage a cross-patient note
 * read.</p>
 *
 * @since 2026-09-14
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotesService.tmpSaveNote unit tests")
@Tag("unit")
@Tag("fast")
class NotesServiceTmpSaveNoteUnitTest extends CarlosUnitTestBase {

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

        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("w"), any()))
                .thenReturn(true);
        lenient().when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class)))
                .thenReturn(true);
        lenient().when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any()))
                .thenReturn(true);
    }

    private NoteTo1 draft(Integer noteId) {
        NoteTo1 note = new NoteTo1();
        note.setNote("draft text");
        note.setNoteId(noteId);
        return note;
    }

    @Test
    @DisplayName("should deny access when caller lacks _eChart write privilege")
    void shouldDenyAccess_whenCallerLacksEChartPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("w"), any())).thenReturn(false);

        NoteTo1 note = draft(0);

        assertThatThrownBy(() -> service.tmpSaveNote(DEMOGRAPHIC_NO, note))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when demographicNo is outside the caller's program domain")
    void shouldDenyAccess_whenDemographicNoOutsideCallerProgramDomain() {
        when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(false);
        when(caseManagementMgr.isClientReferredInProgramDomain(any(List.class), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(false);

        NoteTo1 note = draft(0);

        assertThatThrownBy(() -> service.tmpSaveNote(DEMOGRAPHIC_NO, note))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when the draft references another patient's note")
    void shouldDenyAccess_whenDraftNoteBelongsToAnotherPatient() {
        ProgramProvider programProvider = new ProgramProvider();
        programProvider.setProgramId(5L);
        when(programManager2.getCurrentProgramInDomain(any(), eq(PROVIDER_NO))).thenReturn(programProvider);

        CaseManagementNote otherPatientsNote = new CaseManagementNote();
        otherPatientsNote.setDemographic_no("999");
        when(caseManagementMgr.getNote("4242")).thenReturn(otherPatientsNote);

        NoteTo1 note = draft(4242);

        assertThatThrownBy(() -> service.tmpSaveNote(DEMOGRAPHIC_NO, note))
                .isInstanceOf(AccessDeniedException.class);

        // Nothing was written: the previous draft must not be deleted on a rejected call.
        verify(caseManagementMgr, never()).deleteTmpSave(any(), any(), any());
        verify(caseManagementMgr, never()).tmpSave(any(), any(), any(), any(), any());
    }
}
