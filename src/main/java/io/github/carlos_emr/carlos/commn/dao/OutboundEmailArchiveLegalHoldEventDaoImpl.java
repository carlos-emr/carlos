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

import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveLegalHoldEvent;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA DAO implementation for outbound email archive legal hold events.
 *
 * @since 2026-08-17
 */
@Repository
public class OutboundEmailArchiveLegalHoldEventDaoImpl
        extends AbstractDaoImpl<OutboundEmailArchiveLegalHoldEvent>
        implements OutboundEmailArchiveLegalHoldEventDao {

    public OutboundEmailArchiveLegalHoldEventDaoImpl() {
        super(OutboundEmailArchiveLegalHoldEvent.class);
    }

    @Override
    public List<OutboundEmailArchiveLegalHoldEvent> findByArchiveId(Integer archiveId) {
        // Secondary ordering by id keeps place/release pairs stable when a hold is
        // released and re-applied inside the same DATETIME second.
        TypedQuery<OutboundEmailArchiveLegalHoldEvent> query = entityManager.createQuery(
                "SELECT event FROM OutboundEmailArchiveLegalHoldEvent event WHERE event.archive.id = :archiveId ORDER BY event.eventAt DESC, event.id DESC",
                OutboundEmailArchiveLegalHoldEvent.class);
        query.setParameter("archiveId", archiveId);
        return query.getResultList();
    }
}
