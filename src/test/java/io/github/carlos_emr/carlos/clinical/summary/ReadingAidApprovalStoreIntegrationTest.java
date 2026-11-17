/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ReadingAidApproval;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The approval store over the H2 test database, with real persistence instead of a mocked
 * EntityManager: its JPQL lookup by document and summary version, the record-once rule, the
 * refusal of another original under a stored version, and the (document, summary version)
 * unique key. Each test runs in CarlosTestBase's transaction, which is rolled back afterwards.
 */
@Tag("integration")
@Tag("dao")
class ReadingAidApprovalStoreIntegrationTest extends CarlosTestBase {
    private static final Instant GENERATED = Instant.ofEpochSecond(1_792_450_200L);
    private static final Instant REVIEWED = Instant.ofEpochSecond(1_792_450_680L);
    private static final byte[] ORIGINAL = "FAKE chest X-ray report. FAKE repeat imaging in 3 months."
            .getBytes(StandardCharsets.UTF_8);
    private static final int PATIENT = 999886;
    private static final int DOCUMENT = 999041;

    @PersistenceContext private EntityManager em;
    private final ReadingAidApprovalStore store = new ReadingAidApprovalStore();

    @BeforeEach void wireStore() {
        ReflectionTestUtils.setField(store, "entityManager", em);
    }

    @Test void shouldRecordOnceAndFindTheRow_byDocumentAndSummaryVersion() {
        var approval = approval();
        var row = store.record(PATIENT, DOCUMENT, approval);
        em.clear();

        var found = store.find(DOCUMENT, approval.summaryVersion());
        assertThat(found).isNotNull();
        assertThat(found.getApprovalId()).isEqualTo(row.getApprovalId());
        assertThat(found.getDemographicNo()).isEqualTo(PATIENT);
        assertThat(found.getSourceSha256()).isEqualTo(approval.sourceSha256());
        assertThat(store.find(row.getApprovalId()).getSummaryVersion()).isEqualTo(approval.summaryVersion());
        assertThat(store.find(DOCUMENT, "0".repeat(64))).isNull();
        assertThat(store.find(DOCUMENT + 1, approval.summaryVersion())).isNull();

        // Recording the same approval again returns the stored row and adds none.
        assertThat(store.record(PATIENT, DOCUMENT, approval).getApprovalId()).isEqualTo(row.getApprovalId());
        assertThat(rowsFor(DOCUMENT)).isEqualTo(1L);
        assertThat(store.requireMatching(row.getApprovalId(), PATIENT, DOCUMENT, approval.sourceSha256(),
                approval.summaryVersion()).getApprovalId()).isEqualTo(row.getApprovalId());
    }

    @Test void shouldRefuseAnotherOriginalOrPatient_underAStoredSummaryVersion() {
        var approval = approval();
        // A stored row for this document and version that names another original.
        em.persist(new ReadingAidApproval(ReadingAidApprovalStore.newApprovalId(), DOCUMENT, PATIENT,
                approval.summaryVersion(), "f".repeat(64), DocumentSummaryReviewBinding.NOTICE_VERSION, "en",
                "4242", REVIEWED.getEpochSecond(), GENERATED.getEpochSecond(), null));
        em.flush();
        em.clear();

        assertThatThrownBy(() -> store.record(PATIENT, DOCUMENT, approval)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.record(PATIENT + 1, DOCUMENT, approval)).isInstanceOf(IllegalStateException.class);
        assertThat(rowsFor(DOCUMENT)).isEqualTo(1L);
    }

    @Test void shouldRefuseASecondRowInTheDatabase_forTheSameDocumentAndSummaryVersion() {
        var approval = approval();
        store.record(PATIENT, DOCUMENT, approval);
        // Past record()'s own check: the database's unique key refuses the second row too.
        em.persist(new ReadingAidApproval(ReadingAidApprovalStore.newApprovalId(), DOCUMENT, PATIENT,
                approval.summaryVersion(), approval.sourceSha256(), DocumentSummaryReviewBinding.NOTICE_VERSION, "en",
                "4242", REVIEWED.getEpochSecond(), GENERATED.getEpochSecond(), null));

        assertThatThrownBy(() -> em.flush())
                .isInstanceOf(PersistenceException.class)
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    private long rowsFor(int document) {
        return em.createQuery("select count(a) from ReadingAidApproval a where a.documentNo = :document", Long.class)
                .setParameter("document", document).getSingleResult();
    }

    /** A real approval from the review binding (as the unit test builds it), over FAKE text. */
    private static DocumentSummaryReviewBinding.Approval approval() {
        var output = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", "FAKE chest X-ray report overview.");
        output.putArray("points").addObject().put("text", "FAKE repeat imaging in 3 months.")
                .putArray("evidence").add("FAKE repeat imaging in 3 months.");
        var scope = new DocumentSummaryReviewBinding.Scope("clinic-a", PATIENT, DOCUMENT);
        var meta = new DocumentSummaryReviewBinding.Metadata("FAKE.txt", "text/plain", "FAKE chest X-ray report", 1234L);
        var source = DocumentSummaryReviewBinding.capture(scope, meta, ORIGINAL);
        var draft = new DocumentSummaryReviewBinding(Clock.fixed(GENERATED, ZoneOffset.UTC))
                .prepare(source, new DocumentSummary(output), "FAKE-model", "en");
        var provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("4242");
        when(provider.getFormattedName()).thenReturn("FAKE-Reviewer, Demo");
        var actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProvider()).thenReturn(provider);
        return new DocumentSummaryReviewBinding(Clock.fixed(REVIEWED, ZoneOffset.UTC)).approve(draft, draft.id(), source, actor, true);
    }
}
