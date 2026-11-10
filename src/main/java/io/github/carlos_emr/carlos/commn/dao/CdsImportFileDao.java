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

import io.github.carlos_emr.carlos.commn.model.CdsImportFile;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportFileDao extends AbstractDaoImpl<CdsImportFile> {
    public CdsImportFileDao() { super(CdsImportFile.class); }
    public CdsImportFile lock(String id) { return entityManager.find(CdsImportFile.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportFile get(String id) { return entityManager.find(CdsImportFile.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    public CdsImportFile findLocation(String locationKey) {
        return entityManager.createQuery("select f from CdsImportFile f where f.locationKey=:key", CdsImportFile.class).setParameter("key", locationKey)
                .setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    }
    public java.util.List<CdsImportFile> referenced(String runId, String afterId, int limit) {
        return entityManager.createQuery("select f from CdsImportFile f where f.id>:after and exists(select r.id from CdsImportFileReference r where r.fileId=f.id and (r.runId=:run or exists(select u.id from CdsImportUnit u,CdsImportCheckpoint c where u.runId=:run and u.unitKey=r.unitKey and u.unitKey=c.id and c.firstRunId=r.runId and u.state='ALREADY_COMPLETE'))) order by f.id", CdsImportFile.class)
                .setParameter("run", runId).setParameter("after", afterId).setMaxResults(limit).getResultList();
    }
    public java.util.List<Object[]> owned(String runId, String afterId, int limit) {
        return entityManager.createQuery("select f,(select count(r.id) from CdsImportFileReference r where r.fileId=f.id) from CdsImportFile f where f.runId=:run and f.id>:after order by f.id", Object[].class)
                .setParameter("run", runId).setParameter("after", afterId).setMaxResults(limit).getResultList();
    }
}
