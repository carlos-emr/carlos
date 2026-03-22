/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;

/**
 * DAO interface for group operations.
 *
 * @since 2001
 */

public interface GroupNoteDao extends AbstractDao<GroupNoteLink> {

    /**
     * Find Links By Demographic.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<GroupNoteLink>
     */
    public List<GroupNoteLink> findLinksByDemographic(Integer demographicNo);

    /**
     * Find Links By Demographic Since.
     *
     * @param demographicNo Integer the demographicNo
     * @param lastDateUpdated Date the lastDateUpdated
     * @return List<GroupNoteLink>
     */
    public List<GroupNoteLink> findLinksByDemographicSince(Integer demographicNo, Date lastDateUpdated);

    /**
     * Find Links By Note Id.
     *
     * @param noteId Integer the noteId
     * @return List<GroupNoteLink>
     */
    public List<GroupNoteLink> findLinksByNoteId(Integer noteId);

    /**
     * Get Number Of Links By Note Id.
     *
     * @param noteId Integer the noteId
     * @return int
     */
    public int getNumberOfLinksByNoteId(Integer noteId);
}
