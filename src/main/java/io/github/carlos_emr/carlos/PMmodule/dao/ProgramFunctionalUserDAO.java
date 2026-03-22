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

import io.github.carlos_emr.carlos.PMmodule.model.FunctionalUserType;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramFunctionalUser;

/**
 * Data access interface for managing {@link ProgramFunctionalUser} and
 * {@link FunctionalUserType} entities that define functional user roles
 * within programs (e.g., case manager, counselor).
 *
 * @since 2005-01-18
 * @see ProgramFunctionalUser
 * @see FunctionalUserType
 * @see ProgramFunctionalUserDAOImpl
 */
public interface ProgramFunctionalUserDAO {

    /**
     * Retrieves all functional user type definitions.
     *
     * @return List&lt;FunctionalUserType&gt; all functional user types
     */
    public List<FunctionalUserType> getFunctionalUserTypes();

    /**
     * Retrieves a functional user type by its ID.
     *
     * @param id Long the functional user type ID
     * @return FunctionalUserType the type, or {@code null} if not found
     * @throws IllegalArgumentException if id is {@code null} or not positive
     */
    public FunctionalUserType getFunctionalUserType(Long id);

    /**
     * Saves or updates a functional user type definition.
     *
     * @param fut FunctionalUserType the type to save
     * @throws IllegalArgumentException if fut is {@code null}
     */
    public void saveFunctionalUserType(FunctionalUserType fut);

    /**
     * Deletes a functional user type by its ID.
     *
     * @param id Long the type ID to delete
     * @throws IllegalArgumentException if id is {@code null} or not positive
     */
    public void deleteFunctionalUserType(Long id);

    /**
     * Retrieves functional users assigned to a specific program.
     *
     * @param programId Long the program ID
     * @return List&lt;FunctionalUserType&gt; functional users for the program
     * @throws IllegalArgumentException if programId is {@code null} or not positive
     */
    public List<FunctionalUserType> getFunctionalUsers(Long programId);

    /**
     * Retrieves a functional user assignment by its ID.
     *
     * @param id Long the assignment ID
     * @return ProgramFunctionalUser the assignment, or {@code null} if not found
     * @throws IllegalArgumentException if id is {@code null} or not positive
     */
    public ProgramFunctionalUser getFunctionalUser(Long id);

    /**
     * Saves or updates a functional user assignment.
     *
     * @param pfu ProgramFunctionalUser the assignment to save
     * @throws IllegalArgumentException if pfu is {@code null}
     */
    public void saveFunctionalUser(ProgramFunctionalUser pfu);

    /**
     * Deletes a functional user assignment by its ID.
     *
     * @param id Long the assignment ID to delete
     * @throws IllegalArgumentException if id is {@code null} or not positive
     */
    public void deleteFunctionalUser(Long id);

    /**
     * Retrieves the ID of a functional user assignment by program and user type.
     *
     * @param programId Long the program ID
     * @param userTypeId Long the user type ID
     * @return Long the assignment ID, or {@code null} if not found
     * @throws IllegalArgumentException if parameters are invalid
     */
    public Long getFunctionalUserByUserType(Long programId, Long userTypeId);
}
