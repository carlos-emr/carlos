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
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Allergy;

/**
 * DAO interface for querying patient allergies with merged demographic support.
 * <p>
 * Extends {@link AbstractDao} to provide allergy queries that automatically include
 * records from merged patient demographics. When patients are merged in the EMR,
 * this DAO ensures allergy data from all merged demographic records is returned.
 *
 * @since 2001
 */
public interface AllergyMergedDemographicDao extends AbstractDao<Allergy> {

    /**
     * Finds all allergies for a patient, including records from merged demographics.
     *
     * @param demographic_no Integer the patient demographic number
     * @return List of {@link Allergy} records including merged demographics
     */
    public List<Allergy> findAllergies(final Integer demographic_no);

    /**
     * Finds active (non-archived) allergies for a patient, including records from merged demographics.
     *
     * @param demographic_no Integer the patient demographic number
     * @return List of active {@link Allergy} records including merged demographics
     */
    public List<Allergy> findActiveAllergies(final Integer demographic_no);

    /**
     * Finds active allergies ordered by description, including records from merged demographics.
     *
     * @param demographic_no Integer the patient demographic number
     * @return List of active {@link Allergy} records ordered by description
     */
    public List<Allergy> findActiveAllergiesOrderByDescription(final Integer demographic_no);

    /**
     * Finds allergies updated after the given date, including records from merged demographics.
     *
     * @param demographicId        Integer the patient demographic number
     * @param updatedAfterThisDate Date the cutoff date (exclusive)
     * @return List of {@link Allergy} records updated after the given date
     */
    public List<Allergy> findByDemographicIdUpdatedAfterDate(final Integer demographicId,
                                                             final Date updatedAfterThisDate);

    /**
     * Finds custom allergies (typeCode=0) with null non-drug flag, with pagination.
     *
     * @param start int the zero-based pagination offset
     * @param limit int the maximum number of results to return
     * @return List of {@link Allergy} records with null non-drug flags
     */
    public List<Allergy> findAllCustomAllergiesWithNullNonDrugFlag(int start, int limit);
}
