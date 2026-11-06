/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Current CPP prefills must use authenticated, latest non-archived note reads. */
@Tag("unit")
@Tag("service")
class CppTextLoaderUnitTest {
    private final CaseManagementManager manager = mock(CaseManagementManager.class);
    private final LoggedInInfo login = new LoggedInInfo();
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final CppTextLoader loader = new CppTextLoader(manager, security);

    @BeforeEach
    void prepare() {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        login.setLoggedInProvider(provider);
        request.setParameter("demographicNo", "101");
        ChartProgramContext.remember(request, "0");
        when(security.hasPrivilege(eq(login), anyString(), eq("r"), eq("101"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(true);
    }

    @ParameterizedTest
    @EnumSource(value = CaseManagementManager.IssueType.class, names = {"CONCERNS", "OMEDS"})
    void shouldReadOnlyActiveModernNotes_forRequestedPatientAndSection(CaseManagementManager.IssueType section) {
        Issue issue = new Issue();
        issue.setId(17L);
        when(manager.getIssueByCode(section)).thenReturn(issue);
        when(manager.getActiveNotes(eq(login), eq("101"), aryEq(new String[]{"17"})))
                .thenReturn(List.of(note("First current note"), note(null), note("  "), note("Second current note")));

        assertThat(loader.load(request, login, "101", section)).isEqualTo("First current note\nSecond current note");
        verify(manager).getIssueByCode(section);
        verify(manager).getActiveNotes(eq(login), eq("101"), aryEq(new String[]{"17"}));
        verifyNoMoreInteractions(manager);
    }

    @Test
    void shouldReturnEmpty_whenConcernSectionIsUnseeded() {
        assertThat(loader.load(request, login, "101", CaseManagementManager.IssueType.CONCERNS)).isEmpty();
        verify(manager).getIssueByCode(CaseManagementManager.IssueType.CONCERNS);
        verifyNoMoreInteractions(manager);
    }

    @Test
    void shouldReturnEmpty_whenNoCurrentNotesRemain() {
        Issue issue = new Issue();
        issue.setId(17L);
        when(manager.getIssueByCode(CaseManagementManager.IssueType.CONCERNS)).thenReturn(issue);
        when(manager.getActiveNotes(eq(login), eq("101"), aryEq(new String[]{"17"}))).thenReturn(List.of());
        assertThat(loader.load(request, login, "101", CaseManagementManager.IssueType.CONCERNS)).isEmpty();
    }

    @Test
    void shouldPropagateAccessFailure_whenModernNoteReaderRefusesPatient() {
        Issue issue = new Issue();
        issue.setId(17L);
        when(manager.getIssueByCode(CaseManagementManager.IssueType.CONCERNS)).thenReturn(issue);
        when(manager.getActiveNotes(eq(login), eq("101"), aryEq(new String[]{"17"})))
                .thenThrow(new SecurityException("Access refused"));
        assertThatThrownBy(() -> loader.load(request, login, "101", CaseManagementManager.IssueType.CONCERNS))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldExcludeHiddenNotes_whenProgramRoleOrFacilityPolicyRestrictsAccess() {
        ChartProgramContext.remember(request, "17");
        Issue issue = new Issue();
        issue.setId(17L);
        when(manager.getIssueByCode(CaseManagementManager.IssueType.CONCERNS)).thenReturn(issue);
        CaseManagementNote visible = note("Visible current concern");
        CaseManagementNote hidden = note("Restricted clinical text");
        List<CaseManagementNote> notes = List.of(visible, hidden);
        when(manager.getActiveNotes(eq(login), eq("101"), aryEq(new String[]{"17"}))).thenReturn(notes);
        when(manager.filterNotes(login, "999998", notes, "17")).thenReturn(List.of(visible));

        assertThat(loader.load(request, login, "101", CaseManagementManager.IssueType.CONCERNS))
                .isEqualTo("Visible current concern");
        verify(manager).filterNotes(login, "999998", notes, "17");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_eChart", "_casemgmt.notes"})
    void shouldRefuseUnauthorizedPrefill_beforeReadingAnyClinicalData(String object) {
        when(security.hasPrivilege(login, object, "r", "101")).thenReturn(false);
        assertThatThrownBy(() -> loader.load(request, login, "101", CaseManagementManager.IssueType.CONCERNS))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseRestrictedPatient_beforeReadingAnyClinicalData() {
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(false);
        assertThatThrownBy(() -> loader.load(request, login, "101", CaseManagementManager.IssueType.CONCERNS))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseForeignChartContext_whenConsultationNamesPatientWithDe() {
        request.removeParameter("demographicNo");
        request.setParameter("de", "202");
        when(security.hasPrivilege(eq(login), anyString(), eq("r"), eq("202"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 202)).thenReturn(true);
        assertThatThrownBy(() -> loader.load(request, login, "202", CaseManagementManager.IssueType.CONCERNS))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(manager);
    }

    private static CaseManagementNote note(String text) {
        CaseManagementNote note = new CaseManagementNote();
        note.setNote(text);
        return note;
    }
}
