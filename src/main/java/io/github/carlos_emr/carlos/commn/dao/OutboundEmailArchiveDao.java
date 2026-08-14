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

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Data access contract for durable outbound email archive records.
 */
public interface OutboundEmailArchiveDao extends AbstractDao<OutboundEmailArchive> {

    /**
     * Finds archive rows for an email log, newest archive first.
     *
     * @param emailLogId persisted email log identifier
     * @return archives linked to the email log ordered by archived date descending
     */
    List<OutboundEmailArchive> findByEmailLogId(Integer emailLogId);

    /**
     * Finds an archive row with the associations required for read authorization and file access initialized.
     *
     * @param archiveId persisted archive identifier
     * @return archive row with demographic and document loaded, or {@code null} when no row exists
     */
    OutboundEmailArchive findForRead(Integer archiveId);

    /**
     * Finds an archive row with a write lock for short controlled-deletion critical sections.
     *
     * @param archiveId persisted archive identifier
     * @return locked archive row, or {@code null} when no row exists
     */
    OutboundEmailArchive findForUpdate(Integer archiveId);

    /**
     * Checks whether a document is the backing eDoc for an active outbound email archive.
     *
     * @param documentNo persisted eDoc identifier
     * @return {@code true} when the document is linked to a non-deleted archive row
     */
    boolean existsActiveByDocumentNo(Integer documentNo);

    /**
     * Checks whether a document is or was the backing eDoc for an outbound email archive.
     *
     * @param documentNo persisted eDoc identifier
     * @return {@code true} when the document is linked to any archive row
     */
    boolean existsByDocumentNo(Integer documentNo);

    /**
     * Finds all archive-backed eDoc identifiers in the supplied candidates using one query.
     *
     * @param documentNos candidate persisted eDoc identifiers
     * @return archive-backed identifiers, or an empty set for no candidates
     */
    Set<Integer> findExistingDocumentNos(Collection<Integer> documentNos);

    /**
     * Checks whether a stored eDoc filename is linked to an outbound email archive.
     *
     * @param fileName stored eDoc basename
     * @return {@code true} when the filename is linked to any archive row
     */
    boolean existsByFileName(String fileName);

    /**
     * Finds archive rows for a patient demographic, newest archive first.
     *
     * @param demographicNo patient demographic number
     * @return archives linked to the demographic ordered by archived date descending
     */
    List<OutboundEmailArchive> findByDemographicNo(Integer demographicNo);
}
