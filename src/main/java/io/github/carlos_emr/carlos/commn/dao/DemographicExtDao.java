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

import java.util.*;

import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.enumerator.DemographicExtKey;

/**
 * DAO interface for patient demographic operations.
 *
 * @since 2001
 */

public interface DemographicExtDao extends AbstractDao<DemographicExt> {

    /**
     * Get Demographic Ext.
     *
     * @param id Integer the id
     * @return DemographicExt
     */
    public DemographicExt getDemographicExt(Integer id);

    /**
     * Get Demographic Ext By Demographic No.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<DemographicExt>
     */
    public List<DemographicExt> getDemographicExtByDemographicNo(Integer demographicNo);

    /**
     * Get Demographic Ext.
     *
     * @param demographicNo Integer the demographicNo
     * @param demographicExtKey DemographicExtKey the demographicExtKey
     * @return DemographicExt
     */
    public DemographicExt getDemographicExt(Integer demographicNo, DemographicExtKey demographicExtKey);

    /**
     * Get Demographic Ext.
     *
     * @param demographicNo Integer the demographicNo
     * @param key String the key
     * @return DemographicExt
     */
    public DemographicExt getDemographicExt(Integer demographicNo, String key);

    /**
     * Get Demographic Ext By Key And Value.
     *
     * @param demographicExtKey DemographicExtKey the demographicExtKey
     * @param value String the value
     * @return List<DemographicExt>
     */
    public List<DemographicExt> getDemographicExtByKeyAndValue(DemographicExtKey demographicExtKey, String value);

    /**
     * Get Demographic Ext By Key And Value.
     *
     * @param key String the key
     * @param value String the value
     * @return List<DemographicExt>
     */
    public List<DemographicExt> getDemographicExtByKeyAndValue(String key, String value);

    /**
     * Get Latest Demographic Ext.
     *
     * @param demographicNo Integer the demographicNo
     * @param key String the key
     * @return DemographicExt
     */
    public DemographicExt getLatestDemographicExt(Integer demographicNo, String key);

    /**
     * Update Demographic Ext.
     *
     * @param de DemographicExt the de
     */
    public void updateDemographicExt(DemographicExt de);

    /**
     * Save Demographic Ext.
     *
     * @param demographicNo Integer the demographicNo
     * @param key String the key
     * @param value String the value
     */
    public void saveDemographicExt(Integer demographicNo, String key, String value);

    /**
     * Remove Demographic Ext.
     *
     * @param id Integer the id
     */
    public void removeDemographicExt(Integer id);

    /**
     * Remove Demographic Ext.
     *
     * @param demographicNo Integer the demographicNo
     * @param key String the key
     */
    public void removeDemographicExt(Integer demographicNo, String key);

    /**
     * Get All Values For Demo.
     *
     * @param demo Integer the demo
     * @return Map<String, String>
     */
    public Map<String, String> getAllValuesForDemo(Integer demo);

    /**
     * Add Key.
     *
     * @param providerNo String the providerNo
     * @param demo Integer the demo
     * @param key String the key
     * @param value String the value
     */
    public void addKey(String providerNo, Integer demo, String key, String value);

    /**
     * Add Key.
     *
     * @param providerNo String the providerNo
     * @param demo Integer the demo
     * @param key String the key
     * @param newValue String the newValue
     * @param oldValue String the oldValue
     */
    public void addKey(String providerNo, Integer demo, String key, String newValue, String oldValue);

    /**
     * Get List Of Values For Demo.
     *
     * @param demo Integer the demo
     * @return List<String[]>
     */
    public List<String[]> getListOfValuesForDemo(Integer demo);

    /**
     * Get Value For Demo Key.
     *
     * @param demo Integer the demo
     * @param key String the key
     * @return String
     */
    public String getValueForDemoKey(Integer demo, String key);

    /**
     * Find Demographic Ids By Key Val.
     *
     * @param demographicExtKey DemographicExtKey the demographicExtKey
     * @param val String the val
     * @return List<Integer>
     */
    public List<Integer> findDemographicIdsByKeyVal(DemographicExtKey demographicExtKey, String val);

    /**
     * Find Demographic Ids By Key Val.
     *
     * @param key String the key
     * @param val String the val
     * @return List<Integer>
     */
    public List<Integer> findDemographicIdsByKeyVal(String key, String val);

    public List<DemographicExt> getMultipleDemographicExtKeyForDemographicNumbersByProviderNumber(
            final DemographicExtKey demographicExtKey,
            final Collection<Integer> demographicNumbers,
            final String midwifeNumber);

    public List<Integer> getDemographicNumbersByDemographicExtKeyAndProviderNumberAndDemographicLastNameRegex(
            final DemographicExtKey key,
            final String providerNumber,
            final String lastNameRegex);

}
