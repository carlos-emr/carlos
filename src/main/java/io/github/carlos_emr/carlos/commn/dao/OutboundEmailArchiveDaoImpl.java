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
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA DAO implementation for durable outbound email archive records.
 */
@Repository
public class OutboundEmailArchiveDaoImpl extends AbstractDaoImpl<OutboundEmailArchive> implements OutboundEmailArchiveDao {

    public OutboundEmailArchiveDaoImpl() {
        super(OutboundEmailArchive.class);
    }

    @Override
    public List<OutboundEmailArchive> findByEmailLogId(Integer emailLogId) {
        TypedQuery<OutboundEmailArchive> query = entityManager.createQuery(
                "SELECT archive FROM OutboundEmailArchive archive WHERE archive.emailLog.id = :emailLogId ORDER BY archive.archivedAt DESC, archive.id DESC",
                OutboundEmailArchive.class);
        query.setParameter("emailLogId", emailLogId);
        return query.getResultList();
    }

    @Override
    public OutboundEmailArchive findForUpdate(Integer archiveId) {
        if (archiveId == null) {
            return null;
        }
        TypedQuery<OutboundEmailArchive> query = entityManager.createQuery(
                "SELECT archive FROM OutboundEmailArchive archive WHERE archive.id = :archiveId",
                OutboundEmailArchive.class);
        query.setParameter("archiveId", archiveId);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        List<OutboundEmailArchive> rows = query.getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<OutboundEmailArchive> findByDemographicNo(Integer demographicNo) {
        TypedQuery<OutboundEmailArchive> query = entityManager.createQuery(
                "SELECT archive FROM OutboundEmailArchive archive WHERE archive.demographic.demographicNo = :demographicNo ORDER BY archive.archivedAt DESC, archive.id DESC",
                OutboundEmailArchive.class);
        query.setParameter("demographicNo", demographicNo);
        return query.getResultList();
    }
}
