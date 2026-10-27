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
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/** Protected original/correction/actor/plan evidence; no acknowledgement can clear a clinical blocker. */
@Entity @Table(name="cds_import_review")
public class CdsImportReview extends AbstractModel<String> {
    @Id @Column(length=36) private String id;
    @Column(name="run_id", length=36, nullable=false) private String runId;
    @Column(name="review_version", nullable=false) private long reviewVersion;
    @Column(length=24, nullable=false) private String kind;
    @Column(name="diagnostic_id", nullable=false) private long diagnosticId;
    @Column(name="patient_key", length=64) private String patientKey;
    @Column(name="source_key", length=64) private String sourceKey;
    @Column(name="source_xml_sha256", length=64) private String sourceXmlSha256;
    @Column(name="source_patient_id", length=128) private String sourcePatientId;
    @Column(length=64) private String category;
    @Column(name="applied_at") private Instant appliedAt;
    @Column(name="safe_error_code", length=96) private String safeErrorCode;
    @Column(name="reviewed_source_xpath", length=2048) private String reviewedSourceXPath;
    @Column(name="review_reason", length=4000) private String reviewReason;
    @Column(length=32, nullable=false) private String state;
    @Column(name="operator_name", length=128, nullable=false) private String operator;
    @Column(name="provider_no", length=16, nullable=false) private String providerNo;
    @Column(name="security_no") private Integer securityNo;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Lob @Column(columnDefinition="MEDIUMTEXT", nullable=false) private String payload;
    @Column(name="payload_sha256", length=64, nullable=false) private String payloadSha256;
    @Column(name="manifest_location", length=1024) private String manifestLocation;
    @Column(name="manifest_sha256", length=64) private String manifestSha256;
    @Column(name="plan_location", length=1024, nullable=false) private String planLocation;
    @Column(name="plan_sha256", length=64, nullable=false) private String planSha256;
    @Column(name="plan_bytes", nullable=false) private long planBytes;
    @Column(name="revalidation_sha256", length=64) private String revalidationSha256;
    @Override public String getId(){return id; } public void setId(String v){id=v; }
    public String getRunId(){return runId; } public void setRunId(String v){runId=v; }
    public long getReviewVersion(){return reviewVersion; } public void setReviewVersion(long v){reviewVersion=v; }
    public String getKind(){return kind; } public void setKind(String v){kind=v; }
    public long getDiagnosticId(){return diagnosticId; } public void setDiagnosticId(long v){diagnosticId=v; }
    public String getPatientKey(){return patientKey; } public void setPatientKey(String v){patientKey=v; }
    public String getSourceKey(){return sourceKey; } public void setSourceKey(String v){sourceKey=v; }
    public String getSourceXmlSha256(){return sourceXmlSha256; } public void setSourceXmlSha256(String v){sourceXmlSha256=v; }
    public String getSourcePatientId(){return sourcePatientId; } public void setSourcePatientId(String v){sourcePatientId=v; }
    public String getCategory(){return category; } public void setCategory(String v){category=v; }
    public Instant getAppliedAt(){return appliedAt; } public void setAppliedAt(Instant v){appliedAt=v; }
    public String getSafeErrorCode(){return safeErrorCode; } public void setSafeErrorCode(String v){safeErrorCode=v; }
    public String getReviewedSourceXPath(){return reviewedSourceXPath; } public void setReviewedSourceXPath(String v){reviewedSourceXPath=v; }
    public String getReviewReason(){return reviewReason; } public void setReviewReason(String v){reviewReason=v; }
    public String getState(){return state; } public void setState(String v){state=v; }
    public String getOperator(){return operator; } public void setOperator(String v){operator=v; }
    public String getProviderNo(){return providerNo; } public void setProviderNo(String v){providerNo=v; }
    public Integer getSecurityNo(){return securityNo; } public void setSecurityNo(Integer v){securityNo=v; }
    public Instant getCreatedAt(){return createdAt; } public void setCreatedAt(Instant v){createdAt=v; }
    public String getPayload(){return payload; } public void setPayload(String v){payload=v; }
    public String getPayloadSha256(){return payloadSha256; } public void setPayloadSha256(String v){payloadSha256=v; }
    public String getManifestLocation(){return manifestLocation; } public void setManifestLocation(String v){manifestLocation=v; }
    public String getManifestSha256(){return manifestSha256; } public void setManifestSha256(String v){manifestSha256=v; }
    public String getPlanLocation(){return planLocation; } public void setPlanLocation(String v){planLocation=v; }
    public String getPlanSha256(){return planSha256; } public void setPlanSha256(String v){planSha256=v; }
    public long getPlanBytes(){return planBytes; } public void setPlanBytes(long v){planBytes=v; }
    public String getRevalidationSha256(){return revalidationSha256; } public void setRevalidationSha256(String v){revalidationSha256=v; }
    @Override public String toString(){return "CdsImportReview[protected]"; }
}
