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

/** Private bounded prepared native data; source field evidence stays independently lossless. */
@Entity @Table(name="cds_import_preparation")
public class CdsImportPreparation extends AbstractModel<String> {
    @Id @Column(length=64) private String id;
    @Column(name="run_id", length=36, nullable=false) private String runId;
    @Column(name="unit_key", length=64, nullable=false) private String unitKey;
    @Column(name="source_digest", length=64, nullable=false) private String sourceDigest;
    @Column(name="options_digest", length=64, nullable=false) private String optionsDigest;
    @Column(name="ruleset_digest", length=64, nullable=false) private String rulesetDigest;
    @Column(name="payload_type", length=250, nullable=false) private String payloadType;
    @Lob @Column(columnDefinition="MEDIUMTEXT", nullable=false) private String payload;
    @Column(length=64, nullable=false) private String digest;
    @Override public String getId(){return id; } public void setId(String value){id=value; }
    public String getRunId(){return runId; } public void setRunId(String value){runId=value; }
    public String getUnitKey(){return unitKey; } public void setUnitKey(String value){unitKey=value; }
    public String getSourceDigest(){return sourceDigest; } public void setSourceDigest(String value){sourceDigest=value; }
    public String getOptionsDigest(){return optionsDigest; } public void setOptionsDigest(String value){optionsDigest=value; }
    public String getRulesetDigest(){return rulesetDigest; } public void setRulesetDigest(String value){rulesetDigest=value; }
    public String getPayloadType(){return payloadType; } public void setPayloadType(String value){payloadType=value; }
    public String getPayload(){return payload; } public void setPayload(String value){payload=value; }
    public String getDigest(){return digest; } public void setDigest(String value){digest=value; }
    @Override public String toString(){return "CdsImportPreparation[protected]"; }
}
