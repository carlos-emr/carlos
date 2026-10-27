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
import jakarta.persistence.Version;
import java.time.Instant;

/** Owner-scoped durable upload receipt; never serialize this entity into a response. */
@Entity @Table(name = "cds_import_upload")
public class CdsUpload extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id @Column(length = 36) private String id;
    @Column(name = "operator_key", nullable = false, length = 128) private String operator;
    @Column(name = "source_name", nullable = false, length = 1000) private String sourceName;
    @Column(name = "media_kind", nullable = false, length = 8) private String mediaKind;
    @Column(name = "expected_bytes", nullable = false) private long expectedBytes;
    @Column(name = "received_bytes", nullable = false) private long receivedBytes;
    @Column(name = "chunk_count", nullable = false) private long chunkCount;
    @Column(name = "manifest_digest", length = 64) private String manifestDigest;
    @Column(name = "snapshot_digest", length = 64) private String snapshotDigest;
    @Column(nullable = false, length = 24) private String state;
    @Column(name = "submission_key", nullable = false, length = 128) private String submissionKey;
    @Column(name = "run_id", length = 36) private String runId;
    @Column(name = "dry_run_id", length = 36) private String dryRunId;
    @Column(name = "quota_released", nullable = false) private boolean quotaReleased;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    @Override public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getOperator() { return operator; }
    public void setOperator(String value) { operator = value; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String value) { sourceName = value; }
    public String getMediaKind() { return mediaKind; }
    public void setMediaKind(String value) { mediaKind = value; }
    public long getExpectedBytes() { return expectedBytes; }
    public void setExpectedBytes(long value) { expectedBytes = value; }
    public long getReceivedBytes() { return receivedBytes; }
    public void setReceivedBytes(long value) { receivedBytes = value; }
    public long getChunkCount() { return chunkCount; }
    public void setChunkCount(long value) { chunkCount = value; }
    public String getManifestDigest() { return manifestDigest; }
    public void setManifestDigest(String value) { manifestDigest = value; }
    public String getSnapshotDigest() { return snapshotDigest; }
    public void setSnapshotDigest(String value) { snapshotDigest = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getSubmissionKey() { return submissionKey; }
    public void setSubmissionKey(String value) { submissionKey = value; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public String getDryRunId() { return dryRunId; }
    public void setDryRunId(String value) { dryRunId = value; }
    public boolean isQuotaReleased() { return quotaReleased; }
    public void setQuotaReleased(boolean value) { quotaReleased = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
    public long getVersion() { return version; }
    @Override public String toString() { return "CdsUpload[protected]"; }
}
