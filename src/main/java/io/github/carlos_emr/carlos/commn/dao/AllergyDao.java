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

package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Allergy;

/**
 * DAO interface for managing patient allergy records.
 * <p>
 * Provides operations to query allergies by patient, status (active/archived),
 * and update dates. Supports both clinical display (ordered by severity or
 * description) and integrator synchronization (updated-after queries).
 *
 * @since 2005
 */
public interface AllergyDao extends AbstractDao<Allergy> {

    /**
     * Finds all allergies for a patient, ordered by archived status then severity descending.
     *
     * @param demographic_no Integer the patient demographic number
     * @return List of all {@link Allergy} records for the patient
     */
    public List<Allergy> findAllergies(Integer demographic_no);

    /**
     * Finds active (non-archived) allergies for a patient, ordered by severity.
     *
     * @param demographic_no Integer the patient demographic number
     * @return List of active {@link Allergy} records
     */
    public List<Allergy> findActiveAllergies(Integer demographic_no);

    /**
     * Finds active (non-archived) allergies for a patient, ordered by description alphabetically.
     *
     * @param demographic_no Integer the patient demographic number
     * @return List of active {@link Allergy} records ordered by description
     */
    public List<Allergy> findActiveAllergiesOrderByDescription(Integer demographic_no);

    /**
     * Finds allergies for a patient that were updated after the specified date.
     *
     * @param demographicId        Integer the patient demographic number
     * @param updatedAfterThisDate Date the cutoff date (exclusive)
     * @return List of {@link Allergy} records updated after the given date
     */
    public List<Allergy> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date updatedAfterThisDate);

    /**
     * Finds demographic IDs of patients with allergy records updated after the specified date.
     * Used for integrator synchronization.
     *
     * @param updatedAfterThisDate Date the cutoff date (exclusive)
     * @return List of patient demographic IDs
     */
    public List<Integer> findDemographicIdsUpdatedAfterDate(Date updatedAfterThisDate);

    /**
     * Finds allergies updated on or after the specified date, ordered by last update date.
     *
     * @param updatedAfterThisDateInclusive Date the cutoff date (inclusive)
     * @param itemsToReturn                 int the maximum number of results to return
     * @return List of {@link Allergy} records ordered by last update date
     */
    public List<Allergy> findByUpdateDate(Date updatedAfterThisDateInclusive, int itemsToReturn);

    /**
     * Finds allergies for a patient updated after the specified date, ordered by last update date.
     * Note: the providerNo parameter is currently unused as the provider field is blank.
     *
     * @param providerNo                     String the provider number (currently unused)
     * @param demographicId                  Integer the patient demographic number
     * @param updatedAfterThisDateExclusive  Date the cutoff date (exclusive)
     * @param itemsToReturn                  int the maximum number of results to return
     * @return List of {@link Allergy} records ordered by last update date ascending
     */
    public List<Allergy> findByProviderDemographicLastUpdateDate(String providerNo, Integer demographicId,
                                                                 Date updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Finds custom allergies (typeCode=0) that have a null non-drug flag, with pagination.
     * Used for data migration/cleanup operations.
     *
     * @param start int the zero-based pagination offset
     * @param limit int the maximum number of results to return
     * @return List of {@link Allergy} records with null non-drug flags, ordered by demographic number
     */
    public List<Allergy> findAllCustomAllergiesWithNullNonDrugFlag(int start, int limit);
}
