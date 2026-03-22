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

import java.util.HashMap;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;

/**
 * DAO interface for clinical measurement operations.
 *
 * @since 2001
 */

public interface MeasurementsExtDao extends AbstractDao<MeasurementsExt> {
    /**
     * Get Measurements Ext By Measurement Id.
     *
     * @param measurementId Integer the measurementId
     * @return List<MeasurementsExt>
     */
    List<MeasurementsExt> getMeasurementsExtByMeasurementId(Integer measurementId);

    /**
     * Get Measurements Ext Map By Measurement Id.
     *
     * @param measurementId Integer the measurementId
     * @return HashMap<String, MeasurementsExt>
     */
    HashMap<String, MeasurementsExt> getMeasurementsExtMapByMeasurementId(Integer measurementId);

    /**
     * Get Measurements Ext List By Measurement Id List.
     *
     * @param measurementIdList List<Integer> the measurementIdList
     * @return List<MeasurementsExt>
     */
    List<MeasurementsExt> getMeasurementsExtListByMeasurementIdList(List<Integer> measurementIdList);

    /**
     * Get Measurements Ext By Measurement Id And Key Val.
     *
     * @param measurementId Integer the measurementId
     * @param keyVal String the keyVal
     * @return MeasurementsExt
     */
    MeasurementsExt getMeasurementsExtByMeasurementIdAndKeyVal(Integer measurementId, String keyVal);

    /**
     * Get Measurement Id By Key Value.
     *
     * @param key String the key
     * @param value String the value
     * @return Integer
     */
    Integer getMeasurementIdByKeyValue(String key, String value);

    public Integer getMeasurementIdByLabNoAndTestName(String labNo, String testName); //new

    /**
     * Find By Key Value.
     *
     * @param key String the key
     * @param value String the value
     * @return List<MeasurementsExt>
     */
    List<MeasurementsExt> findByKeyValue(String key, String value);

    /**
     * Find Unmapped Measuremnt Ids.
     *
     * @param excludeList List<String> the excludeList
     * @return List<Integer>
     */
    List<Integer> findUnmappedMeasuremntIds(List<String> excludeList);
}
