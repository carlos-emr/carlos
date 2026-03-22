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

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.model.Facility;

import java.util.List;

/**
 * Data access interface for managing {@link ProgramProvider} entities that
 * link healthcare providers to programs with specific roles.
 *
 * <p>Supports querying provider-program assignments, managing program domains
 * (the set of programs a provider can access), and facility-level filtering.</p>
 *
 * @since 2005-01-18
 * @see ProgramProvider
 * @see ProgramProviderDAOImpl
 */
public interface ProgramProviderDAO {

    /**
     * Retrieves program-provider assignments by provider number and program ID, with caching.
     *
     * @param providerNo String the provider number
     * @param programId Long the program ID
     * @return List&lt;ProgramProvider&gt; matching assignments
     */
    public List<ProgramProvider> getProgramProviderByProviderProgramId(String providerNo, Long programId);

    /**
     * Retrieves all program-provider assignments.
     *
     * @return List&lt;ProgramProvider&gt; all assignments
     */
    public List<ProgramProvider> getAllProgramProviders();

    /**
     * Retrieves program-provider assignments by provider number.
     *
     * @param providerNo String the provider number
     * @return List&lt;ProgramProvider&gt; assignments for the provider
     */
    public List<ProgramProvider> getProgramProviderByProviderNo(String providerNo);

    /**
     * Retrieves all providers assigned to a specific program.
     *
     * @param programId Long the program ID
     * @return List&lt;ProgramProvider&gt; providers in the program
     * @throws IllegalArgumentException if programId is invalid
     */
    public List<ProgramProvider> getProgramProviders(Long programId);

    /**
     * Retrieves all program assignments for a provider.
     *
     * @param providerNo String the provider number
     * @return List&lt;ProgramProvider&gt; program assignments
     * @throws IllegalArgumentException if providerNo is {@code null}
     */
    public List<ProgramProvider> getProgramProvidersByProvider(String providerNo);

    /**
     * Retrieves program-provider assignments for a provider within a specific facility.
     *
     * @param providerNo String the provider number
     * @param facilityId Integer the facility ID
     * @return List program-provider assignments filtered by facility
     * @throws IllegalArgumentException if providerNo is {@code null}
     */
    public List getProgramProvidersByProviderAndFacility(String providerNo, Integer facilityId);

    /**
     * Retrieves a program-provider assignment by its ID.
     *
     * @param id Long the assignment ID
     * @return ProgramProvider the assignment, or {@code null} if not found
     * @throws IllegalArgumentException if id is invalid
     */
    public ProgramProvider getProgramProvider(Long id);

    /**
     * Retrieves a program-provider assignment by provider number and program ID.
     *
     * @param providerNo String the provider number
     * @param programId Long the program ID
     * @return ProgramProvider the assignment, or {@code null} if not found
     * @throws IllegalArgumentException if parameters are invalid
     */
    public ProgramProvider getProgramProvider(String providerNo, Long programId);

    /**
     * Retrieves a program-provider assignment by provider, program, and role.
     *
     * @param providerNo String the provider number
     * @param programId Long the program ID
     * @param roleId Long the role ID
     * @return ProgramProvider the assignment, or {@code null} if not found
     * @throws IllegalArgumentException if parameters are invalid
     */
    public ProgramProvider getProgramProvider(String providerNo, Long programId, Long roleId);

    /**
     * Saves or updates a program-provider assignment and invalidates the related cache.
     *
     * @param pp ProgramProvider the assignment to save
     * @throws IllegalArgumentException if pp is {@code null}
     */
    public void saveProgramProvider(ProgramProvider pp);

    /**
     * Deletes a program-provider assignment by its ID and invalidates the cache.
     *
     * @param id Long the assignment ID to delete
     * @throws IllegalArgumentException if id is invalid
     */
    public void deleteProgramProvider(Long id);

    /**
     * Deletes all provider assignments for a specific program.
     *
     * @param programId Long the program ID
     * @throws IllegalArgumentException if programId is invalid
     */
    public void deleteProgramProviderByProgramId(Long programId);

    /**
     * Retrieves providers assigned to a specific team within a program.
     *
     * @param programId Integer the program ID
     * @param teamId Integer the team ID
     * @return List&lt;ProgramProvider&gt; providers in the specified team
     * @throws IllegalArgumentException if parameters are invalid
     */
    public List<ProgramProvider> getProgramProvidersInTeam(Integer programId, Integer teamId);

    /**
     * Retrieves the program domain (all program assignments) for a provider.
     *
     * @param providerNo String the provider number
     * @return List&lt;ProgramProvider&gt; the provider's program domain
     * @throws IllegalArgumentException if providerNo is {@code null}
     */
    public List<ProgramProvider> getProgramDomain(String providerNo);

    /**
     * Retrieves only active program assignments for a provider.
     *
     * @param providerNo String the provider number
     * @return List&lt;ProgramProvider&gt; active program assignments
     * @throws IllegalArgumentException if providerNo is invalid
     */
    public List<ProgramProvider> getActiveProgramDomain(String providerNo);

    /**
     * Retrieves program assignments for a provider within a specific facility.
     *
     * @param providerNo String the provider number
     * @param facilityId Integer the facility ID
     * @return List&lt;ProgramProvider&gt; program assignments filtered by facility
     * @throws IllegalArgumentException if providerNo is invalid
     */
    public List<ProgramProvider> getProgramDomainByFacility(String providerNo, Integer facilityId);

    /**
     * Checks whether a specific program is in a provider's program domain.
     *
     * @param providerNo String the provider number
     * @param programId Integer the program ID
     * @return {@code true} if the program is in the provider's domain
     * @throws IllegalArgumentException if providerNo is invalid
     */
    public boolean isThisProgramInProgramDomain(String providerNo, Integer programId);

    /**
     * Retrieves the distinct facilities in a provider's program domain.
     *
     * @param providerNo String the provider number
     * @return List&lt;Facility&gt; facilities in the provider's domain
     * @throws IllegalArgumentException if providerNo is invalid
     */
    public List<Facility> getFacilitiesInProgramDomain(String providerNo);

    /**
     * Updates the role of an existing program-provider assignment.
     *
     * @param pp ProgramProvider the assignment to update
     * @param roleId Long the new role ID
     */
    public void updateProviderRole(ProgramProvider pp, Long roleId);
}
