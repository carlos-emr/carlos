/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A clinician's recorded approval of one AI summary of one document (carlos#5537), kept so a
 * reading aid can be sent after the review session has ended. The approval id is opaque: a
 * random reference, never derived from the reviewer, and it is the only part of this row that
 * reaches the patient's device. Times are Unix seconds, as the delivery contract's claims.
 */
@Entity
@Table(name = "clinical_reading_aid_approval")
public class ReadingAidApproval {
    @Id @Column(name = "approval_id", length = 64)
    private String approvalId;
    @Column(name = "document_no", nullable = false)
    private int documentNo;
    @Column(name = "demographic_no", nullable = false)
    private int demographicNo;
    @Column(name = "summary_version", nullable = false, length = 64)
    private String summaryVersion;
    @Column(name = "source_sha256", nullable = false, length = 64)
    private String sourceSha256;
    @Column(name = "notice_version", nullable = false, length = 64)
    private String noticeVersion;
    @Column(name = "language", nullable = false, length = 16)
    private String language;
    @Column(name = "reviewer_provider_no", nullable = false, length = 20)
    private String reviewerProviderNo;
    @Column(name = "reviewed_at_epoch", nullable = false)
    private long reviewedAtEpoch;
    @Column(name = "generated_at_epoch", nullable = false)
    private long generatedAtEpoch;
    @Column(name = "revised_at_epoch")
    private Long revisedAtEpoch;

    protected ReadingAidApproval() { }

    public ReadingAidApproval(String approvalId, int documentNo, int demographicNo, String summaryVersion,
            String sourceSha256, String noticeVersion, String language, String reviewerProviderNo,
            long reviewedAtEpoch, long generatedAtEpoch, Long revisedAtEpoch) {
        this.approvalId = approvalId;
        this.documentNo = documentNo;
        this.demographicNo = demographicNo;
        this.summaryVersion = summaryVersion;
        this.sourceSha256 = sourceSha256;
        this.noticeVersion = noticeVersion;
        this.language = language;
        this.reviewerProviderNo = reviewerProviderNo;
        this.reviewedAtEpoch = reviewedAtEpoch;
        this.generatedAtEpoch = generatedAtEpoch;
        this.revisedAtEpoch = revisedAtEpoch;
    }

    public String getApprovalId() { return approvalId; }
    public int getDocumentNo() { return documentNo; }
    public int getDemographicNo() { return demographicNo; }
    public String getSummaryVersion() { return summaryVersion; }
    public String getSourceSha256() { return sourceSha256; }
    public String getNoticeVersion() { return noticeVersion; }
    public String getLanguage() { return language; }
    public String getReviewerProviderNo() { return reviewerProviderNo; }
    public long getReviewedAtEpoch() { return reviewedAtEpoch; }
    public long getGeneratedAtEpoch() { return generatedAtEpoch; }
    public Long getRevisedAtEpoch() { return revisedAtEpoch; }
    @Override public String toString() { return "ReadingAidApproval[REDACTED]"; }
}
