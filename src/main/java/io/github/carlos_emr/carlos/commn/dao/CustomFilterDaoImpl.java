/**
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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.TypedQuery;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes saved tickler {@link CustomFilter}s.
 *
 * <p><b>Fetch plan (issue #5307).</b> A filter's provider and assignee sets are
 * {@code LAZY}, and nothing upstream keeps a persistence context open: the manager hands
 * these entities to callers outside any transaction. So every read here returns filters
 * with both sets and the program already loaded, in one statement per query
 * ({@link #DETAIL_FETCH}). Do not add a read that skips it; a caller touching
 * {@code getProviders()} on such a filter would get a {@code LazyInitializationException}.</p>
 *
 * <p>Both sets are {@code Set}s, so fetching them together cannot raise
 * {@code MultipleBagFetchException}. The join repeats a filter once per provider x assignee
 * pair; Hibernate collapses those rows to one filter per id.</p>
 */
@Repository
public class CustomFilterDaoImpl extends AbstractDaoImpl<CustomFilter> implements CustomFilterDao {

    /** The detail graph every read loads: both provider sets and the (optional) program. */
    static final String DETAIL_FETCH = "select c from CustomFilter c "
            + "left join fetch c.providers "
            + "left join fetch c.assignees "
            + "left join fetch c.program ";

    // Every read below is DETAIL_FETCH plus a fixed tail, so each query is a compile-time constant.
    private static final String FIND_BY_ID = DETAIL_FETCH + "where c.id = :id";
    private static final String FIND_BY_IDS = DETAIL_FETCH + "where c.id in (:ids) order by c.id";
    private static final String FIND_BY_NAME = DETAIL_FETCH
            + "where c.id = (select min(c2.id) from CustomFilter c2 where c2.name = :name)";
    private static final String FIND_BY_NAME_AND_PROVIDER = DETAIL_FETCH
            + "where c.id = (select min(c2.id) from CustomFilter c2 "
            + "where c2.name = :name and c2.providerNo = :providerNo)";
    private static final String FIND_ALL = DETAIL_FETCH + "order by c.id";
    private static final String FIND_BY_PROVIDER = DETAIL_FETCH + "where c.providerNo = :providerNo order by c.id";
    private static final String FIND_SHORTCUTS_BY_PROVIDER = DETAIL_FETCH
            + "where c.providerNo = :providerNo and c.shortcut = true order by c.id";

    public CustomFilterDaoImpl() {
        super(CustomFilter.class);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the filter with its provider sets and program loaded. A {@code null} or
     * non-{@code Integer} key keeps {@code EntityManager.find}'s own handling, and whatever it
     * returns is initialized before it is handed back.</p>
     */
    @Override
    public CustomFilter find(Object id) {
        if (id instanceof Integer filterId) {
            return firstOrNull(detailQuery(FIND_BY_ID).setParameter("id", filterId));
        }
        return initializeDetail(super.find(id));
    }

    @Override
    public CustomFilter find(int id) {
        return find(Integer.valueOf(id));
    }

    /** Loads the filter through {@link #find(Object)} and then detaches it. */
    @Override
    public CustomFilter findDetached(Object id) {
        CustomFilter filter = find(id);
        if (filter != null) {
            detach(filter);
        }
        return filter;
    }

    /**
     * Pages filter ids first, then loads that page with its detail graph: paging a
     * collection fetch directly would page the joined rows, not the filters.
     */
    @Override
    public List<CustomFilter> findAll(Integer offset, Integer limit) {
        int pageSize = (limit == null) ? getMaxSelectSize() : limit;
        if (pageSize > getMaxSelectSize()) {
            throw new MaxSelectLimitExceededException(getMaxSelectSize(), limit);
        }
        TypedQuery<Integer> ids = entityManager.createQuery(
                "select c.id from CustomFilter c order by c.id", Integer.class);
        if (offset != null && offset > 0) {
            ids.setFirstResult(offset);
        }
        ids.setMaxResults(pageSize);
        List<Integer> page = ids.getResultList();
        if (page.isEmpty()) {
            return new ArrayList<>();
        }
        return detailQuery(FIND_BY_IDS).setParameter("ids", page).getResultList();
    }

    /** When several filters share the name, returns the one with the lowest id. */
    @Override
    public CustomFilter findByName(String name) {
        return firstOrNull(detailQuery(FIND_BY_NAME).setParameter("name", name));
    }

    /** When the provider has several filters with the name, returns the one with the lowest id. */
    @Override
    public CustomFilter findByNameAndProviderNo(String name, String providerNo) {
        return firstOrNull(detailQuery(FIND_BY_NAME_AND_PROVIDER)
                .setParameter("name", name)
                .setParameter("providerNo", providerNo));
    }

    @Override
    public List<CustomFilter> getCustomFilters() {
        return detailQuery(FIND_ALL).getResultList();
    }

    @Override
    public List<CustomFilter> findByProviderNo(String providerNo) {
        return detailQuery(FIND_BY_PROVIDER)
                .setParameter("providerNo", providerNo)
                .getResultList();
    }

    @Override
    public List<CustomFilter> getCustomFilterWithShortCut(String providerNo) {
        return detailQuery(FIND_SHORTCUTS_BY_PROVIDER)
                .setParameter("providerNo", providerNo)
                .getResultList();
    }

    @Override
    public void deleteCustomFilter(String name) {
        CustomFilter filter = findByName(name);
        if (filter != null) {
            remove(filter);
        }
    }

    /** A typed query for one of the constant {@link #DETAIL_FETCH} reads above. */
    private TypedQuery<CustomFilter> detailQuery(String jpql) {
        return entityManager.createQuery(jpql, CustomFilter.class);
    }

    /**
     * The first filter the query returns, or {@code null}. Callers narrow the query to one
     * filter id; setMaxResults is not used because it would cut the joined rows, not filters.
     */
    private static CustomFilter firstOrNull(TypedQuery<CustomFilter> query) {
        List<CustomFilter> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /** Loads both provider sets of a filter the EntityManager returned; {@code null} passes through. */
    private static CustomFilter initializeDetail(CustomFilter filter) {
        if (filter != null) {
            Hibernate.initialize(filter.getProviders());
            Hibernate.initialize(filter.getAssignees());
        }
        return filter;
    }
}
