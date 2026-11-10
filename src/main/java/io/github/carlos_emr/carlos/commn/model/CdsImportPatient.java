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

/** Protected durable CDS import state; never serialize this entity to a public response. */
@Entity
@Table(name = "cds_import_patient")
public class CdsImportPatient extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "patient_key", nullable = false, length = 64)
    private String patientKey;

    @Column(name = "source_patient_id", nullable = false, length = 128)
    private String sourcePatientId;

    @Column(name = "copy_key", nullable = false, length = 64)
    private String copyKey;

    @Column(name = "lineage_key", nullable = false, length = 64)
    private String lineageKey;

    @Column(name = "source_digest", nullable = false, length = 64)
    private String sourceDigest;

    @Column(name = "target_demographic_no", nullable = true)
    private Integer targetDemographicNo;

    @Column(name = "state", nullable = false, length = 48)
    private String state;

    @Column(name = "held", nullable = false)
    private boolean held;

    @Column(name = "expected_units", nullable = false)
    private long expectedUnits;

    @Column(name = "execution_digest", nullable = true, length = 64)
    private String executionDigest;

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public String getPatientKey() { return patientKey; }
    public void setPatientKey(String value) { patientKey = value; }
    public String getSourcePatientId() { return sourcePatientId; }
    public void setSourcePatientId(String value) { sourcePatientId = value; }
    public String getCopyKey() { return copyKey; }
    public void setCopyKey(String value) { copyKey = value; }
    public String getLineageKey() { return lineageKey; }
    public void setLineageKey(String value) { lineageKey = value; }
    public String getSourceDigest() { return sourceDigest; }
    public void setSourceDigest(String value) { sourceDigest = value; }
    public Integer getTargetDemographicNo() { return targetDemographicNo; }
    public void setTargetDemographicNo(Integer value) { targetDemographicNo = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public boolean isHeld() { return held; }
    public void setHeld(boolean value) { held = value; }
    public long getExpectedUnits() { return expectedUnits; }
    public void setExpectedUnits(long value) { expectedUnits = value; }
    public String getExecutionDigest() { return executionDigest; }
    public void setExecutionDigest(String value) { executionDigest = value; }

    @Override
    public String toString() { return "CdsImportPatient[" + id + "]"; }
}
