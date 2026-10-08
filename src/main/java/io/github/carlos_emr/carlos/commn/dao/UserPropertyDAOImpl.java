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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author rjonasz
 */
@Repository
public class UserPropertyDAOImpl extends AbstractDaoImpl<UserProperty> implements UserPropertyDAO {

    /** JPQL conditions for rows of a user, and for clinic-wide rows (no provider). */
    private static final String PROVIDER_ROWS = " and p.providerNo is not null and p.providerNo <> ''";
    private static final String CLINIC_ROWS = " and (p.providerNo is null or p.providerNo = '')";


    /**
     * Creates a new instance of UserPropertyDAO
     */
    public UserPropertyDAOImpl() {
        super(UserProperty.class);
    }


    @Override
    public void delete(UserProperty prop) {
        if (prop != null && prop.getId() != null) {
            remove(prop.getId());
        }
    }
    

    public void saveProp(String provider, String userPropertyName, String value) {
        UserProperty prop = getProp(provider, userPropertyName);
        if (prop == null) {
            prop = new UserProperty();
            prop.setProviderNo(provider);
            prop.setName(userPropertyName);
        }
        prop.setValue(value);
        saveProp(prop);
    }


    public void saveProp(UserProperty prop) {
        if (prop.getId() != null && prop.getId().intValue() > 0) {
            merge(prop);
        } else {
            persist(prop);
        }
    }

    //Should properties be updateable?
    public void saveProp(String name, String val) {
        if (val != null) {
            UserProperty prop = getProp(name);
            if (prop == null) {
                prop = new UserProperty();
                prop.setName(name);
            }
            prop.setValue(val);
            saveProp(prop);
        }
    }

    public String getStringValue(String provider, String propertyName) {
        try {
            return getProp(provider, propertyName).getValue();
        } catch (Exception e) {
            return null;
        }
    }

    public List<UserProperty> getAllProperties(String name, List<String> list) {
        Query query = entityManager.createQuery("select p from UserProperty p where p.name = ?1 and p.providerNo in ?2");
        query.setParameter(1, name);
        query.setParameter(2, list);

        return query.getResultList();
    }

    public List<UserProperty> getPropValues(String name, String value) {
        Query query = entityManager.createQuery("select p from UserProperty p where p.name = ?1 and p.value = ?2");
        query.setParameter(1, name);
        query.setParameter(2, value);

        return query.getResultList();
    }

    @Override
    public List<UserProperty> findProviderProperties(String name) {
        return rows(name, PROVIDER_ROWS);
    }

    @Override
    public List<UserProperty> findClinicProperties(String name) {
        return rows(name, CLINIC_ROWS);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UserProperty> lockClinicProperties(String name) {
        return lockEach(rows(name, CLINIC_ROWS));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UserProperty> lockProviderProperties(String name) {
        return lockEach(rows(name, PROVIDER_ROWS));
    }

    private List<UserProperty> rows(String name, String whose) {
        Query query = entityManager.createQuery("select p from UserProperty p where p.name = ?1" + whose
                + " order by p.id");
        query.setParameter(1, name);
        @SuppressWarnings("unchecked")
        List<UserProperty> list = query.getResultList();
        return list;
    }

    /**
     * Locks each row by its primary key, oldest first, and re-reads it. A locking query by name
     * would lock whatever its index range covers (gaps included, before #3981's
     * {@code property (name, provider_no)} index the whole table); a lock by key touches only
     * these rows.
     */
    private List<UserProperty> lockEach(List<UserProperty> rows) {
        List<UserProperty> locked = new ArrayList<>(rows.size());
        for (UserProperty row : rows) {
            try {
                entityManager.refresh(row, LockModeType.PESSIMISTIC_WRITE);
            } catch (EntityNotFoundException e) {
                // Removed by another transaction since the read above: report it as the
                // concurrent change it is (the transaction is already marked for rollback).
                throw new OptimisticLockException("Property row removed by another transaction", e, row);
            }
            locked.add(row);
        }
        return locked;
    }

    public UserProperty getProp(String prov, String name) {
        Query query = entityManager.createQuery("select p from UserProperty p where p.providerNo = ?1 and p.name = ?2");
        query.setParameter(1, prov);
        query.setParameter(2, name);

        @SuppressWarnings("unchecked")
        List<UserProperty> list = query.getResultList();
        if (list != null && list.size() > 0) {
            UserProperty prop = list.get(0);
            return prop;
        } else
            return null;
    }

    public UserProperty getProp(String name) {
        Query query = entityManager.createQuery("select p from UserProperty p where p.name = ?1");
        query.setParameter(1, name);

        @SuppressWarnings("unchecked")
        List<UserProperty> list = query.getResultList();
        if (list != null && list.size() > 0) {
            UserProperty prop = list.get(0);
            return prop;
        } else
            return null;
    }

    public List<UserProperty> getDemographicProperties(String providerNo) {
        Query query = entityManager.createQuery("select p from UserProperty p where p.providerNo = ?1");
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<UserProperty> list = query.getResultList();

        return list;
    }

    public Map<String, String> getProviderPropertiesAsMap(String providerNo) {
        Map<String, String> map = new HashMap<String, String>();

        Query query = entityManager.createQuery("select p from UserProperty p where p.providerNo = ?1");
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<UserProperty> list = query.getResultList();
        for (UserProperty p : list) {
            map.put(p.getName(), p.getValue());
        }
        return map;
    }

    public void saveProperties(String providerNo, Map<String, String> props) {
        for (String key : props.keySet()) {
            String value = props.get(key);
            if (value == null) value = new String();
            UserProperty prop = null;
            if ((prop = this.getProp(providerNo, key)) != null) {
                prop.setValue(value);
            } else {
                prop = new UserProperty();
                prop.setName(key);
                prop.setProviderNo(providerNo);
                prop.setValue(value);
            }
            saveProp(prop);
        }
    }
}
