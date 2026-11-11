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
@Table(name = "cds_import_unit")
public class CdsImportUnit extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "patient_key", nullable = false, length = 64)
    private String patientKey;

    @Column(name = "unit_key", nullable = false, length = 64)
    private String unitKey;

    @Column(name = "lineage_key", nullable = false, length = 64)
    private String lineageKey;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "ordinal", nullable = false)
    private long ordinal;

    @Column(name = "source_digest", nullable = false, length = 64)
    private String sourceDigest;

    @Column(name = "spool_location", nullable = false, length = 1024)
    private String spoolLocation;

    @Column(name = "state", nullable = false, length = 48)
    private String state;

    @Column(name = "target_references", nullable = true, columnDefinition = "longtext")
    private String targetReferences;

    @Column(name = "target_digest", nullable = true, length = 64)
    private String targetDigest;

    @Column(name = "committed_at", nullable = true)
    private Instant committedAt;

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public String getPatientKey() { return patientKey; }
    public void setPatientKey(String value) { patientKey = value; }
    public String getUnitKey() { return unitKey; }
    public void setUnitKey(String value) { unitKey = value; }
    public String getLineageKey() { return lineageKey; }
    public void setLineageKey(String value) { lineageKey = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public long getOrdinal() { return ordinal; }
    public void setOrdinal(long value) { ordinal = value; }
    public String getSourceDigest() { return sourceDigest; }
    public void setSourceDigest(String value) { sourceDigest = value; }
    public String getSpoolLocation() { return spoolLocation; }
    public void setSpoolLocation(String value) { spoolLocation = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getTargetReferences() { return targetReferences; }
    public void setTargetReferences(String value) { targetReferences = value; }
    public String getTargetDigest() { return targetDigest; }
    public void setTargetDigest(String value) { targetDigest = value; }
    public Instant getCommittedAt() { return committedAt; }
    public void setCommittedAt(Instant value) { committedAt = value; }

    @Override
    public String toString() { return "CdsImportUnit[" + id + "]"; }
}
