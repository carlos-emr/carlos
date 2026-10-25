/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Expanding a shortened note and loading its editor use the same mapped viewNote method.
 * Neither a guessed note ID nor a token from another chart may bypass the list's patient,
 * record or reporter-role policy. The stored note's program can differ when that role is readable.
 *
 * @since 2026-10-25
 */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementViewNoteAccessUnitTest extends CarlosUnitTestBase {
    private CaseManagementView2Action action;
    private CaseManagementManager manager;
    private SecurityInfoManager security;
    private LoggedInInfo user;
    private MockHttpSession session;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CaseManagementNote note;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementView2Action.class, CALLS_REAL_METHODS);
        manager = mock(CaseManagementManager.class);
        security = mock(SecurityInfoManager.class);
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, user);
        request = requestFor("101");
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        action.caseManagementMgr = manager;
        action.securityInfoManager = security;
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq("101"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(true);
        note = new CaseManagementNote();
        note.setId(51L);
        note.setDemographic_no("101");
        note.setProgram_no("8");
        note.setReporter_caisi_role("3");
        note.setNote("Owned note <text>\n患者");
        when(manager.getNote("51")).thenReturn(note);
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(true);
    }

    private MockHttpServletRequest requestFor(String patient) {
        var result = new MockHttpServletRequest();
        result.setSession(session);
        result.setMethod("POST");
        if (patient != null) result.setParameter("demographicNo", patient);
        result.setParameter("method", "viewNote");
        result.setParameter("noteId", "51");
        result.setParameter("raw", "true");
        return result;
    }

    private void capture(String program) {
        var entry = requestFor("101");
        ChartProgramContext.remember(entry, program);
        request.setParameter("chartContext", ChartProgramContext.token(entry));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "-1", "patient", "2147483648"})
    void shouldRefuseBeforeLoadingNote_whenPatientIsMissingOrMalformed(String patient) throws Exception {
        if (patient == null) request.removeParameter("demographicNo");
        else request.setParameter("demographicNo", patient);

        assertThat(action.execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("noteStr")).isNull();
        verifyNoInteractions(manager, security);
    }

    @Test
    void shouldRefuseBeforeLoadingNote_whenPatientAliasesConflict() throws Exception {
        request.setParameter("demographic_no", "202");

        assertThat(action.viewNote()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("noteStr")).isNull();
        verifyNoInteractions(manager, security);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "-1", "1 OR 1=1", "9223372036854775808"})
    void shouldRefuseBeforeLoadingNote_whenNoteIdentityIsMissingOrMalformed(String id) throws Exception {
        if (id == null) request.removeParameter("noteId");
        else request.setParameter("noteId", id);

        assertThat(action.viewNote()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("noteStr")).isNull();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseBeforeLoadingNote_whenRepeatedNoteIdentitiesConflict() throws Exception {
        request.setParameter("noteId", "51", "52");

        assertThat(action.viewNote()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("noteStr")).isNull();
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_eChart", "_casemgmt.notes"})
    void shouldRefuseBeforeLoadingNote_whenPatientReadPrivilegeIsMissing(String object) {
        when(security.hasPrivilege(user, object, "r", "101")).thenReturn(false);

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);

        assertThat(request.getAttribute("noteStr")).isNull();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseBeforeLoadingNote_whenPatientRecordIsLocked() {
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(false);

        assertThatThrownBy(action::viewNote).isInstanceOf(SecurityException.class);

        assertThat(request.getAttribute("noteStr")).isNull();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseWithoutPublishingText_whenStoredNoteBelongsToAnotherPatient() {
        note.setDemographic_no("202");

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);

        assertThat(request.getAttribute("noteStr")).isNull();
        verify(manager).isClientInProgramDomain("4249", "101");
        verify(manager).getNote("51");
        verifyNoMoreInteractions(manager);
        verify(security, never()).hasPrivilege(eq(user), anyString(), anyString(), eq("202"));
    }

    @Test
    void shouldAnswerNotFoundWithoutPublishingText_whenNoteWasRemoved() throws Exception {
        when(manager.getNote("51")).thenReturn(null);

        assertThat(action.viewNote()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(request.getAttribute("noteStr")).isNull();
        verify(manager).isClientInProgramDomain("4249", "101");
        verify(manager).getNote("51");
        verifyNoMoreInteractions(manager);
    }

    @Test
    void shouldRefuseWithoutPublishingText_whenReporterRoleIsHiddenFromChartProgram() {
        capture("7");
        when(manager.filterNotes(user, "4249", List.of(note), "7")).thenReturn(List.of());

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);

        assertThat(request.getAttribute("noteStr")).isNull();
        verify(manager).filterNotes(user, "4249", List.of(note), "7");
    }

    @Test
    void shouldRefuseBeforeLoadingNote_whenPatientIsOutsideProgramAndReferralDomain() {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);

        assertThatThrownBy(action::viewNote).isInstanceOf(SecurityException.class);

        assertThat(request.getAttribute("noteStr")).isNull();
        verify(manager).isClientInProgramDomain("4249", "101");
        verify(manager).isClientReferredInProgramDomain("4249", "101");
        verifyNoMoreInteractions(manager);
    }

    @Test
    void shouldRenderReadableNote_whenPatientIsReferredIntoProviderProgramDomain() throws Exception {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);
        when(manager.isClientReferredInProgramDomain("4249", "101")).thenReturn(true);
        capture("7");
        when(manager.filterNotes(user, "4249", List.of(note), "7")).thenReturn(List.of(note));

        assertThat(action.viewNote()).isEqualTo("displayNote");

        assertThat(request.getAttribute("noteStr")).isEqualTo(note.getNote());
        verify(manager).isClientReferredInProgramDomain("4249", "101");
        verify(manager).filterNotes(user, "4249", List.of(note), "7");
    }

    @Test
    void shouldRefuseBeforeLoadingNote_whenContextBelongsToAnotherPatient() {
        var otherEntry = requestFor("202");
        ChartProgramContext.remember(otherEntry, "9");
        request.setParameter("chartContext", ChartProgramContext.token(otherEntry));

        assertThatThrownBy(action::viewNote).isInstanceOf(SecurityException.class);

        assertThat(request.getAttribute("noteStr")).isNull();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseBeforeLoadingNote_whenContextIsForged() {
        request.setParameter("chartContext", "forged");

        assertThatThrownBy(action::viewNote).isInstanceOf(SecurityException.class);

        assertThat(request.getAttribute("noteStr")).isNull();
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void shouldRenderOwnedSharedNote_whenNativeRequestCarriesAuthorizedChartContext(String raw) throws Exception {
        capture("7");
        // Another window opened last must not change the originating chart's role policy.
        ChartProgramContext.remember(requestFor("101"), "9");
        when(manager.filterNotes(user, "4249", List.of(note), "7")).thenReturn(List.of(note));
        request.setParameter("raw", raw);

        assertThat(action.execute()).isEqualTo("displayNote");

        assertThat(request.getAttribute("noteStr")).isEqualTo(note.getNote());
        assertThat(request.getAttribute("raw")).isEqualTo(Boolean.parseBoolean(raw));
        verify(manager).filterNotes(user, "4249", List.of(note), "7");
        verify(security).hasPrivilege(user, "_eChart", "r", "101");
        verify(security).hasPrivilege(user, "_casemgmt.notes", "r", "101");
        assertThat(note.getProgram_no()).isEqualTo("8");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0"})
    void shouldPreserveLegacyReadPolicy_whenChartHasNoProgram(String program) throws Exception {
        if (program != null) capture(program);
        request.setParameter("demographicNo", "00101", "101");
        request.setParameter("demographic_no", "101");
        request.setParameter("noteId", "051", "51");

        assertThat(action.viewNote()).isEqualTo("displayNote");

        assertThat(request.getAttribute("noteStr")).isEqualTo(note.getNote());
        verify(manager).isClientInProgramDomain("4249", "101");
        verify(manager).getNote("51");
        verifyNoMoreInteractions(manager);
    }
}
