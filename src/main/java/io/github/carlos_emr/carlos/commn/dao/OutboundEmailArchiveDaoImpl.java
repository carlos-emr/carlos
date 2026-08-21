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

import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.Collection;

/**
 * JPA DAO implementation for durable outbound email archive records.
 *
 * @since 2026-08-14
 */
@Repository
public class OutboundEmailArchiveDaoImpl extends AbstractDaoImpl<OutboundEmailArchive> implements OutboundEmailArchiveDao {

    public OutboundEmailArchiveDaoImpl() {
        super(OutboundEmailArchive.class);
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
        // Single text block rather than concatenated literals: the repo's SQL-safety hook treats
        // any '+' inside createQuery(...) as an injection risk, and a constant-only concatenation
        // is not worth an exception to that rule.
        TypedQuery<OutboundEmailArchive> query = entityManager.createQuery("""
                SELECT archive FROM OutboundEmailArchive archive
                LEFT JOIN FETCH archive.demographic
                LEFT JOIN FETCH archive.document
                WHERE archive.id = :archiveId
                """,
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
    public boolean existsByDocumentNo(Integer documentNo) {
        if (documentNo == null) {
            return false;
        }
        // Matches the archive's own artifact document OR any attachment's document: both are
        // eDocs on the patient file, and both are equally part of the record of what was sent.
        TypedQuery<Long> query = entityManager.createQuery("""
                SELECT COUNT(archive) FROM OutboundEmailArchive archive
                WHERE archive.document.documentNo = :documentNo
                   OR EXISTS (SELECT attachment.id FROM OutboundEmailArchiveAttachment attachment
                              WHERE attachment.archive = archive
                                AND attachment.document.documentNo = :documentNo)
                """,
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
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }
        TypedQuery<Integer> query = entityManager.createQuery("""
                SELECT DISTINCT document.documentNo FROM Document document
                WHERE document.documentNo IN :documentNos
                  AND (EXISTS (SELECT archive.id FROM OutboundEmailArchive archive
                               WHERE archive.document = document)
                    OR EXISTS (SELECT attachment.id FROM OutboundEmailArchiveAttachment attachment
                               WHERE attachment.document = document))
                """,
                Integer.class);
        query.setParameter("documentNos", candidates);
        return Set.copyOf(query.getResultList());
    }

    @Override
    public boolean existsByFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        TypedQuery<Long> query = entityManager.createQuery("""
                SELECT COUNT(archive) FROM OutboundEmailArchive archive
                WHERE archive.fileName = :fileName
                   OR EXISTS (SELECT attachment.id FROM OutboundEmailArchiveAttachment attachment
                              WHERE attachment.archive = archive AND attachment.fileName = :fileName)
                """,
                Long.class);
        query.setParameter("fileName", fileName);
        return query.getSingleResult() > 0L;
    }

    @Override
    public Integer findDemographicNoById(Integer archiveId) {
        if (archiveId == null) {
            return null;
        }
        // Scalar projection on purpose. Dereferencing only the identifier of a @ManyToOne
        // reads the FK column without a join, and selecting a scalar leaves the
        // persistence context empty so findForUpdate still hydrates under its lock.
        TypedQuery<Integer> query = entityManager.createQuery(
                "SELECT archive.demographic.demographicNo FROM OutboundEmailArchive archive WHERE archive.id = :archiveId",
                Integer.class);
        query.setParameter("archiveId", archiveId);
        List<Integer> rows = query.getResultList();
        return rows.isEmpty() ? null : rows.get(0);
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
