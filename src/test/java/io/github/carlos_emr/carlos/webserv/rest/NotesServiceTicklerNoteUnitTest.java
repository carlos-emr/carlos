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

import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.TicklerNoteResponse;

/**
 * Unit tests for {@link NotesService#ticklerGetNote(Integer)} and
 * {@link NotesService#ticklerSaveNote(com.fasterxml.jackson.databind.node.ObjectNode)}.
 *
 * <p>Regression coverage for issue #2839's IDOR class: both endpoints only
 * checked generic role privileges, with no check that the linked note (or
 * caller-supplied demographicNo) belonged to a patient the caller may access.</p>
 *
 * @since 2026-07-06
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotesService tickler note unit tests")
@Tag("unit")
@Tag("fast")
class NotesServiceTicklerNoteUnitTest extends CarlosUnitTestBase {

    private static final Integer TICKLER_NO = 7;
    private static final Long NOTE_ID = 42L;
    private static final String OWNING_DEMOGRAPHIC_NO = "100";
    private static final String PROVIDER_NO = "provider1";

    @Mock
    private CaseManagementManager caseManagementMgr;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private ProviderManager providerMgr;

    @Mock
    private CaseManagementNote note;

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
        injectDependency(service, "providerMgr", providerMgr);

        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_tickler"), any(), any())).thenReturn(true);
        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), any(), any())).thenReturn(true);
        lenient().when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(true);
        lenient().when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);
    }

    private CaseManagementNoteLink linkTo(Long noteId) {
        CaseManagementNoteLink link = new CaseManagementNoteLink();
        link.setNoteId(noteId);
        return link;
    }

    @Test
    @DisplayName("ticklerGetNote should return the note when caller is in the patient's program domain")
    void ticklerGetNote_shouldReturnNote_whenCallerInProgramDomain() {
        when(caseManagementMgr.getLatestLinkByTableId(CaseManagementNoteLink.TICKLER, Long.valueOf(TICKLER_NO))).thenReturn(linkTo(NOTE_ID));
        when(caseManagementMgr.getNote(NOTE_ID.toString())).thenReturn(note);
        when(note.getDemographic_no()).thenReturn(OWNING_DEMOGRAPHIC_NO);
        when(note.getId()).thenReturn(NOTE_ID);
        when(note.getProviderNo()).thenReturn(PROVIDER_NO);
        when(providerMgr.getProvider(PROVIDER_NO)).thenReturn(new Provider());

        TicklerNoteResponse response = service.ticklerGetNote(TICKLER_NO);

        assertThat(response.getTicklerNote()).isNotNull();
    }

    @Test
    @DisplayName("ticklerGetNote should not return the note when it is outside the caller's program domain")
    void ticklerGetNote_shouldNotReturnNote_whenNoteNotInCallerProgramDomain() {
        when(caseManagementMgr.getLatestLinkByTableId(CaseManagementNoteLink.TICKLER, Long.valueOf(TICKLER_NO))).thenReturn(linkTo(NOTE_ID));
        when(caseManagementMgr.getNote(NOTE_ID.toString())).thenReturn(note);
        when(note.getDemographic_no()).thenReturn(OWNING_DEMOGRAPHIC_NO);
        when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(false);
        when(caseManagementMgr.isClientReferredInProgramDomain(any(List.class), eq(OWNING_DEMOGRAPHIC_NO))).thenReturn(false);

        TicklerNoteResponse response = service.ticklerGetNote(TICKLER_NO);

        assertThat(response.getTicklerNote()).isNull();
    }

    @Test
    @DisplayName("ticklerGetNote should deny access when caller lacks _tickler privilege")
    void ticklerGetNote_shouldDenyAccess_whenCallerLacksTicklerPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_tickler"), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.ticklerGetNote(TICKLER_NO)).isInstanceOf(RuntimeException.class);
    }

    private ObjectNode ticklerSaveNoteJson() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        json.put("note", "note text");
        ObjectNode tickler = mapper.createObjectNode();
        tickler.put("id", 1);
        tickler.put("demographicNo", Integer.valueOf(OWNING_DEMOGRAPHIC_NO));
        json.set("tickler", tickler);
        return json;
    }

    @Test
    @DisplayName("ticklerSaveNote should deny access when demographicNo is outside the caller's program domain")
    void ticklerSaveNote_shouldDenyAccess_whenDemographicNoNotInCallerProgramDomain() {
        when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(false);
        when(caseManagementMgr.isClientReferredInProgramDomain(any(List.class), eq(OWNING_DEMOGRAPHIC_NO))).thenReturn(false);

        assertThatThrownBy(() -> service.ticklerSaveNote(ticklerSaveNoteJson()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ticklerSaveNote should deny access when caller lacks _eChart write privilege")
    void ticklerSaveNote_shouldDenyAccess_whenCallerLacksEChartPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.ticklerSaveNote(ticklerSaveNoteJson()))
                .isInstanceOf(RuntimeException.class);
    }
}
