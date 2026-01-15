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
package ca.openosp.openo.commn.dao;

import java.util.List;

import ca.openosp.openo.commn.model.Property;

public interface PropertyDao extends AbstractDao<Property> {
    List<Property> findByName(String name);

    List<Property> findGlobalByName(String name);

    List<Property> findGlobalByName(Property.PROPERTY_KEY propertyName);

    List<Property> findByNameAndProvider(Property.PROPERTY_KEY propertyName, String providerNo);

    List<Property> findByNameAndProvider(String propertyName, String providerNo);

    List<Property> findByProvider(String providerNo);

    Property checkByName(String name);

    String getValueByNameAndDefault(String name, String defaultValue);

    List<Property> findByNameAndValue(String name, String value);

    void removeByName(String name);

    Boolean isActiveBooleanProperty(Property.PROPERTY_KEY name);

    Boolean isActiveBooleanProperty(Property.PROPERTY_KEY name, String providerNo);

    Boolean isActiveBooleanProperty(String name);

    Boolean isActiveBooleanProperty(String name, String providerNo);
}