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

import io.github.carlos_emr.carlos.commn.model.CdsExportAttempt;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Attempt metadata only. Callers lock the owning run before locking its attempt. */
@Repository
public class CdsExportAttemptDao extends AbstractDaoImpl<CdsExportAttempt> {
    public CdsExportAttemptDao() {
        super(CdsExportAttempt.class);
    }

    public CdsExportAttempt find(String runId, long epoch) {
        return entityManager
                .createQuery(
                        "select a from CdsExportAttempt a where a.runId=:run and a.epoch=:epoch",
                        CdsExportAttempt.class)
                .setParameter("run", runId)
                .setParameter("epoch", epoch)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    public CdsExportAttempt lock(String runId, long epoch) {
        var found = find(runId, epoch);
        if (found != null) entityManager.refresh(found, LockModeType.PESSIMISTIC_WRITE);
        return found;
    }

    public List<String> expiredRunIds(Instant now, int limit) {
        return expiredRunIds(now, "", limit);
    }

    public List<String> expiredRunIds(Instant now, String after, int limit) {
        return entityManager
                .createQuery(
                        "select distinct a.runId from CdsExportAttempt a where a.runId>:after and"
                                + " a.state<>'PURGED' and a.expiresAt<=:now and (a.downloadUntil is"
                                + " null or a.downloadUntil<=:now) and (a.cleanupUntil is null or"
                                + " a.cleanupUntil<=:now) order by a.runId",
                        String.class)
                .setParameter("after", after)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Long> epochs(String runId, long after, Instant now, int limit) {
        return entityManager
                .createQuery(
                        "select a.epoch from CdsExportAttempt a where a.runId=:run and"
                            + " a.epoch>:after and a.state<>'PURGED' and a.expiresAt<=:now order by"
                            + " a.epoch",
                        Long.class)
                .setParameter("run", runId)
                .setParameter("after", after)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    public void flush() {
        entityManager.flush();
    }

    /**
     * The attempts of a run whose finished charts are kept for a resumed attempt: stopped
     * ({@code KEEPING}, then {@code KEPT} once the retention sweep erased every member their
     * checkpoint does not pin) and not yet due for erasure. Newest first.
     */
    public List<CdsExportAttempt> kept(String runId, Instant now) {
        return entityManager
                .createQuery(
                        "select a from CdsExportAttempt a where a.runId=:run and a.state in"
                                + " ('KEEPING','KEPT') and a.checkpointMember is not null and"
                                + " a.expiresAt>:now order by a.epoch desc",
                        CdsExportAttempt.class)
                .setParameter("run", runId)
                .setParameter("now", now)
                .getResultList();
    }

    /**
     * Attempts of a run whose worker was lost (no outcome was ever recorded) and that sealed a
     * finished-chart checkpoint: the next claim keeps their charts for it, until their own
     * expiry.
     */
    public List<CdsExportAttempt> orphanedCheckpoints(String runId, Instant now) {
        return entityManager
                .createQuery(
                        "select a from CdsExportAttempt a where a.runId=:run and a.state in"
                                + " ('CAPTURING','PREPARED','ENCRYPTED') and a.checkpointMember is"
                                + " not null and a.expiresAt>:now order by a.epoch",
                        CdsExportAttempt.class)
                .setParameter("run", runId)
                .setParameter("now", now)
                .getResultList();
    }

    /**
     * Kept attempts whose unpinned members the retention sweep has not erased yet, oldest change
     * first; an active cleanup or trim lease excludes one.
     */
    public List<CdsExportAttempt> untrimmed(Instant now, int limit) {
        return entityManager
                .createQuery(
                        "select a from CdsExportAttempt a where a.state='KEEPING' and"
                                + " a.checkpointMember is not null and a.expiresAt>:now and"
                                + " (a.cleanupUntil is null or a.cleanupUntil<=:now) order by"
                                + " a.updatedAt,a.id",
                        CdsExportAttempt.class)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Makes every generation of a run due for the retention sweep except those whose finished
     * charts are kept for a resumed attempt; active download/cleanup leases still fence erasure.
     */
    public void expireUnkeptForRun(String runId, Instant now) {
        entityManager
                .createQuery(
                        "update CdsExportAttempt a set"
                                + " a.expiresAt=:now,a.updatedAt=:now,a.revision=a.revision+1 where"
                                + " a.runId=:run and a.state not in ('PURGED','KEEPING','KEPT') and"
                                + " a.expiresAt>:now")
                .setParameter("run", runId)
                .setParameter("now", now)
                .executeUpdate();
    }

    /** Makes a run's kept generations due for the retention sweep: their charts are no longer needed. */
    public void expireKeptForRun(String runId, Instant now) {
        entityManager
                .createQuery(
                        "update CdsExportAttempt a set"
                                + " a.expiresAt=:now,a.updatedAt=:now,a.revision=a.revision+1 where"
                                + " a.runId=:run and a.state in ('KEEPING','KEPT') and"
                                + " a.expiresAt>:now")
                .setParameter("run", runId)
                .setParameter("now", now)
                .executeUpdate();
    }

    /** Cancellation makes all retained generations due; active download/cleanup leases still fence erasure. */
    public void expireForRun(String runId, Instant now) {
        entityManager
                .createQuery(
                        "update CdsExportAttempt a set"
                                + " a.expiresAt=:now,a.updatedAt=:now,a.revision=a.revision+1 where"
                                + " a.runId=:run and a.state<>'PURGED' and a.expiresAt>:now")
                .setParameter("run", runId)
                .setParameter("now", now)
                .executeUpdate();
    }
}
