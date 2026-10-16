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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
 * draft-restore signal under one name per login, so opening another chart consumed it.
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
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        LoggedInInfo.setLoggedInInfoIntoSession(session, user);
        manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        // What the action used to fall back to: the other window's chart, opened last.
        session.setAttribute("casemgmt_DemoNo", "202");
    }

    @Test
    void shouldRefuseWithoutWritingAnyPatient_whenADiseaseRegistryAddNamesNoPatient() throws Exception {
        request.setParameter("issue_code", "73");

        assertThat(action.addToDx()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).saveToDx(any(), any(), any(), any(), anyBoolean());
        verify(manager, never()).saveToDx(any(), any(), any());
    }

    @Test
    void shouldRefuseWithoutWritingAnyPatient_whenADiseaseRegistryAddNamesAnInvalidPatient() throws Exception {
        request.setParameter("demographicNo", "202abc");
        request.setParameter("issue_code", "73");

        assertThat(action.addToDx()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).saveToDx(any(), any(), any(), any(), anyBoolean());
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
