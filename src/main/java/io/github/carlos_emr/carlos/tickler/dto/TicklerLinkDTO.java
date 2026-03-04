/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.tickler.dto;

import java.io.Serializable;

/**
 * Lightweight data transfer object for tickler link display, used for
 * batch loading links to avoid N+1 query problems. The {@code tableName}
 * field determines the link type (e.g., "HL7", "document", "HRM").
 *
 * @since 2026-02-27
 */
public class TicklerLinkDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer ticklerNo;
    private String tableName;
    private Long tableId;

    /**
     * Default constructor required by frameworks.
     */
    public TicklerLinkDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions.
     *
     * @param id Integer the link ID
     * @param ticklerNo Integer the parent tickler ID
     * @param tableName String the linked table name (determines link type)
     * @param tableId Long the record ID in the linked table
     */
    public TicklerLinkDTO(Integer id, Integer ticklerNo, String tableName, Long tableId) {
        this.id = id;
        this.ticklerNo = ticklerNo;
        this.tableName = tableName;
        this.tableId = tableId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTicklerNo() {
        return ticklerNo;
    }

    public void setTicklerNo(Integer ticklerNo) {
        this.ticklerNo = ticklerNo;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }
}
