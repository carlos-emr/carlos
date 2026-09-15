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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for the ownership checks added to
 * {@link NotesService#saveNote(Integer, ObjectNode)}.
 *
 * <p>Regression coverage for issue #2839's IDOR class. This endpoint had no privilege or
 * ownership check at all, and it copied the payload's {@code uuid} straight onto the note it
 * persisted. Because the note-history and {@code getNotesByUUID} lookups are UUID-wide rather
 * than demographic-scoped, that let a caller append a revision to another patient's chain.</p>
 *
 * @since 2026-09-14
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotesService.saveNote unit tests")
@Tag("unit")
@Tag("fast")
class NotesServiceSaveNoteUnitTest extends CarlosUnitTestBase {

    private static final Integer DEMOGRAPHIC_NO = 100;
    private static final String PROVIDER_NO = "provider1";
    private static final String OTHER_PATIENTS_UUID = "11111111-2222-3333-4444-555555555555";

    @Mock
    private CaseManagementManager caseManagementMgr;

    @Mock
    private SecurityInfoManager securityInfoManager;

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

        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("w"), any()))
                .thenReturn(true);
        lenient().when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class)))
                .thenReturn(true);
        lenient().when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any()))
                .thenReturn(true);
    }

    private ObjectNode notePayload(String uuid) {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("note", "note text");
        if (uuid != null) {
            json.put("uuid", uuid);
        }
        return json;
    }

    @Test
    @DisplayName("should deny access when caller lacks _eChart write privilege")
    void shouldDenyAccess_whenCallerLacksEChartPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("w"), any())).thenReturn(false);

        ObjectNode json = notePayload(null);

        assertThatThrownBy(() -> service.saveNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when demographicNo is outside the caller's program domain")
    void shouldDenyAccess_whenDemographicNoOutsideCallerProgramDomain() {
        when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(false);
        when(caseManagementMgr.isClientReferredInProgramDomain(any(List.class), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(false);

        ObjectNode json = notePayload(null);

        assertThatThrownBy(() -> service.saveNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when the payload's uuid names another patient's revision chain")
    void shouldDenyAccess_whenUuidBelongsToAnotherPatientsChain() throws Exception {
        CaseManagementNote otherPatientsNote = new CaseManagementNote();
        otherPatientsNote.setDemographic_no("999");
        when(caseManagementMgr.getNotesByUUID(OTHER_PATIENTS_UUID)).thenReturn(List.of(otherPatientsNote));

        ObjectNode json = notePayload(OTHER_PATIENTS_UUID);

        assertThatThrownBy(() -> service.saveNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);

        verify(caseManagementMgr, never()).saveNote(any(), any(), any(), any(), any(), any());
    }
}
