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

import io.github.carlos_emr.carlos.commn.model.CdsImportEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportEventDao extends AbstractDaoImpl<CdsImportEvent> {
    public CdsImportEventDao() { super(CdsImportEvent.class); }
    public CdsImportEvent lock(Long id) { return entityManager.find(CdsImportEvent.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportEvent get(Long id) { return entityManager.find(CdsImportEvent.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    /** Run-row locking serializes these control events; latest cancellation supersedes an older resume. */
    public long pendingResumeEvent(String runId) {
        var rows=entityManager.createQuery("select e.id from CdsImportEvent e where e.runId=:run and e.eventType='RESUME_REQUESTED' and e.id=(select max(last.id) from CdsImportEvent last where last.runId=:run and last.eventType='RESUME_REQUESTED') and not exists(select c.id from CdsImportEvent c where c.runId=:run and c.eventType='CANCEL_REQUESTED' and c.id>e.id) and not exists(select a.id from CdsImportEvent a where a.runId=:run and a.eventType='RESUME_INTENT_APPLIED' and a.id>e.id)", Long.class)
                .setParameter("run", runId).setMaxResults(1).getResultList();
        return rows.isEmpty()?0:rows.getFirst();
    }
    public List<CdsImportEvent> page(String runId, long afterId, int limit) {
        return entityManager.createQuery("select e from CdsImportEvent e where e.runId=:run and e.id>:after order by e.id", CdsImportEvent.class)
                .setParameter("run", runId).setParameter("after", afterId).setMaxResults(limit).getResultList();
    }
}
