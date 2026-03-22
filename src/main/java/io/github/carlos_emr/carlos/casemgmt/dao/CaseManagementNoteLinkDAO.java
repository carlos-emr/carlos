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

package io.github.carlos_emr.carlos.casemgmt.dao;

import java.util.List;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;

/**
 * Data access interface for {@link CaseManagementNoteLink} entities.
 * Provides query and persistence operations for links between case management notes
 * and other clinical data entities (prescriptions, labs, documents, etc.).
 *
 * @since 2026-03-17
 */
public interface CaseManagementNoteLinkDAO {

    /**
     * Retrieves a note link by its unique identifier.
     *
     * @param id Long the link ID
     * @return CaseManagementNoteLink the link entity, or null if not found
     */
    public CaseManagementNoteLink getNoteLink(Long id);

    /**
     * Retrieves all links for the specified table type and record ID.
     *
     * @param tableName Integer the linked table type constant
     * @param tableId Long the linked record's primary key
     * @return List&lt;CaseManagementNoteLink&gt; the matching links
     */
    public List<CaseManagementNoteLink> getLinkByTableId(Integer tableName, Long tableId);

    /**
     * Retrieves all links for the specified table type, record ID, and other ID.
     *
     * @param tableName Integer the linked table type constant
     * @param tableId Long the linked record's primary key
     * @param otherId String an additional identifier for filtering
     * @return List&lt;CaseManagementNoteLink&gt; the matching links
     */
    public List<CaseManagementNoteLink> getLinkByTableId(Integer tableName, Long tableId, String otherId);

    /**
     * Retrieves all links for the specified table type and record ID, ordered by ID descending.
     *
     * @param tableName Integer the linked table type constant
     * @param tableId Long the linked record's primary key
     * @return List&lt;CaseManagementNoteLink&gt; the matching links in descending order
     */
    public List<CaseManagementNoteLink> getLinkByTableIdDesc(Integer tableName, Long tableId);

    /**
     * Retrieves all links for the specified table type, record ID, and other ID, ordered by ID descending.
     *
     * @param tableName Integer the linked table type constant
     * @param tableId Long the linked record's primary key
     * @param otherId String an additional identifier for filtering
     * @return List&lt;CaseManagementNoteLink&gt; the matching links in descending order
     */
    public List<CaseManagementNoteLink> getLinkByTableIdDesc(Integer tableName, Long tableId, String otherId);

    /**
     * Retrieves all links associated with the specified note.
     *
     * @param noteId Long the case management note ID
     * @return List&lt;CaseManagementNoteLink&gt; the links for the note
     */
    public List<CaseManagementNoteLink> getLinkByNote(Long noteId);

    /**
     * Retrieves the most recent link for the specified table type, record ID, and other ID.
     *
     * @param tableName Integer the linked table type constant
     * @param tableId Long the linked record's primary key
     * @param otherId String an additional identifier for filtering
     * @return CaseManagementNoteLink the most recent link, or null if none found
     */
    public CaseManagementNoteLink getLastLinkByTableId(Integer tableName, Long tableId, String otherId);

    /**
     * Retrieves the most recent link for the specified table type and record ID.
     *
     * @param tableName Integer the linked table type constant
     * @param tableId Long the linked record's primary key
     * @return CaseManagementNoteLink the most recent link, or null if none found
     */
    public CaseManagementNoteLink getLastLinkByTableId(Integer tableName, Long tableId);

    /**
     * Retrieves the most recent link associated with the specified note.
     *
     * @param noteId Long the case management note ID
     * @return CaseManagementNoteLink the most recent link, or null if none found
     */
    public CaseManagementNoteLink getLastLinkByNote(Long noteId);

    /**
     * Persists a new note link entity.
     *
     * @param cLink CaseManagementNoteLink the link to save
     */
    public void save(CaseManagementNoteLink cLink);

    /**
     * Updates an existing note link entity.
     *
     * @param cLink CaseManagementNoteLink the link to update
     */
    public void update(CaseManagementNoteLink cLink);
}
