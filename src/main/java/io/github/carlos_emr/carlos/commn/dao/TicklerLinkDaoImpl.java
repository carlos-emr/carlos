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

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.TicklerLink;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link TicklerLinkDao} for tickler (task reminder) data access.
 *
 * @since 2001
 */

public class TicklerLinkDaoImpl extends AbstractDaoImpl<TicklerLink> implements TicklerLinkDao {

    /** Constructs this DAO for the {@link TicklerLink} entity class. */

    public TicklerLinkDaoImpl() {
        super(TicklerLink.class);
    }

    /** {@inheritDoc} */

    @Override
    public TicklerLink getTicklerLink(Integer id) {
        return find(id);
    }

    /** {@inheritDoc} */

    @Override
    public List<TicklerLink> getLinkByTableId(String tableName, Long tableId) {
        Query query = entityManager.createQuery(
                "SELECT tLink from TicklerLink tLink WHERE tLink.tableName = ?1 and tLink.tableId = ?2 order by tLink.id");
        query.setParameter(1, tableName);
        query.setParameter(2, tableId);

        @SuppressWarnings("unchecked")
        List<TicklerLink> results = query.getResultList();

        return results;
    }

    /** {@inheritDoc} */

    @Override
    public List<TicklerLink> getLinkByTickler(Integer ticklerNo) {
        Query query = entityManager.createQuery("SELECT tLink from TicklerLink tLink WHERE tLink.ticklerNo = ?1 order by tLink.id");
        query.setParameter(1, ticklerNo);

        @SuppressWarnings("unchecked")
        List<TicklerLink> results = query.getResultList();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public void save(TicklerLink cLink) {
        persist(cLink);
    }

    /** {@inheritDoc} */

    @Override
    public void update(TicklerLink cLink) {
        merge(cLink);
    }
}
