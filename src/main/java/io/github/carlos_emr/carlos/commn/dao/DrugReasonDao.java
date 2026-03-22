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

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.DrugReason;

/**
 * DAO interface for drug and prescription operations.
 *
 * @since 2001
 */

public interface DrugReasonDao extends AbstractDao<DrugReason> {

    /**
     * Add New Drug Reason.
     *
     * @param d DrugReason the d
     * @return boolean
     */
    boolean addNewDrugReason(DrugReason d);

    /**
     * Has Reason.
     *
     * @param drugId Integer the drugId
     * @param codingSystem String the codingSystem
     * @param code String the code
     * @param onlyActive boolean the onlyActive
     * @return Boolean
     */
    Boolean hasReason(Integer drugId, String codingSystem, String code, boolean onlyActive);

    /**
     * Get Reasons For Drug I D.
     *
     * @param drugId Integer the drugId
     * @param onlyActive boolean the onlyActive
     * @return List<DrugReason>
     */
    List<DrugReason> getReasonsForDrugID(Integer drugId, boolean onlyActive);

    /**
     * Get Reasons By Icd9 Code And Demographic No.
     *
     * @param icd9Code String the icd9Code
     * @param demographicNo Integer the demographicNo
     * @return List<DrugReason>
     */
    List<DrugReason> getReasonsByIcd9CodeAndDemographicNo(String icd9Code, Integer demographicNo);
}
