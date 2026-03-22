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

import java.util.Collection;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.DemographicSets;

/**
 * DAO interface for patient demographic operations.
 *
 * @since 2001
 */

public interface DemographicSetsDao extends AbstractDao<DemographicSets> {

    /**
     * Find By Set Name.
     *
     * @param setName String the setName
     * @return List<DemographicSets>
     */
    public List<DemographicSets> findBySetName(String setName);

    /**
     * Find By Set Names.
     *
     * @param setNameList Collection<String> the setNameList
     * @return List<DemographicSets>
     */
    public List<DemographicSets> findBySetNames(Collection<String> setNameList);

    /**
     * Find By Set Name And Eligibility.
     *
     * @param setName String the setName
     * @param eligibility String the eligibility
     * @return List<DemographicSets>
     */
    public List<DemographicSets> findBySetNameAndEligibility(String setName, String eligibility);

    /**
     * Find Set Names By Demographic No.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<String>
     */
    public List<String> findSetNamesByDemographicNo(Integer demographicNo);

    /**
     * Find Set Names.
     * @return List<String>
     */
    public List<String> findSetNames();

    /**
     * Find By Set Name And Demographic No.
     *
     * @param setName String the setName
     * @param demographicNo int the demographicNo
     * @return List<DemographicSets>
     */
    public List<DemographicSets> findBySetNameAndDemographicNo(String setName, int demographicNo);
}
