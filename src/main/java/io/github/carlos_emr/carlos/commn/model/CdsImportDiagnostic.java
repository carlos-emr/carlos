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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Protected durable CDS import state; never serialize this entity to a public response. */
@Entity
@Table(name = "cds_import_diagnostic")
public class CdsImportDiagnostic extends AbstractModel<Long> {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "patient_key", nullable = true, length = 64)
    private String patientKey;

    @Column(name = "unit_key", nullable = true, length = 64)
    private String unitKey;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "code", nullable = false, length = 96)
    private String code;

    @Column(name = "stage", nullable = false, length = 64)
    private String stage;

    @Column(name = "source_path", nullable = true, length = 2048)
    private String sourcePath;

    @Column(name = "safe_summary", nullable = false, length = 2048)
    private String safeSummary;

    @Column(name = "protected_detail_location", nullable = true, length = 1024)
    private String protectedDetailLocation;

    @Column(name = "blocking", nullable = false)
    private boolean blocking;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public String getPatientKey() { return patientKey; }
    public void setPatientKey(String value) { patientKey = value; }
    public String getUnitKey() { return unitKey; }
    public void setUnitKey(String value) { unitKey = value; }
    public String getSeverity() { return severity; }
    public void setSeverity(String value) { severity = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getStage() { return stage; }
    public void setStage(String value) { stage = value; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String value) { sourcePath = value; }
    public String getSafeSummary() { return safeSummary; }
    public void setSafeSummary(String value) { safeSummary = value; }
    public String getProtectedDetailLocation() { return protectedDetailLocation; }
    public void setProtectedDetailLocation(String value) { protectedDetailLocation = value; }
    public boolean isBlocking() { return blocking; }
    public void setBlocking(boolean value) { blocking = value; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean value) { resolved = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }

    @Override
    public String toString() { return "CdsImportDiagnostic[" + id + "]"; }
}
