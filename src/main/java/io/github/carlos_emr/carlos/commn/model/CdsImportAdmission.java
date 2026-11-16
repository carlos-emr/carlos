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
@Table(name = "cds_import_admission")
public class CdsImportAdmission extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "owner_run_id", nullable = true, length = 36)
    private String ownerRunId;

    @Column(name = "reserved_bytes", nullable = false)
    private long reservedBytes;

    @Column(name = "maintenance_active", nullable = false)
    private boolean maintenanceActive;
    @Column(name = "maintenance_generation", nullable = false)
    private long maintenanceGeneration;
    @Column(name = "maintenance_owner_token", length = 36)
    private String maintenanceOwnerToken;
    @Column(name = "maintenance_connection_id")
    private Long maintenanceConnectionId;
    public boolean isMaintenanceActive() { return maintenanceActive; }
    public void setMaintenanceActive(boolean value) { maintenanceActive=value; }
    public long getMaintenanceGeneration() { return maintenanceGeneration; }
    public void setMaintenanceGeneration(long value) { maintenanceGeneration=value; }
    public String getMaintenanceOwnerToken() { return maintenanceOwnerToken; }
    public void setMaintenanceOwnerToken(String value) { maintenanceOwnerToken=value; }
    public Long getMaintenanceConnectionId() { return maintenanceConnectionId; }
    public void setMaintenanceConnectionId(Long value) { maintenanceConnectionId=value; }

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerRunId() { return ownerRunId; }
    public void setOwnerRunId(String value) { ownerRunId = value; }
    public long getReservedBytes() { return reservedBytes; }
    public void setReservedBytes(long value) { reservedBytes = value; }

    @Override
    public String toString() { return "CdsImportAdmission[" + id + "]"; }
}
