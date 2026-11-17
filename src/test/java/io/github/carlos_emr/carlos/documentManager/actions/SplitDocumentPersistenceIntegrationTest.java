/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** Real JPA transactions and production routing helpers, including the nested lab-routing transaction. */
@Tag("integration")
@Tag("document")
@Isolated("Temporarily binds the legacy EDocUtil document DAO cache to the real integration bean")
class SplitDocumentPersistenceIntegrationTest extends CarlosTestBase {
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DocumentDao documents;
    @Autowired CtlDocumentDao links;
    @Autowired ProviderInboxRoutingDao inbox;
    @Autowired QueueDocumentLinkDao queues;
    @Autowired PatientLabRoutingDao patients;
    @Autowired ProviderLabRoutingDao providers;
    @PersistenceContext(unitName = "entityManagerFactory") EntityManager entityManager;
    @TempDir Path directory;
    private Field cachedDocumentDao;
    private Object previousDocumentDao;

    @BeforeEach void bindRealDocumentDaoAndCreateMigrationOwnedRoutingLockTable() throws Exception {
        // SpringUtils context restoration cannot replace EDocUtil's already cached
        // DAO. Earlier unit tests can leave a mock there whose persist assigns no
        // ID. Keep the actual production helper and real JPA transaction in this
        // regression, and restore the prior cache after each test.
        cachedDocumentDao = EDocUtil.class.getDeclaredField("documentDao");
        cachedDocumentDao.setAccessible(true);
        previousDocumentDao = cachedDocumentDao.get(null);
        cachedDocumentDao.set(null, documents);
        // This lock table is created by the production migration, not an entity
        // mapping, so Hibernate's test schema generation cannot create it.
        entityManager.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)")
                .executeUpdate();
    }

    @AfterEach void restoreLegacyDocumentDao() throws Exception {
        if (cachedDocumentDao != null) cachedDocumentDao.set(null, previousDocumentDao);
    }

    private TransactionTemplate isolatedTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    @Test void failureAfterAllRoutingRollsBackDocumentLinksAndOnlyOwnedPublishedFile() throws Exception {
        Path original = directory.resolve("source.pdf");
        try (PDDocument pdf = new PDDocument()) { pdf.addPage(new PDPage()); pdf.save(original.toFile()); }
        byte[] bytes = Files.readAllBytes(original);
        AtomicInteger newNo = new AtomicInteger();
        try (var prepared = SplitDocumentPdfWork.prepareNow(original, directory, SplitDocumentPdfWork.Operation.SPLIT,
                SplitDocumentPdfWork.selections(new String[]{"1,0"}))) {
            var publication = new SplitDocumentPdfWork.Publication(prepared, original, false);
            assertThatThrownBy(() -> isolatedTransaction().executeWithoutResult(status -> {
                TransactionSynchronizationManager.registerSynchronization(publication);
                try { publication.publish(); } catch (java.io.IOException failure) { throw new UncheckedIOException(failure); }
                EDoc document = new EDoc("test split rollback", "", publication.target.getFileName().toString(), "",
                        "991501", "991501", "", 'A', LocalDate.now().toString(), "", "", "demographic", "-1", 1);
                document.setFileName(publication.target.getFileName().toString());
                document.setDocPublic("0"); document.setContentType("application/pdf");
                newNo.set(Integer.parseInt(EDocUtil.addDocumentSQL(document)));
                inbox.addToProviderInboxStrict("991501", newNo.get(), "DOC");
                queues.addActiveQueueDocumentLink(1, newNo.get());
                PatientLabRouting patient = new PatientLabRouting(); patient.setLabNo(newNo.get());
                patient.setLabType("DOC"); patient.setDemographicNo(991502); patients.persist(patient);
                CtlDocument link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic", 991502, newNo.get()));
                link.setStatus("A"); links.persist(link);
                new ProviderLabRouting().routeMagic(newNo.get(), "991501", "DOC");
                entityManager.flush();
                // Verify helpers really wrote before the deliberately late routing failure.
                assertThat(inbox.getProvidersWithRoutingForDocument("DOC", newNo.get())).isNotEmpty();
                assertThat(providers.getProviderLabRoutingDocuments(newNo.get())).isNotEmpty();
                throw new IllegalStateException("Injected failure after routing");
            })).isInstanceOf(IllegalStateException.class).hasMessage("Injected failure after routing");
            assertThat(newNo.get()).isPositive();
            assertThat(documents.getDocument(String.valueOf(newNo.get()))).isNull();
            assertThat(links.findByDocumentNoAndModule(newNo.get(), "demographic")).isEmpty();
            assertThat(inbox.getProvidersWithRoutingForDocument("DOC", newNo.get())).isEmpty();
            assertThat(queues.getQueueFromDocument(newNo.get())).isEmpty();
            assertThat(patients.findDocByDemographic(newNo.get())).isEmpty();
            assertThat(providers.getProviderLabRoutingDocuments(newNo.get())).isEmpty();
            assertThat(publication.target).doesNotExist();
            assertThat(publication.mutationStarted).isFalse();
        }
        assertThat(Files.readAllBytes(original)).isEqualTo(bytes);
    }

    @Test void strictForwardingTerminatesCyclesAndVisitsEveryRecipientOnce() {
        rule("991511", "991512"); rule("991512", "991511"); rule("991512", "991513"); rule("991511", "991513");
        entityManager.flush();
        inbox.addToProviderInboxStrict("991511", 1991511, "DOC");
        entityManager.flush();
        assertThat(inbox.getProvidersWithRoutingForDocument("DOC", 1991511))
                .extracting(ProviderInboxItem::getProviderNo).containsExactlyInAnyOrder("991511", "991512", "991513");
    }

    @Test void strictForwardingFailurePropagatesAndRollsBackEarlierRecipients() {
        assertThatThrownBy(() -> isolatedTransaction().executeWithoutResult(status -> {
            rule("991521", "991522"); rule("991522", "");
            entityManager.flush();
            inbox.addToProviderInboxStrict("991521", 1991521, "DOC");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("Missing forwarding recipient");
        assertThat(inbox.getProvidersWithRoutingForDocument("DOC", 1991521)).isEmpty();
    }

    @Test void pageCountUpdatePreservesAnotherSessionsCommittedClassificationAndRefreshesStaleEntity() {
        Integer id = isolatedTransaction().execute(status -> {
            Document document = new Document(); document.setDocfilename("page-count-overlap.pdf");
            document.setDocdesc("old description"); document.setDoccreator("991531"); document.setResponsible("991531");
            document.setDoctype(""); document.setStatus('A'); document.setContenttype("application/pdf");
            document.setPublic1(0); document.setNumberofpages(3); document.setProgramId(1); document.setRestrictToProgram(false);
            documents.persist(document); entityManager.flush(); return document.getId();
        });
        try {
            isolatedTransaction().executeWithoutResult(status -> {
                Document stale = documents.getDocument(String.valueOf(id));
                assertThat(stale.getDocdesc()).isEqualTo("old description");
                isolatedTransaction().executeWithoutResult(other -> {
                    Document edited = documents.getDocument(String.valueOf(id));
                    edited.setDocdesc("new description"); edited.setProgramId(17); edited.setRestrictToProgram(true);
                    edited.setResponsible("991532"); documents.merge(edited);
                });
                assertThat(stale.getDocdesc()).isEqualTo("old description");
                documents.updatePageCount(id, 2);
                entityManager.flush();
                assertThat(stale.getNumberofpages()).isEqualTo(2);
                assertThat(stale.getDocdesc()).isEqualTo("new description");
                assertThat(stale.getProgramId()).isEqualTo(17);
                assertThat(stale.isRestrictToProgram()).isTrue();
                assertThat(stale.getResponsible()).isEqualTo("991532");
            });
        } finally {
            isolatedTransaction().executeWithoutResult(status -> documents.remove(id));
        }
    }

    private void rule(String source, String destination) {
        IncomingLabRules rule = new IncomingLabRules(); rule.setProviderNo(source); rule.setFrwdProviderNo(destination);
        rule.setStatus("A"); rule.setArchive("0"); entityManager.persist(rule);
    }
}
