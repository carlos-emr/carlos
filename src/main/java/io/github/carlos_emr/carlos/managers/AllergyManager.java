/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.managers;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Service interface for managing patient allergy records in the CARLOS EMR system.
 *
 * <p>Provides retrieval operations for allergy data including lookup by patient,
 * date-based synchronization queries, and program-scoped filtering. All methods
 * require authenticated session context via {@link LoggedInInfo}.</p>
 *
 * @see AllergyManagerImpl
 * @see io.github.carlos_emr.carlos.commn.model.Allergy
 * @since 2026-03-17
 */
public interface AllergyManager {

    /**
     * Retrieves a single allergy record by its identifier.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param id Integer the allergy record identifier
     * @return Allergy the allergy record, or null if not found
     */
    public Allergy getAllergy(LoggedInInfo loggedInInfo, Integer id);

    /**
     * Retrieves all active (non-archived) allergies for a patient.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicNo Integer the patient demographic number
     * @return List of active Allergy records for the patient
     */
    public List<Allergy> getActiveAllergies(LoggedInInfo loggedInInfo, Integer demographicNo);

    /**
     * Retrieves allergy records updated on or after the specified date,
     * useful for incremental data synchronization.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param updatedAfterThisDateInclusive Date the inclusive lower bound for update timestamps
     * @param itemsToReturn int the maximum number of records to return
     * @return List of Allergy records updated after the specified date
     */
    public List<Allergy> getUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateInclusive,
                                             int itemsToReturn);

    /**
     * Retrieves allergy records for a specific patient updated after the given date.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicId Integer the patient demographic number
     * @param updatedAfterThisDate Date the lower bound for update timestamps
     * @return List of Allergy records matching the criteria
     */
    public List<Allergy> getByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo, Integer demographicId,
                                                            Date updatedAfterThisDate);

    /**
     * Retrieves allergy records filtered by program, provider, patient, and date criteria.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param programId Integer the program identifier to filter by
     * @param providerNo String the provider number to filter by
     * @param demographicId Integer the patient demographic number
     * @param updatedAfterThisDateInclusive Calendar the inclusive lower bound for update timestamps
     * @param itemsToReturn int the maximum number of records to return
     * @return List of Allergy records matching all specified criteria
     */
    public List<Allergy> getAllergiesByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId,
                                                                      String providerNo, Integer demographicId, Calendar updatedAfterThisDateInclusive, int itemsToReturn);
}
