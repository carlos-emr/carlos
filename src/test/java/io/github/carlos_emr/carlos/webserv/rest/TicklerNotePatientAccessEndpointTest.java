/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import jakarta.ws.rs.client.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
class TicklerNotePatientAccessEndpointTest extends CarlosRestTestBase {
    @Mock private SecurityInfoManager security;
    @Mock private TicklerManager ticklers;
    @Mock private CaseManagementManager notes;
    @Mock private ProviderManager providers;
    @Override protected Object getServiceBean() {
        var service = new NotesService();
        injectDependency(service,"securityInfoManager",security);
        injectDependency(service,"noteTicklerManager",ticklers);
        injectDependency(service,"caseManagementMgr",notes);
        injectDependency(service,"providerMgr",providers); return service;
    }
    @BeforeEach void grantFixture() {
        when(security.hasPrivilege(mockLoggedInInfo,"_tickler","r",(String)null)).thenReturn(true);
        when(security.hasPrivilege(mockLoggedInInfo,"_tickler","w",(String)null)).thenReturn(true);
        when(security.hasPrivilege(mockLoggedInInfo,"_eChart","r",(String)null)).thenReturn(true);
        when(security.hasPrivilege(mockLoggedInInfo,"_eChart","w",(String)null)).thenReturn(true);
        for (String module : java.util.List.of("_tickler","_eChart")) {
            for (String right : java.util.List.of("r","w")) when(security.hasPrivilege(mockLoggedInInfo,module,right,"42")).thenReturn(true);
        }
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(true);
        when(ticklers.getTicklerDemographicNo(mockLoggedInInfo,7)).thenReturn(42);
    }
    private int save(String json) { return request().path("/notes/ticklerSaveNote").post(Entity.json(json)).getStatus(); }
    @Test void shouldDenyPatientOverride_beforeLookingUpAnyClinicalNote() {
        when(security.hasPrivilege(mockLoggedInInfo,"_tickler","r","42")).thenReturn(false);
        assertThat(request().path("/notes/ticklerGetNote/7").get().getStatus()).isEqualTo(403);
        verifyNoInteractions(notes,providers);
    }
    @Test void shouldDenyBlockedChart_beforeLookingUpAnyClinicalNote() {
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(false);
        assertThat(request().path("/notes/ticklerGetNote/7").get().getStatus()).isEqualTo(403);
        verifyNoInteractions(notes,providers);
    }
    @Test void shouldReturn404_whenTheTicklerDoesNotExist() {
        when(ticklers.getTicklerDemographicNo(mockLoggedInInfo,8)).thenReturn(null);
        assertThat(request().path("/notes/ticklerGetNote/8").get().getStatus()).isEqualTo(404);
        verifyNoInteractions(notes,providers);
    }
    @Test void shouldReturnTheAuthorizedLinkedNote_withoutChangingItsContents() {
        var link = new CaseManagementNoteLink(); link.setNoteId(9L);
        when(notes.getLatestLinkByTableId(CaseManagementNoteLink.TICKLER,7L)).thenReturn(link);
        var note = new CaseManagementNote(); note.setId(9L); note.setDemographic_no("42"); note.setProviderNo("p");
        note.setNote("Retained clinical note"); note.setRevision("3");
        when(notes.getNote("9")).thenReturn(note);
        when(providers.getProvider("p")).thenReturn(new Provider("p","Clinician","doctor","F","","Test"));
        var response = request().path("/notes/ticklerGetNote/7").get();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(responseJson(response).at("/ticklerNote/note").asText()).isEqualTo("Retained clinical note");
    }
    @Test void shouldRefuseAMismatchedPatient_beforeAnyNoteReadOrWrite() {
        assertThat(save("{\"tickler\":{\"id\":7,\"demographicNo\":43},\"note\":\"new text\"}")).isEqualTo(400);
        verifyNoInteractions(notes,providers);
    }
    @Test void shouldDenyScopedWrite_beforeAnyNoteReadOrWrite() {
        when(security.hasPrivilege(mockLoggedInInfo,"_eChart","w","42")).thenReturn(false);
        assertThat(save("{\"tickler\":{\"id\":7,\"demographicNo\":42},\"note\":\"new text\"}")).isEqualTo(403);
        verifyNoInteractions(notes,providers);
    }
    @Test void shouldRefuseCrossPatientRevision_beforeReadingItsClinicalHistory() {
        var original = spy(new CaseManagementNote()); original.setDemographic_no("43");
        when(notes.getNote("9")).thenReturn(original);
        assertThat(save("{\"tickler\":{\"id\":7,\"demographicNo\":42},\"noteId\":9,\"note\":\"new text\"}")).isEqualTo(400);
        verify(original,never()).getHistory(); verify(notes,never()).saveNoteSimple(any());
    }
    @Test void shouldRejectMissingTicklerInput_insteadOfReportingAnInternalFailure() {
        assertThat(save("{}")).isEqualTo(400); verifyNoInteractions(ticklers,notes,providers);
    }
}
