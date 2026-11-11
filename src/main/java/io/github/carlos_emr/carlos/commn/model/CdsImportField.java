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
@Table(name = "cds_import_field")
public class CdsImportField extends AbstractModel<String> {
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

    @Column(name = "demographic_no", nullable = false)
    private Integer demographicNo;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "source_path", nullable = false, length = 2048)
    private String sourcePath;

    @Column(name = "occurrence", nullable = false)
    private long occurrence;

    @Column(name = "value_type", nullable = false, length = 4608)
    private String valueType;

    @Column(name = "date_precision", nullable = true, length = 32)
    private String precision;

    @Column(name = "original_value", nullable = true, columnDefinition = "longtext")
    private String originalValue;

    @Column(name = "normalized_value", nullable = true, columnDefinition = "longtext")
    private String normalizedValue;

    @Column(name = "payload_location", nullable = true, length = 1024)
    private String payloadLocation;

    @Column(name = "payload_digest", nullable = true, length = 64)
    private String payloadDigest;

    @Column(name = "payload_offset", nullable = false)
    private long payloadOffset;

    @Column(name = "payload_length", nullable = false)
    private long payloadLength;

    @Column(name = "source_namespace", length = 250, nullable = false)
    private String sourceNamespace;
    public String getSourceNamespace() { return sourceNamespace; }
    public void setSourceNamespace(String value) { sourceNamespace=value; }

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public String getPatientKey() { return patientKey; }
    public void setPatientKey(String value) { patientKey = value; }
    public String getUnitKey() { return unitKey; }
    public void setUnitKey(String value) { unitKey = value; }
    public Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(Integer value) { demographicNo = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String value) { sourcePath = value; }
    public long getOccurrence() { return occurrence; }
    public void setOccurrence(long value) { occurrence = value; }
    public String getValueType() { return valueType; }
    public void setValueType(String value) { valueType = value; }
    public String getPrecision() { return precision; }
    public void setPrecision(String value) { precision = value; }
    public String getOriginalValue() { return originalValue; }
    public void setOriginalValue(String value) { originalValue = value; }
    public String getNormalizedValue() { return normalizedValue; }
    public void setNormalizedValue(String value) { normalizedValue = value; }
    public String getPayloadLocation() { return payloadLocation; }
    public void setPayloadLocation(String value) { payloadLocation = value; }
    public String getPayloadDigest() { return payloadDigest; }
    public void setPayloadDigest(String value) { payloadDigest = value; }
    public long getPayloadOffset() { return payloadOffset; }
    public void setPayloadOffset(long value) { payloadOffset = value; }
    public long getPayloadLength() { return payloadLength; }
    public void setPayloadLength(long value) { payloadLength = value; }

    @Override
    public String toString() { return "CdsImportField[" + id + "]"; }
}
