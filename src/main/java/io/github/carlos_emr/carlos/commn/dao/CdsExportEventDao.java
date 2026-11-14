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

import io.github.carlos_emr.carlos.commn.model.CdsExportEvent;
import org.springframework.stereotype.Repository;

/** Append-only export lifecycle/disclosure audit; no clinical payload or credentials. */
@Repository
public class CdsExportEventDao extends AbstractDaoImpl<CdsExportEvent> {
    public CdsExportEventDao() { super(CdsExportEvent.class); }
    /**
     * One page of a run's events, newest first, after {@code after} (null for the first page);
     * a report reads every page. Only lifecycle scalars are projected; actor identifiers and
     * physical capabilities never leave this DAO.
     */
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportAuditRowDto> page(
            String run, io.github.carlos_emr.carlos.commn.dto.CdsExportAuditRowDto after, int page) {
        if (page < 1) throw new IllegalArgumentException("CDS_EXPORT_EVENT_PAGE_INVALID");
        var query = entityManager.createQuery("select new io.github.carlos_emr.carlos.commn.dto.CdsExportAuditRowDto(e.id,e.eventType,e.epoch,e.safeCode,e.createdAt) from CdsExportEvent e where e.runId=:run"
                        + (after == null ? "" : " and (e.createdAt<:at or (e.createdAt=:at and e.id<:id))")
                        + " order by e.createdAt desc,e.id desc",
                io.github.carlos_emr.carlos.commn.dto.CdsExportAuditRowDto.class).setParameter("run", run);
        if (after != null) query.setParameter("at", after.at()).setParameter("id", after.id());
        return query.setMaxResults(page).getResultList();
    }

    /** Safe codes of one generation's events of one type, oldest first; no actor or payload. */
    public java.util.List<String> safeCodes(String run, long epoch, String type, int limit) {
        return entityManager.createQuery("select e.safeCode from CdsExportEvent e where e.runId=:run and e.epoch=:epoch and e.eventType=:type order by e.createdAt asc,e.id asc",
                String.class).setParameter("run", run).setParameter("epoch", epoch).setParameter("type", type).setMaxResults(limit).getResultList();
    }
}
