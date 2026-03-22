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

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.MeasurementMap;

/**
 * DAO interface for clinical measurement operations.
 *
 * @since 2001
 */

public interface MeasurementMapDao extends AbstractDao<MeasurementMap> {

    /**
     * Add Measurement Map.
     *
     * @param measurementMap MeasurementMap the measurementMap
     */
    public void addMeasurementMap(MeasurementMap measurementMap);

    /**
     * Get All Maps.
     * @return List<MeasurementMap>
     */
    public List<MeasurementMap> getAllMaps();

    /**
     * Get Maps By Ident.
     *
     * @param identCode String the identCode
     * @return List<MeasurementMap>
     */
    public List<MeasurementMap> getMapsByIdent(String identCode);

    /**
     * Find By Loinc Code.
     *
     * @param loincCode String the loincCode
     * @return List<MeasurementMap>
     */
    public List<MeasurementMap> findByLoincCode(String loincCode);

    /**
     * Get Maps By Loinc.
     *
     * @param loinc String the loinc
     * @return List<MeasurementMap>
     */
    public List<MeasurementMap> getMapsByLoinc(String loinc);

    /**
     * Find By Loinc Code And Lab Type.
     *
     * @param loincCode String the loincCode
     * @param labType String the labType
     * @return List<MeasurementMap>
     */
    public List<MeasurementMap> findByLoincCodeAndLabType(String loincCode, String labType);

    public MeasurementMap findByLonicCodeLabTypeAndMeasurementName(String loincCode, String labType,
                                                                   String measurementName);

    /**
     * Find Distinct Lab Types.
     * @return List<String>
     */
    public List<String> findDistinctLabTypes();

    /**
     * Find Distinct Loinc Codes.
     * @return List<String>
     */
    public List<String> findDistinctLoincCodes();

    /**
     * Find Distinct Loinc Codes By Lab Type.
     *
     * @param lab_type MeasurementMap.LAB_TYPE the lab_type
     * @return List<String>
     */
    public List<String> findDistinctLoincCodesByLabType(MeasurementMap.LAB_TYPE lab_type);

    /**
     * Find Measurements.
     *
     * @param labType String the labType
     * @param idCode String the idCode
     * @param name String the name
     * @return List<Object[]>
     */
    public List<Object[]> findMeasurements(String labType, String idCode, String name);

    /**
     * Find Measurements By Name.
     *
     * @param searchString String the searchString
     * @return List<MeasurementMap>
     */
    public List<MeasurementMap> findMeasurementsByName(String searchString);

    /**
     * Search Measurements By Name.
     *
     * @param searchString String the searchString
     * @return List<MeasurementMap>
     */
    public List<MeasurementMap> searchMeasurementsByName(String searchString);
}
