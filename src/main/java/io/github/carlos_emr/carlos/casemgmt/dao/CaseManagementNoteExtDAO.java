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

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;

/**
 * Data access interface for {@link CaseManagementNoteExt} entities.
 * Provides CRUD operations and queries for note extension key-value metadata.
 *
 * @since 2026-03-17
 */
public interface CaseManagementNoteExtDAO {

    /**
     * Retrieves a note extension by its unique identifier.
     *
     * @param id Long the extension ID
     * @return CaseManagementNoteExt the extension entity, or null if not found
     */
    public CaseManagementNoteExt getNoteExt(Long id);

    /**
     * Retrieves all extensions associated with the specified note.
     *
     * @param noteId Long the case management note ID
     * @return List&lt;CaseManagementNoteExt&gt; the list of extensions for the note
     */
    public List<CaseManagementNoteExt> getExtByNote(Long noteId);

    /**
     * Retrieves all extensions matching the specified key.
     *
     * @param keyVal String the extension key to search for
     * @return List the list of matching extensions
     */
    public List getExtByKeyVal(String keyVal);

    /**
     * Retrieves all extensions matching the specified key and string value.
     *
     * @param keyVal String the extension key
     * @param value String the extension value to match
     * @return List the list of matching extensions
     */
    public List getExtByValue(String keyVal, String value);

    /**
     * Retrieves all extensions with the specified key whose date value is before the given date.
     *
     * @param keyVal String the extension key
     * @param dateValue Date the upper bound date (exclusive)
     * @return List the list of matching extensions
     */
    public List getExtBeforeDate(String keyVal, Date dateValue);

    /**
     * Retrieves all extensions with the specified key whose date value is after the given date.
     *
     * @param keyVal String the extension key
     * @param dateValue Date the lower bound date (exclusive)
     * @return List the list of matching extensions
     */
    public List getExtAfterDate(String keyVal, Date dateValue);

    /**
     * Persists a new note extension entity.
     *
     * @param cExt CaseManagementNoteExt the extension to save
     */
    public void save(CaseManagementNoteExt cExt);

    /**
     * Updates an existing note extension entity.
     *
     * @param cExt CaseManagementNoteExt the extension to update
     */
    public void update(CaseManagementNoteExt cExt);
}
