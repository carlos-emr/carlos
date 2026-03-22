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

import io.github.carlos_emr.carlos.commn.model.Property;

/**
 * DAO interface for system property operations.
 *
 * @since 2001
 */

public interface PropertyDao extends AbstractDao<Property> {
    /**
     * Find By Name.
     *
     * @param name String the name
     * @return List<Property>
     */
    List<Property> findByName(String name);

    /**
     * Find Global By Name.
     *
     * @param name String the name
     * @return List<Property>
     */
    List<Property> findGlobalByName(String name);

    /**
     * Find Global By Name.
     *
     * @param propertyName Property.PROPERTY_KEY the propertyName
     * @return List<Property>
     */
    List<Property> findGlobalByName(Property.PROPERTY_KEY propertyName);

    /**
     * Find By Name And Provider.
     *
     * @param propertyName Property.PROPERTY_KEY the propertyName
     * @param providerNo String the providerNo
     * @return List<Property>
     */
    List<Property> findByNameAndProvider(Property.PROPERTY_KEY propertyName, String providerNo);

    /**
     * Find By Name And Provider.
     *
     * @param propertyName String the propertyName
     * @param providerNo String the providerNo
     * @return List<Property>
     */
    List<Property> findByNameAndProvider(String propertyName, String providerNo);

    /**
     * Find By Provider.
     *
     * @param providerNo String the providerNo
     * @return List<Property>
     */
    List<Property> findByProvider(String providerNo);

    /**
     * Check By Name.
     *
     * @param name String the name
     * @return Property
     */
    Property checkByName(String name);

    /**
     * Get Value By Name And Default.
     *
     * @param name String the name
     * @param defaultValue String the defaultValue
     * @return String
     */
    String getValueByNameAndDefault(String name, String defaultValue);

    /**
     * Find By Name And Value.
     *
     * @param name String the name
     * @param value String the value
     * @return List<Property>
     */
    List<Property> findByNameAndValue(String name, String value);

    /**
     * Remove By Name.
     *
     * @param name String the name
     */
    void removeByName(String name);

    /**
     * Is Active Boolean Property.
     *
     * @param name Property.PROPERTY_KEY the name
     * @return Boolean
     */
    Boolean isActiveBooleanProperty(Property.PROPERTY_KEY name);

    /**
     * Is Active Boolean Property.
     *
     * @param name Property.PROPERTY_KEY the name
     * @param providerNo String the providerNo
     * @return Boolean
     */
    Boolean isActiveBooleanProperty(Property.PROPERTY_KEY name, String providerNo);

    /**
     * Is Active Boolean Property.
     *
     * @param name String the name
     * @return Boolean
     */
    Boolean isActiveBooleanProperty(String name);

    /**
     * Is Active Boolean Property.
     *
     * @param name String the name
     * @param providerNo String the providerNo
     * @return Boolean
     */
    Boolean isActiveBooleanProperty(String name, String providerNo);
}