/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDaoBaseIntegrationTest;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.services.security.RolesManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
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
    @org.springframework.beans.factory.annotation.Autowired
    private DemographicDao demographics;
    @org.springframework.beans.factory.annotation.Autowired
    private SecuserroleDao activeRoleAssignments;
    @org.springframework.beans.factory.annotation.Autowired
    private SecObjPrivilegeDao privileges;

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
        return history(id, expectedStatus, "999998", null);
    }

    private MockHttpServletRequest history(long id, int expectedStatus, String providerNo, SecurityInfoManager authority) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession().setAttribute("userrole", "custom_reader");
        request.setParameter("demographicNo", "202");
        request.setParameter("noteId", Long.toString(id));
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn(providerNo);
        Facility facility = new Facility();
        facility.setEnableGroupNotes(true);
        when(user.getCurrentFacility()).thenReturn(facility);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        CaseManagementManager manager = mock(CaseManagementManager.class);
        when(manager.isClientInProgramDomain(providerNo, "202")).thenReturn(true);
        when(manager.getNote(anyString())).thenAnswer(call -> caseManagementNoteDAO.getNote(Long.parseLong(call.getArgument(0))));
        when(manager.getHistory(anyString())).thenAnswer(call -> caseManagementNoteDAO.getHistory(caseManagementNoteDAO.getNote(Long.parseLong(call.getArgument(0)))));
        SecurityInfoManager security = authority;
        if (security == null) {
            security = mock(SecurityInfoManager.class);
            when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq("202"))).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(user, 202)).thenReturn(true);
        }
        try (MockedStatic<SpringUtils> beans = mockStatic(SpringUtils.class)) {
            beans.when(() -> SpringUtils.getBean(GroupNoteDao.class)).thenReturn(groups);
            beans.when(() -> SpringUtils.getBean(SecObjPrivilegeDao.class)).thenReturn(privileges);
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
    void shouldLeaveTheOwnerRowNullsAndNoteOwnershipUntouched_whenDisplayingAnActualLinkedNote() {
        // Explicit near-MAX_VALUE IDs advance H2's shared identity sequence even after rollback.
        // Let the database allocate this fixture so subsequent integration tests retain ID space.
        String ownerName = java.util.UUID.randomUUID().toString().substring(0, 24);
        entityManager.createNativeQuery("INSERT INTO demographic (first_name,last_name,sex,phone,phone2,chart_no,family_doctor,alias,pronoun,gender) "
                + "VALUES (:name,'Synthetic','F',NULL,NULL,NULL,NULL,NULL,NULL,NULL)")
                .setParameter("name", ownerName).executeUpdate();
        int owner = ((Number) entityManager.createNativeQuery("SELECT demographic_no FROM demographic WHERE first_name=:name")
                .setParameter("name", ownerName).getSingleResult()).intValue();
        entityManager.createNativeQuery("UPDATE casemgmt_note SET demographic_no=:owner WHERE uuid=:uuid")
                .setParameter("owner", owner).setParameter("uuid", shared.getUuid()).executeUpdate();
        entityManager.clear();
        CaseManagementNote source = caseManagementNoteDAO.getNote(shared.getId());
        assertThat(entityManager.contains(source)).isTrue();
        SecUserRoleDao held = mock(SecUserRoleDao.class);
        SecUserRole doctor = new SecUserRole();
        doctor.setRoleName("doctor");
        doctor.setActive(true);
        when(held.getUserRoles("999998")).thenReturn(List.of(doctor));
        RolesManager reporters = mock(RolesManager.class);
        Secrole reporter = new Secrole();
        reporter.setRoleName("doctor");
        when(reporters.getRole("1")).thenReturn(reporter);
        DemographicDao observedDemographics = mock(DemographicDao.class,
                org.mockito.AdditionalAnswers.delegatesTo(demographics));
        CaseManagementView2Action action = mock(CaseManagementView2Action.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(action, "secUserRoleDao", held);
        ReflectionTestUtils.setField(action, "groupNoteDao", groups);
        ReflectionTestUtils.setField(action, "caseManagementNoteDao", caseManagementNoteDAO);
        ReflectionTestUtils.setField(action, "demographicDao", observedDemographics);
        ReflectionTestUtils.setField(action, "roleMgr", reporters);
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        Facility facility = new Facility();
        facility.setEnableGroupNotes(true);
        when(user.getCurrentFacility()).thenReturn(facility);
        List<CaseManagementNote> copies = new ArrayList<>();
        try (var displays = mockConstruction(NoteDisplayLocal.class,
                (display, context) -> copies.add((CaseManagementNote) context.arguments().get(1)))) {
            ArrayList<NoteDisplay> visible = new ArrayList<>();
            ReflectionTestUtils.invokeMethod(action, "addGroupNotes", user, visible, 202, null);
            assertThat(visible).hasSize(1);
            assertThat(copies).hasSize(1);
            assertThat(copies.getFirst()).isNotSameAs(source);
            assertThat(copies.getFirst().getId()).isEqualTo(shared.getId());
            assertThat(copies.getFirst().getDemographic_no()).isEqualTo("202");
            assertThat(copies.getFirst().getRevision()).isEqualTo(source.getRevision()).isEqualTo("3");
            assertThat(copies.getFirst().getRoleName()).isEqualTo(source.getRoleName());
            assertThat(copies.getFirst().getProgramName()).isEqualTo(source.getProgramName());
            verify(displays.constructed().getFirst()).setLocation(Integer.toString(owner));
        }
        assertThat(source.getDemographic_no()).isEqualTo(Integer.toString(owner));
        verify(observedDemographics, never()).getDemographic(anyString());
        entityManager.flush();
        entityManager.clear();
        Object[] ownerFields = (Object[]) entityManager.createNativeQuery(
                        "SELECT phone,phone2,chart_no,family_doctor,alias,pronoun,gender FROM demographic WHERE demographic_no=:owner")
                .setParameter("owner", owner).getSingleResult();
        assertThat(ownerFields).hasSize(7).containsOnlyNulls();
        assertThat(caseManagementNoteDAO.getNote(shared.getId()).getDemographic_no()).isEqualTo(Integer.toString(owner));
    }

    private void assignRole(String providerNo, String name, int active) {
        Secuserrole assignment = new Secuserrole();
        assignment.setProviderNo(providerNo);
        assignment.setRoleName(name);
        assignment.setActiveyn(active);
        assignment.setLastUpdateDate(new java.util.Date());
        entityManager.persist(assignment);
    }

    private void grantRead(String role, String object) {
        SecObjPrivilege grant = new SecObjPrivilege();
        grant.setId(new SecObjPrivilegePrimaryKey(role, object));
        grant.setPrivilege("r");
        grant.setPriority(0);
        entityManager.merge(grant);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldUseOnlyActiveClinicalReadGrants_whenReadingAnActualLinkedRevision(boolean recipientNotesRead) {
        String providerNo = "949199";
        assertThat(activeRoleAssignments.findByProviderNo(providerNo)).isEmpty();
        assignRole(providerNo, "history_active", 1);
        assignRole(providerNo, "doctor", 0);
        grantRead("history_active", "_demographic");
        grantRead("doctor", "_casemgmt.notes");
        if (recipientNotesRead) grantRead("history_active", "_casemgmt.notes");
        entityManager.flush();
        entityManager.clear();
        SecurityInfoManagerImpl authority = new SecurityInfoManagerImpl();
        ReflectionTestUtils.setField(authority, "secUserRoleDao", activeRoleAssignments);
        LoggedInInfo reader = mock(LoggedInInfo.class);
        when(reader.getLoggedInProviderNo()).thenReturn(providerNo);
        assertThat(authority.getRoles(reader)).extracting(Secuserrole::getRoleName).containsExactly("history_active");
        MockHttpServletRequest request = history(shared.getId(), recipientNotesRead ? 200 : 403, providerNo, authority);
        if (recipientNotesRead) {
            @SuppressWarnings("unchecked") List<CaseManagementNote> visible = (List<CaseManagementNote>) request.getAttribute("history");
            assertThat(visible).extracting(CaseManagementNote::getId).containsExactly(shared.getId());
            assertThat(visible.getFirst().getHistory()).isNull();
        } else {
            assertThat(request.getAttribute("history")).isNull();
            assertThat(request.getAttribute("demoName")).isNull();
        }
        assertThat(caseManagementNoteDAO.getNote(shared.getId()).getDemographic_no()).isEqualTo("1");
    }

    @Test
    void shouldReadTheDatabaseLinkedRevision_whenTheReporterRoleIsUnknownAndRecipientReadIsGranted() {
        CaseManagementNote stored = caseManagementNoteDAO.getNote(shared.getId());
        stored.setReporter_caisi_role("2147483000");
        entityManager.flush();
        entityManager.clear();
        MockHttpServletRequest request = history(shared.getId(), 200);
        @SuppressWarnings("unchecked") List<CaseManagementNote> visible = (List<CaseManagementNote>) request.getAttribute("history");
        assertThat(visible).extracting(CaseManagementNote::getId).containsExactly(shared.getId());
        assertThat(visible.getFirst().getReporter_caisi_role()).isEqualTo("2147483000");
        assertThat(visible.getFirst().getRoleName()).isNull();
        assertThat(visible.getFirst().getHistory()).isNull();
        assertThat(caseManagementNoteDAO.getNote(shared.getId()).getDemographic_no()).isEqualTo("1");
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
