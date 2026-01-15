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

import javax.persistence.NoResultException;
import javax.persistence.Query;

import ca.openosp.openo.commn.model.Property;
import org.springframework.stereotype.Repository;

@Repository
public class PropertyDaoImpl extends AbstractDaoImpl<Property> implements PropertyDao {

    public PropertyDaoImpl() {
        super(Property.class);
    }

    /**
     * Find by name.
     *
     * @param name: property key name
     */
    @Override
    public List<Property> findByName(String name) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.name=?1";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, name);
        return query.getResultList();
    }

    /**
     * Find a property by name where the providers number is null. This identifies a globally set property that is not tied to a specific providers.
     * This is more of a legacy function, since most new global properties should be added to SystemPreferences instead.
     *
     * @param name property key name
     * @return list of properties found matching criteria
     */
    @Override
    public List<Property> findGlobalByName(String name) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.name=?1 and x.providerNo is null";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, name);
        return query.getResultList();
    }

    @Override
	public List<Property> findGlobalByName(Property.PROPERTY_KEY propertyName) {
		return findGlobalByName(propertyName.name());
	}

    public List<Property> findByNameAndProvider(Property.PROPERTY_KEY propertyName, String providerNo) {
        return findByNameAndProvider(propertyName.name(), providerNo);
    }

    /**
     * use method with enum parameter
     */
    @Deprecated
    @Override
    public List<Property> findByNameAndProvider(String propertyName, String providerNo) {
        Query query = createQuery("p", "p.name = ?1 AND p.providerNo = ?2");
        query.setParameter(1, propertyName);
        query.setParameter(2, providerNo);
        return query.getResultList();
    }

    @Override
    public List<Property> findByProvider(String providerNo) {
        Query query = createQuery("p", "p.providerNo = ?1");
        query.setParameter(1, providerNo);
        return query.getResultList();
    }

    @Override
    public Property checkByName(String name) {

        String sql = " select x from " + modelClass.getSimpleName() + " x where x.name=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, name);

        try {
            return (Property) query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }

    }

    @Override
    public String getValueByNameAndDefault(String name, String defaultValue) {
        Property result = checkByName(name);
        if (result == null) {
            return defaultValue;
        } else {
            return result.getValue();
        }
    }

    @Override
    public List<Property> findByNameAndValue(String name, String value) {
        String sqlCommand = "select x from Property x where x.name=?1 and x.value=?2";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, name);
        query.setParameter(2, value);
        return query.getResultList();
    }

    @Override
    public void removeByName(String name) {
        String sqlCommand = "delete from " + modelClass.getSimpleName() + " where name=?1";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, name);
        query.executeUpdate();
    }

    @Override
    public Boolean isActiveBooleanProperty(Property.PROPERTY_KEY name) {
        return isActiveBooleanProperty(name.name());
    }

    @Deprecated
    @Override
    public Boolean isActiveBooleanProperty(String name) {
        List<Property> properties = findByName(name);
        return !properties.isEmpty() && "true".equals(properties.get(0).getValue());
    }

    @Override
    public Boolean isActiveBooleanProperty(Property.PROPERTY_KEY name, String providerNo) {
        return isActiveBooleanProperty(name.name(), providerNo);
    }

    /**
     * use method with the enum parameter
     */
    @Deprecated
    @Override
    public Boolean isActiveBooleanProperty(String name, String providerNo) {
        List<Property> properties = findByNameAndProvider(name, providerNo);
        return !properties.isEmpty() && "true".equals(properties.get(0).getValue());
    }
}
