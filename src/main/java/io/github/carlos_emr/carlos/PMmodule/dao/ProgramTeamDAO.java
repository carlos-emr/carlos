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

import io.github.carlos_emr.carlos.PMmodule.model.ProgramTeam;

/**
 * Data access interface for managing {@link ProgramTeam} entities that organize
 * providers into teams within programs.
 *
 * @since 2005-01-18
 * @see ProgramTeam
 * @see ProgramTeamDAOImpl
 */
public interface ProgramTeamDAO {

    /**
     * Checks whether a team with the given ID exists.
     *
     * @param teamId Integer the team ID to check
     * @return {@code true} if the team exists, {@code false} otherwise
     */
    public boolean teamExists(Integer teamId);

    /**
     * Checks whether a team name already exists within a program.
     *
     * @param programId Integer the program ID
     * @param teamName String the team name to check
     * @return {@code true} if the name already exists, {@code false} otherwise
     * @throws IllegalArgumentException if parameters are invalid
     */
    public boolean teamNameExists(Integer programId, String teamName);

    /**
     * Retrieves a program team by its ID.
     *
     * @param id Integer the team ID
     * @return ProgramTeam the team, or {@code null} if not found
     * @throws IllegalArgumentException if id is {@code null} or not positive
     */
    public ProgramTeam getProgramTeam(Integer id);

    /**
     * Retrieves all teams for a specific program.
     *
     * @param programId Integer the program ID
     * @return List&lt;ProgramTeam&gt; teams for the program
     * @throws IllegalArgumentException if programId is invalid
     */
    public List<ProgramTeam> getProgramTeams(Integer programId);

    /**
     * Saves or updates a program team.
     *
     * @param team ProgramTeam the team to save
     * @throws IllegalArgumentException if team is {@code null}
     */
    public void saveProgramTeam(ProgramTeam team);

    /**
     * Deletes a program team by its ID.
     *
     * @param id Integer the team ID to delete
     * @throws IllegalArgumentException if id is invalid
     * @throws org.springframework.dao.EmptyResultDataAccessException if no team found
     */
    public void deleteProgramTeam(Integer id);
}
