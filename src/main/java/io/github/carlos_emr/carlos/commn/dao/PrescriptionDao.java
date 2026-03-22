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

import io.github.carlos_emr.carlos.commn.model.Prescription;

/**
 * DAO interface for prescription operations.
 *
 * @since 2001
 */

public interface PrescriptionDao extends AbstractDao<Prescription> {

    /**
     * Find By Demographic Id.
     *
     * @param demographicId Integer the demographicId
     * @return List<Prescription>
     */
    public List<Prescription> findByDemographicId(Integer demographicId);

    /**
     * Find By Demographic Id Updated After Date.
     *
     * @param demographicId Integer the demographicId
     * @param afterThisDate Date the afterThisDate
     * @return List<Prescription>
     */
    public List<Prescription> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date afterThisDate);

    /**
     * Find By Demographic Id Updated After Date Exclusive.
     *
     * @param demographicId Integer the demographicId
     * @param afterThisDate Date the afterThisDate
     * @return List<Prescription>
     */
    public List<Prescription> findByDemographicIdUpdatedAfterDateExclusive(Integer demographicId, Date afterThisDate);

    /**
     * Update Prescriptions By Script No.
     *
     * @param scriptNo Integer the scriptNo
     * @param comment String the comment
     * @return int
     */
    public int updatePrescriptionsByScriptNo(Integer scriptNo, String comment);

    /**
     * Find By Update Date.
     *
     * @param updatedAfterThisDateExclusive Date the updatedAfterThisDateExclusive
     * @param itemsToReturn int the itemsToReturn
     * @return List<Prescription>
     */
    public List<Prescription> findByUpdateDate(Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Prescription> findByProviderDemographicLastUpdateDate(String providerNo, Integer demographicId,
                                                                      Date updatedAfterThisDateExclusive, int itemsToReturn);
}
