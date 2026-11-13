/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ReadingAidApproval;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The persisted approval a reading aid is sent from: opaque ids, one row per document and version, exact matching. */
@Tag("unit")
class ReadingAidApprovalStoreUnitTest {
    private static final Instant GENERATED = Instant.ofEpochSecond(1_792_450_200L);
    private static final Instant REVIEWED = Instant.ofEpochSecond(1_792_450_680L);
    private static final byte[] ORIGINAL = "FAKE chest X-ray report. FAKE repeat imaging in 3 months.".getBytes(StandardCharsets.UTF_8);
    private static final int PATIENT = 7;
    private static final int DOCUMENT = 11;

    private final EntityManager em = mock(EntityManager.class);
    private final ReadingAidApprovalStore store = new ReadingAidApprovalStore();
    @SuppressWarnings("unchecked") private final TypedQuery<ReadingAidApproval> query = mock(TypedQuery.class);

    ReadingAidApprovalStoreUnitTest() {
        ReflectionTestUtils.setField(store, "entityManager", em);
        when(em.createQuery(anyString(), eq(ReadingAidApproval.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenAnswer(call -> Stream.<ReadingAidApproval>empty());
    }

    @Test
    void recordingRunsInATransactionBecauseNoCallerOpensOne() throws Exception {
        var method = ReadingAidApprovalStore.class.getMethod("record", int.class, int.class,
                DocumentSummaryReviewBinding.Approval.class);
        var transactional = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(transactional).as("record() must be @Transactional").isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void recordsOneRowWithAnOpaqueIdNotDerivedFromTheReviewerAndNoReviewerName() {
        var approval = approval();
        var row = store.record(PATIENT, DOCUMENT, approval);
        verify(em).persist(row);
        verify(em).flush();
        // Random generation is independent of the reviewer; coincidental character overlap is valid.
        assertThat(row.getApprovalId()).matches("apr-[a-z2-7]{24}");
        assertThat(row.getDocumentNo()).isEqualTo(DOCUMENT);
        assertThat(row.getDemographicNo()).isEqualTo(PATIENT);
        assertThat(row.getSummaryVersion()).isEqualTo(approval.summaryVersion()).matches("[0-9a-f]{64}");
        assertThat(row.getSourceSha256()).isEqualTo(approval.sourceSha256());
        assertThat(row.getNoticeVersion()).isEqualTo(DocumentSummaryReviewBinding.NOTICE_VERSION);
        assertThat(row.getLanguage()).isEqualTo("en");
        assertThat(row.getReviewerProviderNo()).isEqualTo("4242");
        assertThat(row.getReviewedAtEpoch()).isEqualTo(REVIEWED.getEpochSecond());
        assertThat(row.getGeneratedAtEpoch()).isEqualTo(GENERATED.getEpochSecond());
        assertThat(row.getRevisedAtEpoch()).isNull();
        assertThat(row.toString()).doesNotContain("4242", approval.summaryVersion());
        assertThat(ReadingAidApprovalStore.newApprovalId()).isNotEqualTo(ReadingAidApprovalStore.newApprovalId());
    }

    @Test
    void recordingTheSameApprovalAgainReturnsTheStoredRowAndAnotherOriginalIsRefused() {
        var approval = approval();
        var stored = new ReadingAidApproval("apr-abcdefghijklmnopqrstuvwx", DOCUMENT, PATIENT, approval.summaryVersion(),
                approval.sourceSha256(), "carlos-5537-en-1", "en", "4242", 1L, 1L, null);
        when(query.getResultStream()).thenAnswer(call -> Stream.of(stored));
        assertThat(store.record(PATIENT, DOCUMENT, approval)).isSameAs(stored);
        verify(em, never()).persist(any());
        assertThatThrownBy(() -> store.record(PATIENT + 1, DOCUMENT, approval)).isInstanceOf(IllegalStateException.class);
        var other = new ReadingAidApproval("apr-zzzzzzzzzzzzzzzzzzzzzzzz", DOCUMENT, PATIENT, approval.summaryVersion(),
                "f".repeat(64), "carlos-5537-en-1", "en", "4242", 1L, 1L, null);
        when(query.getResultStream()).thenAnswer(call -> Stream.of(other));
        assertThatThrownBy(() -> store.record(PATIENT, DOCUMENT, approval)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordsTheNoticeVersionOfTheSummarysLanguage() {
        var row = store.record(PATIENT, DOCUMENT, approval("fr"));
        assertThat(row.getLanguage()).isEqualTo("fr");
        assertThat(row.getNoticeVersion()).isEqualTo(DocumentSummaryReviewBinding.NOTICE_VERSION_FR).isNotEqualTo(DocumentSummaryReviewBinding.NOTICE_VERSION);
    }

    @Test
    void refusesWithoutAPatientAndADocument() {
        assertThatThrownBy(() -> store.record(0, DOCUMENT, approval())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.record(PATIENT, 0, approval())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.record(PATIENT, DOCUMENT, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aSendUsesAnApprovalOnlyWhenItNamesTheExactOriginalSummaryPatientAndDocument() {
        var approval = approval();
        var stored = new ReadingAidApproval("apr-abcdefghijklmnopqrstuvwx", DOCUMENT, PATIENT, approval.summaryVersion(),
                approval.sourceSha256(), "carlos-5537-en-1", "en", "4242", 1L, 1L, null);
        when(em.find(ReadingAidApproval.class, "apr-abcdefghijklmnopqrstuvwx")).thenReturn(stored);
        assertThat(store.requireMatching("apr-abcdefghijklmnopqrstuvwx", PATIENT, DOCUMENT, approval.sourceSha256(),
                approval.summaryVersion())).isSameAs(stored);
        for (Object[] wrong : new Object[][] {
                {"apr-abcdefghijklmnopqrstuvwx", PATIENT + 1, DOCUMENT, approval.sourceSha256(), approval.summaryVersion()},
                {"apr-abcdefghijklmnopqrstuvwx", PATIENT, DOCUMENT + 1, approval.sourceSha256(), approval.summaryVersion()},
                {"apr-abcdefghijklmnopqrstuvwx", PATIENT, DOCUMENT, "0".repeat(64), approval.summaryVersion()},
                {"apr-abcdefghijklmnopqrstuvwx", PATIENT, DOCUMENT, approval.sourceSha256(), "0".repeat(64)},
                {"apr-unknownunknownunknownun", PATIENT, DOCUMENT, approval.sourceSha256(), approval.summaryVersion()},
                {null, PATIENT, DOCUMENT, approval.sourceSha256(), approval.summaryVersion()}}) {
            assertThatThrownBy(() -> store.requireMatching((String) wrong[0], (int) wrong[1], (int) wrong[2], (String) wrong[3],
                    (String) wrong[4])).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void theEntityColumnsAreExactlyTheMigrationsColumns() throws Exception {
        String sql = Files.readString(Path.of("database", "mysql", "migration", "common", "V1.0.84__reading_aid_approval.sql"));
        String body = sql.substring(sql.indexOf("CREATE TABLE IF NOT EXISTS clinical_reading_aid_approval"));
        List<String> sqlColumns = body.lines().map(String::strip)
                .filter(line -> line.matches("[a-z_0-9]+ (VARCHAR|CHAR|INT|BIGINT).*")).map(line -> line.split(" ")[0]).toList();
        List<String> entityColumns = java.util.Arrays.stream(ReadingAidApproval.class.getDeclaredFields())
                .map(f -> f.getAnnotation(jakarta.persistence.Column.class)).filter(java.util.Objects::nonNull)
                .map(jakarta.persistence.Column::name).toList();
        assertThat(entityColumns).containsExactlyInAnyOrderElementsOf(sqlColumns);
        Field id = ReadingAidApproval.class.getDeclaredField("approvalId");
        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
        assertThat(body).contains("PRIMARY KEY (approval_id)", "UNIQUE KEY reading_aid_approval_document_version (document_no, summary_version)");
    }

    private static DocumentSummaryReviewBinding.Approval approval() { return approval("en"); }

    private static DocumentSummaryReviewBinding.Approval approval(String language) {
        var output = ClinicalSummaryAgentProtocol.JSON.createObjectNode().put("overview", "FAKE chest X-ray report overview.");
        output.putArray("points").addObject().put("text", "FAKE repeat imaging in 3 months.").putArray("evidence").add("FAKE repeat imaging in 3 months.");
        var scope = new DocumentSummaryReviewBinding.Scope("clinic-a", PATIENT, DOCUMENT);
        var meta = new DocumentSummaryReviewBinding.Metadata("FAKE.txt", "text/plain", "FAKE chest X-ray report", 1234L);
        var source = DocumentSummaryReviewBinding.capture(scope, meta, ORIGINAL);
        var draft = new DocumentSummaryReviewBinding(Clock.fixed(GENERATED, ZoneOffset.UTC)).prepare(source, new DocumentSummary(output), "FAKE-model", language);
        var provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("4242");
        when(provider.getFormattedName()).thenReturn("FAKE-Reviewer, Demo");
        var actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProvider()).thenReturn(provider);
        return new DocumentSummaryReviewBinding(Clock.fixed(REVIEWED, ZoneOffset.UTC)).approve(draft, draft.id(), source, actor, true);
    }
}
