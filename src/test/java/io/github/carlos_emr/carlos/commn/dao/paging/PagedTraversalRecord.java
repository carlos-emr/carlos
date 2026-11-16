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
package io.github.carlos_emr.carlos.commn.dao.paging;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Test-only synthetic row for the {@code findAll} paging regression (issue #5308).
 *
 * <p>The table holds nothing but its key and one indexed column, so the secondary index on
 * {@code sort_key} covers every column. For a query without {@code ORDER BY}, MariaDB then prefers
 * a scan of that smaller index over the clustered primary key and returns rows in {@code sort_key}
 * order. Fixtures give {@code sort_key} values in the opposite order to the generated ids, so an
 * unordered page comes back visibly out of identifier order on MariaDB. The table exists only in the
 * Hibernate-generated test schema; it is never part of a Flyway migration.</p>
 *
 * @since 2026-10-29
 */
@Entity
@Table(name = "carlos_test_paged_record",
        indexes = @Index(name = "idx_carlos_test_paged_record_sort", columnList = "sort_key"))
public class PagedTraversalRecord extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "sort_key", nullable = false, length = 32)
    private String sortKey;

    public PagedTraversalRecord() {
    }

    public PagedTraversalRecord(String sortKey) {
        this.sortKey = sortKey;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public String getSortKey() {
        return sortKey;
    }
}
