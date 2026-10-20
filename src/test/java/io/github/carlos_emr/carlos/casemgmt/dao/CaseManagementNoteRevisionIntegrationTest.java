/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.StaleNoteRevisionException;
import jakarta.persistence.LockModeType;
import org.hibernate.exception.SnapshotIsolationException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.sql.SQLException;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Verifies that chart revisions insert new rows and retain historical associations.
 *
 * @since 2026-10-14
 */
class CaseManagementNoteRevisionIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @Autowired
    @Qualifier("CaseManagementNoteExtDAO")
    private CaseManagementNoteExtDAO extensions;
    @Autowired
    @Qualifier("CaseManagementNoteLinkDAO")
    private CaseManagementNoteLinkDAO links;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldLockCurrentMetadataRows_inTheExistingPrecedenceOrder() {
        CaseManagementNote note = createNote("1", "Metadata locking");
        CaseManagementNoteExt older = new CaseManagementNoteExt();
        older.setNoteId(note.getId());
        older.setKeyVal("startDate");
        older.setValue("2020");
        extensions.save(older);
        CaseManagementNoteExt newer = new CaseManagementNoteExt();
        newer.setNoteId(note.getId());
        newer.setKeyVal("startDate");
        newer.setValue("2021");
        extensions.save(newer);
        CaseManagementNoteLink first = new CaseManagementNoteLink(CaseManagementNoteLink.DOCUMENT, 77L, note.getId());
        CaseManagementNoteLink second = new CaseManagementNoteLink(CaseManagementNoteLink.DOCUMENT, 78L, note.getId());
        links.save(first);
        links.save(second);
        entityManager.flush();
        entityManager.clear();

        var currentExtensions = extensions.getExtByNoteForUpdate(note.getId());
        assertThat(currentExtensions).extracting(CaseManagementNoteExt::getId)
                .containsExactly(newer.getId(), older.getId());
        currentExtensions.forEach(row -> assertThat(entityManager.getLockMode(row)).isEqualTo(LockModeType.PESSIMISTIC_WRITE));
        var currentLinks = links.getLinkByNoteForUpdate(note.getId());
        assertThat(currentLinks).extracting(CaseManagementNoteLink::getId)
                .containsExactly(first.getId(), second.getId());
        currentLinks.forEach(row -> assertThat(entityManager.getLockMode(row)).isEqualTo(LockModeType.PESSIMISTIC_WRITE));
    }

    @Test
    void shouldRollBackInsertedRevisionAndMetadata_whenALaterLockingReadFails() {
        CaseManagementNote original = createNote("1", "Original assessment");
        String uuid = original.getUuid();
        CaseManagementNoteExt extension = new CaseManagementNoteExt();
        extension.setNoteId(original.getId());
        extension.setKeyVal("startDate");
        extension.setValue("2020");
        extensions.save(extension);
        entityManager.flush();
        entityManager.clear();
        CaseManagementNote submitted = caseManagementNoteDAO.getNote(original.getId());
        entityManager.detach(submitted);
        submitted.setNote("Unsaved draft");

        CaseManagementNoteLinkDAO failingLinks = mock(CaseManagementNoteLinkDAO.class);
        when(failingLinks.getLinkByNoteForUpdate(original.getId())).thenThrow(
                new SnapshotIsolationException("snapshot conflict",
                        new SQLException("record changed", "HY000", 1020), "select 1"));
        CaseManagementManagerImpl target = new CaseManagementManagerImpl();
        target.setCaseManagementNoteDAO(caseManagementNoteDAO);
        target.setCaseManagementNoteExtDAO(extensions);
        target.setCaseManagementNoteLinkDAO(failingLinks);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        CaseManagementManager service = (CaseManagementManager) factory.getProxy();

        assertThatThrownBy(() -> service.saveNoteRevisionSimple(submitted))
                .isInstanceOf(SnapshotIsolationException.class);
        Long attemptedId = caseManagementNoteDAO.getMostRecentNote(uuid).getId();
        assertThat(attemptedId).isNotEqualTo(original.getId());
        assertThat(extensions.getExtByNote(attemptedId)).hasSize(1);
        assertThat(submitted.getId()).isEqualTo(original.getId());
        assertThat(submitted.getNote()).isEqualTo("Unsaved draft");

        // Even a caller attempting to commit must lose every write in the failed transaction.
        TestTransaction.flagForCommit();
        assertThatThrownBy(TestTransaction::end).isInstanceOf(UnexpectedRollbackException.class);
        TestTransaction.start();
        assertThat(caseManagementNoteDAO.getMostRecentNote(uuid)).isNull();
        assertThat(extensions.getExtByNote(attemptedId)).isEmpty();
        assertThat(links.getLinkByNote(attemptedId)).isEmpty();
    }

    @Test
    void shouldRetainOriginalAndRelatedRows_whenDetachedEditIsSavedAsRevision() {
        CaseManagementNote original = createNote("1", "Original assessment", new Date(1000));
        original.setSigning_provider_no("999998");
        original.setSigned(true);
        original.setHistory("Original assessment");
        var issue = createCaseManagementIssue("1", createIssue("REVISION", "Revision association"));
        original.getIssues().add(issue);
        caseManagementNoteDAO.saveNote(original);
        Long originalId = original.getId();
        String uuid = original.getUuid();
        CaseManagementNoteExt extension = new CaseManagementNoteExt();
        extension.setNoteId(originalId);
        extension.setKeyVal("startDate");
        extension.setValue("2020");
        extensions.save(extension);
        links.save(new CaseManagementNoteLink(CaseManagementNoteLink.DOCUMENT, 77L, originalId));
        entityManager.flush();
        entityManager.clear();

        CaseManagementNote submitted = caseManagementNoteDAO.getNote(originalId);
        entityManager.detach(submitted);
        submitted.setNote("Amended assessment");
        submitted.setUpdate_date(new Date(5000));
        submitted.setProviderNo("999999");
        submitted.setSigned(false);
        submitted.setSigning_provider_no("");
        CaseManagementManagerImpl service = new CaseManagementManagerImpl();
        service.setCaseManagementNoteDAO(caseManagementNoteDAO);
        service.setCaseManagementNoteExtDAO(extensions);
        service.setCaseManagementNoteLinkDAO(links);
        CaseManagementNote revision = service.saveNoteRevisionSimple(submitted);
        Long revisionId = revision.getId();
        entityManager.flush();
        entityManager.clear();

        CaseManagementNote storedOriginal = caseManagementNoteDAO.getNote(originalId);
        CaseManagementNote storedRevision = caseManagementNoteDAO.getNote(revisionId);
        assertThat(revisionId).isNotEqualTo(originalId);
        assertThat(storedOriginal.getNote()).isEqualTo("Original assessment");
        assertThat(storedOriginal.getProviderNo()).isEqualTo("999998");
        assertThat(storedOriginal.getSigning_provider_no()).isEqualTo("999998");
        assertThat(storedOriginal.isSigned()).isTrue();
        assertThat(storedOriginal.getUpdate_date().getTime()).isEqualTo(1000L);
        assertThat(storedRevision.getNote()).isEqualTo("Amended assessment");
        assertThat(storedRevision.getProviderNo()).isEqualTo("999999");
        assertThat(storedRevision.isSigned()).isFalse();
        assertThat(storedRevision.getUuid()).isEqualTo(uuid);
        assertThat(storedRevision.getRevision()).isEqualTo("2");
        assertThat(storedOriginal.getIssues()).extracting("id").containsExactly(issue.getId());
        assertThat(storedRevision.getIssues()).extracting("id").containsExactly(issue.getId());
        assertThat(caseManagementNoteDAO.getMostRecentNote(uuid).getId()).isEqualTo(revisionId);
        assertThat(extensions.getExtByNote(originalId)).hasSize(1);
        assertThat(extensions.getExtByNote(revisionId)).hasSize(1);
        assertThat(extensions.getExtByNote(revisionId).getFirst().getId())
                .isNotEqualTo(extensions.getExtByNote(originalId).getFirst().getId());
        assertThat(links.getLinkByNote(originalId)).hasSize(1);
        assertThat(links.getLinkByNote(revisionId)).hasSize(1);
    }

    @Test
    void shouldRejectEarlierDetachedEdit_whenAnotherRevisionHasBeenSaved() {
        CaseManagementNote original = createNote("1", "Original assessment");
        Long originalId = original.getId();
        entityManager.flush();
        entityManager.clear();
        CaseManagementNote submitted = caseManagementNoteDAO.getNote(originalId);
        entityManager.detach(submitted);
        CaseManagementManagerImpl service = new CaseManagementManagerImpl();
        service.setCaseManagementNoteDAO(caseManagementNoteDAO);
        service.setCaseManagementNoteExtDAO(extensions);
        service.setCaseManagementNoteLinkDAO(links);
        submitted.setNote("First editor's changes");
        CaseManagementNote latest = service.saveNoteRevisionSimple(submitted);
        entityManager.flush();
        entityManager.clear();
        submitted.setNote("Stale editor's changes");

        assertThatThrownBy(() -> service.saveNoteRevisionSimple(submitted))
                .isInstanceOf(StaleNoteRevisionException.class);
        assertThat(caseManagementNoteDAO.getMostRecentNote(submitted.getUuid()).getId()).isEqualTo(latest.getId());
        assertThat(caseManagementNoteDAO.getMostRecentNote(submitted.getUuid()).getNote()).isEqualTo("First editor's changes");
        assertThat(caseManagementNoteDAO.getNote(originalId).getNote()).isEqualTo("Original assessment");
    }
}
