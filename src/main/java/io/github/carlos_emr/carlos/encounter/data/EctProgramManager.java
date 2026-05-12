/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2025.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.encounter.data;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.util.LabelValueBean;

import java.util.Date;
import java.util.List;

/**
 * Encounter-specific Program Manager.
 * Acts as a UI-friendly bridge between the core Program Management (PM) Module and 
 * the Encounter (e-chart) interface. It is responsible for formatting program lists
 * into LabelValueBeans for Struts/JSP rendering and managing provider-specific 
 * encounter program defaults.
 *
 * @since 2026-05-05
 */
public interface EctProgramManager {

    /**
     * Retrieves all programs formatted for UI dropdowns.
     * @return List of LabelValueBeans containing program ID and names.
     */
    List<LabelValueBean> getProgramBeans();

    /**
     * Retrieves active programs for a specific provider and facility.
     * @param providerNo The provider's ID.
     * @param facilityId The facility ID.
     * @return List of formatted LabelValueBeans.
     */
    List<LabelValueBean> getProgramBeans(String providerNo, Integer facilityId);

    /**
     * Retrieves program label/value beans available for the specified facility.
     *
     * @param facilityId the facility identifier; callers should pass a non-null positive value
     * @return a list of program name/id beans, or an empty list when no programs match
     */
    List<LabelValueBean> getProgramBeansByFacilityId(Integer facilityId);

    /**
     * Retrieves appointment-view program beans for the specified provider and optional facility filter.
     *
     * @param providerNo the provider identifier; blank values yield no programs
     * @param facilityId the facility identifier, or {@code null} to include all facilities
     * @return appointment-view program label/value beans, or an empty list when none are available
     */
    List<LabelValueBean> getProgramForApptViewBeans(String providerNo, Integer facilityId);

    /**
     * Retrieves demographic label/value beans for patients assigned to beds in a program as of a date.
     *
     * @param programId the program identifier to query
     * @param dt the effective lookup date; must not be {@code null}
     * @param archiveView {@code "true"} to query archived demographics, otherwise active demographics
     * @return demographic beans whose label is {@code "LastName, FirstName"} and value is the demographic number
     */
    List<LabelValueBean> getDemographicByBedProgramIdBeans(int programId, Date dt, String archiveView);

    /**
     * @return the legacy global default program ID.
     * @deprecated Use {@link #getDefaultProgramId(String)} for provider-specific defaults.
     */
    @Deprecated
    int getDefaultProgramId();

    /**
     * Retrieves the default program ID the specified provider has selected for encounters.
     * @param providerNo The provider's ID.
     * @return The default program ID.
     */
    int getDefaultProgramId(String providerNo);

    /**
     * Persists the default encounter program for the specified provider, replacing any existing value.
     *
     * @param providerNo the provider identifier whose default program should be updated
     * @param programId the program identifier to persist as the default
     * @implNote Implementations persist the change immediately and overwrite any prior default for the provider.
     */
    void setDefaultProgramId(String providerNo, int programId);

    /**
     * Retrieves whether the specified provider has signature mode enabled.
     *
     * @param providerNo the provider identifier to query
     * @return {@code Boolean.TRUE} when enabled, otherwise {@code Boolean.FALSE}; implementations may create a default row when absent
     */
    Boolean getProviderSig(String providerNo);

    /**
     * Toggles the persisted signature mode flag for the specified provider.
     *
     * @param providerNo the provider identifier whose signature setting should be toggled
     */
    void toggleSig(String providerNo);

    /**
     * Returns the underlying program-provider DAO exposed by this legacy interface.
     *
     * @return the backing {@link ProgramProviderDAO} instance
     * @implNote The trailing {@code T} is a legacy compatibility artifact in the method name.
     */
    ProgramProviderDAO getProgramProviderDAOT();

    /**
     * Retrieves the basic program metadata array for the specified program.
     *
     * @param programId the program identifier to look up
     * @return a new two-element array containing {@code [0] = program name} and {@code [1] = program ID string},
     *         or an empty array when the program is not found
     */
    String[] getProgramInformation(int programId);
}
