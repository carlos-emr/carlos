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

import java.util.Collection;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;

/**
 * Data access interface for managing {@link ProgramClientRestriction} entities that
 * control client access restrictions to specific programs.
 *
 * <p>Restrictions can be scoped by program, client (demographic), or facility,
 * and can be enabled or disabled. Each restriction is hydrated with related
 * client, program, and provider objects.</p>
 *
 * @since 2005-05-28
 * @see ProgramClientRestriction
 * @see ProgramClientRestrictionDAOImpl
 */
public interface ProgramClientRestrictionDAO {

    /**
     * Finds enabled restrictions for a specific program and client.
     *
     * @param programId int the program ID
     * @param demographicNo int the demographic number of the client
     * @return Collection&lt;ProgramClientRestriction&gt; matching enabled restrictions
     */
    public Collection<ProgramClientRestriction> find(int programId, int demographicNo);

    /**
     * Saves or updates a client restriction record.
     *
     * @param restriction ProgramClientRestriction the restriction to persist
     */
    public void save(ProgramClientRestriction restriction);

    /**
     * Finds a restriction by its unique identifier.
     *
     * @param restrictionId int the restriction ID
     * @return ProgramClientRestriction the restriction, or {@code null} if not found
     */
    public ProgramClientRestriction find(int restrictionId);

    /**
     * Finds all enabled restrictions for a specific program.
     *
     * @param programId int the program ID
     * @return Collection&lt;ProgramClientRestriction&gt; enabled restrictions ordered by demographic number
     */
    public Collection<ProgramClientRestriction> findForProgram(int programId);

    /**
     * Finds all disabled restrictions for a specific program.
     *
     * @param programId int the program ID
     * @return Collection&lt;ProgramClientRestriction&gt; disabled restrictions ordered by demographic number
     */
    public Collection<ProgramClientRestriction> findDisabledForProgram(int programId);

    /**
     * Finds all enabled restrictions for a specific client.
     *
     * @param demographicNo int the demographic number of the client
     * @return Collection&lt;ProgramClientRestriction&gt; enabled restrictions ordered by program ID
     */
    public Collection<ProgramClientRestriction> findForClient(int demographicNo);

    /**
     * Finds all enabled restrictions for a client within a specific facility.
     *
     * @param demographicNo int the demographic number of the client
     * @param facilityId int the facility ID
     * @return Collection&lt;ProgramClientRestriction&gt; enabled restrictions ordered by program ID
     */
    public Collection<ProgramClientRestriction> findForClient(int demographicNo, int facilityId);

    /**
     * Finds all disabled restrictions for a specific client.
     *
     * @param demographicNo int the demographic number of the client
     * @return Collection&lt;ProgramClientRestriction&gt; disabled restrictions ordered by program ID
     */
    public Collection<ProgramClientRestriction> findDisabledForClient(int demographicNo);

    /**
     * Sets the DemographicDao used for hydrating restriction relationships.
     *
     * @param demographicDao DemographicDao the demographic data access object
     */
    public void setDemographicDao(DemographicDao demographicDao);

    /**
     * Sets the ProgramDao used for hydrating restriction relationships.
     *
     * @param programDao ProgramDao the program data access object
     */
    public void setProgramDao(ProgramDao programDao);

    /**
     * Sets the ProviderDao used for hydrating restriction relationships.
     *
     * @param providerDao ProviderDao the provider data access object
     */
    public void setProviderDao(ProviderDao providerDao);

}
 