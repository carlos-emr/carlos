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

/** Protected per-epoch export assets and mutually exclusive durable download/cleanup leases. */
@Entity
@Table(name = "cds_export_attempt", uniqueConstraints = @jakarta.persistence.UniqueConstraint(name = "uq_cds_export_attempt", columnNames = {"run_id", "epoch"}))
public class CdsExportAttempt extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;
    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;
    @Column(name = "epoch", nullable = false)
    private long epoch;
    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;
    @Column(name = "state", nullable = false, length = 32)
    private String state;
    @Column(name = "context_document", nullable = true, columnDefinition = "longtext")
    private String contextDocument;
    @Column(name = "context_digest", nullable = true, length = 64)
    private String contextDigest;
    @Column(name = "download_token", nullable = true, length = 36)
    private String downloadToken;
    @Column(name = "download_until", nullable = true)
    private java.time.Instant downloadUntil;
    @Column(name = "download_operator", nullable = true, length = 128)
    private String downloadOperator;
    @Column(name = "cleanup_token", nullable = true, length = 36)
    private String cleanupToken;
    @Column(name = "cleanup_until", nullable = true)
    private java.time.Instant cleanupUntil;
    @Column(name = "safe_code", nullable = true, length = 96)
    private String safeCode;
    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;
    @Column(name = "expires_at", nullable = false)
    private java.time.Instant expiresAt;
    @jakarta.persistence.Version
    @Column(name = "revision", nullable = false)
    private long revision;
    @Override
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public long getEpoch() { return epoch; }
    public void setEpoch(long value) { epoch = value; }
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getContextDocument() { return contextDocument; }
    public void setContextDocument(String value) { contextDocument = value; }
    public String getContextDigest() { return contextDigest; }
    public void setContextDigest(String value) { contextDigest = value; }
    public String getDownloadToken() { return downloadToken; }
    public void setDownloadToken(String value) { downloadToken = value; }
    public java.time.Instant getDownloadUntil() { return downloadUntil; }
    public void setDownloadUntil(java.time.Instant value) { downloadUntil = value; }
    public String getDownloadOperator() { return downloadOperator; }
    public void setDownloadOperator(String value) { downloadOperator = value; }
    public String getCleanupToken() { return cleanupToken; }
    public void setCleanupToken(String value) { cleanupToken = value; }
    public java.time.Instant getCleanupUntil() { return cleanupUntil; }
    public void setCleanupUntil(java.time.Instant value) { cleanupUntil = value; }
    public String getSafeCode() { return safeCode; }
    public void setSafeCode(String value) { safeCode = value; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant value) { createdAt = value; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.Instant value) { updatedAt = value; }
    public java.time.Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(java.time.Instant value) { expiresAt = value; }
    public long getRevision() { return revision; }
    public void setRevision(long value) { revision = value; }
    @Override public String toString() { return "CdsExportAttempt[protected]"; }
}
