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

import io.github.carlos_emr.carlos.commn.model.CdsExportRun;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Only export run metadata. Clinical reads and cross-DAO orchestration belong to snapshot/services. */
@Repository
public class CdsExportRunDao extends AbstractDaoImpl<CdsExportRun> {
    public CdsExportRunDao() { super(CdsExportRun.class); }
    public CdsExportRun lock(String id) {
        var run = entityManager.find(CdsExportRun.class, id);
        if (run != null) entityManager.refresh(run, LockModeType.PESSIMISTIC_WRITE);
        return run;
    }
    /** Authority only; bounded intent prefix is an optional facility hint, never disclosure authority. */
    public io.github.carlos_emr.carlos.commn.dto.CdsExportRunOwnerRowDto owner(String id) {
        return entityManager.createQuery("select new io.github.carlos_emr.carlos.commn.dto.CdsExportRunOwnerRowDto"
                + "(r.operator,r.providerNo,substring(r.requestDocument,1,262145),r.requestDigest) from CdsExportRun r where r.id=:id",
                io.github.carlos_emr.carlos.commn.dto.CdsExportRunOwnerRowDto.class).setParameter("id", id).setMaxResults(1)
                .getResultList().stream().findFirst().orElse(null);
    }
    public CdsExportRun get(String id) { return entityManager.find(CdsExportRun.class, id); }
    /**
     * A heartbeat changes ownership timing, not browser control intent. The caller must hold
     * the row lock and have checked the live lease and cancellation. Keep Hibernate's version
     * for every other mutation; refresh the managed row after this explicitly unversioned update.
     */
    public void renewLease(CdsExportRun run, Instant now, Instant until) {
        if (!entityManager.contains(run)
                || entityManager.getLockMode(run) != LockModeType.PESSIMISTIC_WRITE)
            throw new IllegalStateException("CDS_EXPORT_LEASE_LOCK_REQUIRED");
        int changed = entityManager.createQuery(
                "update CdsExportRun r set r.leaseUntil=:until,r.updatedAt=:now"
                        + " where r.id=:id and r.ownerEpoch=:epoch and r.ownerToken=:token and r.revision=:revision")
                .setParameter("until", until).setParameter("now", now)
                .setParameter("id", run.getId()).setParameter("epoch", run.getOwnerEpoch())
                .setParameter("token", run.getOwnerToken()).setParameter("revision", run.getRevision())
                .executeUpdate();
        if (changed != 1) throw new IllegalStateException("CDS_EXPORT_LEASE_LOST");
        entityManager.refresh(run, LockModeType.PESSIMISTIC_WRITE);
    }
    public void flush() { entityManager.flush(); }
    public CdsExportRun submission(String operator, String key) {
        return entityManager.createQuery("select r from CdsExportRun r where r.operator=:operator and r.submissionKey=:key", CdsExportRun.class)
                .setParameter("operator", operator).setParameter("key", key).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    }
    public List<String> pending(Instant now, int limit) {
        return entityManager.createQuery("select r.id from CdsExportRun r where r.state in ('ACCEPTED','DISCOVERING','CAPTURING','RECONCILING','SERIALIZING','VALIDATING','PACKAGING','ENCRYPTING') and (r.ownerToken is null or r.leaseUntil<=:now) order by r.updatedAt,r.id", String.class)
                .setParameter("now", now).setMaxResults(limit).getResultList();
    }
    public List<CdsExportRun> history(String operator, String after, int limit) {
        String order = " order by r.createdAt desc,r.id desc";
        String query = "select r from CdsExportRun r where r.operator=:operator";
        if (after == null || after.isEmpty())
            return entityManager.createQuery(query + order, CdsExportRun.class)
                    .setParameter("operator", operator).setMaxResults(limit).getResultList();
        // Resolve a cursor only within the same operator's history. A foreign and a
        // missing cursor have the same outcome and reveal no other job metadata.
        var cursor = entityManager.createQuery(
                "select r.createdAt from CdsExportRun r where r.operator=:operator and r.id=:after", Instant.class)
                .setParameter("operator", operator).setParameter("after", after).getResultList();
        if (cursor.isEmpty()) throw new IllegalArgumentException("CDS_EXPORT_HISTORY_CURSOR_INVALID");
        return entityManager.createQuery(query
                + " and (r.createdAt<:created or (r.createdAt=:created and r.id<:after))" + order, CdsExportRun.class)
                .setParameter("operator", operator).setParameter("created", cursor.getFirst())
                .setParameter("after", after).setMaxResults(limit).getResultList();
    }
    /** Database clock governs leases across application nodes; no process clock can steal another lease. */
    public Instant now() {
        Number seconds = (Number) entityManager.createNativeQuery("SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))", Object.class).getSingleResult();
        java.math.BigDecimal value = new java.math.BigDecimal(seconds.toString());
        return Instant.ofEpochSecond(value.longValue(), value.remainder(java.math.BigDecimal.ONE).movePointRight(9).longValue());
    }
}
