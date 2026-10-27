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

import io.github.carlos_emr.carlos.commn.model.CdsImportRun;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportRunDao extends AbstractDaoImpl<CdsImportRun> {
    public CdsImportRunDao() { super(CdsImportRun.class); }
    public CdsImportRun lock(String id) { return entityManager.find(CdsImportRun.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportRun get(String id) { return entityManager.find(CdsImportRun.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    public CdsImportRun findSubmission(String operator, String submissionKey) {
        return entityManager.createQuery("select r from CdsImportRun r where r.operator=:operator and r.submissionKey=:key", CdsImportRun.class)
                .setParameter("operator", operator).setParameter("key", submissionKey).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    }
    public List<CdsImportRun> pending(String afterId, int limit) {
        return entityManager.createQuery("select r from CdsImportRun r where r.id>:after and (r.state in ('RESUME_REQUESTED','ACCEPTED','DISCOVERING','VALIDATING','PREFLIGHT','PREFLIGHTING','PLANNING','SEALING','READY','RUNNING','FINALIZING','REPORTING') or (r.state in ('COMPLETE','DRY_RUN_COMPLETE','DRY_RUN_INCOMPLETE','INCOMPLETE','RECONCILIATION_REQUIRED','REVIEW_REQUIRED','FAILED_BEFORE_WRITE','CANCELLED_BEFORE_WRITE') and r.reportState='PENDING')) order by r.id", CdsImportRun.class).setParameter("after", afterId).setMaxResults(limit).getResultList();
    }
    public List<CdsImportRun> page(String operator, String afterId, int limit) {
        return entityManager.createQuery("select r from CdsImportRun r where r.operator=:operator and r.id>:after order by r.id", CdsImportRun.class)
                .setParameter("operator", operator).setParameter("after", afterId).setMaxResults(limit).getResultList();
    }
}
