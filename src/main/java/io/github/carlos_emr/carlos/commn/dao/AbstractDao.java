/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public interface AbstractDao<T extends AbstractModel<?>> {
    public static final int MAX_LIST_RETURN_SIZE = 5000;

    void merge(AbstractModel<?> o);

    void persist(AbstractModel<?> o);

    /**
     * @deprecated Transaction semantics are ambiguous. Use an explicitly named batch method.
     */
    @Deprecated
    void batchPersist(List<T> oList);

    /**
     * @deprecated Transaction semantics are ambiguous. Use an explicitly named batch method.
     */
    @Deprecated
    void batchPersist(List<T> oList, int batchSize);

    void batchPersistAtomically(List<T> oList);

    void batchPersistAtomically(List<T> oList, int batchSize);

    void batchPersistWithIndependentCommits(List<T> oList);

    void batchPersistWithIndependentCommits(List<T> oList, int batchSize);

    void remove(AbstractModel<?> o);

    /**
     * @deprecated Transaction semantics are ambiguous. Use an explicitly named batch method.
     */
    @Deprecated
    void batchRemove(List<T> oList);

    /**
     * @deprecated Transaction semantics are ambiguous. Use an explicitly named batch method.
     */
    @Deprecated
    void batchRemove(List<T> oList, int batchSize);

    void batchRemoveAtomically(List<T> oList);

    void batchRemoveAtomically(List<T> oList, int batchSize);

    void batchRemoveWithIndependentCommits(List<T> oList);

    void batchRemoveWithIndependentCommits(List<T> oList, int batchSize);

    void refresh(AbstractModel<?> o);

    T find(Object id);

    T find(int id);

    T findDetached(Object id);

    void detach(@Nonnull T t);

    boolean contains(AbstractModel<?> o);

    /**
     * Returns one page of all rows of this DAO's entity, in a deterministic order.
     *
     * <p><strong>Order.</strong> Rows are ordered by the entity identifier, ascending; a DAO may put
     * a domain order (for example by name) in front of it, but the identifier always comes last as
     * the unique tie-breaker. For a composite key every key attribute takes part, sorted by
     * attribute name. Pages read with increasing offsets therefore concatenate to the same sequence
     * a single unpaged read would return, without repeats or gaps, as long as the table does not
     * change in between.</p>
     *
     * <p><strong>Limits.</strong> {@code limit} defaults to, and may not exceed, the DAO's maximum
     * select size ({@link #MAX_LIST_RETURN_SIZE} unless the DAO lowers it); a larger value throws
     * {@link MaxSelectLimitExceededException} before any query runs. A {@code null}, zero or
     * negative {@code offset} starts at the first row.</p>
     *
     * <p><strong>Concurrent changes.</strong> {@code ORDER BY} fixes the order of the rows, not the
     * set of rows: it is not a snapshot. When pages are read in separate transactions, a row deleted
     * from an earlier page shifts every later row back by one, so the next page skips a row, and a
     * row inserted ahead of the current offset is returned twice. Rows inserted with a higher
     * identifier than any already read (the normal case for generated identifiers) simply appear on
     * a later page. With a domain order, an update to a sort column can move a row across pages. A
     * caller that must visit each row exactly once while other users write should either read all
     * pages in one transaction (MariaDB's default {@code REPEATABLE READ} isolation then serves
     * every page from the same consistent snapshot), or page by key instead of by offset, asking for
     * rows whose identifier is greater than the last one it has seen, which neither skips nor
     * repeats rows that exist for the whole traversal.</p>
     *
     * @param offset index of the first row to return, counted from zero
     * @param limit  maximum number of rows to return, or {@code null} for the maximum select size
     * @return the requested page, possibly empty
     * @throws MaxSelectLimitExceededException if {@code limit} is greater than the maximum select size
     */
    List<T> findAll(Integer offset, Integer limit);

    boolean remove(Object id);

    int getCountAll();

    List<Object[]> runParameterizedNativeQuery(String sql, Map<String, Object> params);

    T saveEntity(T entity);

    Class<T> getModelClass();

    void flush();

}
