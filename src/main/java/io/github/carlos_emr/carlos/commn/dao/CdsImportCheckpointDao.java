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

import io.github.carlos_emr.carlos.commn.model.CdsImportCheckpoint;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportCheckpointDao extends AbstractDaoImpl<CdsImportCheckpoint> {
    public CdsImportCheckpointDao() { super(CdsImportCheckpoint.class); }
    public CdsImportCheckpoint lock(String id) { return entityManager.find(CdsImportCheckpoint.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportCheckpoint get(String id) { return entityManager.find(CdsImportCheckpoint.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    public long countLineage(String lineageKey) {
        return entityManager.createQuery("select count(c) from CdsImportCheckpoint c where c.lineageKey=:lineage", Long.class).setParameter("lineage", lineageKey).getSingleResult();
    }
    public long incompleteReplay(String runId, java.util.Collection<String> unitKeys, String options, String rules, int target) {
        if (unitKeys.isEmpty()) return 0;
        return entityManager.createQuery("select count(u) from CdsImportUnit u where u.runId=:run and u.unitKey in :keys and not exists(select c.id from CdsImportCheckpoint c where c.id=u.unitKey and c.lineageKey=u.lineageKey and c.sourceDigest=u.sourceDigest and c.optionsDigest=:options and c.rulesetDigest=:rules and exists(select p.id from CdsImportPatient p where p.runId=c.firstRunId and p.lineageKey=c.lineageKey and p.targetDemographicNo=:target and p.held=false and p.state in ('COMPLETE','COMPLETE_WITH_NONLOSSY_WARNINGS','ALREADY_COMPLETE')))", Long.class)
                .setParameter("run", runId).setParameter("keys", unitKeys).setParameter("options", options).setParameter("rules", rules).setParameter("target", target).getSingleResult();
    }
    public long anyForLineages(java.util.Collection<String> lineageKeys) {
        if (lineageKeys.isEmpty()) return 0;
        return entityManager.createQuery("select count(c) from CdsImportCheckpoint c where c.lineageKey in :keys", Long.class).setParameter("keys", lineageKeys).getSingleResult();
    }
}
