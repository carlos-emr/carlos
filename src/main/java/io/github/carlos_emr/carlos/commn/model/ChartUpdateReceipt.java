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
import java.time.LocalDateTime;

/** Committed in the same transaction as the approved chart addition; contains no clinical prose. */
@Entity
@Table(name = "clinical_chart_update_receipt")
public class ChartUpdateReceipt {
    @Id @Column(name = "proposal_key", length = 64)
    private String key;
    @Column(name = "demographic_no", nullable = false)
    private int patient;
    @Column(name = "document_no", nullable = false)
    private int document;
    @Column(name = "provider_no", nullable = false, length = 20)
    private String provider;
    @Column(nullable = false, length = 16)
    private String kind;
    @Column(name = "target_id", nullable = false)
    private long target;
    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;
    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    protected ChartUpdateReceipt() { }
    public ChartUpdateReceipt(String key, int patient, int document, String provider, String kind, long target, String sourceHash) {
        this.key = key;
        this.patient = patient;
        this.document = document;
        this.provider = provider;
        this.kind = kind;
        this.target = target;
        this.sourceHash = sourceHash;
        acceptedAt = LocalDateTime.now();
    }
    public String getKey() { return key; }
    public String getSourceHash() { return sourceHash; }
    public String getKind() { return kind; }
    public long getTarget() { return target; }
}
