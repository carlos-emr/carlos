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

import io.github.carlos_emr.carlos.commn.model.Drug;

/**
 * DAO interface for drug and prescription operations.
 *
 * @since 2001
 */

public interface DrugMergedDemographicDao extends DrugDao {
    /**
     * Find By Demographic Id.
     *
     * @param demographicId Integer the demographicId
     * @return List<Drug>
     */
    List<Drug> findByDemographicId(Integer demographicId);

    /**
     * Find By Demographic Id.
     *
     * @param demographicId Integer the demographicId
     * @param archived Boolean the archived
     * @return List<Drug>
     */
    List<Drug> findByDemographicId(Integer demographicId, Boolean archived);

    /**
     * Find By Demographic Id Order By Date.
     *
     * @param demographicId Integer the demographicId
     * @param archived Boolean the archived
     * @return List<Drug>
     */
    List<Drug> findByDemographicIdOrderByDate(Integer demographicId, Boolean archived);

    /**
     * Find By Demographic Id Order By Position.
     *
     * @param demographicId Integer the demographicId
     * @param archived Boolean the archived
     * @return List<Drug>
     */
    List<Drug> findByDemographicIdOrderByPosition(Integer demographicId, Boolean archived);

    /**
     * Find By Demographic Id Similar Drug Order By Date.
     *
     * @param demographicId Integer the demographicId
     * @param regionalIdentifier String the regionalIdentifier
     * @param customName String the customName
     * @return List<Drug>
     */
    List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier, String customName);

    /**
     * Find By Demographic Id Similar Drug Order By Date.
     *
     * @param demographicId Integer the demographicId
     * @param regionalIdentifier String the regionalIdentifier
     * @param customName String the customName
     * @param brandName String the brandName
     * @return List<Drug>
     */
    List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier, String customName, String brandName);

    /**
     * Find By Demographic Id Similar Drug Order By Date.
     *
     * @param demographicId Integer the demographicId
     * @param regionalIdentifier String the regionalIdentifier
     * @param customName String the customName
     * @param brandName String the brandName
     * @param atc String the atc
     * @return List<Drug>
     */
    List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier, String customName, String brandName, String atc);

    /**
     * Get Unique Prescriptions.
     *
     * @param demographic_no String the demographic_no
     * @return List<Drug>
     */
    List<Drug> getUniquePrescriptions(String demographic_no);

    /**
     * Get Prescriptions.
     *
     * @param demographic_no String the demographic_no
     * @return List<Drug>
     */
    List<Drug> getPrescriptions(String demographic_no);

    /**
     * Get Prescriptions.
     *
     * @param demographic_no String the demographic_no
     * @param all boolean the all
     * @return List<Drug>
     */
    List<Drug> getPrescriptions(String demographic_no, boolean all);

    /**
     * Find By Demographic Id Updated After Date.
     *
     * @param demographicId Integer the demographicId
     * @param updatedAfterThisDate Date the updatedAfterThisDate
     * @return List<Drug>
     */
    List<Drug> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date updatedAfterThisDate);

    /**
     * Find By Demographic Id And Atc.
     *
     * @param demographicNo int the demographicNo
     * @param atc String the atc
     * @return List<Drug>
     */
    List<Drug> findByDemographicIdAndAtc(int demographicNo, String atc);

    /**
     * Find By Demographic Id And Region.
     *
     * @param demographicNo int the demographicNo
     * @param regionalIdentifier String the regionalIdentifier
     * @return List<Drug>
     */
    List<Drug> findByDemographicIdAndRegion(int demographicNo, String regionalIdentifier);

    /**
     * Find By Demographic Id And Drug Id.
     *
     * @param demographicNo int the demographicNo
     * @param drugId Integer the drugId
     * @return List<Drug>
     */
    List<Drug> findByDemographicIdAndDrugId(int demographicNo, Integer drugId);

    /**
     * Find Drugs And Prescriptions.
     *
     * @param demographicNo int the demographicNo
     * @return List<Object[]>
     */
    List<Object[]> findDrugsAndPrescriptions(int demographicNo);

    /**
     * Find By Region Brand Demographic And Provider.
     *
     * @param regionalIdentifier String the regionalIdentifier
     * @param brandName String the brandName
     * @param demographicNo int the demographicNo
     * @param providerNo String the providerNo
     * @return List<Drug>
     */
    List<Drug> findByRegionBrandDemographicAndProvider(String regionalIdentifier, String brandName, int demographicNo, String providerNo);

    /**
     * Find By Brand Name Demographic And Provider.
     *
     * @param brandName String the brandName
     * @param demographicNo int the demographicNo
     * @param providerNo String the providerNo
     * @return Drug
     */
    Drug findByBrandNameDemographicAndProvider(String brandName, int demographicNo, String providerNo);

    /**
     * Find By Custom Name Demographic Id And Provider No.
     *
     * @param customName String the customName
     * @param demographicNo int the demographicNo
     * @param providerNo String the providerNo
     * @return Drug
     */
    Drug findByCustomNameDemographicIdAndProviderNo(String customName, int demographicNo, String providerNo);

    /**
     * Find Last Not Archived Id.
     *
     * @param brandName String the brandName
     * @param genericName String the genericName
     * @param demographicNo int the demographicNo
     * @return Integer
     */
    Integer findLastNotArchivedId(String brandName, String genericName, int demographicNo);

    /**
     * Find By Demographic Id Regional Identifier And Atc Code.
     *
     * @param atcCode String the atcCode
     * @param regionalIdentifier String the regionalIdentifier
     * @param demographicNo int the demographicNo
     * @return Drug
     */
    Drug findByDemographicIdRegionalIdentifierAndAtcCode(String atcCode, String regionalIdentifier, int demographicNo);
}
