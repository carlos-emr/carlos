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
@Table(name = "cds_import_event")
public class CdsImportEvent extends AbstractModel<Long> {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "unit_key", nullable = true, length = 64)
    private String unitKey;

    @Column(name = "epoch", nullable = false)
    private long epoch;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "operator", nullable = false, length = 128)
    private String operator;

    @Column(name = "provider_no", nullable = false, length = 16)
    private String providerNo;

    @Column(name = "observation_context", nullable = true, length = 1024)
    private String observationContext;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public String getUnitKey() { return unitKey; }
    public void setUnitKey(String value) { unitKey = value; }
    public long getEpoch() { return epoch; }
    public void setEpoch(long value) { epoch = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public String getOperator() { return operator; }
    public void setOperator(String value) { operator = value; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String value) { providerNo = value; }
    public String getObservationContext() { return observationContext; }
    public void setObservationContext(String value) { observationContext=value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }

    @Override
    public String toString() { return "CdsImportEvent[" + id + "]"; }
}
