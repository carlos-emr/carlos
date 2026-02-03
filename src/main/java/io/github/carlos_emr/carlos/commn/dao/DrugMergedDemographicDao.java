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
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Drug;

public interface DrugMergedDemographicDao extends DrugDao {
    List<Drug> findByDemographicId(Integer demographicId);

    List<Drug> findByDemographicId(Integer demographicId, Boolean archived);

    List<Drug> findByDemographicIdOrderByDate(Integer demographicId, Boolean archived);

    List<Drug> findByDemographicIdOrderByPosition(Integer demographicId, Boolean archived);

    List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier, String customName);

    List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier, String customName, String brandName);

    List<Drug> findByDemographicIdSimilarDrugOrderByDate(Integer demographicId, String regionalIdentifier, String customName, String brandName, String atc);

    List<Drug> getUniquePrescriptions(String demographic_no);

    List<Drug> getPrescriptions(String demographic_no);

    List<Drug> getPrescriptions(String demographic_no, boolean all);

    List<Drug> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date updatedAfterThisDate);

    List<Drug> findByDemographicIdAndAtc(int demographicNo, String atc);

    List<Drug> findByDemographicIdAndRegion(int demographicNo, String regionalIdentifier);

    List<Drug> findByDemographicIdAndDrugId(int demographicNo, Integer drugId);

    List<Object[]> findDrugsAndPrescriptions(int demographicNo);

    List<Drug> findByRegionBrandDemographicAndProvider(String regionalIdentifier, String brandName, int demographicNo, String providerNo);

    Drug findByBrandNameDemographicAndProvider(String brandName, int demographicNo, String providerNo);

    Drug findByCustomNameDemographicIdAndProviderNo(String customName, int demographicNo, String providerNo);

    Integer findLastNotArchivedId(String brandName, String genericName, int demographicNo);

    Drug findByDemographicIdRegionalIdentifierAndAtcCode(String atcCode, String regionalIdentifier, int demographicNo);
}
