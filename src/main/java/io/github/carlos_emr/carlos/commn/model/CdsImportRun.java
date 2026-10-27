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
import java.time.Instant;

/** Protected durable CDS import state; never serialize this entity to a public response. */
@Entity
@Table(name = "cds_import_run")
public class CdsImportRun extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "submission_key", nullable = false, length = 128)
    private String submissionKey;

    @Column(name = "operator", nullable = false, length = 128)
    private String operator;

    @Column(name = "provider_no", nullable = false, length = 16)
    private String providerNo;

    @Column(name = "source_namespace", nullable = false, length = 128)
    private String sourceNamespace;

    @Column(name = "profile", nullable = false, length = 64)
    private String profile;

    @Column(name = "source_digest", nullable = false, length = 64)
    private String sourceDigest;

    @Column(name = "options_digest", nullable = false, length = 64)
    private String optionsDigest;

    @Column(name = "ruleset_digest", nullable = false, length = 64)
    private String rulesetDigest;

    @Column(name = "snapshot_location", nullable = false, length = 1024)
    private String snapshotLocation;

    @Column(name = "state", nullable = false, length = 48)
    private String state;

    @Column(name = "manifest_digest", nullable = true, length = 64)
    private String manifestDigest;

    @Column(name = "execution_digest", nullable = true, length = 64)
    private String executionDigest;

    @Column(name = "expected_patients", nullable = false)
    private long expectedPatients;

    @Column(name = "expected_units", nullable = false)
    private long expectedUnits;

    @Column(name = "owner_epoch", nullable = false)
    private long ownerEpoch;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "report_generation", nullable = false)
    private long reportGeneration;

    @Column(name = "report_location", nullable = true, length = 1024)
    private String reportLocation;

    @Column(name = "report_digest", nullable = true, length = 64)
    private String reportDigest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean value) { dryRun=value; }

    @Column(name = "report_state", length = 48, nullable = false)
    private String reportState;
    public String getReportState() { return reportState; }
    public void setReportState(String value) { reportState=value; }
    @Column(name = "cleanup_state", length = 48, nullable = false)
    private String cleanupState;
    public String getCleanupState() { return cleanupState; }
    public void setCleanupState(String value) { cleanupState=value; }
    @Column(name = "safe_error_code", length = 96, nullable = true)
    private String safeErrorCode;
    public String getSafeErrorCode() { return safeErrorCode; }
    public void setSafeErrorCode(String value) { safeErrorCode=value; }
    @Column(name = "review_version", nullable = false)
    private long reviewVersion;
    public long getReviewVersion() { return reviewVersion; }
    public void setReviewVersion(long value) { reviewVersion=value; }
    @Column(name = "discovery_complete", nullable = false)
    private boolean discoveryComplete;
    public boolean isDiscoveryComplete() { return discoveryComplete; }
    public void setDiscoveryComplete(boolean value) { discoveryComplete=value; }
    @Column(name = "discovered_files", nullable = false)
    private long discoveredFiles;
    public long getDiscoveredFiles() { return discoveredFiles; }
    public void setDiscoveredFiles(long value) { discoveredFiles=value; }
    @Column(name = "patient_xml_files", nullable = false)
    private long patientXmlFiles;
    public long getPatientXmlFiles() { return patientXmlFiles; }
    public void setPatientXmlFiles(long value) { patientXmlFiles=value; }
    @Column(name = "source_bytes", nullable = false)
    private long sourceBytes;
    public long getSourceBytes() { return sourceBytes; }
    public void setSourceBytes(long value) { sourceBytes=value; }

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSubmissionKey() { return submissionKey; }
    public void setSubmissionKey(String value) { submissionKey = value; }
    public String getOperator() { return operator; }
    public void setOperator(String value) { operator = value; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String value) { providerNo = value; }
    public String getSourceNamespace() { return sourceNamespace; }
    public void setSourceNamespace(String value) { sourceNamespace = value; }
    public String getProfile() { return profile; }
    public void setProfile(String value) { profile = value; }
    public String getSourceDigest() { return sourceDigest; }
    public void setSourceDigest(String value) { sourceDigest = value; }
    public String getOptionsDigest() { return optionsDigest; }
    public void setOptionsDigest(String value) { optionsDigest = value; }
    public String getRulesetDigest() { return rulesetDigest; }
    public void setRulesetDigest(String value) { rulesetDigest = value; }
    public String getSnapshotLocation() { return snapshotLocation; }
    public void setSnapshotLocation(String value) { snapshotLocation = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getManifestDigest() { return manifestDigest; }
    public void setManifestDigest(String value) { manifestDigest = value; }
    public String getExecutionDigest() { return executionDigest; }
    public void setExecutionDigest(String value) { executionDigest = value; }
    public long getExpectedPatients() { return expectedPatients; }
    public void setExpectedPatients(long value) { expectedPatients = value; }
    public long getExpectedUnits() { return expectedUnits; }
    public void setExpectedUnits(long value) { expectedUnits = value; }
    public long getOwnerEpoch() { return ownerEpoch; }
    public void setOwnerEpoch(long value) { ownerEpoch = value; }
    public boolean isCancelRequested() { return cancelRequested; }
    public void setCancelRequested(boolean value) { cancelRequested = value; }
    public long getReportGeneration() { return reportGeneration; }
    public void setReportGeneration(long value) { reportGeneration = value; }
    public String getReportLocation() { return reportLocation; }
    public void setReportLocation(String value) { reportLocation = value; }
    public String getReportDigest() { return reportDigest; }
    public void setReportDigest(String value) { reportDigest = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }

    @Override
    public String toString() { return "CdsImportRun[" + id + "]"; }
}
