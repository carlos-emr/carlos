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
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Test-only synthetic row with a composite {@code @EmbeddedId}, for the {@code findAll} paging
 * regression (issue #5308).
 *
 * <p>Like {@link PagedTraversalRecord}, the only non-key column carries a secondary index that
 * covers the whole table, so MariaDB returns an unordered query in {@code sort_key} order rather
 * than key order. Exists only in the Hibernate-generated test schema.</p>
 *
 * @since 2026-10-29
 */
@Entity
@Table(name = "carlos_test_paged_composite",
        indexes = @Index(name = "idx_carlos_test_paged_composite_sort", columnList = "sort_key"))
public class PagedTraversalCompositeRecord extends AbstractModel<PagedTraversalCompositeKey> {

    @EmbeddedId
    private PagedTraversalCompositeKey id;

    @Column(name = "sort_key", nullable = false, length = 32)
    private String sortKey;

    public PagedTraversalCompositeRecord() {
    }

    public PagedTraversalCompositeRecord(PagedTraversalCompositeKey id, String sortKey) {
        this.id = id;
        this.sortKey = sortKey;
    }

    @Override
    public PagedTraversalCompositeKey getId() {
        return id;
    }

    public String getSortKey() {
        return sortKey;
    }
}
