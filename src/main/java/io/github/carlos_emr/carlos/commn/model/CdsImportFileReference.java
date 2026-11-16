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
@Table(name = "cds_import_file_reference")
public class CdsImportFileReference extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "unit_key", nullable = false, length = 64)
    private String unitKey;

    @Column(name = "file_id", nullable = false, length = 64)
    private String fileId;

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public String getUnitKey() { return unitKey; }
    public void setUnitKey(String value) { unitKey = value; }
    public String getFileId() { return fileId; }
    public void setFileId(String value) { fileId = value; }

    @Override
    public String toString() { return "CdsImportFileReference[" + id + "]"; }
}
