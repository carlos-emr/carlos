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
import java.util.Map;


import io.github.carlos_emr.carlos.commn.model.UserProperty;

/**
 * DAO interface for user operations.
 *
 * @since 2001
 */

public interface UserPropertyDAO extends AbstractDao<UserProperty> {
    /**
     * Delete.
     *
     * @param prop UserProperty the prop
     */
    void delete(UserProperty prop);

    /**
     * Save Prop.
     *
     * @param provider String the provider
     * @param userPropertyName String the userPropertyName
     * @param value String the value
     */
    void saveProp(String provider, String userPropertyName, String value);

    /**
     * Save Prop.
     *
     * @param prop UserProperty the prop
     */
    void saveProp(UserProperty prop);

    /**
     * Save Prop.
     *
     * @param name String the name
     * @param val String the val
     */
    void saveProp(String name, String val);

    /**
     * Get String Value.
     *
     * @param provider String the provider
     * @param propertyName String the propertyName
     * @return String
     */
    String getStringValue(String provider, String propertyName);

    /**
     * Get All Properties.
     *
     * @param name String the name
     * @param list List<String> the list
     * @return List<UserProperty>
     */
    List<UserProperty> getAllProperties(String name, List<String> list);

    /**
     * Get Prop Values.
     *
     * @param name String the name
     * @param value String the value
     * @return List<UserProperty>
     */
    List<UserProperty> getPropValues(String name, String value);

    /**
     * Get Prop.
     *
     * @param prov String the prov
     * @param name String the name
     * @return UserProperty
     */
    UserProperty getProp(String prov, String name);

    /**
     * Get Prop.
     *
     * @param name String the name
     * @return UserProperty
     */
    UserProperty getProp(String name);

    /**
     * Get Demographic Properties.
     *
     * @param providerNo String the providerNo
     * @return List<UserProperty>
     */
    List<UserProperty> getDemographicProperties(String providerNo);

    /**
     * Get Provider Properties As Map.
     *
     * @param providerNo String the providerNo
     * @return Map<String, String>
     */
    Map<String, String> getProviderPropertiesAsMap(String providerNo);

    /**
     * Save Properties.
     *
     * @param providerNo String the providerNo
     * @param props Map<String, String> the props
     */
    void saveProperties(String providerNo, Map<String, String> props);

    public final static String COLOR_PROPERTY = "ProviderColour";
}
