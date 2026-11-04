/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.casemgmt.service.StaleNoteRevisionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Uses the isolated test database, including separate transactions for competing legacy editors.
 *
 * @since 2026-10-15
 */
class CaseManagementLegacyNoteRevisionIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @Autowired
    @Qualifier("CaseManagementNoteExtDAO")
    private CaseManagementNoteExtDAO extensions;
    @Autowired
    @Qualifier("CaseManagementNoteLinkDAO")
    private CaseManagementNoteLinkDAO links;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private CaseManagementManagerImpl service() {
        CaseManagementManagerImpl service = new CaseManagementManagerImpl();
        service.setCaseManagementNoteDAO(caseManagementNoteDAO);
        service.setCaseManagementNoteExtDAO(extensions);
        service.setCaseManagementNoteLinkDAO(links);
        service.setCaseManagementIssueDAO(caseManagementIssueDAO);
        return service;
    }

    private Long legacyNote(String uuid) {
        CaseManagementNote original = createNote("1", "Original assessment", new Date(1000));
        original.setHistory("Original history");
        original.setSigning_provider_no("999998");
        original.setSigned(true);
        entityManager.flush();
        // Simulate the nullable schema/import path rather than saveNote(), which supplies null UUIDs.
        entityManager.createQuery("update CaseManagementNote n set n.uuid = :uuid where n.id = :id")
                .setParameter("uuid", uuid).setParameter("id", original.getId()).executeUpdate();
        entityManager.clear();
        return original.getId();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldInitializeOneChainAndRetainHistoryAndMetadata_whenLegacyDraftIsRevised(String legacyUuid) {
        Long originalId = legacyNote(legacyUuid);
        CaseManagementNoteExt extension = new CaseManagementNoteExt();
        extension.setNoteId(originalId);
        extension.setKeyVal("startDate");
        extension.setValue("2020");
        extensions.save(extension);
        links.save(new CaseManagementNoteLink(CaseManagementNoteLink.DOCUMENT, 77L, originalId));
        entityManager.flush();
        entityManager.clear();
        CaseManagementNote submitted = caseManagementNoteDAO.getNote(originalId);
        CaseManagementNote stale = submitted.copyForRevision();
        stale.setId(originalId);
        entityManager.detach(submitted);
        submitted.setNote("Amended assessment");
        submitted.setSigned(false);
        submitted.setSigning_provider_no("");
        submitted.setUpdate_date(new Date(5000));

        CaseManagementNote revision = service().saveNoteRevisionSimple(submitted);
        entityManager.flush();
        entityManager.clear();

        CaseManagementNote original = caseManagementNoteDAO.getNote(originalId);
        assertThat(UUID.fromString(revision.getUuid())).hasToString(revision.getUuid());
        assertThat(original.getUuid()).isEqualTo(revision.getUuid());
        assertThat(original.getNote()).isEqualTo("Original assessment");
        assertThat(original.getHistory()).isEqualTo("Original history");
        assertThat(original.getUpdate_date().getTime()).isEqualTo(1000L);
        assertThat(original.getObservation_date().getTime()).isEqualTo(1000L);
        assertThat(original.isSigned()).isTrue();
        assertThat(original.getSigning_provider_no()).isEqualTo("999998");
        assertThat(submitted.getUuid()).isEqualTo(legacyUuid);
        assertThat(caseManagementNoteDAO.getNote(revision.getId()).getNote()).isEqualTo("Amended assessment");
        assertThat(extensions.getExtByNote(originalId)).hasSize(1);
        assertThat(extensions.getExtByNote(revision.getId())).extracting(CaseManagementNoteExt::getValue).containsExactly("2020");
        assertThat(links.getLinkByNote(originalId)).hasSize(1);
        assertThat(links.getLinkByNote(revision.getId())).extracting(CaseManagementNoteLink::getTableId).containsExactly(77L);
        CaseManagementManagerImpl manager = service();
        assertThatThrownBy(() -> manager.saveNoteRevisionSimple(stale)).isInstanceOf(StaleNoteRevisionException.class);
        assertThat(caseManagementNoteDAO.getNotesByUUID(revision.getUuid())).extracting(CaseManagementNote::getId)
                .containsExactly(originalId, revision.getId());
    }

    @Test
    void shouldKeepHistoricalAuthorship_whenLegacyInputIsAnEditedManagedEntity() {
        Long originalId = legacyNote(null);
        CaseManagementNote submitted = caseManagementNoteDAO.getNote(originalId);
        assertThat(entityManager.contains(submitted)).isTrue();
        submitted.setNote("Managed draft");
        submitted.setHistory("Edited history");
        submitted.setUpdate_date(new Date(9000));
        submitted.setSigned(false);
        submitted.setSigning_provider_no("");

        CaseManagementNote revision = service().saveNoteRevisionSimple(submitted);
        entityManager.flush();
        entityManager.clear();

        CaseManagementNote original = caseManagementNoteDAO.getNote(originalId);
        assertThat(original.getNote()).isEqualTo("Original assessment");
        assertThat(original.getHistory()).isEqualTo("Original history");
        assertThat(original.getUpdate_date().getTime()).isEqualTo(1000L);
        assertThat(original.isSigned()).isTrue();
        assertThat(original.getSigning_provider_no()).isEqualTo("999998");
        assertThat(original.getUuid()).isEqualTo(revision.getUuid());
        assertThat(caseManagementNoteDAO.getNote(revision.getId()).getNote()).isEqualTo("Managed draft");
        assertThat(submitted.getUuid()).isNull();
        assertThat(submitted.getNote()).isEqualTo("Managed draft");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollBackChainInitialization_whenRevisionMetadataCannotBeCopied() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long originalId = tx.execute(status -> legacyNote(null));
        try {
            CaseManagementNote submitted = tx.execute(status -> caseManagementNoteDAO.getNote(originalId));
            submitted.setSigned(false);
            submitted.setNote("Retryable draft");
            long countBefore = tx.execute(status -> noteCount());
            CaseManagementNoteLinkDAO failingLinks = mock(CaseManagementNoteLinkDAO.class);
            when(failingLinks.getLinkByNoteForUpdate(originalId)).thenThrow(new IllegalStateException("copy failed"));
            CaseManagementManagerImpl manager = service();
            manager.setCaseManagementNoteLinkDAO(failingLinks);

            assertThatThrownBy(() -> tx.execute(status -> manager.saveNoteRevisionSimple(submitted)))
                    .isInstanceOf(IllegalStateException.class).hasMessage("copy failed");
            long countAfter = tx.execute(status -> noteCount());
            String rolledBackUuid = tx.execute(status -> caseManagementNoteDAO.getNote(originalId).getUuid());
            assertThat(countAfter).isEqualTo(countBefore);
            assertThat(rolledBackUuid).isNull();
            assertThat(submitted.getUuid()).isNull();
            // The same submitted draft can retry after rollback; it has no uncommitted chain UUID.
            CaseManagementNote saved = tx.execute(status -> service().saveNoteRevisionSimple(submitted));
            String committedUuid = tx.execute(status -> caseManagementNoteDAO.getNote(originalId).getUuid());
            assertThat(committedUuid).isEqualTo(saved.getUuid());
        } finally {
            tx.executeWithoutResult(status -> removeChain(originalId));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldSerializeLegacyEditors_andRejectTheSecondDraftAfterFirstCommit() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long originalId = tx.execute(status -> legacyNote(null));
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            CaseManagementNote first = tx.execute(status -> caseManagementNoteDAO.getNote(originalId));
            first.setSigned(false);
            first.setNote("First legacy editor");
            CaseManagementNote second = first.copyForRevision();
            second.setId(originalId);
            second.setNote("Stale legacy editor");
            var firstResult = executor.submit(() -> tx.execute(status -> {
                CaseManagementNote saved = service().saveNoteRevisionSimple(first);
                firstWritten.countDown();
                await(allowCommit);
                return saved;
            }));
            assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
            var secondResult = executor.submit(() -> tx.execute(status -> {
                // Establish the old view before attempting the locking read.
                assertThat(caseManagementNoteDAO.getNote(originalId).getUuid()).isNull();
                secondStarted.countDown();
                return service().saveNoteRevisionSimple(second);
            }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> secondResult.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                allowCommit.countDown();
            }
            CaseManagementNote saved = firstResult.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> secondResult.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(StaleNoteRevisionException.class);
            int revisions = tx.execute(status -> caseManagementNoteDAO.getNotesByUUID(saved.getUuid()).size());
            String latestText = tx.execute(status -> caseManagementNoteDAO.getMostRecentNote(saved.getUuid()).getNote());
            assertThat(revisions).isEqualTo(2);
            assertThat(latestText).isEqualTo("First legacy editor");
            assertThat(second.getUuid()).isNull();
        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> removeChain(originalId));
        }
    }

    @Test
    void shouldRollBackPendingIssue_whenStaleRevisionFailsInTheSameOuterTransaction() {
        CaseManagementNote original = createNote("1", "Original assessment");
        entityManager.clear();
        CaseManagementNote stale = caseManagementNoteDAO.getNote(original.getId());
        entityManager.detach(stale);
        service().saveNoteRevisionSimple(stale);
        CaseManagementIssue pending = new CaseManagementIssue();
        var issue = createIssue("PENDING-REVISION", "Pending issue");
        pending.setDemographic_no(1);
        pending.setIssue_id(issue.getId());
        pending.setIssue(issue);
        CaseManagementManagerImpl target = service();
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        CaseManagementManager manager = (CaseManagementManager) factory.getProxy();

        // Exercise the same two service operations under the outer transaction, without mocking
        // their DAOs. This is rollback evidence, not an invocation of the full signing workflow.
        manager.saveAndUpdateCaseIssues(List.of(pending));
        entityManager.flush();
        Long pendingId = pending.getId();
        assertThat(pendingId).isNotNull();
        assertThat(entityManager.find(CaseManagementIssue.class, pendingId)).isNotNull();
        assertThatThrownBy(() -> manager.saveNoteRevisionSimple(stale)).isInstanceOf(StaleNoteRevisionException.class);
        TestTransaction.flagForCommit();
        assertThatThrownBy(TestTransaction::end).isInstanceOf(UnexpectedRollbackException.class);
        TestTransaction.start();
        assertThat(entityManager.find(CaseManagementIssue.class, pendingId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPersistExactlyOnePendingIssueJoin_whenSavingNewNoteOrRevision(boolean isRevision) {
        CaseManagementNote submitted;
        Long originalId = null;
        if (isRevision) {
            originalId = createNote("1", "Original without issues").getId();
            entityManager.clear();
            submitted = caseManagementNoteDAO.getNote(originalId);
            entityManager.detach(submitted);
        } else {
            submitted = new CaseManagementNote();
            submitted.setDemographic_no("1");
            submitted.setProviderNo("999998");
            submitted.setObservation_date(new Date(1000));
        }
        submitted.setNote("Assessment with pending issue");
        CaseManagementIssue pending = new CaseManagementIssue();
        var issue = createIssue("PENDING-JOIN", "Pending issue association");
        pending.setDemographic_no(1);
        pending.setIssue_id(issue.getId());
        pending.setIssue(issue);
        // HashSet membership precedes identity assignment, as in the form action.
        submitted.getIssues().add(pending);
        assertThat(pending.getId()).isNull();
        CaseManagementManagerImpl manager = service();
        manager.saveAndUpdateCaseIssues(List.of(pending));
        assertThat(pending.getId()).isNotNull();
        CaseManagementNote saved = manager.saveNoteRevisionSimple(submitted);
        entityManager.flush();
        entityManager.clear();

        assertThat(caseManagementNoteDAO.getNote(saved.getId()).getIssues())
                .extracting(CaseManagementIssue::getId).containsExactly(pending.getId());
        Number joins = (Number) entityManager.createNativeQuery(
                "select count(*) from casemgmt_issue_notes where note_id = :noteId and id = :issueId")
                .setParameter("noteId", saved.getId()).setParameter("issueId", pending.getId()).getSingleResult();
        assertThat(joins.intValue()).isEqualTo(1);
        if (originalId != null) assertThat(caseManagementNoteDAO.getNote(originalId).getIssues()).isEmpty();
    }

    private long noteCount() {
        return entityManager.createQuery("select count(n) from CaseManagementNote n", Long.class).getSingleResult();
    }

    private void removeChain(Long originalId) {
        CaseManagementNote original = caseManagementNoteDAO.getNote(originalId);
        if (original == null) return;
        List<CaseManagementNote> chain = original.getUuid() == null || original.getUuid().isBlank()
                ? List.of(original) : caseManagementNoteDAO.getNotesByUUID(original.getUuid());
        chain.forEach(entityManager::remove);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("editor coordination timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("editor interrupted", e);
        }
    }
}
