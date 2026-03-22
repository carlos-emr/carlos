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
 * Service interface for managing program assignments and provider-program relationships
 * within the encounter context. Provides methods to retrieve program listings,
 * manage default program assignments for providers, and query demographics by program.
 *
 * @since 2005-01-01
 */
public interface EctProgramManager {
    /**
     * Retrieves all programs as label-value beans.
     *
     * @return List of LabelValueBean representing all programs
     */
    List<LabelValueBean> getProgramBeans();

    /**
     * Retrieves programs assigned to a specific provider, filtered by facility.
     *
     * @param providerNo String the provider number
     * @param facilityId Integer the facility ID to filter by (may be null for all facilities)
     * @return List of LabelValueBean representing the provider's programs
     */
    List<LabelValueBean> getProgramBeans(String providerNo, Integer facilityId);

    /**
     * Retrieves programs for a specific facility.
     *
     * @param facilityId Integer the facility ID
     * @return List of LabelValueBean representing programs at the facility
     */
    List<LabelValueBean> getProgramBeansByFacilityId(Integer facilityId);

    /**
     * Retrieves programs visible in the appointment view for a specific provider and facility.
     *
     * @param providerNo String the provider number
     * @param facilityId Integer the facility ID (may be null)
     * @return List of LabelValueBean representing appointment-visible programs
     */
    List<LabelValueBean> getProgramForApptViewBeans(String providerNo, Integer facilityId);

    /**
     * Retrieves demographics (patients) enrolled in a bed program, with archive filtering.
     *
     * @param programId int the bed program ID
     * @param dt Date the reference date for enrollment status
     * @param archiveView String "true" to include archived patients, otherwise active only
     * @return List of LabelValueBean representing patient name and demographic number pairs
     */
    List<LabelValueBean> getDemographicByBedProgramIdBeans(int programId, Date dt, String archiveView);

    /**
     * Returns the system-wide default program ID.
     *
     * @return int the default program ID
     */
    int getDefaultProgramId();

    /**
     * Returns the default program ID configured for a specific provider.
     *
     * @param providerNo String the provider number
     * @return int the provider's default program ID, or 0 if not set
     */
    int getDefaultProgramId(String providerNo);

    /**
     * Sets the default program for a provider.
     *
     * @param providerNo String the provider number
     * @param programId int the program ID to set as default
     */
    void setDefaultProgramId(String providerNo, int programId);

    /**
     * Returns the signature preference for a provider.
     *
     * @param providerNo String the provider number
     * @return Boolean true if the provider has signature enabled
     */
    Boolean getProviderSig(String providerNo);

    /**
     * Toggles the signature preference for a provider.
     *
     * @param providerNo String the provider number
     */
    void toggleSig(String providerNo);

    /**
     * Returns the underlying ProgramProviderDAO.
     *
     * @return ProgramProviderDAO the program-provider data access object
     */
    ProgramProviderDAO getProgramProviderDAOT();

    /**
     * Returns program name and ID as a string array.
     *
     * @param programId int the program ID
     * @return String[] array containing [programName, programId], or empty array if not found
     */
    String[] getProgramInformation(int programId);
}
