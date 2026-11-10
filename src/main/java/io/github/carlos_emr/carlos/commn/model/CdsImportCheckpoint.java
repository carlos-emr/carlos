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
@Table(name = "cds_import_checkpoint")
public class CdsImportCheckpoint extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "lineage_key", nullable = false, length = 64)
    private String lineageKey;

    @Column(name = "source_digest", nullable = false, length = 64)
    private String sourceDigest;

    @Column(name = "options_digest", nullable = false, length = 64)
    private String optionsDigest;

    @Column(name = "ruleset_digest", nullable = false, length = 64)
    private String rulesetDigest;

    @Column(name = "target_references", nullable = false, columnDefinition = "longtext")
    private String targetReferences;

    @Column(name = "target_digest", nullable = false, length = 64)
    private String targetDigest;

    @Column(name = "first_run_id", nullable = false, length = 36)
    private String firstRunId;

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLineageKey() { return lineageKey; }
    public void setLineageKey(String value) { lineageKey = value; }
    public String getSourceDigest() { return sourceDigest; }
    public void setSourceDigest(String value) { sourceDigest = value; }
    public String getOptionsDigest() { return optionsDigest; }
    public void setOptionsDigest(String value) { optionsDigest = value; }
    public String getRulesetDigest() { return rulesetDigest; }
    public void setRulesetDigest(String value) { rulesetDigest = value; }
    public String getTargetReferences() { return targetReferences; }
    public void setTargetReferences(String value) { targetReferences = value; }
    public String getTargetDigest() { return targetDigest; }
    public void setTargetDigest(String value) { targetDigest = value; }
    public String getFirstRunId() { return firstRunId; }
    public void setFirstRunId(String value) { firstRunId = value; }
    public Instant getCommittedAt() { return committedAt; }
    public void setCommittedAt(Instant value) { committedAt = value; }

    @Override
    public String toString() { return "CdsImportCheckpoint[" + id + "]"; }
}
