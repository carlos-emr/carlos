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
package io.github.carlos_emr.carlos.PMmodule.service;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.AgencyDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.Agency;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * Service interface for managing healthcare providers within the CARLOS EMR Program Management module.
 *
 * <p>Provides operations for provider retrieval, search, program domain management,
 * facility access, agency domain lookup, and security role management.</p>
 *
 * @see ProviderManagerImpl
 * @see Provider
 * @see ProgramProvider
 * @since 2005
 */
public interface ProviderManager {

    /**
     * Sets the provider data access object.
     *
     * @param providerDao ProviderDao the provider DAO to inject
     */
    void setProviderDao(ProviderDao providerDao);

    /**
     * Sets the agency data access object.
     *
     * @param agencyDao AgencyDao the agency DAO to inject
     */
    void setAgencyDao(AgencyDao agencyDao);

    /**
     * Sets the program provider data access object.
     *
     * @param dao ProgramProviderDAO the DAO to inject
     */
    void setProgramProviderDAO(ProgramProviderDAO dao);

    /**
     * Sets the security user role data access object.
     *
     * @param secUserRoleDao SecUserRoleDao the user role DAO to inject
     */
    void setSecUserRoleDao(SecUserRoleDao secUserRoleDao);

    /**
     * Retrieves a provider by their provider number.
     *
     * @param providerNo String the provider number
     * @return Provider the provider record, or {@code null} if not found
     */
    Provider getProvider(String providerNo);

    /**
     * Retrieves the display name of a provider.
     *
     * @param providerNo String the provider number
     * @return String the provider's formatted name
     */
    String getProviderName(String providerNo);

    /**
     * Retrieves all providers.
     *
     * @return List&lt;Provider&gt; list of all provider records
     */
    List<Provider> getProviders();

    /**
     * Retrieves all active providers.
     *
     * @return List&lt;Provider&gt; list of active provider records
     */
    List<Provider> getActiveProviders();

    /**
     * Retrieves active providers filtered by facility and program.
     *
     * @param facilityId String the facility identifier
     * @param programId String the program identifier
     * @return List&lt;Provider&gt; list of active providers matching the criteria
     */
    List<Provider> getActiveProviders(String facilityId, String programId);

    /**
     * Retrieves active providers filtered by provider number and shelter.
     *
     * @param providerNo String the provider number
     * @param shelterId Integer the shelter identifier
     * @return List&lt;Provider&gt; list of active providers matching the criteria
     */
    List<Provider> getActiveProviders(String providerNo, Integer shelterId);

    /**
     * Searches for providers by name.
     *
     * @param name String the name search term
     * @return List&lt;Provider&gt; list of matching providers
     */
    List<Provider> search(String name);

    /**
     * Retrieves the program domain (program assignments) for a provider.
     *
     * @param providerNo String the provider number
     * @return List&lt;ProgramProvider&gt; list of program provider assignments
     */
    List<ProgramProvider> getProgramDomain(String providerNo);

    /**
     * Retrieves the program domain for a provider within a specific facility.
     *
     * @param providerNo String the provider number
     * @param facilityId Integer the facility identifier
     * @return List&lt;ProgramProvider&gt; list of program provider assignments at the facility
     */
    List<ProgramProvider> getProgramDomainByFacility(String providerNo, Integer facilityId);

    /**
     * Retrieves all facilities that a provider has program access to.
     *
     * @param providerNo String the provider number
     * @return List&lt;Facility&gt; list of facilities in the provider's program domain
     */
    List<Facility> getFacilitiesInProgramDomain(String providerNo);

    /**
     * Retrieves the agency domain for a provider. Currently returns only the local agency.
     *
     * @param providerNo String the provider number
     * @return List&lt;Agency&gt; list of agencies accessible by the provider
     */
    List<Agency> getAgencyDomain(String providerNo);

    /**
     * Retrieves providers filtered by their type.
     *
     * @param type String the provider type
     * @return List&lt;Provider&gt; list of providers of the specified type
     */
    List<Provider> getProvidersByType(String type);

    /**
     * Retrieves security roles for a provider.
     *
     * @param providerNo String the provider number
     * @return List&lt;SecUserRole&gt; list of security user roles
     */
    List<SecUserRole> getSecUserRoles(String providerNo);

    /**
     * Saves a security user role assignment.
     *
     * @param sur SecUserRole the user role to save
     */
    void saveUserRole(SecUserRole sur);
}
