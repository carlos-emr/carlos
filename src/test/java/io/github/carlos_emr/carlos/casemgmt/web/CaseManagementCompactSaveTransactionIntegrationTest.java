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

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.utility.ProgramAccessCache;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.*;
import io.github.carlos_emr.carlos.casemgmt.model.*;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.CaseManagementTmpSaveDao;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.dao.HashAuditDao;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.sql.Clob;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

/**
 * Drives the real compact action into a proxied manager and real note/issue/draft DAOs.
 * Service calls commit or roll back themselves; no surrounding test transaction hides partial writes.
 *
 * @since 2026-10-24
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CaseManagementCompactSaveTransactionIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("CaseManagementNoteExtDAO") private CaseManagementNoteExtDAO extensions;
    @Autowired @Qualifier("CaseManagementNoteLinkDAO") private CaseManagementNoteLinkDAO links;
    @Autowired @Qualifier("CaseManagementTmpSaveDao") private CaseManagementTmpSaveDao drafts;
    @Autowired @Qualifier("CaseManagementCPPDAO") private CaseManagementCPPDAO cpps;
    @Autowired private HashAuditDao hashes;

    private final List<Integer> draftIds = new ArrayList<>();
    private final Set<Long> attemptedNoteIds = new HashSet<>();
    private String marker;
    private boolean createdProvider;
    private String ownedProviderName;
    private String submittedText;
    private Long lookupId;
    private Long pendingLookupId;
    private Long originalId;
    private Long caseIssueId;
    private CaseManagementNote original;
    private CaseManagementEntryFormBean form;
    private CaseManagementEntry2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CaseManagementManagerImpl target;
    private CaseManagementManager manager;
    private CasemgmtNoteLockDao locks;
    private CasemgmtNoteLock lock;
    private ProgramManager programs;
    private AdmissionManager admissions;
    private MockedStatic<SpringUtils> beans;
    private MockedStatic<RoleCache> roles;
    private MockedStatic<ProgramAccessCache> access;
    private final Map<String, ProgramAccess> readGrants = new HashMap<>();

    @BeforeEach
    void prepareChartAndIndependentDrafts() {
        marker = "compact-" + UUID.randomUUID();
        submittedText = marker + " revised\n<literal>& unchanged";
        createdProvider = false;
        ownedProviderName = "CMP" + UUID.randomUUID().toString().substring(0, 12);
        Provider author = tx().execute(status -> {
            Provider existing = entityManager.find(Provider.class, "999998");
            if (existing == null) {
                existing = new Provider("999998", ownedProviderName, "doctor", "M", "GP", "Compact");
                existing.setStatus("1");
                entityManager.persist(existing);
                entityManager.flush();
                createdProvider = true;
                entityManager.clear();
                existing = entityManager.find(Provider.class, "999998");
            }
            return existing;
        });
        original = tx().execute(status -> {
            Issue lookup = new Issue();
            lookup.setCode("CMP" + UUID.randomUUID().toString().substring(0, 8));
            lookup.setDescription("Compact transaction issue");
            lookup.setRole("doctor");
            lookup.setType("system");
            entityManager.persist(lookup);
            lookupId = lookup.getId();
            CaseManagementIssue issue = new CaseManagementIssue();
            issue.setDemographic_no(1);
            issue.setIssue_id(lookupId);
            issue.setIssue(lookup);
            issue.setProgram_id(8);
            issue.setType("doctor");
            issue.setUpdate_date(new Date(1_700_000_000_000L));
            entityManager.persist(issue);
            caseIssueId = issue.getId();
            CaseManagementNote source = new CaseManagementNote();
            source.setDemographic_no("1");
            source.setProviderNo("999998");
            source.setProgram_no("8");
            source.setReporter_program_team("83");
            source.setReporter_caisi_role("801");
            source.setUuid(UUID.randomUUID().toString());
            source.setNote(marker + " original");
            source.setHistory("Original compact history");
            source.setRevision("1"); // The database Formula also counts this sole UUID row as revision 1.
            source.setObservation_date(new Date(1_700_000_000_000L));
            source.getIssues().add(issue);
            caseManagementNoteDAO.saveNote(source);
            originalId = source.getId();
            seedDraft("999998", 1, 7, "Originating draft");
            seedDraft("999998", 1, 8, "Other program draft");
            seedDraft("999998", 1, 0, "Global chart draft");
            seedDraft("999997", 1, 7, "Another provider draft");
            seedDraft("999998", 2, 7, "Another patient draft");
            CaseManagementNote selected = source.copyForRevision();
            selected.setId(source.getId());
            selected.setRevision("1");
            return selected;
        });
        target = spy(new CaseManagementManagerImpl());
        target.setCaseManagementNoteDAO(caseManagementNoteDAO);
        target.setCaseManagementNoteExtDAO(extensions);
        target.setCaseManagementNoteLinkDAO(links);
        target.setCaseManagementIssueDAO(caseManagementIssueDAO);
        target.setCaseManagementTmpSaveDao(drafts);
        target.setCaseManagementCPPDAO(cpps);
        target.seteChartDao(mock(io.github.carlos_emr.carlos.commn.dao.EChartDao.class));
        ReflectionTestUtils.setField(target, "hashAuditDao", hashes);
        programs = mock(ProgramManager.class);
        admissions = mock(AdmissionManager.class);
        ProgramProvider originRole = membership(701L, "Origin nurse");
        ProgramProvider storedRole = membership(801L, "Stored doctor");
        when(programs.getProgramProvider("999998", "7")).thenReturn(originRole);
        when(programs.getProgramProvider("999998", "8")).thenReturn(storedRole);
        when(programs.hasAccessBasedOnCurrentFacility(any(), anyInt())).thenReturn(true);
        target.setProgramManager(programs);
        ProgramProviderDAO memberships = mock(ProgramProviderDAO.class);
        when(memberships.getProgramProviderByProviderProgramId("999998", 7L)).thenReturn(List.of(originRole));
        target.setProgramProviderDao(memberships);
        target.setRoleProgramAccessDAO(mock(RoleProgramAccessDAO.class));
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        manager = (CaseManagementManager) proxy.getProxy();
        beans = mockStatic(SpringUtils.class, CALLS_REAL_METHODS);
        beans.when(() -> SpringUtils.getBean(ProgramManager.class)).thenReturn(programs);
        beans.when(() -> SpringUtils.getBean(AdmissionManager.class)).thenReturn(admissions);
        roles = mockStatic(RoleCache.class);
        roles.when(() -> RoleCache.getRole(701L)).thenReturn(originRole.getRole());
        roles.when(() -> RoleCache.getRole(801L)).thenReturn(storedRole.getRole());
        access = mockStatic(ProgramAccessCache.class);
        ProgramAccess allowed = new ProgramAccess();
        allowed.setAllRoles(true);
        readGrants.put("read stored doctor notes", allowed);
        access.when(() -> ProgramAccessCache.getAccessMap(7L)).thenReturn(readGrants);

        action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest("POST", "/CaseManagementEntry");
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        action.caseManagementMgr = manager;
        ReflectionTestUtils.setField(action, "caseManagementIssueDao", caseManagementIssueDAO);
        ReflectionTestUtils.setField(action, "issueDao", issueDAO);
        ReflectionTestUtils.setField(action, "caseManagementNoteDao", caseManagementNoteDAO);
        request.setParameter("demographicNo", "1");
        request.setParameter("nId", originalId.toString());
        request.setParameter("noteTxt", submittedText);
        request.setParameter("obsDate", "14-Oct-2026 10:00");
        request.setParameter("numIssues", "1");
        request.setParameter("issue0", "on");
        request.setParameter("hourOfEncounterTime", "2");
        request.setParameter("minuteOfEncounterTime", "17");
        request.setParameter("hourOfEncTransportationTime", "1");
        request.setParameter("minuteOfEncTransportationTime", "29");
        request.getSession().setAttribute("userrole", "doctor");
        ChartProgramContext.remember(request, "7");
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(login.getLoggedInProvider()).thenReturn(author);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(login, "_eChart", "w", "1")).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", "1")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 1)).thenReturn(true);
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        locks = mock(CasemgmtNoteLockDao.class);
        ReflectionTestUtils.setField(action, "casemgmtNoteLockDao", locks);
        lock = new CasemgmtNoteLock();
        lock.setId(10L);
        lock.setNoteId(originalId);
        lock.setSessionId(request.getSession().getId());
        when(locks.find(10L)).thenReturn(lock);
        request.getSession().setAttribute("casemgmtNoteLock1", lock);
        form = new CaseManagementEntryFormBean();
        form.setCaseNote(original);
        CaseManagementIssue staged = tx().execute(status -> {
            CaseManagementIssue stored = entityManager.find(CaseManagementIssue.class, caseIssueId);
            CaseManagementIssue copy = new CaseManagementIssue(stored);
            copy.setId(stored.getId());
            copy.setProgram_id(stored.getProgram_id());
            copy.setNotes(new HashSet<>());
            copy.setResolved(true);
            return copy;
        });
        CheckBoxBean checkbox = new CheckBoxBean();
        checkbox.setIssue(staged);
        checkbox.setChecked("off");
        form.setIssueCheckList(new ArrayList<>(List.of(checkbox)));
        request.getSession().setAttribute(ChartProgramContext.formKey(request, "1"), form);
        WebApplicationContext spring = mock(WebApplicationContext.class);
        when(spring.getBean(AdmissionManager.class)).thenReturn(admissions);
        doReturn(spring).when(action).getSpringContext();
        Admission admission = new Admission();
        admission.setTeamId(73);
        when(admissions.getAdmission("7", 1)).thenReturn(admission);
        Admission storedAdmission = new Admission();
        storedAdmission.setTeamId(83);
        when(admissions.getAdmission("8", 1)).thenReturn(storedAdmission);
    }

    @AfterEach
    void removeOnlyOwnedFixtures() {
        if (access != null) access.close();
        if (roles != null) roles.close();
        if (beans != null) beans.close();
        tx().executeWithoutResult(status -> {
            for (Long id : attemptedNoteIds) entityManager.createQuery("delete HashAudit h where h.type='enc' and h.id2=:id")
                    .setParameter("id", id.toString()).executeUpdate();
            if (!draftIds.isEmpty()) entityManager.createQuery("delete CaseManagementTmpSave d where d.id in :ids")
                    .setParameter("ids", draftIds).executeUpdate();
            List<CaseManagementNote> notes = entityManager.createQuery("from CaseManagementNote n where n.note like :marker", CaseManagementNote.class)
                    .setParameter("marker", marker + "%").getResultList();
            for (CaseManagementNote note : notes) {
                entityManager.createQuery("delete HashAudit h where h.type = 'enc' and h.id2 = :id")
                        .setParameter("id", note.getId().toString()).executeUpdate();
                entityManager.remove(note);
            }
            entityManager.flush();
            if (pendingLookupId != null) entityManager.createQuery("delete CaseManagementIssue i where i.issue_id=:lookup")
                    .setParameter("lookup", pendingLookupId).executeUpdate();
            if (caseIssueId != null) entityManager.remove(entityManager.find(CaseManagementIssue.class, caseIssueId));
            entityManager.flush();
            if (pendingLookupId != null) entityManager.remove(entityManager.find(Issue.class, pendingLookupId));
            if (lookupId != null) entityManager.remove(entityManager.find(Issue.class, lookupId));
            entityManager.flush();
            if (createdProvider) {
                Provider owned = entityManager.find(Provider.class, "999998");
                assertThat(owned).isNotNull();
                assertThat(owned.getLastName()).isEqualTo(ownedProviderName);
                assertThat(owned.getFirstName()).isEqualTo("Compact");
                entityManager.remove(owned);
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"true,false,false", "true,false,true", "true,true,false", "true,true,true",
            "false,false,false", "false,false,true", "false,true,false", "false,true,true"})
    void shouldCommitSharedCompactRevisionWithoutMovingItsProgramOrTeam(boolean memberOfStoredProgram, boolean signed, boolean legacy) throws Exception {
        if (!memberOfStoredProgram) when(programs.getProgramProvider("999998", "8")).thenReturn(null);
        setSigned(signed);
        useOrigin(legacy);
        Snapshot before = snapshot();

        assertThat(action.ajaxsave()).isEqualTo("issueList_ajax");

        assertSuccessfulCompactSave(before, memberOfStoredProgram ? "801" : "701", signed);
        assertThat(original.getNote()).isEqualTo(marker + " original");
        assertThat(original.getProgram_no()).isEqualTo("8");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturn409WithoutIssueOrDraftWrites_whenTheSelectedRevisionBecameStale(boolean signed) throws Exception {
        setSigned(signed);
        tx().executeWithoutResult(status -> {
            CaseManagementNote concurrent = original.copyForRevision();
            concurrent.setNote(marker + " concurrent");
            caseManagementNoteDAO.saveNote(concurrent);
        });
        Snapshot before = snapshot();
        CheckBoxBean retained = form.getIssueCheckList().getFirst();

        assertThat(action.ajaxsave()).isNull();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(form.getCaseNote()).isSameAs(original);
        assertThat(form.getIssueCheckList().getFirst()).isSameAs(retained);
        assertThat(retained.getChecked()).isEqualTo("off");
        verify(locks, never()).merge(any());
        verify(target, never()).saveAndUpdateCaseIssues(anyList());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void shouldRollBackIssueRevisionHashAndDeletedDrafts_thenRetryTheSameEditorAfterCleanupFailure(boolean signed, boolean legacyUuid) throws Exception {
        setSigned(signed);
        if (legacyUuid) {
            original.setUuid(null);
            tx().executeWithoutResult(status -> entityManager.createQuery("update CaseManagementNote n set n.uuid=null where n.id=:id")
                    .setParameter("id", originalId).executeUpdate());
        }
        Snapshot before = snapshot();
        CheckBoxBean retained = form.getIssueCheckList().getFirst();
        CaseManagementTmpSaveDao failing = mock(CaseManagementTmpSaveDao.class, delegatesTo(drafts));
        doAnswer(call -> {
            drafts.remove(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            entityManager.flush();
            assertThat(raw("SELECT * FROM casemgmt_tmpsave WHERE provider_no='999998' AND demographic_no=1 AND program_id=7")).isEmpty();
            assertThat(entityManager.find(CaseManagementIssue.class, caseIssueId).isResolved()).isTrue();
            assertThat(entityManager.createQuery("select count(n) from CaseManagementNote n where n.note like :marker", Long.class)
                    .setParameter("marker", marker + "%").getSingleResult()).isEqualTo(2L);
            attemptedNoteIds.add(entityManager.createQuery("select max(n.id) from CaseManagementNote n where n.note like :marker", Long.class)
                    .setParameter("marker", marker + "%").getSingleResult());
            throw new IllegalStateException("Compact cleanup failed");
        }).when(failing).remove("999998", 1, 7);
        target.setCaseManagementTmpSaveDao(failing);

        assertThatThrownBy(() -> action.ajaxsave()).isInstanceOf(IllegalStateException.class).hasMessage("Compact cleanup failed");

        assertThat(snapshot()).isEqualTo(before);
        assertThat(form.getCaseNote()).isSameAs(original);
        assertThat(form.getIssueCheckList().getFirst()).isSameAs(retained);
        assertThat(original.getRevision()).isEqualTo("1");
        assertThat(original.getNote()).isEqualTo(marker + " original");
        for (Long id : attemptedNoteIds) {
            Long orphanHashes = tx().execute(status -> entityManager.createQuery(
                            "select count(h) from HashAudit h where h.type='enc' and h.id2=:id", Long.class)
                    .setParameter("id", id.toString()).getSingleResult());
            assertThat(orphanHashes).isZero();
        }
        verify(locks, never()).merge(any());
        target.setCaseManagementTmpSaveDao(drafts);

        assertThat(action.ajaxsave()).isEqualTo("issueList_ajax");

        assertSuccessfulCompactSave(before, "801", signed);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unselected", "foreign", "hidden"})
    void shouldRefuseForgedOrHiddenCompactReferences_withoutChangingClinicalOrEditorState(String boundary) throws Exception {
        if ("hidden".equals(boundary)) readGrants.clear();
        else {
            Long otherId = tx().execute(status -> {
                CaseManagementNote other = original.copyForRevision();
                other.setUuid(UUID.randomUUID().toString());
                other.setDemographic_no("foreign".equals(boundary) ? "2" : "1");
                other.setNote(marker + " " + boundary);
                caseManagementNoteDAO.saveNote(other);
                return other.getId();
            });
            request.setParameter("nId", otherId.toString());
        }
        Snapshot before = snapshot();

        assertThat(action.ajaxsave()).isNull();

        assertThat(response.getStatus()).isEqualTo("hidden".equals(boundary) ? 403 : 409);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(form.getCaseNote()).isSameAs(original);
        assertThat(form.getIssueCheckList().getFirst().getChecked()).isEqualTo("off");
        verify(target, never()).saveCompactNoteRevision(any(), any(), anyList(), any());
        verify(locks, never()).merge(any());
    }

    @ParameterizedTest
    @CsvSource({"7,0", "7,01", "7,0.1", "0,0", "0,01", "0,0.1"})
    void shouldSaveANewCompactEditorInItsOriginProgram_forCanonicalClientIds(String origin, String id) throws Exception {
        original.setId(null);
        request.setParameter("nId", id);
        ChartProgramContext.remember(request, origin);
        request.getSession().setAttribute(ChartProgramContext.formKey(request, "1"), form);
        Snapshot before = snapshot();

        assertThat(action.ajaxsave()).isEqualTo("issueList_ajax");

        assertThat(response.getStatus()).isEqualTo(200);
        CaseManagementNote saved = form.getCaseNote();
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getProgram_no()).isEqualTo(origin);
        assertThat(saved.getReporter_program_team()).isEqualTo("0".equals(origin) ? "0" : "73");
        assertThat(saved.getReporter_caisi_role()).isEqualTo("0".equals(origin) ? "0" : "701");
        assertThat(saved.getNote()).isEqualTo(submittedText);
        assertThat(saved.getUuid()).isNotBlank();
        assertThat(saved.getUuid()).isNotEqualTo(tx().execute(status -> caseManagementNoteDAO.getNote(originalId).getUuid()));
        assertThat(snapshot().notes().getFirst()).isEqualTo(before.notes().getFirst());
        assertTimes(saved);
        tx().executeWithoutResult(status -> assertTimes(caseManagementNoteDAO.getNote(saved.getId())));
        assertThat(snapshot().identities()).isEqualTo(before.identities());
        String removed = marker + ("0".equals(origin) ? " Global chart draft" : " Originating draft");
        assertThat(snapshot().drafts()).containsExactlyElementsOf(before.drafts().stream().filter(row -> !row.contains(removed)).toList());
    }

    private void assertSuccessfulCompactSave(Snapshot before, String role, boolean signed) {
        assertThat(response.getStatus()).isEqualTo(200);
        CaseManagementNote saved = form.getCaseNote();
        assertThat(saved.getId()).isNotEqualTo(originalId);
        assertThat(saved.getProgram_no()).isEqualTo("8");
        assertThat(saved.getReporter_program_team()).isEqualTo("83");
        assertThat(saved.getReporter_caisi_role()).isEqualTo(role);
        assertThat(saved.getUuid()).isNotBlank();
        assertThat(saved.getRevision()).isEqualTo("2");
        assertThat(saved.getNote()).isEqualTo(submittedText);
        assertThat(saved.getHistory()).containsOnlyOnce("---------History Record---------").endsWith("Original compact history");
        assertThat(saved.isSigned()).isEqualTo(signed);
        Snapshot after = snapshot();
        assertThat(after.identities()).isEqualTo(before.identities());
        assertThat(after.drafts()).containsExactlyElementsOf(otherDrafts(before));
        assertThat(after.notes()).hasSize(2);
        if (original.getUuid() == null) {
            List<Integer> changed = java.util.stream.IntStream.range(0, before.notes().getFirst().size())
                    .filter(i -> !Objects.equals(before.notes().getFirst().get(i), after.notes().getFirst().get(i)))
                    .boxed().toList();
            assertThat(changed).singleElement().satisfies(i -> {
                assertThat(before.notes().getFirst().get(i)).isNull();
                assertThat(after.notes().getFirst().get(i)).isEqualTo(saved.getUuid());
            });
        } else assertThat(after.notes().getFirst()).isEqualTo(before.notes().getFirst());
        tx().executeWithoutResult(status -> {
            CaseManagementNote persisted = caseManagementNoteDAO.getNote(saved.getId());
            CaseManagementNote previous = caseManagementNoteDAO.getNote(originalId);
            assertThat(previous.getUuid()).isEqualTo(persisted.getUuid());
            assertThat(previous.getIssues()).extracting(CaseManagementIssue::getId).containsExactly(caseIssueId);
            assertThat(persisted.getProgram_no()).isEqualTo("8");
            assertThat(persisted.getReporter_program_team()).isEqualTo("83");
            assertThat(persisted.getReporter_caisi_role()).isEqualTo(role);
            assertThat(persisted.getNote()).isEqualTo(submittedText);
            assertThat(persisted.getRevision()).isEqualTo("2");
            assertTimes(persisted);
            assertThat(entityManager.find(CaseManagementIssue.class, caseIssueId).isResolved()).isTrue();
            assertThat(persisted.getIssues()).extracting(CaseManagementIssue::getId).containsExactly(caseIssueId);
            assertThat(entityManager.createQuery("select count(h) from HashAudit h where h.type='enc' and h.id2=:id", Long.class)
                    .setParameter("id", saved.getId().toString()).getSingleResult()).isEqualTo(signed ? 1L : 0L);
        });
        assertThat(manager.filterNotes(null, "999998", List.of(saved), "7")).containsExactly(saved);
        assertThat(form.getIssueCheckList().getFirst().getChecked()).isEqualTo("on");
        assertThat(form.getIssueCheckList().getFirst().getIssue().getId()).isEqualTo(caseIssueId);
        verify(target, never()).getSignature(any(), any(), any(), any(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldClearExplicitlyBlankDurationsAndPreserveAbsentFields_withoutChangingTheHistoricalNote(boolean blank) throws Exception {
        tx().executeWithoutResult(status -> {
            CaseManagementNote stored = entityManager.find(CaseManagementNote.class, originalId);
            stored.setHourOfEncounterTime(5);
            stored.setMinuteOfEncounterTime(6);
            stored.setHourOfEncTransportationTime(7);
            stored.setMinuteOfEncTransportationTime(8);
        });
        original.setHourOfEncounterTime(5);
        original.setMinuteOfEncounterTime(6);
        original.setHourOfEncTransportationTime(7);
        original.setMinuteOfEncTransportationTime(8);
        for (String field : List.of("hourOfEncounterTime", "minuteOfEncounterTime",
                "hourOfEncTransportationTime", "minuteOfEncTransportationTime")) {
            if (blank) request.setParameter(field, "");
            else request.removeParameter(field);
        }
        Snapshot before = snapshot();

        assertThat(action.ajaxsave()).isEqualTo("issueList_ajax");

        assertThat(response.getStatus()).isEqualTo(200);
        CaseManagementNote saved = form.getCaseNote();
        assertDurations(saved, blank ? null : 5, blank ? null : 6, blank ? null : 7, blank ? null : 8);
        tx().executeWithoutResult(status -> assertDurations(caseManagementNoteDAO.getNote(saved.getId()),
                blank ? null : 5, blank ? null : 6, blank ? null : 7, blank ? null : 8));
        Snapshot after = snapshot();
        assertThat(after.notes().getFirst()).isEqualTo(before.notes().getFirst());
        assertThat(after.identities()).isEqualTo(before.identities());
        assertThat(after.drafts()).containsExactlyElementsOf(otherDrafts(before));
        assertDurations(original, 5, 6, 7, 8);
    }

    @ParameterizedTest
    @CsvSource({"hourOfEncounterTime,not-a-duration", "minuteOfEncounterTime,not-a-duration",
            "hourOfEncTransportationTime,not-a-duration", "minuteOfEncTransportationTime,not-a-duration",
            "hourOfEncounterTime,' '", "minuteOfEncounterTime,' 2'"})
    void shouldRefuseMalformedDurationBeforeWrites_andKeepTheSelectedEditor(String field, String value) throws Exception {
        request.setParameter(field, value);
        Snapshot before = snapshot();

        assertThat(action.ajaxsave()).isNull();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(form.getCaseNote()).isSameAs(original);
        assertThat(form.getIssueCheckList().getFirst().getChecked()).isEqualTo("off");
        verify(target, never()).saveCompactNoteRevision(any(), any(), anyList(), any());
        verify(locks, never()).merge(any());
    }

    private void assertTimes(CaseManagementNote note) {
        assertDurations(note, 2, 17, 1, 29);
    }

    @ParameterizedTest
    @CsvSource({"save,true", "save,false", "saveAndExit,true", "saveAndExit,false"})
    void shouldClearExplicitlyBlankFullSaveDurationsAndPreserveAbsentFields(String operation, boolean blank) throws Exception {
        prepareFullSave(submittedText);
        seedFullSaveDurations();
        for (String field : List.of("hourOfEncounterTime", "minuteOfEncounterTime",
                "hourOfEncTransportationTime", "minuteOfEncTransportationTime")) {
            if (blank) request.setParameter(field, "");
            else request.removeParameter(field);
        }
        Snapshot before = snapshot();

        assertThat("save".equals(operation) ? action.save() : action.saveAndExit())
                .isEqualTo("save".equals(operation) ? "view" : "windowClose");

        assertThat(response.getStatus()).isEqualTo(200);
        CaseManagementNote saved = form.getCaseNote();
        assertThat(saved.getNote()).isEqualTo(submittedText);
        assertThat(saved.getProgram_no()).isEqualTo("8");
        assertDurations(saved, blank ? null : 5, blank ? null : 6, blank ? null : 7, blank ? null : 8);
        tx().executeWithoutResult(status -> assertDurations(caseManagementNoteDAO.getNote(saved.getId()),
                blank ? null : 5, blank ? null : 6, blank ? null : 7, blank ? null : 8));
        Snapshot after = snapshot();
        assertThat(after.notes().getFirst()).isEqualTo(before.notes().getFirst());
        assertThat(after.issues()).isEqualTo(before.issues());
        assertThat(after.identities()).isEqualTo(before.identities());
        assertThat(after.drafts()).containsExactlyElementsOf(otherDrafts(before));
    }

    @ParameterizedTest
    @CsvSource({"save,hourOfEncounterTime,not-a-duration", "saveAndExit,minuteOfEncounterTime,not-a-duration",
            "save,hourOfEncTransportationTime,not-a-duration", "saveAndExit,minuteOfEncTransportationTime,not-a-duration",
            "save,hourOfEncounterTime,' '", "saveAndExit,minuteOfEncounterTime,' 2'"})
    void shouldRefuseMalformedFullSaveDurationsBeforeSessionMutation_andPublishTheRawRecoveryDraft(
            String operation, String field, String value) throws Exception {
        String raw = "\n" + submittedText + "  ";
        prepareFullSave(raw);
        seedFullSaveDurations();
        request.setParameter(field, value);
        Snapshot before = snapshot();

        assertThat("save".equals(operation) ? action.save() : action.saveAndExit()).isEqualTo("windowCloseError");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(form.getCaseNote()).isSameAs(original);
        assertThat(original.getNote()).isEqualTo(marker + " original");
        assertDurations(original, 5, 6, 7, 8);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo(raw);
        assertThat(request.getAttribute("noteInputRejected")).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute("observationDateRejected")).isNull();
        verify(target, never()).saveCaseManagementNote(any(), any(), anyList(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
        verify(locks, never()).merge(any());
    }

    @ParameterizedTest
    @CsvSource({"true,true,success,false", "true,false,success,false", "false,true,success,false", "false,false,success,false",
            "true,true,stale,false", "true,false,stale,false", "false,true,stale,false", "false,false,stale,false",
            "true,true,checked,false", "true,false,checked,false", "false,true,checked,false", "false,false,checked,false",
            "true,false,success,true", "false,false,success,true", "true,false,stale,true", "false,false,stale,true",
            "true,false,checked,true", "false,false,checked,true"})
    void shouldCommitFullIssueChangesWithTheRevisionOrRollThemBackBeforeRetry(
            boolean modern, boolean freshIssue, String scenario, boolean readOnly) throws Exception {
        CheckBoxBean input = prepareFullIssueSave(modern, freshIssue);
        if (readOnly) {
            input.getIssueDisplay().setWriteAccess(false);
            request.removeParameter("issueCheckList[0].checked"); // A disabled native checkbox is not serialized.
        }
        if (!modern && !freshIssue) {
            // Legacy markup also omits the checked attribute for enabled server-selected issues.
            request.removeParameter("issueCheckList[0].checked");
        }
        CaseManagementIssue inputIssue = input.getIssue();
        if ("stale".equals(scenario)) {
            tx().executeWithoutResult(status -> {
                CaseManagementNote concurrent = original.copyForRevision();
                concurrent.setNote(marker + " concurrent");
                caseManagementNoteDAO.saveNote(concurrent);
            });
        }
        Snapshot before = snapshot();
        IOException failure = new IOException("Checked failure after full issue save and draft deletion");
        if ("checked".equals(scenario)) {
            doAnswer(call -> {
                entityManager.flush();
                assertThat(raw("SELECT * FROM casemgmt_tmpsave WHERE provider_no='999998' AND demographic_no=1 AND program_id=7")).isEmpty();
                CaseManagementIssue attempted = caseManagementIssueDAO.getIssuebyId("1", String.valueOf(inputIssue.getIssue_id()));
                assertThat(attempted).isNotNull();
                assertThat(attempted.isAcute()).isEqualTo(!readOnly);
                assertThat(attempted.isCertain()).isEqualTo(!readOnly);
                assertThat(attempted.isMajor()).isEqualTo(!readOnly);
                assertThat(attempted.isResolved()).isEqualTo(!readOnly);
                throw failure;
            }).when(target).getLinkByTableIdDesc(CaseManagementNoteLink.CASEMGMTNOTE, originalId);
            assertThatThrownBy(() -> action.save()).isSameAs(failure);
            assertThat(snapshot()).isEqualTo(before);
            assertThat(input.getIssue()).isSameAs(inputIssue);
            assertThat(inputIssue.getId()).isEqualTo(freshIssue ? null : caseIssueId);
            doCallRealMethod().when(target).getLinkByTableIdDesc(CaseManagementNoteLink.CASEMGMTNOTE, originalId);
        }
        if ("stale".equals(scenario)) {
            assertThat(action.save()).isEqualTo("windowCloseError");
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(snapshot()).isEqualTo(before);
            assertThat(input.getIssue()).isSameAs(inputIssue);
            assertThat(inputIssue.getId()).isEqualTo(freshIssue ? null : caseIssueId);
            verify(locks, never()).merge(any());
            return;
        }

        assertThat(action.save()).isEqualTo("view");

        assertThat(response.getStatus()).isEqualTo(200);
        CaseManagementNote saved = form.getCaseNote();
        assertThat(saved.getProgram_no()).isEqualTo("8");
        assertThat(saved.getReporter_program_team()).isEqualTo("83");
        assertThat(saved.getRevision()).isEqualTo("2");
        assertThat(input.getIssue().getId()).isNotNull();
        assertThat(input.getIssue().getId()).isPositive();
        assertThat(input.getIssue().getProgram_id()).isEqualTo(freshIssue ? 7 : 8);
        tx().executeWithoutResult(status -> {
            CaseManagementNote reloaded = caseManagementNoteDAO.getNote(saved.getId());
            assertThat(reloaded.getIssues()).hasSize(1);
            CaseManagementIssue savedIssue = reloaded.getIssues().iterator().next();
            assertThat(savedIssue.getIssue_id()).isEqualTo(inputIssue.getIssue_id());
            assertThat(savedIssue.getId()).isEqualTo(input.getIssue().getId());
            assertThat(savedIssue.getProgram_id()).isEqualTo(freshIssue ? 7 : 8);
            assertThat(savedIssue.isAcute()).isEqualTo(!readOnly);
            assertThat(savedIssue.isCertain()).isEqualTo(!readOnly);
            assertThat(savedIssue.isMajor()).isEqualTo(!readOnly);
            assertThat(savedIssue.isResolved()).isEqualTo(!readOnly);
            assertThat(caseManagementNoteDAO.getNote(originalId).getIssues()).extracting(CaseManagementIssue::getId)
                    .containsExactly(caseIssueId);
            assertThat(entityManager.createQuery("select count(i) from CaseManagementIssue i where i.demographic_no=1 and i.issue_id=:lookup", Long.class)
                    .setParameter("lookup", inputIssue.getIssue_id()).getSingleResult()).isEqualTo(1L);
        });
        Snapshot after = snapshot();
        assertThat(after.notes().getFirst()).isEqualTo(before.notes().getFirst());
        if (readOnly) assertThat(after.issues()).isEqualTo(before.issues());
        assertThat(after.identities()).isEqualTo(before.identities());
        assertThat(after.drafts()).containsExactlyElementsOf(otherDrafts(before));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveLegacyNullIssueProgramForANewGlobalChartIssue(boolean modern) throws Exception {
        prepareFullIssueSave(modern, true);
        ChartProgramContext.remember(request, "0");
        request.getSession().setAttribute(ChartProgramContext.formKey(request, "1"), form);
        List<List<String>> draftsBefore = snapshot().drafts();

        assertThat(action.save()).isEqualTo("view");

        tx().executeWithoutResult(status -> assertThat(caseManagementNoteDAO.getNote(form.getCaseNote().getId()).getIssues())
                .singleElement().satisfies(issue -> assertThat(issue.getProgram_id()).isNull()));
        assertThat(snapshot().drafts()).containsExactlyElementsOf(draftsBefore.stream()
                .filter(row -> !row.contains(marker + " Global chart draft")).toList());
        assertThat(form.getCaseNote().getProgram_no()).isEqualTo("8");
    }

    @Test
    void shouldDeselectAWritableModernIssueWithoutRemovingItsHistoricalAssociation() throws Exception {
        CheckBoxBean checkbox = prepareFullIssueSave(true, false);
        checkbox.getIssueDisplay().setAcute("chronic");
        checkbox.getIssueDisplay().setCertain("uncertain");
        checkbox.getIssueDisplay().setMajor("not major");
        checkbox.getIssueDisplay().setResolved("unresolved");
        // A writable native checkbox that the user unchecks is absent from Form.serialize.
        request.removeParameter("issueCheckList[0].checked");
        Snapshot before = snapshot();

        assertThat(action.save()).isEqualTo("view");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(checkbox.getChecked()).isEqualTo("off");
        CaseManagementNote saved = form.getCaseNote();
        assertThat(saved.getProgram_no()).isEqualTo("8");
        assertThat(saved.getReporter_program_team()).isEqualTo("83");
        tx().executeWithoutResult(status -> {
            assertThat(caseManagementNoteDAO.getNote(saved.getId()).getIssues()).isEmpty();
            assertThat(caseManagementNoteDAO.getNote(originalId).getIssues()).extracting(CaseManagementIssue::getId)
                    .containsExactly(caseIssueId);
            CaseManagementIssue retained = entityManager.find(CaseManagementIssue.class, caseIssueId);
            assertThat(retained).isNotNull();
            assertThat(retained.isAcute()).isFalse();
            assertThat(retained.isCertain()).isFalse();
            assertThat(retained.isMajor()).isFalse();
            assertThat(retained.isResolved()).isFalse();
        });
        Snapshot after = snapshot();
        assertThat(after.notes().getFirst()).isEqualTo(before.notes().getFirst());
        assertThat(after.identities()).isEqualTo(before.identities());
        assertThat(after.drafts()).containsExactlyElementsOf(otherDrafts(before));
    }

    private CheckBoxBean prepareFullIssueSave(boolean modern, boolean freshIssue) {
        prepareFullSave(submittedText);
        request.getSession().setAttribute("newCaseManagement", String.valueOf(modern));
        Issue catalog = tx().execute(status -> {
            if (!freshIssue) return entityManager.find(Issue.class, lookupId);
            Issue created = new Issue();
            created.setCode("PND" + UUID.randomUUID().toString().substring(0, 8));
            created.setDescription("Pending full save issue");
            created.setRole("doctor");
            created.setType("system");
            entityManager.persist(created);
            entityManager.flush();
            pendingLookupId = created.getId();
            return created;
        });
        CaseManagementIssue inputIssue = new CaseManagementIssue();
        inputIssue.setId(freshIssue ? null : caseIssueId);
        inputIssue.setDemographic_no(1);
        inputIssue.setIssue_id(catalog.getId());
        inputIssue.setIssue(catalog);
        inputIssue.setProgram_id(freshIssue ? 7 : 8);
        inputIssue.setType("doctor");
        CheckBoxBean checkbox = new CheckBoxBean();
        checkbox.setIssue(inputIssue);
        checkbox.setChecked("on");
        var display = checkbox.getIssueDisplay();
        display.setCode(catalog.getCode());
        display.setDescription(catalog.getDescription());
        display.setWriteAccess(true);
        display.setAcute("acute");
        display.setCertain("certain");
        display.setMajor("major");
        display.setResolved("resolved");
        form.setIssueCheckList(new ArrayList<>(List.of(checkbox)));
        request.setParameter("issueCheckList[0].checked", "on");
        return checkbox;
    }

    private void prepareFullSave(String rawNote) {
        form.setIssueCheckList(new ArrayList<>());
        request.getSession().setAttribute("newCaseManagement", "true");
        request.setParameter("caseNote_note", rawNote);
        action.setObservation_date("14-Oct-2026 10:00");
        doReturn(rawNote).when(action).getCaseNote_note();
        doReturn("Owned patient").when(action).getDemoName(anyString());
        doReturn("46").when(action).getDemoAge(anyString());
        doReturn("1980-01-02").when(action).getDemoDOB(anyString());
        doReturn("Note saved").when(action).getText("note.saved");
        doNothing().when(action).addActionMessage(anyString());
    }

    private void seedFullSaveDurations() {
        tx().executeWithoutResult(status -> {
            CaseManagementNote stored = entityManager.find(CaseManagementNote.class, originalId);
            stored.setHourOfEncounterTime(5);
            stored.setMinuteOfEncounterTime(6);
            stored.setHourOfEncTransportationTime(7);
            stored.setMinuteOfEncTransportationTime(8);
        });
        original.setHourOfEncounterTime(5);
        original.setMinuteOfEncounterTime(6);
        original.setHourOfEncTransportationTime(7);
        original.setMinuteOfEncTransportationTime(8);
    }

    private void assertDurations(CaseManagementNote note, Integer hours, Integer minutes, Integer transportHours, Integer transportMinutes) {
        assertThat(note.getHourOfEncounterTime()).isEqualTo(hours);
        assertThat(note.getMinuteOfEncounterTime()).isEqualTo(minutes);
        assertThat(note.getHourOfEncTransportationTime()).isEqualTo(transportHours);
        assertThat(note.getMinuteOfEncTransportationTime()).isEqualTo(transportMinutes);
    }

    private void setSigned(boolean signed) {
        original.setSigned(signed);
        tx().executeWithoutResult(status -> entityManager.createQuery("update CaseManagementNote n set n.signed=:signed where n.id=:id")
                .setParameter("signed", signed).setParameter("id", originalId).executeUpdate());
    }

    private void useOrigin(boolean legacy) {
        if (legacy) {
            request.removeAttribute(ChartProgramContext.class.getName() + ".requestContext");
            request.getSession().setAttribute(ChartProgramContext.formKey(request, "1"), form);
        } else {
            request.setParameter(ChartProgramContext.PARAMETER, ChartProgramContext.token(request));
            MockHttpServletRequest other = new MockHttpServletRequest();
            other.setSession(request.getSession());
            other.setParameter("demographicNo", "1");
            ChartProgramContext.remember(other, "8");
        }
    }

    private void seedDraft(String provider, int patient, int program, String text) {
        CaseManagementTmpSave draft = new CaseManagementTmpSave();
        draft.setProviderNo(provider);
        draft.setDemographicNo(patient);
        draft.setProgramId(program);
        draft.setNoteId(Math.toIntExact(originalId));
        draft.setNote(marker + " " + text);
        draft.setUpdateDate(new Date(1_700_000_000_000L));
        entityManager.persist(draft);
        entityManager.flush();
        draftIds.add(draft.getId());
    }

    private TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }

    private Snapshot snapshot() {
        return tx().execute(status -> new Snapshot(
                raw("SELECT * FROM casemgmt_note WHERE note LIKE :marker ORDER BY note_id", Map.of("marker", marker + "%")),
                raw("SELECT * FROM casemgmt_issue WHERE id=" + caseIssueId
                        + (pendingLookupId == null ? "" : " OR issue_id=" + pendingLookupId) + " ORDER BY id"),
                raw("SELECT * FROM casemgmt_tmpsave WHERE id IN (" + String.join(",", draftIds.stream().map(String::valueOf).toList()) + ") ORDER BY id"),
                List.of(raw("SELECT * FROM provider WHERE provider_no IN ('999998','999997') ORDER BY provider_no"),
                        raw("SELECT * FROM demographic WHERE demographic_no IN (1,2) ORDER BY demographic_no"))));
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> raw(String query) {
        return raw(query, Map.of());
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> raw(String query, Map<String, Object> parameters) {
        var statement = entityManager.createNativeQuery(query);
        parameters.forEach(statement::setParameter);
        List<Object[]> rows = statement.getResultList();
        return rows.stream().map(row -> Arrays.stream(row).map(value -> {
            if (value == null) return (String) null;
            try {
                if (value instanceof Clob clob) return clob.getSubString(1, Math.toIntExact(clob.length()));
                if (value instanceof java.sql.Blob blob) return HexFormat.of().formatHex(blob.getBytes(1, Math.toIntExact(blob.length())));
                if (value instanceof byte[] bytes) return HexFormat.of().formatHex(bytes);
                return value.toString();
            }
            catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
        }).toList()).toList();
    }

    private List<List<String>> otherDrafts(Snapshot before) {
        // The run-specific exact text identifies our captured origin row regardless of
        // physical column order; every field of each unrelated row is compared intact.
        return before.drafts().stream().filter(row -> !row.contains(marker + " Originating draft")).toList();
    }

    private static ProgramProvider membership(long id, String name) {
        Secrole role = new Secrole();
        role.setId(id);
        role.setName(name);
        ProgramProvider provider = new ProgramProvider();
        provider.setRole(role);
        return provider;
    }

    private record Snapshot(List<List<String>> notes, List<List<String>> issues,
                            List<List<String>> drafts, List<List<List<String>>> identities) { }
}
