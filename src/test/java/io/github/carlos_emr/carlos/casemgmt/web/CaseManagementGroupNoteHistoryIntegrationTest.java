/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
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
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Real H2 revision-chain and active-link queries feed the actual history action. */
class CaseManagementGroupNoteHistoryIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    private GroupNoteDao groups;
    private CaseManagementNote shared;
    private CaseManagementNote privateRow;
    private CaseManagementNote future;
    private GroupNoteLink link;

    @BeforeEach
    void prepareSharedHistory() {
        GroupNoteDaoImpl dao = new GroupNoteDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        groups = dao;
        privateRow = createNote("1", "Private older revision");
        shared = createNote("1", "Explicitly shared revision");
        future = createNote("1", "Unshared future revision");
        shared.setUuid(privateRow.getUuid());
        future.setUuid(privateRow.getUuid());
        shared.setReporter_caisi_role("1");
        shared.setHistory("Private cumulative prose");
        link = new GroupNoteLink();
        link.setNoteId(shared.getId().intValue());
        link.setDemographicNo(202);
        link.setActive(true);
        groups.persist(link);
        entityManager.flush();
        entityManager.clear();
    }

    private MockHttpServletRequest history(long id, int expectedStatus) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession().setAttribute("userrole", "doctor");
        request.setParameter("demographicNo", "202");
        request.setParameter("noteId", Long.toString(id));
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        Facility facility = new Facility();
        facility.setEnableGroupNotes(true);
        when(user.getCurrentFacility()).thenReturn(facility);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        CaseManagementManager manager = mock(CaseManagementManager.class);
        when(manager.isClientInProgramDomain("999998", "202")).thenReturn(true);
        when(manager.getNote(anyString())).thenAnswer(call -> caseManagementNoteDAO.getNote(Long.parseLong(call.getArgument(0))));
        when(manager.getHistory(anyString())).thenAnswer(call -> caseManagementNoteDAO.getHistory(caseManagementNoteDAO.getNote(Long.parseLong(call.getArgument(0)))));
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq("202"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 202)).thenReturn(true);
        SecUserRoleDao held = mock(SecUserRoleDao.class);
        SecUserRole role = new SecUserRole();
        role.setRoleName("doctor");
        when(held.getUserRoles("999998")).thenReturn(List.of(role));
        RolesManager roles = mock(RolesManager.class);
        Secrole reporter = new Secrole();
        reporter.setRoleName("doctor");
        when(roles.getRole("1")).thenReturn(reporter);
        try (MockedStatic<SpringUtils> beans = mockStatic(SpringUtils.class)) {
            beans.when(() -> SpringUtils.getBean(GroupNoteDao.class)).thenReturn(groups);
            beans.when(() -> SpringUtils.getBean(SecUserRoleDao.class)).thenReturn(held);
            beans.when(() -> SpringUtils.getBean(RolesManager.class)).thenReturn(roles);
            CaseManagementEntry2Action action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
            action.request = request;
            action.response = response;
            action.caseManagementMgr = manager;
            ReflectionTestUtils.setField(action, "securityInfoManager", security);
            assertThat(action.notehistory()).isEqualTo(expectedStatus == 200 ? "showHistory" : "none");
            assertThat(response.getStatus()).isEqualTo(expectedStatus);
        }
        return request;
    }

    @Test
    void shouldRenderOnlyTheLinkedRevision_whenTheDatabaseContainsPrivateAndFutureSiblings() {
        MockHttpServletRequest request = history(shared.getId(), 200);
        @SuppressWarnings("unchecked") List<CaseManagementNote> visible = (List<CaseManagementNote>) request.getAttribute("history");
        assertThat(visible).extracting(CaseManagementNote::getId).containsExactly(shared.getId());
        assertThat(visible.getFirst().getHistory()).isNull();
        assertThat(caseManagementNoteDAO.getNote(shared.getId()).getHistory()).isEqualTo("Private cumulative prose");
        assertThat(caseManagementNoteDAO.getNote(shared.getId()).getDemographic_no()).isEqualTo("1");
    }

    @Test
    void shouldRefuseTheFutureSibling_whenOnlyTheEarlierRevisionIsLinked() {
        assertThat(history(future.getId(), 403).getAttribute("history")).isNull();
    }

    @Test
    void shouldRefuseARevokedLink_whenTheDatabaseLinkBecomesInactive() {
        GroupNoteLink stored = groups.find(link.getId());
        stored.setActive(false);
        entityManager.flush();
        entityManager.clear();
        assertThat(groups.findLinksByDemographic(202)).isEmpty();
        assertThat(history(shared.getId(), 403).getAttribute("history")).isNull();
    }
}
