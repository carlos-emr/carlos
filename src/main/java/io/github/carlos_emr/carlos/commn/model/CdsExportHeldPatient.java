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

/**
 * One selected patient that an export attempt left out of its package, with the safe reason
 * code. Rows are written once per attempt epoch, in the same transaction that retains the package
 * context, so the operator-visible list always matches the encrypted ReadMe. No clinical value is
 * stored here.
 */
@Entity
@Table(name = "cds_export_held_patient")
public class CdsExportHeldPatient extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;
    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;
    @Column(name = "epoch", nullable = false)
    private long epoch;
    @Column(name = "demographic_no", nullable = false)
    private int demographicNo;
    @Column(name = "safe_code", nullable = false, length = 96)
    private String safeCode;
    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;
    @Override
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public long getEpoch() { return epoch; }
    public void setEpoch(long value) { epoch = value; }
    public int getDemographicNo() { return demographicNo; }
    public void setDemographicNo(int value) { demographicNo = value; }
    public String getSafeCode() { return safeCode; }
    public void setSafeCode(String value) { safeCode = value; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant value) { createdAt = value; }
    @Override public String toString() { return "CdsExportHeldPatient[protected]"; }
}
