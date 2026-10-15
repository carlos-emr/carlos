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
 *
 * Ported from the openo-beta/Open-O tickler attachment component
 * (PR #2491, Sebastian Ibanez) and adapted for CARLOS.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Collections;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.TicklerDocs;
import org.springframework.stereotype.Repository;

/**
 * Default {@link TicklerDocsDao}. Mirrors {@link ConsultDocsDaoImpl}; every query filters
 * out soft-deleted rows.
 *
 * @since 2026-09-26
 */
@Repository
@SuppressWarnings("unchecked")
public class TicklerDocsDaoImpl extends AbstractDaoImpl<TicklerDocs> implements TicklerDocsDao {

    public TicklerDocsDaoImpl() {
        super(TicklerDocs.class);
    }

    @Override
    public List<TicklerDocs> findByTicklerIdDocNoDocType(Integer ticklerId, Integer documentNo, String docType) {
        Query query = entityManager.createQuery(
                "select x from TicklerDocs x where x.ticklerId = :ticklerId and x.documentNo = :documentNo"
                        + " and x.docType = :docType and x.deleted is null order by x.id");
        query.setParameter("ticklerId", ticklerId);
        query.setParameter("documentNo", documentNo);
        query.setParameter("docType", docType);
        return query.getResultList();
    }

    @Override
    public List<TicklerDocs> findAllByTicklerIdForUpdate(Integer ticklerId) {
        Query query = entityManager.createQuery(
                "select x from TicklerDocs x where x.ticklerId = :ticklerId order by x.id");
        query.setParameter("ticklerId", ticklerId);
        query.setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList();
    }

    @Override
    public List<TicklerDocs> findByTicklerIdDocType(Integer ticklerId, String docType) {
        Query query = entityManager.createQuery(
                "select x from TicklerDocs x where x.ticklerId = :ticklerId and x.docType = :docType"
                        + " and x.deleted is null order by x.id");
        query.setParameter("ticklerId", ticklerId);
        query.setParameter("docType", docType);
        return query.getResultList();
    }

    @Override
    public List<TicklerDocs> findByTicklerId(Integer ticklerId) {
        Query query = entityManager.createQuery(
                "select x from TicklerDocs x where x.ticklerId = :ticklerId and x.deleted is null order by x.id");
        query.setParameter("ticklerId", ticklerId);
        return query.getResultList();
    }

    @Override
    public List<TicklerDocs> findByTicklerIds(List<Integer> ticklerIds) {
        // An unguarded "in ()" is not valid JPQL; a page with no ticklers reaches this finder.
        if (ticklerIds == null || ticklerIds.isEmpty()) {
            return Collections.emptyList();
        }
        Query query = entityManager.createQuery(
                "select x from TicklerDocs x where x.ticklerId in (:ticklerIds) and x.deleted is null"
                        + " order by x.ticklerId, x.id");
        query.setParameter("ticklerIds", ticklerIds);
        return query.getResultList();
    }

    @Override
    public List<TicklerDocs> findByDocument(Integer documentNo, String docType) {
        Query query = entityManager.createQuery(
                "select x from TicklerDocs x where x.documentNo = :documentNo and x.docType = :docType"
                        + " and x.deleted is null order by x.id");
        query.setParameter("documentNo", documentNo);
        query.setParameter("docType", docType);
        return query.getResultList();
    }

    @Override
    public List<TicklerDocs> findByLab(Integer labNo, String labType) {
        Query query = entityManager.createQuery(
                "select x from TicklerDocs x where x.documentNo = :labNo and x.docType = :docType"
                        + " and x.labType = :labType and x.deleted is null order by x.id");
        query.setParameter("labNo", labNo);
        query.setParameter("docType", TicklerDocs.DOCTYPE_LAB);
        query.setParameter("labType", labType);
        return query.getResultList();
    }
}
