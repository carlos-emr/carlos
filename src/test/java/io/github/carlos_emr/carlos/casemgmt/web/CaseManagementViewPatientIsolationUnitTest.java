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

import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The chart view action takes its patient from the request alone (issue #4673). It used to fall
 * back to a session attribute holding the chart opened last in any window of the login whenever a
 * request named no patient or an invalid one, so a CPP note list or a note search from one window
 * read the other window's patient and a Disease Registry add wrote to it. It also kept a patient's
 * draft-restore signal under one name per login, so opening another chart consumed it. The
 * Disease Registry add, which no page calls, also answered a GET with no privilege check.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("Case management view patient isolation")
class CaseManagementViewPatientIsolationUnitTest extends CarlosUnitTestBase {
    private CaseManagementView2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;
    private CaseManagementManager manager;
    private SecurityInfoManager security;
    private LoggedInInfo user;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementView2Action.class, CALLS_REAL_METHODS);
        session = new MockHttpSession();
        request = new MockHttpServletRequest();
        request.setSession(session);
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        session.setAttribute("userrole", "doctor");
        session.setAttribute("user", "4249");
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        LoggedInInfo.setLoggedInInfoIntoSession(session, user);
        manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), nullable(String.class)))
                .thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt())).thenReturn(true);
        action.securityInfoManager = security;
        // What the action used to fall back to: the other window's chart, opened last.
        session.setAttribute("casemgmt_DemoNo", "202");
    }

    @Test
    void shouldRestoreSecurityServiceAndRefuseWrite_whenTransientReferenceIsAbsent() {
        registerMock(SecurityInfoManager.class, security);
        action.securityInfoManager = null;
        request.setMethod("POST");
        request.setParameter("demographicNo", "101");
        when(security.hasPrivilege(user, "_dxresearch", SecurityInfoManager.WRITE, "101")).thenReturn(false);

        assertThatThrownBy(action::addToDx).isInstanceOf(SecurityException.class);
        verify(security).hasPrivilege(user, "_dxresearch", SecurityInfoManager.WRITE, "101");
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"GET,save", "HEAD,save", "GET,saveAndExit",
            "HEAD,saveAndExit", "GET,patientCPPSave", "HEAD,patientCPPSave"})
    void shouldRefuseAllCppWrites_whenRequestUsesGetOrHead(String method, String operation) throws Exception {
        request.setMethod(method);
        request.setParameter("demographicNo", "101");
        String result = switch (operation) {
            case "save" -> action.save();
            case "saveAndExit" -> action.saveAndExit();
            default -> action.patientCPPSave();
        };
        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseCppWrite_whenPatientRecordAccessIsDenied() {
        request.setMethod("POST");
        request.setParameter("demographicNo", "101");
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(false);
        assertThatThrownBy(action::save).isInstanceOf(SecurityException.class);
        assertThatThrownBy(action::patientCPPSave).isInstanceOf(SecurityException.class);
        verifyNoInteractions(manager);
    }

    @Test
    void shouldBindCppIdentityFromStorage_whenSubmittedIdentityNamesAnotherPatient() throws Exception {
        request.setMethod("POST");
        request.setParameter("demographicNo", "101");
        var existing = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementCPP();
        existing.setId(11L);
        existing.setDemographic_no("101");
        when(manager.getCPP("101")).thenReturn(existing);
        var submitted = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementCPP();
        submitted.setId(22L);
        submitted.setDemographic_no("202");
        doReturn(submitted).when(action).getCpp();
        action.save();
        assertThat(submitted.getId()).isEqualTo(11L);
        assertThat(submitted.getDemographic_no()).isEqualTo("101");
        verify(manager).saveCPP(submitted, "4249");
    }

    @Test
    void shouldRefuseNoteReads_whenPatientPrivilegeIsDenied() {
        request.setParameter("demographicNo", "101");
        when(security.hasPrivilege(user, "_eChart", "r", "101")).thenReturn(false);
        assertThatThrownBy(action::listNotes).isInstanceOf(SecurityException.class);
        assertThatThrownBy(action::viewNotesOpt).isInstanceOf(SecurityException.class);
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseWithoutWritingAnyPatient_whenADiseaseRegistryAddNamesNoPatient() throws Exception {
        request.setMethod("POST");
        request.setParameter("issue_code", "73");

        assertThat(action.addToDx()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).saveToDx(any(), any(), any(), any(), anyBoolean());
        verify(manager, never()).saveToDx(any(), any(), any());
    }

    @Test
    void shouldRefuseWithoutWritingAnyPatient_whenADiseaseRegistryAddNamesAnInvalidPatient() throws Exception {
        request.setMethod("POST");
        request.setParameter("demographicNo", "202abc");
        request.setParameter("issue_code", "73");

        assertThat(action.addToDx()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).saveToDx(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void shouldRefuseWithoutWriting_whenADiseaseRegistryAddIsNotPosted() throws Exception {
        request.setMethod("GET");
        request.setParameter("demographicNo", "101");
        request.setParameter("issue_code", "73");

        assertThat(action.addToDx()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(manager, never()).saveToDx(any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(security);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"registry write privilege", "patient record access"})
    void shouldRefuseWithoutWriting_whenTheUserMayNotWriteThePatientsRegistry(String deniedBy) {
        request.setMethod("POST");
        request.setParameter("demographicNo", "101");
        request.setParameter("issue_code", "73");
        if ("registry write privilege".equals(deniedBy)) {
            when(security.hasPrivilege(user, "_dxresearch", "w", "101")).thenReturn(false);
        } else {
            when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(false);
        }

        assertThatThrownBy(action::addToDx)
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_dxresearch)");

        verify(manager, never()).saveToDx(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void shouldAddToTheNamedPatientsRegistry_whenPostedByAUserWhoMayWriteIt() throws Exception {
        request.setMethod("POST");
        request.setParameter("demographicNo", "101");
        request.setParameter("issue_code", "73");
        doReturn("view").when(action).view();

        assertThat(action.addToDx()).isEqualTo("view");

        verify(manager).saveToDx(eq(user), eq("101"), eq("73"), nullable(String.class), eq(false));
        verify(manager, never()).saveToDx(any(), eq("202"), any(), any(), anyBoolean());
    }

    @Test
    void shouldRefuseWithoutReadingNotes_whenACppNoteListNamesNoPatient() throws Exception {
        request.setParameter("issue_code", "SocHistory");

        assertThat(action.listNotes()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseWithoutSearching_whenANoteSearchNamesNoPatient() throws Exception {
        assertThat(action.search()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).search(any());
    }

    @Test
    void shouldResolveTheRequestsPatient_whenTheRequestNamesOne() {
        request.setParameter("demographicNo", "0101");

        assertThat(action.getDemographicNo(request)).isEqualTo("101");
        assertThat(session.getAttribute("casemgmt_DemoNo")).isEqualTo("202");
    }

    @Test
    void shouldOfferThisChartsDraftRestore_whenAnotherChartSignalledItsOwnRestore() {
        session.setAttribute(ChartSessionKeys.draftRestoring("202"), "true");

        assertThat(CaseManagementView2Action.offersDraftRestore(session, "101")).isTrue();

        assertThat(session.getAttribute(ChartSessionKeys.draftRestoring("202"))).isEqualTo("true");
    }

    @Test
    void shouldWithholdTheRestoreOnce_whenThisChartsEditorIsRestoringItsDraft() {
        session.setAttribute(ChartSessionKeys.draftRestoring("101"), "true");

        assertThat(CaseManagementView2Action.offersDraftRestore(session, "101")).isFalse();
        assertThat(CaseManagementView2Action.offersDraftRestore(session, "101")).isTrue();
    }
}
