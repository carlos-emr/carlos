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

import java.util.List;

/**
 * Data access contract for durable outbound email archive records.
 *
 * @since 2026-08-14
 */
public interface OutboundEmailArchiveDao extends AbstractDao<OutboundEmailArchive> {

    /**
     * Finds archive rows for an email log, newest archive first.
     *
     * @param emailLogId persisted email log identifier
     * <p>Includes archives retired by {@code recordControlledDeletion} — retirement is a
     * {@code deleted} flag plus a tombstone, not a row removal, and this query does not filter
     * on it. A caller rendering archives to a user is responsible for suppressing them.</p>
     *
     * @return archives linked to the email log ordered by archived date descending, retired ones included
     */
    List<OutboundEmailArchive> findByEmailLogId(Integer emailLogId);

    /**
     * Finds an archive row with a write lock for short controlled-deletion critical sections.
     *
     * <p><b>Must be the first read of the row in its transaction.</b> A JPA query does not
     * refresh an entity that is already managed, so if the archive was loaded earlier in
     * the same transaction this returns that instance with its pre-lock state -- the row
     * lock is taken, but the state guarded by it is stale. See
     * {@code findDemographicNoById} for the read to use ahead of the lock.</p>
     *
     * @param archiveId persisted archive identifier
     * @return locked archive row, or {@code null} when no row exists
     */
    OutboundEmailArchive findForUpdate(Integer archiveId);

    /**
     * Finds an archive row for reading, with its demographic and document hydrated.
     *
     * <p>Takes no lock. Use this for reads that only report archive metadata. Artifact reads
     * take {@link #findForUpdate(Integer)} instead, so a controlled deletion cannot remove the
     * stored file between the integrity check and the read.</p>
     *
     * @param archiveId persisted archive identifier
     * @return the archive with demographic and document fetched, or {@code null} when no row exists
     */
    OutboundEmailArchive findForRead(Integer archiveId);

    /**
     * Reads just the demographic number for an archive, without loading the archive.
     *
     * <p>Exists so an authorization check can run before {@link #findForUpdate} without
     * putting the entity in the persistence context, which would make the subsequent
     * locked read return stale state.</p>
     *
     * @param archiveId persisted archive identifier
     * @return demographic number, or {@code null} when no row exists
     */
    Integer findDemographicNoById(Integer archiveId);

    /**
     * Finds archive rows for a patient demographic, newest archive first.
     *
     * @param demographicNo patient demographic number
     * <p>Includes retired archives, for the same reason as {@link #findByEmailLogId}.</p>
     *
     * @return archives linked to the demographic ordered by archived date descending, retired ones included
     */
    List<OutboundEmailArchive> findByDemographicNo(Integer demographicNo);
}
