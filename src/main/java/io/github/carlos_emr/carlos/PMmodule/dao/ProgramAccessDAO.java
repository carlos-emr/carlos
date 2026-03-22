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

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.AccessType;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;

/**
 * Data access interface for managing {@link ProgramAccess} entities and
 * {@link AccessType} entities that control role-based access to programs.
 *
 * @since 2005-01-18
 * @see ProgramAccess
 * @see AccessType
 * @see ProgramAccessDAOImpl
 */
public interface ProgramAccessDAO {

    /**
     * Retrieves the access list for a specific program, with caching.
     *
     * @param programId Long the program ID
     * @return List&lt;ProgramAccess&gt; access records for the program
     */
    public List<ProgramAccess> getAccessListByProgramId(Long programId);

    /**
     * Retrieves a program access record by its ID.
     *
     * @param id Long the record ID
     * @return ProgramAccess the record, or {@code null} if not found
     * @throws IllegalArgumentException if id is {@code null} or not positive
     */
    public ProgramAccess getProgramAccess(Long id);

    /**
     * Retrieves a program access record by program ID and access type ID.
     *
     * @param programId Long the program ID
     * @param accessTypeId Long the access type ID
     * @return ProgramAccess the matching record, or {@code null} if not found
     * @throws IllegalArgumentException if either parameter is {@code null} or not positive
     */
    public ProgramAccess getProgramAccess(Long programId, Long accessTypeId);

    /**
     * Retrieves program access records for a program filtered by access type name.
     *
     * @param programId Long the program ID
     * @param accessType String the access type name pattern
     * @return List&lt;ProgramAccess&gt; matching records
     */
    public List<ProgramAccess> getProgramAccessListByType(Long programId, String accessType);

    /**
     * Saves or updates a program access record and invalidates the related cache.
     *
     * @param pa ProgramAccess the record to save
     * @throws IllegalArgumentException if pa is {@code null}
     */
    public void saveProgramAccess(ProgramAccess pa);

    /**
     * Deletes a program access record by its ID and invalidates the related cache.
     *
     * @param id Long the record ID to delete
     * @throws IllegalArgumentException if id is {@code null} or not positive
     */
    public void deleteProgramAccess(Long id);

    /**
     * Retrieves all access type definitions.
     *
     * @return List&lt;AccessType&gt; all access type records
     */
    public List<AccessType> getAccessTypes();

    /**
     * Retrieves an access type by its ID.
     *
     * @param id Long the access type ID
     * @return AccessType the record, or {@code null} if not found
     * @throws IllegalArgumentException if id is {@code null} or not positive
     */
    public AccessType getAccessType(Long id);
}
