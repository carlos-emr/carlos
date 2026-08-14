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

package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JPA DAO implementation for durable outbound email archive records.
 */
@Repository
public class OutboundEmailArchiveDaoImpl extends AbstractDaoImpl<OutboundEmailArchive> implements OutboundEmailArchiveDao {

    private static final String PHYSICAL_DELETE_DISABLED_MESSAGE =
            "Outbound email archives must be deleted through the controlled tombstone workflow";

    public OutboundEmailArchiveDaoImpl() {
        super(OutboundEmailArchive.class);
    }

    @Override
    public void remove(AbstractModel<?> o) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public boolean remove(Object id) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public void batchRemove(List<OutboundEmailArchive> oList) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public void batchRemove(List<OutboundEmailArchive> oList, int batchSize) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public void batchRemoveAtomically(List<OutboundEmailArchive> oList) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public void batchRemoveAtomically(List<OutboundEmailArchive> oList, int batchSize) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public void batchRemoveWithIndependentCommits(List<OutboundEmailArchive> oList) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public void batchRemoveWithIndependentCommits(List<OutboundEmailArchive> oList, int batchSize) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public List<OutboundEmailArchive> findByEmailLogId(Integer emailLogId) {
        TypedQuery<OutboundEmailArchive> query = entityManager.createQuery(
                "SELECT archive FROM OutboundEmailArchive archive WHERE archive.emailLog.id = :emailLogId ORDER BY archive.archivedAt DESC",
                OutboundEmailArchive.class);
        query.setParameter("emailLogId", emailLogId);
        return query.getResultList();
    }

    @Override
    public OutboundEmailArchive findForRead(Integer archiveId) {
        if (archiveId == null) {
            return null;
        }
        TypedQuery<OutboundEmailArchive> query = entityManager.createQuery(
                "SELECT archive FROM OutboundEmailArchive archive "
                        + "LEFT JOIN FETCH archive.demographic "
                        + "LEFT JOIN FETCH archive.document "
                        + "WHERE archive.id = :archiveId",
                OutboundEmailArchive.class);
        query.setParameter("archiveId", archiveId);
        List<OutboundEmailArchive> rows = query.getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public OutboundEmailArchive findForUpdate(Integer archiveId) {
        if (archiveId == null) {
            return null;
        }
        Query query = entityManager.createNativeQuery(
                "SELECT * FROM outboundEmailArchive WHERE id = ?1 FOR UPDATE",
                OutboundEmailArchive.class);
        query.setParameter(1, archiveId);
        List<OutboundEmailArchive> rows = query.getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public boolean existsActiveByDocumentNo(Integer documentNo) {
        if (documentNo == null) {
            return false;
        }
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(archive) FROM OutboundEmailArchive archive "
                        + "WHERE archive.document.documentNo = :documentNo "
                        + "AND archive.deleted = false",
                Long.class);
        query.setParameter("documentNo", documentNo);
        return query.getSingleResult() > 0L;
    }

    @Override
    public boolean existsByDocumentNo(Integer documentNo) {
        if (documentNo == null) {
            return false;
        }
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(archive) FROM OutboundEmailArchive archive "
                        + "WHERE archive.document.documentNo = :documentNo",
                Long.class);
        query.setParameter("documentNo", documentNo);
        return query.getSingleResult() > 0L;
    }

    @Override
    public Set<Integer> findExistingDocumentNos(Collection<Integer> documentNos) {
        if (documentNos == null || documentNos.isEmpty()) {
            return Set.of();
        }
        List<Integer> candidates = documentNos.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }
        TypedQuery<Integer> query = entityManager.createQuery(
                "SELECT DISTINCT archive.document.documentNo FROM OutboundEmailArchive archive "
                        + "WHERE archive.document.documentNo IN :documentNos",
                Integer.class);
        query.setParameter("documentNos", candidates);
        return new HashSet<>(query.getResultList());
    }

    @Override
    public boolean existsByFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(archive) FROM OutboundEmailArchive archive "
                        + "WHERE archive.fileName = :fileName",
                Long.class);
        query.setParameter("fileName", fileName);
        return query.getSingleResult() > 0L;
    }

    @Override
    public List<OutboundEmailArchive> findByDemographicNo(Integer demographicNo) {
        TypedQuery<OutboundEmailArchive> query = entityManager.createQuery(
                "SELECT archive FROM OutboundEmailArchive archive WHERE archive.demographic.demographicNo = :demographicNo ORDER BY archive.archivedAt DESC",
                OutboundEmailArchive.class);
        query.setParameter("demographicNo", demographicNo);
        return query.getResultList();
    }
}
