/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.model.*;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.casemgmt.service.StaleNoteRevisionException;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.HashAuditDao;
import io.github.carlos_emr.carlos.commn.model.HashAudit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

/**
 * Exercises the actual CPP service through Spring's transaction interceptor and real H2 DAOs.
 * No test transaction surrounds the service call: failure must roll back before it returns.
 *
 * @since 2026-10-15
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CaseManagementCppRevisionTransactionIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    private static final AtomicInteger NEXT_DEMOGRAPHIC = new AtomicInteger(780000);
    @Autowired @Qualifier("CaseManagementNoteExtDAO")
    private CaseManagementNoteExtDAO extensions;
    @Autowired @Qualifier("CaseManagementNoteLinkDAO")
    private CaseManagementNoteLinkDAO links;
    @Autowired @Qualifier("CaseManagementCPPDAO")
    private CaseManagementCPPDAO cpps;
    @Autowired private HashAuditDao hashes;
    @Autowired private EChartDao charts;
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;
    private String demo;
    private String previousAbandonOldChart;
    private final List<Long> lookupIds = new ArrayList<>();

    @BeforeEach
    void prepareTransactionFixture() {
        tx = new TransactionTemplate(transactionManager);
        demo = String.valueOf(NEXT_DEMOGRAPHIC.incrementAndGet());
        previousAbandonOldChart = CarlosProperties.getInstance().getProperty("AbandonOldChart");
        CarlosProperties.getInstance().setProperty("AbandonOldChart", "false");
    }

    @AfterEach
    void removeCommittedFixtures() {
        try {
            tx.executeWithoutResult(status -> {
                List<Long> noteIds = entityManager.createQuery(
                        "select n.id from CaseManagementNote n where n.demographic_no = :demo", Long.class)
                        .setParameter("demo", demo).getResultList();
                for (Long noteId : noteIds) {
                    extensions.getExtByNote(noteId).forEach(entityManager::remove);
                    links.getLinkByNote(noteId).forEach(entityManager::remove);
                    entityManager.createQuery("delete HashAudit h where h.type = :type and h.id2 = :id")
                            .setParameter("type", HashAudit.NOTE).setParameter("id", noteId.toString()).executeUpdate();
                    entityManager.flush();
                    entityManager.remove(entityManager.find(CaseManagementNote.class, noteId));
                }
                entityManager.flush();
                entityManager.createQuery("delete CaseManagementIssue i where i.demographic_no = :demo")
                        .setParameter("demo", Integer.valueOf(demo)).executeUpdate();
                entityManager.createQuery("delete CaseManagementCPP c where c.demographic_no = :demo")
                        .setParameter("demo", demo).executeUpdate();
                entityManager.createQuery("delete EChart c where c.demographicNo = :demo")
                        .setParameter("demo", Integer.valueOf(demo)).executeUpdate();
                // Bulk-deleted case issues must not remain managed while their lookup is removed.
                entityManager.clear();
                lookupIds.forEach(id -> entityManager.remove(entityManager.find(Issue.class, id)));
            });
        } finally {
            if (previousAbandonOldChart == null) CarlosProperties.getInstance().remove("AbandonOldChart");
            else CarlosProperties.getInstance().setProperty("AbandonOldChart", previousAbandonOldChart);
        }
    }

    private CaseManagementManager service(CaseManagementNoteExtDAO extensionDao) {
        CaseManagementManagerImpl target = new CaseManagementManagerImpl();
        target.setCaseManagementNoteDAO(caseManagementNoteDAO);
        target.setCaseManagementNoteExtDAO(extensionDao);
        target.setCaseManagementNoteLinkDAO(links);
        target.setCaseManagementIssueDAO(caseManagementIssueDAO);
        target.setCaseManagementCPPDAO(cpps);
        ReflectionTestUtils.setField(target, "hashAuditDao", hashes);
        ReflectionTestUtils.setField(target, "eChartDao", charts);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (CaseManagementManager) factory.getProxy();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRollBackEveryWriteAndRetrySameDraft_whenExtensionWriteFailsLate(boolean existingNote) {
        Fixture fixture = tx.execute(status -> fixture(existingNote));
        CaseManagementNote draft = draft(fixture);
        List<CaseManagementNoteExt> submitted = List.of(extension("startDate", "2026", null),
                extension("newKey", "new value", java.sql.Date.valueOf("2026-10-15")));
        Map<Long, Integer> positions = Map.of(fixture.siblingId(), 0);
        Snapshot before = tx.execute(status -> snapshot());
        AtomicReference<Long> attemptedNote = new AtomicReference<>();
        AtomicInteger submittedWrites = new AtomicInteger();
        CaseManagementNoteExtDAO failing = mock(CaseManagementNoteExtDAO.class, delegatesTo(extensions));
        doAnswer(call -> {
            CaseManagementNoteExt value = call.getArgument(0);
            extensions.save(value);
            if ("newKey".equals(value.getKeyVal())) {
                attemptedNote.set(value.getNoteId());
                submittedWrites.incrementAndGet();
                entityManager.flush();
                assertThat(extensions.getExtByNote(value.getNoteId())).extracting(CaseManagementNoteExt::getValue)
                        .contains("2026", "new value");
                assertThat(caseManagementNoteDAO.getNote(fixture.siblingId()).getPosition()).isZero();
                assertThat(cpps.getCPP(demo).getSocialHistory()).contains("Atomic signed draft");
                assertThat(charts.getLatestChart(Integer.parseInt(demo)).getEncounter()).contains("Atomic signed draft");
                assertThat(hashCount(value.getNoteId())).isEqualTo(1);
                assertThat(issueCount()).isEqualTo(1);
                throw new IllegalStateException("late extension failure");
            }
            return null;
        }).when(failing).save(any());
        CaseManagementManager manager = service(failing);

        assertThatThrownBy(() -> manager.saveCppNoteRevision(draft, "Doctor", "", positions, submitted))
                .isInstanceOf(IllegalStateException.class).hasMessage("late extension failure");

        assertThat(submittedWrites).hasValue(1);
        Snapshot after = tx.execute(status -> snapshot());
        long attemptedHashes = tx.execute(status -> hashCount(attemptedNote.get()));
        assertThat(after).isEqualTo(before);
        assertThat(attemptedHashes).isZero();
        assertThat(draft.getId()).isEqualTo(fixture.originalId());
        assertThat(draft.getIssues()).allSatisfy(issue -> assertThat(issue.getId()).isNull());
        assertThat(submitted).allSatisfy(extension -> assertThat(extension.getId()).isNull());

        // This is the SAME original-ID/new-note draft and pending issue, not a reconstructed request.
        CaseManagementNote saved = service(extensions).saveCppNoteRevision(draft, "Doctor", "", positions, submitted).note();
        assertThat(saved.getId()).isNotEqualTo(attemptedNote.get());
        tx.executeWithoutResult(status -> assertSuccessfulSave(fixture, saved, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"new", "revision", "archive"})
    void shouldCommitAllCppState_whenSavingNewRevisionOrArchive(String operation) {
        Fixture fixture = tx.execute(status -> fixture(!"new".equals(operation)));
        CaseManagementNote draft = draft(fixture);
        boolean archived = "archive".equals(operation);
        draft.setArchived(archived);
        Map<Long, Integer> positions = fixture.originalId() == null ? Map.of(fixture.siblingId(), 0)
                : Map.of(fixture.siblingId(), 0, fixture.originalId(), 99);

        var result = service(extensions).saveCppNoteRevision(draft, "Doctor", "", positions,
                List.of(extension("startDate", "2026", null), extension("newKey", "new value", java.sql.Date.valueOf("2026-10-15"))));

        assertThat(result.legacyChartText()).contains(draft.getNote());
        tx.executeWithoutResult(status -> assertSuccessfulSave(fixture, result.note(), archived));
        assertThat(draft.getId()).isEqualTo(fixture.originalId());
        assertThat(draft.getIssues()).allSatisfy(issue -> assertThat(issue.getId()).isNull());
    }

    @ParameterizedTest
    @CsvSource({"2,0,1,2", "2,1,0,2", "0,2,0,1"})
    void shouldCommitContiguousPositionsAndPreserveHistory_whenMovingCppRevision(
            int oldPosition, int newPosition, int firstPosition, int secondPosition) {
        Fixture fixture = tx.execute(status -> fixture(true));
        Long secondId = tx.execute(status -> {
            caseManagementNoteDAO.getNote(fixture.originalId()).setPosition(oldPosition);
            caseManagementNoteDAO.getNote(fixture.siblingId()).setPosition(oldPosition == 0 ? 1 : 0);
            CaseManagementNote second = createNote(demo, "Second sibling", new Date(1000));
            second.setPosition(oldPosition == 0 ? 2 : 1);
            return second.getId();
        });
        CaseManagementNote submitted = draft(fixture);
        submitted.setPosition(newPosition);
        CaseManagementNote saved = service(extensions).saveCppNoteRevision(submitted, "Doctor", "",
                Map.of(fixture.siblingId(), firstPosition, secondId, secondPosition), List.of()).note();

        // Read in a separate committed transaction, not from the objects passed into the service.
        tx.executeWithoutResult(status -> {
            assertThat(caseManagementNoteDAO.getMostRecentNote(saved.getUuid()).getPosition()).isEqualTo(newPosition);
            assertThat(caseManagementNoteDAO.getNote(fixture.siblingId()).getPosition()).isEqualTo(firstPosition);
            assertThat(caseManagementNoteDAO.getNote(secondId).getPosition()).isEqualTo(secondPosition);
            assertThat(caseManagementNoteDAO.getNote(fixture.originalId()).getPosition()).isEqualTo(oldPosition);
            assertThat(caseManagementNoteDAO.getNote(fixture.originalId()).getNote()).isEqualTo("Original signed history");
            assertThat(saved.getId()).isNotEqualTo(fixture.originalId());
        });
    }

    @Test
    void shouldClearEveryCurrentTextExtensionAndPreserveHistory_whenSubmittedValueIsEmpty() {
        Fixture fixture = tx.execute(status -> fixture(true));
        tx.executeWithoutResult(status -> {
            for (String value : List.of("First treatment", "Second treatment")) {
                CaseManagementNoteExt historical = extension(CaseManagementNoteExt.TREATMENT, value, null);
                historical.setNoteId(fixture.originalId());
                extensions.save(historical);
            }
        });

        CaseManagementNote saved = service(extensions).saveCppNoteRevision(draft(fixture), "Doctor", "",
                Map.of(), List.of(extension(CaseManagementNoteExt.TREATMENT, "", null))).note();

        tx.executeWithoutResult(status -> {
            assertThat(extensions.getExtByNote(saved.getId()))
                    .filteredOn(e -> CaseManagementNoteExt.TREATMENT.equals(e.getKeyVal()))
                    .hasSize(2).allSatisfy(e -> assertThat(e.getValue()).isEmpty());
            assertThat(extensions.getExtByNote(fixture.originalId()))
                    .filteredOn(e -> CaseManagementNoteExt.TREATMENT.equals(e.getKeyVal()))
                    .extracting(CaseManagementNoteExt::getValue)
                    .containsExactlyInAnyOrder("First treatment", "Second treatment");
            assertThat(extensions.getExtByNote(saved.getId())).filteredOn(e -> "unsubmitted".equals(e.getKeyVal()))
                    .singleElement().satisfies(e -> assertThat(e.getValue()).isEqualTo("keep"));
            assertHistoricalRows(fixture.originalId(), saved, "Second treatment", "First treatment", "keep", "2002", "2001");
        });
    }

    @Test
    void shouldRejectStaleJsonRevision_beforePersistingPendingIssue() {
        Fixture fixture = tx.execute(status -> fixture(true));
        CaseManagementNote draft = draft(fixture);
        CaseManagementManager manager = service(extensions);
        manager.saveNoteRevision(null, draft, draft.getProviderNo(), "Doctor", "", null);
        Snapshot before = tx.execute(status -> snapshot());

        String providerNo = draft.getProviderNo();
        assertThatThrownBy(() -> manager.saveNoteRevision(null, draft, providerNo, "Doctor", "", null))
                .isInstanceOf(StaleNoteRevisionException.class);

        Snapshot after = tx.execute(status -> snapshot());
        assertThat(after).isEqualTo(before);
        assertThat(draft.getIssues()).allSatisfy(issue -> assertThat(issue.getId()).isNull());
    }

    @Test
    void shouldRollBack_whenSiblingPositionBelongsToAnotherPatient() {
        Fixture fixture = tx.execute(status -> fixture(true));
        Long foreignId = tx.execute(status -> createNote("1", "Foreign patient position", new Date(1000)).getId());
        try {
            CaseManagementNote draft = draft(fixture);
            Snapshot before = tx.execute(status -> snapshot());
            CaseManagementManager manager = service(extensions);
            Map<Long, Integer> positions = Map.of(foreignId, 7);
            List<CaseManagementNoteExt> submitted = List.of();

            assertThatThrownBy(() -> manager.saveCppNoteRevision(draft, "Doctor", "", positions, submitted))
                    .isInstanceOf(SecurityException.class);

            Snapshot after = tx.execute(status -> snapshot());
            int foreignPosition = tx.execute(status -> caseManagementNoteDAO.getNote(foreignId).getPosition());
            assertThat(after).isEqualTo(before);
            assertThat(foreignPosition).isZero();
        } finally {
            tx.executeWithoutResult(status -> entityManager.remove(entityManager.find(CaseManagementNote.class, foreignId)));
        }
    }

    private Fixture fixture(boolean existingNote) {
        Issue issue = new Issue();
        issue.setCode("SocHistory");
        issue.setDescription("Social history");
        issue.setRole("doctor");
        issue.setType("system");
        entityManager.persist(issue);
        lookupIds.add(issue.getId());
        CaseManagementNote sibling = createNote(demo, "Untouched sibling", new Date(1000));
        sibling.setPosition(4);
        if (!existingNote) return new Fixture(null, sibling.getId(), issue);
        CaseManagementNote original = createNote(demo, "Original signed history", new Date(1000));
        original.setSigned(true);
        original.setSigning_provider_no("999998");
        original.setHistory("Immutable authored history");
        original.setPosition(3);
        HashAudit hash = new HashAudit();
        hash.setType(HashAudit.NOTE);
        hash.setId2(original.getId().toString());
        hash.makeHash(original.getNote().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        hashes.persist(hash);
        for (CaseManagementNoteExt extension : List.of(extension("startDate", "2001", new Date(1000)),
                extension("startDate", "2002", new Date(2000)), extension("unsubmitted", "keep", null))) {
            extension.setNoteId(original.getId());
            extensions.save(extension);
        }
        links.save(new CaseManagementNoteLink(CaseManagementNoteLink.DOCUMENT, 77L, original.getId()));
        CaseManagementCPP cpp = new CaseManagementCPP();
        cpp.setDemographic_no(demo);
        cpp.setProviderNo("999998");
        cpp.setSocialHistory("Old projection");
        cpps.saveCPP(cpp);
        charts.saveEchart(original, cpp, "Doctor", "");
        return new Fixture(original.getId(), sibling.getId(), issue);
    }

    private CaseManagementNote draft(Fixture fixture) {
        CaseManagementNote draft = fixture.originalId() == null ? new CaseManagementNote()
                : tx.execute(status -> caseManagementNoteDAO.getNote(fixture.originalId()));
        draft.setDemographic_no(demo);
        draft.setProviderNo("999998");
        draft.setNote("Atomic signed draft");
        draft.setSigned(true);
        draft.setSigning_provider_no("999998");
        draft.setPosition(1);
        draft.setUpdate_date(new Date(5000));
        draft.setObservation_date(new Date(5000));
        CaseManagementIssue pending = new CaseManagementIssue();
        pending.setDemographic_no(Integer.parseInt(demo));
        pending.setIssue_id(fixture.issue().getId());
        pending.setIssue(fixture.issue());
        pending.setProgram_id(42);
        pending.setType("system");
        draft.getIssues().add(pending);
        return draft;
    }

    private static CaseManagementNoteExt extension(String key, String value, Date date) {
        CaseManagementNoteExt result = new CaseManagementNoteExt();
        result.setKeyVal(key);
        result.setValue(value);
        result.setDateValue(date);
        return result;
    }

    private void assertSuccessfulSave(Fixture fixture, CaseManagementNote saved, boolean archived) {
        CaseManagementNote current = caseManagementNoteDAO.getMostRecentNote(saved.getUuid());
        assertThat(current.getId()).isEqualTo(saved.getId());
        assertThat(current.getNote()).isEqualTo("Atomic signed draft");
        assertThat(current.isArchived()).isEqualTo(archived);
        assertThat(current.isSigned()).isTrue();
        assertThat(current.getIssues()).hasSize(1);
        CaseManagementIssue issue = current.getIssues().iterator().next();
        assertThat(issue.getId()).isNotNull();
        assertThat(issue.getProgram_id()).isEqualTo(42);
        assertThat(saved.getIssues()).contains(issue);
        Number joins = (Number) entityManager.createNativeQuery("select count(*) from casemgmt_issue_notes where note_id = :id")
                .setParameter("id", saved.getId()).getSingleResult();
        assertThat(joins.intValue()).isEqualTo(1);
        assertThat(hashCount(saved.getId())).isEqualTo(1);
        CaseManagementNote sibling = caseManagementNoteDAO.getNote(fixture.siblingId());
        assertThat(sibling.getPosition()).isZero();
        assertThat(sibling.getNote()).isEqualTo("Untouched sibling");
        assertThat(sibling.getUpdate_date().getTime()).isEqualTo(1000L);
        String projection = cpps.getCPP(demo).getSocialHistory();
        assertThat(projection).contains("\n-----[[").endsWith("]]-----\nAtomic signed draft");
        assertThat(charts.getLatestChart(Integer.parseInt(demo)).getSocialHistory()).isEqualTo(projection);
        assertThat(charts.getLatestChart(Integer.parseInt(demo)).getEncounter()).contains("Atomic signed draft");
        List<CaseManagementNoteExt> currentExtensions = extensions.getExtByNote(saved.getId());
        assertThat(currentExtensions).filteredOn(e -> "startDate".equals(e.getKeyVal()))
                .hasSize(fixture.originalId() == null ? 1 : 2)
                .allSatisfy(e -> { assertThat(e.getValue()).isEqualTo("2026"); assertThat(e.getDateValue()).isNull(); });
        assertThat(currentExtensions).filteredOn(e -> "newKey".equals(e.getKeyVal()))
                .singleElement().satisfies(e -> { assertThat(e.getValue()).isEqualTo("new value"); assertThat(e.getDateValue()).isEqualTo(java.sql.Date.valueOf("2026-10-15")); });
        if (fixture.originalId() != null) assertHistoricalRows(fixture.originalId(), saved, "keep", "2002", "2001");
    }

    private void assertHistoricalRows(Long originalId, CaseManagementNote saved, String... expectedExtensionValues) {
        CaseManagementNote original = caseManagementNoteDAO.getNote(originalId);
        assertThat(original.getNote()).isEqualTo("Original signed history");
        assertThat(original.getHistory()).isEqualTo("Immutable authored history");
        assertThat(original.getUpdate_date().getTime()).isEqualTo(1000L);
        assertThat(original.getPosition()).isEqualTo(3);
        assertThat(original.isArchived()).isFalse();
        assertThat(original.isSigned()).isTrue();
        assertThat(original.getIssues()).isEmpty();
        assertThat(hashCount(originalId)).isEqualTo(1);
        assertThat(extensions.getExtByNote(originalId)).extracting(CaseManagementNoteExt::getValue)
                .containsExactly(expectedExtensionValues);
        assertThat(extensions.getExtByNote(saved.getId())).filteredOn(e -> "unsubmitted".equals(e.getKeyVal()))
                .singleElement().extracting(CaseManagementNoteExt::getValue).isEqualTo("keep");
        assertThat(links.getLinkByNote(originalId)).hasSize(1);
        assertThat(links.getLinkByNote(saved.getId())).extracting(CaseManagementNoteLink::getTableId).containsExactly(77L);
    }

    private long hashCount(Long noteId) {
        return entityManager.createQuery("select count(h) from HashAudit h where h.type = :type and h.id2 = :id", Long.class)
                .setParameter("type", HashAudit.NOTE).setParameter("id", noteId.toString()).getSingleResult();
    }

    private long issueCount() {
        return entityManager.createQuery("select count(i) from CaseManagementIssue i where i.demographic_no = :demo", Long.class)
                .setParameter("demo", Integer.valueOf(demo)).getSingleResult();
    }

    private Snapshot snapshot() {
        List<String> notes = entityManager.createQuery("from CaseManagementNote n where n.demographic_no = :demo order by n.id", CaseManagementNote.class)
                .setParameter("demo", demo).getResultList().stream().map(n -> n.getId() + ":" + n.getUuid() + ":" + n.getNote()
                        + ":" + n.getHistory() + ":" + n.getUpdate_date().getTime() + ":" + n.isSigned() + ":" + n.getPosition()
                        + ":" + n.getIssues().size() + ":" + hashCount(n.getId()) + ":" + extensions.getExtByNote(n.getId()).stream()
                        .map(e -> e.getId() + ":" + e.getKeyVal() + ":" + e.getValue() + ":" + e.getDateValue()).toList()
                        + ":" + links.getLinkByNote(n.getId()).stream().map(l -> l.getId() + ":" + l.getTableId()).toList()).toList();
        CaseManagementCPP cpp = cpps.getCPP(demo);
        List<String> chartState = charts.getChartsForDemographic(Integer.parseInt(demo)).stream()
                .map(c -> c.getId() + ":" + c.getEncounter() + ":" + c.getSocialHistory() + ":" + c.getTimestamp().getTime()).toList();
        return new Snapshot(notes, cpp == null ? null : cpp.getId() + ":" + cpp.getSocialHistory() + ":" + cpp.getUpdate_date().getTime(),
                chartState, issueCount());
    }

    private record Fixture(Long originalId, Long siblingId, Issue issue) { }
    private record Snapshot(List<String> notes, String cpp, List<String> charts, long issues) { }
}
