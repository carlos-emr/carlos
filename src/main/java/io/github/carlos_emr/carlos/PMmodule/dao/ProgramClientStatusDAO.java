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

import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientStatus;
import io.github.carlos_emr.carlos.commn.model.Admission;

/**
 * Data access interface for managing {@link ProgramClientStatus} entities that
 * define client status categories within programs.
 *
 * @since 2005-01-18
 * @see ProgramClientStatus
 * @see ProgramClientStatusDAOImpl
 */
public interface ProgramClientStatusDAO {

    /**
     * Retrieves all client status definitions for a program.
     *
     * @param programId Integer the program ID
     * @return List&lt;ProgramClientStatus&gt; status definitions for the program
     */
    public List<ProgramClientStatus> getProgramClientStatuses(Integer programId);

    /**
     * Saves or updates a program client status definition.
     *
     * @param status ProgramClientStatus the status to save
     */
    public void saveProgramClientStatus(ProgramClientStatus status);

    /**
     * Retrieves a program client status by its ID.
     *
     * @param id String the status ID
     * @return ProgramClientStatus the status record, or {@code null} if not found
     * @throws IllegalArgumentException if id is {@code null} or represents a negative number
     */
    public ProgramClientStatus getProgramClientStatus(String id);

    /**
     * Deletes a program client status by its ID.
     *
     * @param id String the status ID to delete
     */
    public void deleteProgramClientStatus(String id);

    /**
     * Checks whether a client status name already exists for a program.
     *
     * @param programId Integer the program ID
     * @param statusName String the status name to check
     * @return {@code true} if the name already exists, {@code false} otherwise
     * @throws IllegalArgumentException if parameters are invalid
     */
    public boolean clientStatusNameExists(Integer programId, String statusName);

    /**
     * Retrieves all currently admitted clients in a specific status within a program.
     *
     * @param programId Integer the program ID
     * @param statusId Integer the status ID (stored as team ID in admissions)
     * @return List&lt;Admission&gt; current admissions matching the criteria
     * @throws IllegalArgumentException if parameters are invalid
     */
    public List<Admission> getAllClientsInStatus(Integer programId, Integer statusId);
}
