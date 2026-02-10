//CHECKSTYLE:OFF
/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
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
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 *
 * <p>
 * Ported from openo-beta/Open-O PR #2268 by LiamStanziani.
 * Original: ca.openosp.openo.tickler.dto.TicklerLinkDTO
 */
package io.github.carlos_emr.carlos.tickler.dto;

import java.io.Serializable;

/**
 * Data Transfer Object for Tickler links in list views.
 * <p>
 * This DTO provides a lightweight representation of tickler link data,
 * used for batch loading links to avoid N+1 query problems.
 * </p>
 *
 * @since 2026-01-30
 */
public class TicklerLinkDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer ticklerNo;
    private String tableName;
    private Long tableId;

    /**
     * Default constructor required for frameworks.
     */
    public TicklerLinkDTO() {
    }

    /**
     * Constructor for JPQL projection queries.
     *
     * @param id Integer the link ID
     * @param ticklerNo Integer the parent tickler ID
     * @param tableName String the linked table name (determines link type)
     * @param tableId Long the ID in the linked table
     */
    public TicklerLinkDTO(Integer id, Integer ticklerNo, String tableName, Long tableId) {
        this.id = id;
        this.ticklerNo = ticklerNo;
        this.tableName = tableName;
        this.tableId = tableId;
    }

    /**
     * Returns the link ID.
     *
     * @return Integer the unique identifier for this link
     * @since 2026-01-30
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the link ID.
     *
     * @param id Integer the unique identifier for this link
     * @since 2026-01-30
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Returns the parent tickler ID.
     *
     * @return Integer the tickler ID this link belongs to
     * @since 2026-01-30
     */
    public Integer getTicklerNo() {
        return ticklerNo;
    }

    /**
     * Sets the parent tickler ID.
     *
     * @param ticklerNo Integer the tickler ID this link belongs to
     * @since 2026-01-30
     */
    public void setTicklerNo(Integer ticklerNo) {
        this.ticklerNo = ticklerNo;
    }

    /**
     * Returns the linked table name that determines the link type.
     *
     * @return String the table name (e.g., "HL7", "document", "HRM")
     * @since 2026-01-30
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Sets the linked table name.
     *
     * @param tableName String the table name that determines link type
     * @since 2026-01-30
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Returns the ID of the linked record in the target table.
     *
     * @return Long the record ID in the linked table
     * @since 2026-01-30
     */
    public Long getTableId() {
        return tableId;
    }

    /**
     * Sets the ID of the linked record in the target table.
     *
     * @param tableId Long the record ID in the linked table
     * @since 2026-01-30
     */
    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }
}
