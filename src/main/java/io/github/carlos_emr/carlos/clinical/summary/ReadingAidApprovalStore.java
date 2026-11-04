/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.carlos.commn.model.ReadingAidApproval;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.security.SecureRandom;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * Persists the clinician approvals a reading aid is later built from. No sending happens here:
 * the sender looks an approval up at send time and refuses unless it names the exact original
 * and the exact summary version being sent (decision 2 of the delivery plan).
 */
@Repository
public class ReadingAidApprovalStore {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Records {@code approval} for the document and patient it was reviewed under. One row per
     * document and summary version: recording the same approval again returns the stored row,
     * and a different original under the same version is refused.
     */
    public ReadingAidApproval record(int demographicNo, int documentNo, DocumentSummaryReviewBinding.Approval approval) {
        Objects.requireNonNull(approval);
        if (demographicNo <= 0 || documentNo <= 0) throw new IllegalArgumentException("Patient and document are required.");
        var existing = find(documentNo, approval.summaryVersion());
        if (existing != null) {
            if (!existing.getSourceSha256().equals(approval.sourceSha256()) || existing.getDemographicNo() != demographicNo) {
                throw new IllegalStateException("A different original is already approved under this summary version.");
            }
            return existing;
        }
        var row = new ReadingAidApproval(newApprovalId(), documentNo, demographicNo, approval.summaryVersion(),
                approval.sourceSha256(), DocumentSummaryReviewBinding.noticeVersion(approval.language()), approval.language(),
                approval.reviewerId(), approval.reviewedAt().getEpochSecond(), approval.generatedAt().getEpochSecond(),
                approval.revisedAt() == null ? null : approval.revisedAt().getEpochSecond());
        entityManager.persist(row);
        entityManager.flush();
        return row;
    }

    public ReadingAidApproval find(String approvalId) { return entityManager.find(ReadingAidApproval.class, approvalId); }

    public ReadingAidApproval find(int documentNo, String summaryVersion) {
        return entityManager.createQuery("from ReadingAidApproval where documentNo = :document and summaryVersion = :version",
                ReadingAidApproval.class).setParameter("document", documentNo).setParameter("version", summaryVersion)
                .getResultStream().findFirst().orElse(null);
    }

    /**
     * The approval a send may use: it must exist and name exactly this original and this summary
     * version, for this patient and document; otherwise the aid is not sent.
     */
    public ReadingAidApproval requireMatching(String approvalId, int demographicNo, int documentNo, String originalSha256,
            String summaryVersion) {
        var row = approvalId == null ? null : find(approvalId);
        if (row == null || row.getDemographicNo() != demographicNo || row.getDocumentNo() != documentNo
                || !row.getSourceSha256().equals(originalSha256) || !row.getSummaryVersion().equals(summaryVersion)) {
            throw new IllegalStateException("No clinician approval matches this original and summary version.");
        }
        return row;
    }

    /** "apr-" and 24 random characters: opaque, not derived from the reviewer, the document or the time. */
    static String newApprovalId() {
        var id = new StringBuilder("apr-");
        for (int i = 0; i < 24; i++) id.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return id.toString();
    }
}
