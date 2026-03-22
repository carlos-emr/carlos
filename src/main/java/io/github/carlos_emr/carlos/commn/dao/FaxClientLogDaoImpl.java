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

import java.util.Collections;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.FaxClientLog;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link FaxClientLogDao} for fax data access.
 *
 * @since 2001
 */

public class FaxClientLogDaoImpl extends AbstractDaoImpl<FaxClientLog> implements FaxClientLogDao {

    /** Constructs this DAO for the {@link FaxClientLog} entity class. */

    public FaxClientLogDaoImpl() {
        super(FaxClientLog.class);
    }

    /** {@inheritDoc} */

    @Override
    public FaxClientLog findClientLogbyFaxId(int faxId) {
        Query query = entityManager.createQuery("select log from FaxClientLog log where log.faxId = ?1");

        // faxId is the id for an entry in the Faxes table.
        query.setParameter(1, faxId);

        return super.getSingleResultOrNull(query);
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<FaxClientLog> findClientLogbyFaxIds(List<Integer> faxIds) {
        if (faxIds == null || faxIds.size() == 0) {
            return Collections.emptyList();
        }

        Query query = entityManager.createNativeQuery("SELECT * FROM FaxClientLog WHERE faxId IN (?1)",
                FaxClientLog.class);
        query.setParameter(1, faxIds);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<FaxClientLog> findClientLogbyRequestId(int requestId) {

        // only the most recent entries
        Query query = entityManager.createQuery(
                "select log from FaxClientLog log where log.requestId = ?1 order by log.startTime desc");

        // faxId is the id for an entry in the Faxes table.
        query.setParameter(1, requestId);
        List<FaxClientLog> results = query.getResultList();
        if (results == null) {
            results = Collections.emptyList();
        }
        return results;
    }
}
