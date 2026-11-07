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
package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Protected durable export state; application DTOs own observations. */
@Entity
@Table(name = "cds_export_run", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uq_cds_export_submission", columnNames = {"operator", "submission_key"}))
public class CdsExportRun extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;
    @Column(name = "submission_key", nullable = false, length = 128)
    private String submissionKey;
    @Column(name = "operator", nullable = false, length = 128)
    private String operator;
    @Column(name = "provider_no", nullable = false, length = 16)
    private String providerNo;
    @Column(name = "request_document", nullable = false, columnDefinition = "longtext")
    private String requestDocument;
    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;
    @Column(name = "compatibility_digest", nullable = false, length = 64)
    private String compatibilityDigest;
    @Column(name = "state", nullable = false, length = 32)
    private String state;
    @Column(name = "owner_epoch", nullable = false)
    private long ownerEpoch;
    @Column(name = "owner_token", nullable = true, length = 36)
    private String ownerToken;
    @Column(name = "lease_until", nullable = true)
    private java.time.Instant leaseUntil;
    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;
    @Column(name = "expected_patients", nullable = true)
    private Long expectedPatients;
    @Column(name = "processed_patients", nullable = false)
    private long processedPatients;
    @Column(name = "blocked_patients", nullable = false)
    private long blockedPatients;
    @Column(name = "cohort_member", nullable = true, length = 64)
    private String cohortMember;
    @Column(name = "cohort_bytes", nullable = true)
    private Long cohortBytes;
    @Column(name = "cohort_digest", nullable = true, length = 64)
    private String cohortDigest;
    @Column(name = "selected_at", nullable = true)
    private java.time.Instant selectedAt;
    @Column(name = "artifact_member", nullable = true, length = 64)
    private String artifactMember;
    @Column(name = "artifact_digest", nullable = true, length = 64)
    private String artifactDigest;
    @Column(name = "artifact_bytes", nullable = true)
    private Long artifactBytes;
    @Column(name = "expires_at", nullable = true)
    private java.time.Instant expiresAt;
    @Column(name = "safe_code", nullable = true, length = 96)
    private String safeCode;
    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;
    @jakarta.persistence.Version
    @Column(name = "revision", nullable = false)
    private long revision;
    @Override
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getSubmissionKey() { return submissionKey; }
    public void setSubmissionKey(String value) { submissionKey = value; }
    public String getOperator() { return operator; }
    public void setOperator(String value) { operator = value; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String value) { providerNo = value; }
    public String getRequestDocument() { return requestDocument; }
    public void setRequestDocument(String value) { requestDocument = value; }
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }
    public String getCompatibilityDigest() { return compatibilityDigest; }
    public void setCompatibilityDigest(String value) { compatibilityDigest = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public long getOwnerEpoch() { return ownerEpoch; }
    public void setOwnerEpoch(long value) { ownerEpoch = value; }
    public String getOwnerToken() { return ownerToken; }
    public void setOwnerToken(String value) { ownerToken = value; }
    public java.time.Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(java.time.Instant value) { leaseUntil = value; }
    public boolean isCancelRequested() { return cancelRequested; }
    public void setCancelRequested(boolean value) { cancelRequested = value; }
    public Long getExpectedPatients() { return expectedPatients; }
    public void setExpectedPatients(Long value) { expectedPatients = value; }
    public long getProcessedPatients() { return processedPatients; }
    public void setProcessedPatients(long value) { processedPatients = value; }
    public long getBlockedPatients() { return blockedPatients; }
    public void setBlockedPatients(long value) { blockedPatients = value; }
    public String getCohortMember() { return cohortMember; }
    public void setCohortMember(String value) { cohortMember = value; }
    public Long getCohortBytes() { return cohortBytes; }
    public void setCohortBytes(Long value) { cohortBytes = value; }
    public String getCohortDigest() { return cohortDigest; }
    public void setCohortDigest(String value) { cohortDigest = value; }
    public java.time.Instant getSelectedAt() { return selectedAt; }
    public void setSelectedAt(java.time.Instant value) { selectedAt = value; }
    public String getArtifactMember() { return artifactMember; }
    public void setArtifactMember(String value) { artifactMember = value; }
    public String getArtifactDigest() { return artifactDigest; }
    public void setArtifactDigest(String value) { artifactDigest = value; }
    public Long getArtifactBytes() { return artifactBytes; }
    public void setArtifactBytes(Long value) { artifactBytes = value; }
    public java.time.Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(java.time.Instant value) { expiresAt = value; }
    public String getSafeCode() { return safeCode; }
    public void setSafeCode(String value) { safeCode = value; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant value) { createdAt = value; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.Instant value) { updatedAt = value; }
    public long getRevision() { return revision; }
    public void setRevision(long value) { revision = value; }
    @Override public String toString() { return "CdsExportRun[protected]"; }
}
