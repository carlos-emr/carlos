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

import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDaoBaseIntegrationTest;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.services.security.RolesManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Real active-link queries must support the visible recipient copy's read-only expansion.
 *
 * @since 2026-10-25
 */
class CaseManagementViewGroupNoteAccessIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    private CaseManagementView2Action action;
    private CaseManagementManager manager;
    private MockHttpServletRequest request;
    private CaseManagementNote note;
    private GroupNoteDao groups;
    private GroupNoteLink link;
    private LoggedInInfo user;

    @BeforeEach
    void prepareRecipientAndPersistedLink() {
        GroupNoteDaoImpl dao = new GroupNoteDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        groups = dao;
        note = createNote("1", "Synthetic owner note <text>\n患者");
        note.setReporter_caisi_role("1");
        note.setProgram_no("8");
        note.setHistory("Original owner history");
        link = new GroupNoteLink();
        link.setNoteId(note.getId().intValue());
        link.setDemographicNo(202);
        link.setActive(true);
        groups.persist(link);
        entityManager.flush();

        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        Facility facility = new Facility();
        facility.setEnableGroupNotes(true);
        when(user.getCurrentFacility()).thenReturn(facility);
        request = new MockHttpServletRequest("POST", "/CaseManagementView");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        request.setParameter("method", "viewNote");
        request.setParameter("demographicNo", "202");
        request.setParameter("noteId", note.getId().toString());
        request.setParameter("raw", "false");
        ChartProgramContext.remember(request, "7");
        request.setParameter(ChartProgramContext.PARAMETER, ChartProgramContext.token(request));

        manager = mock(CaseManagementManager.class);
        when(manager.isClientInProgramDomain("999998", "202")).thenReturn(true);
        when(manager.getNote(note.getId().toString())).thenAnswer(call -> caseManagementNoteDAO.getNote(note.getId()));
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq("202"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 202)).thenReturn(true);
        SecUserRoleDao assignments = mock(SecUserRoleDao.class);
        SecUserRole doctor = new SecUserRole();
        doctor.setRoleName("doctor");
        doctor.setActive(true);
        when(assignments.getUserRoles("999998")).thenReturn(List.of(doctor));
        RolesManager reporters = mock(RolesManager.class);
        Secrole reporter = new Secrole();
        reporter.setRoleName("doctor");
        when(reporters.getRole("1")).thenReturn(reporter);
        action = mock(CaseManagementView2Action.class, CALLS_REAL_METHODS);
        action.request = request;
        action.response = new MockHttpServletResponse();
        action.caseManagementMgr = manager;
        action.securityInfoManager = security;
        ReflectionTestUtils.setField(action, "groupNoteDao", groups);
        ReflectionTestUtils.setField(action, "caseManagementNoteDao", caseManagementNoteDAO);
        ReflectionTestUtils.setField(action, "secUserRoleDao", assignments);
        ReflectionTestUtils.setField(action, "roleMgr", reporters);
    }

    private List<Object> storedRow() {
        entityManager.flush();
        return Arrays.asList((Object[]) entityManager.createNativeQuery(
                "SELECT * FROM casemgmt_note WHERE note_id=:id").setParameter("id", note.getId()).getSingleResult());
    }

    @Test
    void shouldExpandTheActualVisibleGroupNote_withoutChangingItsOwnerOrApplyingAnUnrelatedProgramFilter() throws Exception {
        List<Object> before = storedRow();
        ArrayList<NoteDisplay> visible = new ArrayList<>();
        try (var displays = mockConstruction(NoteDisplayLocal.class)) {
            ReflectionTestUtils.invokeMethod(action, "addGroupNotes", user, visible, 202, null);
            assertThat(visible).containsExactlyElementsOf(displays.constructed());
            assertThat(visible).hasSize(1);
            verify(displays.constructed().getFirst()).setReadOnly(true);
            verify(displays.constructed().getFirst()).setGroupNote(true);
        }

        assertThat(action.execute()).isEqualTo("displayNote");

        assertThat(request.getAttribute("noteStr")).isEqualTo(note.getNote());
        assertThat(request.getAttribute("raw")).isEqualTo(false);
        assertThat(note.getDemographic_no()).isEqualTo("1");
        assertThat(note.getHistory()).isEqualTo("Original owner history");
        assertThat(storedRow()).isEqualTo(before);
        verify(manager, never()).filterNotes(any(), anyString(), anyList(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "other-recipient", "other-note", "edit"})
    void shouldRefuseAnUnsharedOrEditableReference_withRealActiveLinkQueries(String boundary) throws Exception {
        if ("inactive".equals(boundary)) link.setActive(false);
        if ("other-recipient".equals(boundary)) link.setDemographicNo(303);
        if ("other-note".equals(boundary)) link.setNoteId(note.getId().intValue() + 1);
        if ("edit".equals(boundary)) request.setParameter("raw", "true");
        List<Object> before = storedRow();

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);

        assertThat(request.getAttribute("noteStr")).isNull();
        assertThat(storedRow()).isEqualTo(before);
        assertThat(note.getDemographic_no()).isEqualTo("1");
    }
}
