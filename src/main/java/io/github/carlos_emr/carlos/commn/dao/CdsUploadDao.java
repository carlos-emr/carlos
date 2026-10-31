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

import io.github.carlos_emr.carlos.commn.model.CdsUpload;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** One-table owner-scoped upload queries with explicit page limits. */
@Repository
public class CdsUploadDao extends AbstractDaoImpl<CdsUpload> {
    public CdsUploadDao() { super(CdsUpload.class); }
    public CdsUpload lock(String id) { return entityManager.find(CdsUpload.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsUpload get(String id) { return entityManager.find(CdsUpload.class, id); }
    public CdsUpload lockActualRun(String runId) {
        var rows = entityManager.createQuery("select u from CdsUpload u where u.runId=:run", CdsUpload.class)
                .setParameter("run", runId).setMaxResults(2).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if (rows.size() > 1) throw new IllegalStateException("UPLOAD_RUN_ASSOCIATION_CONFLICT");
        return rows.isEmpty() ? null : rows.get(0);
    }
    public long reservedCount() {
        return entityManager.createQuery("select count(u) from CdsUpload u where u.quotaReleased=false", Long.class).getSingleResult();
    }
    public List<CdsUpload> page(String operator, String after, int limit) {
        return entityManager.createQuery("select u from CdsUpload u where u.operator=:operator and u.id>:after and u.state<>'ABANDONED' order by u.id", CdsUpload.class)
                .setParameter("operator", operator).setParameter("after", after).setMaxResults(limit).getResultList();
    }
    public List<CdsUpload> expired(Instant before, int limit) {
        return entityManager.createQuery("select u from CdsUpload u where u.state in ('OPEN','COMPLETE','ABANDONED') and u.updatedAt<:before order by u.updatedAt,u.id", CdsUpload.class)
                .setParameter("before", before).setMaxResults(limit).getResultList();
    }
    public void flush() { entityManager.flush(); }
}
