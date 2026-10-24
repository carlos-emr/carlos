/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;
import io.github.carlos_emr.carlos.PMmodule.utility.ProgramAccessCache;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.casemgmt.service.StaleNoteRevisionException;
import io.github.carlos_emr.carlos.commn.dao.CaseManagementTmpSaveDao;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.HashAuditDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

/**
 * Exercises real revision insertion and real draft deletion in the manager's own transaction.
 * No surrounding test transaction can hide an accidental second-program deletion or rollback.
 *
 * @since 2026-10-24
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CaseManagementNoteDraftCleanupIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("CaseManagementNoteExtDAO") private CaseManagementNoteExtDAO extensions;
    @Autowired @Qualifier("CaseManagementNoteLinkDAO") private CaseManagementNoteLinkDAO links;
    @Autowired @Qualifier("CaseManagementTmpSaveDao") private CaseManagementTmpSaveDao drafts;
    @Autowired private HashAuditDao hashes;

    private final List<Integer> ownedDraftIds = new ArrayList<>();
    private String uuid;
    private CaseManagementNote submitted;
    private CaseManagementManagerImpl target;
    private CaseManagementManager service;
    private MockedStatic<SpringUtils> beans;
    private ProgramManager programs;
    private AdmissionManager admissions;

    @BeforeEach
    void prepareIndependentWindows() {
        uuid = UUID.randomUUID().toString();
        submitted = tx().execute(status -> {
            CaseManagementNote original = new CaseManagementNote();
            original.setUuid(uuid);
            original.setDemographic_no("1");
            original.setProviderNo("999998");
            original.setProgram_no("8");
            original.setReporter_caisi_role("801");
            original.setNote("Original shared assessment");
            original.setHistory("Original shared assessment");
            original.setUpdate_date(new Date(1_700_000_000_000L));
            original.setObservation_date(new Date(1_700_000_000_000L));
            caseManagementNoteDAO.saveNote(original);
            entityManager.flush();
            seed("999998", 1, 7, original.getId(), "Originating chart draft");
            seed("999998", 1, 7, original.getId(), "Earlier originating draft");
            seed("999998", 1, 8, original.getId(), "Other program's independent text");
            seed("999998", 1, 0, original.getId(), "Independent global chart draft");
            seed("999997", 1, 7, original.getId(), "Another provider in origin program");
            seed("999997", 1, 8, original.getId(), "Another provider in note program");
            seed("999998", 2, 7, original.getId(), "Another patient's text");
            CaseManagementNote edit = original.copyForRevision();
            edit.setId(original.getId());
            edit.setRevision("1");
            edit.setNote("Revised shared assessment");
            return edit;
        });

        target = spy(new CaseManagementManagerImpl());
        target.setCaseManagementNoteDAO(caseManagementNoteDAO);
        target.setCaseManagementNoteExtDAO(extensions);
        target.setCaseManagementNoteLinkDAO(links);
        target.setCaseManagementIssueDAO(caseManagementIssueDAO);
        target.setCaseManagementTmpSaveDao(drafts);
        ReflectionTestUtils.setField(target, "hashAuditDao", hashes);
        ReflectionTestUtils.setField(target, "eChartDao", mock(EChartDao.class));
        doReturn("doctor").when(target).getRoleName(any(), any());
        doReturn("Test signed marker").when(target).getSignature(any(), any(), any(), any(), anyInt());
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        service = (CaseManagementManager) proxy.getProxy();

        programs = mock(ProgramManager.class);
        admissions = mock(AdmissionManager.class);
        when(programs.getProgramProvider("999998", "8")).thenReturn(membership(801L, "Stored doctor"));
        // Leave other Spring lookups real so cache class initialization keeps the
        // real DAO references when a readback test installs scoped cache mocks.
        beans = mockStatic(SpringUtils.class, CALLS_REAL_METHODS);
        beans.when(() -> SpringUtils.getBean(ProgramManager.class)).thenReturn(programs);
        beans.when(() -> SpringUtils.getBean(AdmissionManager.class)).thenReturn(admissions);
        beans.when(() -> SpringUtils.getBean(OscarAppointmentDao.class)).thenReturn(mock(OscarAppointmentDao.class));
    }

    @AfterEach
    void removeOnlyOwnedRows() {
        if (beans != null) beans.close();
        tx().executeWithoutResult(status -> {
            if (!ownedDraftIds.isEmpty()) {
                entityManager.createQuery("delete CaseManagementTmpSave d where d.id in :ids")
                        .setParameter("ids", ownedDraftIds).executeUpdate();
            }
            if (uuid != null) {
                entityManager.createQuery("delete HashAudit h where h.type = 'enc' and h.id2 in (select cast(n.id as string) from CaseManagementNote n where n.uuid = :uuid)")
                        .setParameter("uuid", uuid).executeUpdate();
                entityManager.createQuery("delete CaseManagementNote n where n.uuid = :uuid")
                        .setParameter("uuid", uuid).executeUpdate();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "7", "8"})
    void shouldCommitRevisionAndClearOnlyTheSubmittingWindow_whenTheNoteProgramMayDiffer(String origin) throws Exception {
        List<Draft> before = snapshot();

        CaseManagementNote saved = save(origin);

        assertThat(saved.getId()).isNotEqualTo(submitted.getId());
        assertThat(saved.getProgram_no()).isEqualTo("8");
        assertThat(snapshot()).containsExactlyElementsOf(before.stream().filter(d -> !ownedBy(d, origin)).toList());
        assertThat(noteTexts()).containsExactly("Original shared assessment", "Revised shared assessment");
    }

    @Test
    void shouldKeepHistoricalNoteProgramCleanup_forTheExistingRestAndIntegrationSignature() throws Exception {
        when(programs.getProgramProvider("999998", "8")).thenReturn(null);
        List<Draft> before = snapshot();

        CaseManagementNote saved = service.saveCaseManagementNote(mock(LoggedInInfo.class), submitted, List.of(), null, null,
                false, Locale.ENGLISH, new Date(), "Doctor", "999998", "192.0.2.1", "");

        assertThat(saved.getReporter_caisi_role()).isEqualTo("0");
        assertThat(snapshot()).containsExactlyElementsOf(before.stream().filter(d -> !ownedBy(d, "8")).toList());
        assertThat(noteTexts()).hasSize(2);
    }

    @Test
    void shouldPreserveEveryDraft_whenNoOriginatingDraftProgramWasSupplied() throws Exception {
        List<Draft> before = snapshot();

        save(null);

        assertThat(snapshot()).containsExactlyElementsOf(before);
        assertThat(noteTexts()).hasSize(2);
    }

    @Test
    void shouldKeepEveryDraft_whenAStaleEditorCannotSaveItsRevision() {
        tx().executeWithoutResult(status -> {
            CaseManagementNote newer = submitted.copyForRevision();
            newer.setNote("Concurrent revision");
            caseManagementNoteDAO.saveNote(newer);
        });
        List<Draft> before = snapshot();

        assertThatThrownBy(() -> save("7")).isInstanceOf(StaleNoteRevisionException.class);

        assertThat(snapshot()).containsExactlyElementsOf(before);
        assertThat(noteTexts()).containsExactly("Original shared assessment", "Concurrent revision");
    }

    @Test
    void shouldRollBackInsertedRevisionAndPreserveEveryDraft_whenRevisionStorageFails() {
        CaseManagementNoteDAO failingNotes = mock(CaseManagementNoteDAO.class, delegatesTo(caseManagementNoteDAO));
        doAnswer(call -> {
            caseManagementNoteDAO.saveNote(call.getArgument(0));
            entityManager.flush();
            throw new IllegalStateException("Revision storage failed");
        }).when(failingNotes).saveNote(any());
        target.setCaseManagementNoteDAO(failingNotes);
        List<Draft> before = snapshot();

        assertThatThrownBy(() -> save("7")).isInstanceOf(IllegalStateException.class)
                .hasMessage("Revision storage failed");

        assertThat(snapshot()).containsExactlyElementsOf(before);
        assertThat(noteTexts()).containsExactly("Original shared assessment");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRollBackRevisionAndDeletedDraftsAndPermitRetry_whenDraftCleanupFailsAfterItsDatabaseWrite(boolean signed) throws Exception {
        submitted.setSigned(signed);
        if (signed) submitted.setSigning_provider_no("999998");
        CaseManagementTmpSaveDao failingDrafts = mock(CaseManagementTmpSaveDao.class, delegatesTo(drafts));
        doAnswer(call -> {
            drafts.remove(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            entityManager.flush();
            assertThat(snapshotInTransaction()).noneMatch(d -> ownedBy(d, "7"));
            assertThat(entityManager.createQuery("select count(n) from CaseManagementNote n where n.uuid = :uuid", Long.class)
                    .setParameter("uuid", uuid).getSingleResult()).isEqualTo(2L);
            throw new IllegalStateException("Draft cleanup failed");
        }).when(failingDrafts).remove("999998", 1, 7);
        target.setCaseManagementTmpSaveDao(failingDrafts);
        List<Draft> before = snapshot();

        assertThatThrownBy(() -> save("7")).isInstanceOf(IllegalStateException.class)
                .hasMessage("Draft cleanup failed");

        assertThat(snapshot()).containsExactlyElementsOf(before);
        assertThat(noteTexts()).containsExactly("Original shared assessment");
        assertThat(submitted.getRevision()).isEqualTo("1");
        assertThat(submitted.getNote()).isEqualTo("Revised shared assessment");

        target.setCaseManagementTmpSaveDao(drafts);
        CaseManagementNote saved = save("7");

        assertThat(saved.getRevision()).isEqualTo("2");
        assertThat(saved.getNote()).isEqualTo(signed
                ? "Revised shared assessment\nTest signed marker" : "Revised shared assessment");
        assertThat(snapshot()).containsExactlyElementsOf(before.stream().filter(d -> !ownedBy(d, "7")).toList());
        assertThat(noteTexts()).containsExactly("Original shared assessment", saved.getNote());
    }

    @ParameterizedTest
    @CsvSource({"false,save", "false,sign", "false,verify", "true,save", "true,sign", "true,verify"})
    void shouldKeepSharedRevisionReadableWithTheWritersRole_whenMembershipIsOnlyInTheOriginOrInBothPrograms(
            boolean belongsToStoredProgram, String operation) throws Exception {
        ProgramProvider originMembership = membership(701L, "Origin nurse");
        ProgramProvider storedMembership = membership(801L, "Stored doctor");
        when(programs.getProgramProvider("999998", "8")).thenReturn(belongsToStoredProgram ? storedMembership : null);
        when(programs.getProgramProvider("999998", "7")).thenReturn(originMembership);
        Admission storedAdmission = new Admission();
        storedAdmission.setTeamId(83);
        when(admissions.getAdmission("8", 1)).thenReturn(storedAdmission);
        when(programs.hasAccessBasedOnCurrentFacility(any(), eq(8))).thenReturn(true);
        target.setProgramManager(programs);
        ProgramProviderDAO memberships = mock(ProgramProviderDAO.class);
        when(memberships.getProgramProviderByProviderProgramId("999998", 7L)).thenReturn(List.of(originMembership));
        target.setProgramProviderDao(memberships);
        target.setRoleProgramAccessDAO(mock(RoleProgramAccessDAO.class));
        submitted.setSigned("sign".equals(operation));
        if (submitted.isSigned()) submitted.setSigning_provider_no("999998");
        doAnswer(call -> (call.<Integer>getArgument(4) == target.SIGNATURE_VERIFY ? "Verified as " : "Signed as ")
                + call.<String>getArgument(2)).when(target).getSignature(any(), any(), any(), any(), anyInt());
        String expectedRole = belongsToStoredProgram ? "801" : "701";
        String expectedRoleName = belongsToStoredProgram ? "Stored doctor" : "Origin nurse";
        List<Draft> before = snapshot();

        try (MockedStatic<RoleCache> roles = mockStatic(RoleCache.class);
             MockedStatic<ProgramAccessCache> access = mockStatic(ProgramAccessCache.class)) {
            roles.when(() -> RoleCache.getRole(801L)).thenReturn(storedMembership.getRole());
            roles.when(() -> RoleCache.getRole(701L)).thenReturn(originMembership.getRole());
            ProgramAccess sharedRead = new ProgramAccess();
            sharedRead.setAllRoles(true);
            access.when(() -> ProgramAccessCache.getAccessMap(7L)).thenReturn(Map.of("read stored doctor notes", sharedRead));
            // The Y-only provider can already read this explicitly selected X note.
            CaseManagementNote original = tx().execute(status -> caseManagementNoteDAO.getNote(submitted.getId()));
            assertThat(service.filterNotes(null, "999998", List.of(original), "7")).containsExactly(original);

            CaseManagementNote saved = save("7", "verify".equals(operation));

            CaseManagementNote readback = tx().execute(status -> caseManagementNoteDAO.getNote(saved.getId()));
            assertThat(readback.getProgram_no()).isEqualTo("8");
            assertThat(readback.getReporter_program_team()).isEqualTo("83");
            assertThat(readback.getReporter_caisi_role()).isEqualTo(expectedRole);
            assertThat(service.filterNotes(null, "999998", List.of(readback), "7")).containsExactly(readback);
            assertThat(readback.getNote()).isEqualTo("save".equals(operation) ? "Revised shared assessment"
                    : "Revised shared assessment\n" + ("verify".equals(operation) ? "Verified as " : "Signed as ") + expectedRoleName);
            if (!"save".equals(operation)) verify(target).getSignature(eq("999998"), eq("Doctor"), eq(expectedRoleName),
                    eq(Locale.ENGLISH), eq("verify".equals(operation) ? target.SIGNATURE_VERIFY : target.SIGNATURE_SIGNED));
        }
        assertThat(snapshot()).containsExactlyElementsOf(before.stream().filter(d -> !ownedBy(d, "7")).toList());
        assertThat(noteTexts()).hasSize(2);
        verify(admissions, never()).getAdmission("7", 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "missing-role", "missing-id", "blank-name"})
    void shouldRefuseBeforeClinicalWrites_whenNeitherProgramHasAValidWriterRole(String defect) {
        ProgramProvider invalid = switch (defect) {
            case "absent" -> null;
            case "missing-role" -> new ProgramProvider();
            case "missing-id" -> membership(null, "Invalid role");
            default -> membership(801L, " ");
        };
        when(programs.getProgramProvider("999998", "8")).thenReturn(invalid);
        when(programs.getProgramProvider("999998", "7")).thenReturn(null);
        submitted.setSigned(true);
        List<Draft> before = snapshot();

        assertThatThrownBy(() -> save("7")).isInstanceOf(SecurityException.class);

        assertThat(snapshot()).containsExactlyElementsOf(before);
        assertThat(noteTexts()).containsExactly("Original shared assessment");
        assertThat(submitted.getReporter_caisi_role()).isEqualTo("801");
        assertThat(submitted.getRevision()).isEqualTo("1");
        assertThat(submitted.getNote()).isEqualTo("Revised shared assessment");
        verifyNoInteractions(admissions);
        verify(target, never()).saveAndUpdateCaseIssues(anyList());
        verify(target, never()).saveNoteRevision(any(), any(), any(), any(), any(), any());
        verify(target, never()).getSignature(any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldRetainHistoricalRoleFallback_forTheGlobalChartProgram() throws Exception {
        when(programs.getProgramProvider("999998", "8")).thenReturn(null);
        List<Draft> before = snapshot();

        CaseManagementNote saved = save("0");

        assertThat(saved.getReporter_caisi_role()).isEqualTo("0");
        assertThat(saved.getProgram_no()).isEqualTo("8");
        assertThat(snapshot()).containsExactlyElementsOf(before.stream().filter(d -> !ownedBy(d, "0")).toList());
        assertThat(noteTexts()).hasSize(2);
    }

    private static ProgramProvider membership(Long roleId, String roleName) {
        Secrole role = new Secrole();
        role.setId(roleId);
        role.setName(roleName);
        ProgramProvider membership = new ProgramProvider();
        membership.setRole(role);
        return membership;
    }

    private CaseManagementNote save(String origin) throws Exception {
        return save(origin, false);
    }

    private CaseManagementNote save(String origin, boolean verify) throws Exception {
        return service.saveCaseManagementNote(mock(LoggedInInfo.class), submitted, List.of(), null, null,
                verify, Locale.ENGLISH, new Date(), "Doctor", "999998", "192.0.2.1", "", origin);
    }

    private void seed(String provider, int patient, int program, long noteId, String text) {
        CaseManagementTmpSave draft = new CaseManagementTmpSave();
        draft.setProviderNo(provider);
        draft.setDemographicNo(patient);
        draft.setProgramId(program);
        draft.setNoteId(Math.toIntExact(noteId));
        draft.setNote(text);
        draft.setUpdateDate(new Date(1_700_000_000_000L));
        entityManager.persist(draft);
        entityManager.flush();
        ownedDraftIds.add(draft.getId());
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private List<Draft> snapshot() {
        return tx().execute(status -> snapshotInTransaction());
    }

    private List<Draft> snapshotInTransaction() {
        return entityManager.createQuery("from CaseManagementTmpSave d where d.id in :ids order by d.id", CaseManagementTmpSave.class)
                .setParameter("ids", ownedDraftIds).getResultList().stream()
                .map(d -> new Draft(d.getId(), d.getProviderNo(), d.getDemographicNo(), d.getProgramId(),
                        d.getNoteId(), d.getNote(), d.getUpdateDate().getTime())).toList();
    }

    private List<String> noteTexts() {
        return tx().execute(status -> entityManager.createQuery(
                        "select n.note from CaseManagementNote n where n.uuid = :uuid order by n.id", String.class)
                .setParameter("uuid", uuid).getResultList());
    }

    private boolean ownedBy(Draft draft, String program) {
        return draft.provider().equals("999998") && draft.patient() == 1 && draft.program() == Integer.parseInt(program);
    }

    private record Draft(int id, String provider, int patient, int program, int noteId, String note, long updated) { }
}
