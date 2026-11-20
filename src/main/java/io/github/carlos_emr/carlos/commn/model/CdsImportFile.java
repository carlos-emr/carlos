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
@Table(name = "cds_import_file")
public class CdsImportFile extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "location_key", nullable = false, length = 64)
    private String locationKey;

    @Column(name = "location", nullable = false, length = 1024)
    private String location;

    @Column(name = "digest", nullable = false, length = 64)
    private String digest;

    @Column(name = "byte_length", nullable = false)
    private long byteLength;

    @Column(name = "state", nullable = false, length = 32)
    private String state;

    @Column(name = "verified_epoch", nullable = false)
    private long verifiedEpoch;

    @Column(name = "generation", nullable = false)
    private long generation;

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public String getLocationKey() { return locationKey; }
    public void setLocationKey(String value) { locationKey = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value; }
    public String getDigest() { return digest; }
    public void setDigest(String value) { digest = value; }
    public long getByteLength() { return byteLength; }
    public void setByteLength(long value) { byteLength = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public long getVerifiedEpoch() { return verifiedEpoch; }
    public void setVerifiedEpoch(long value) { verifiedEpoch=value; }
    public long getGeneration() { return generation; }
    public void setGeneration(long value) { generation = value; }

    @Override
    public String toString() { return "CdsImportFile[" + id + "]"; }
}
