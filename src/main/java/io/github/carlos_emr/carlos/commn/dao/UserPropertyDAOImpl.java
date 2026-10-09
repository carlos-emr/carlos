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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.springframework.stereotype.Repository;

/**
 * @author rjonasz
 */
@Repository
public class UserPropertyDAOImpl extends AbstractDaoImpl<UserProperty> implements UserPropertyDAO {

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

    @Override
    public List<UserProperty> findClinicEmailFooter() {
        return entityManager.createQuery("select p from UserProperty p where p.name = :name "
                + "and (p.providerNo is null or p.providerNo = '') order by p.id", UserProperty.class)
                .setParameter("name", "email_footer_clinic_default").getResultList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<UserProperty> findClinicEmailFooterForUpdate() {
        // A locking read sees the winner, or strict MariaDB snapshot isolation refuses the
        // transaction. Neither an earlier snapshot nor a cached entity may overwrite the winner.
        try {
            // Scalar native rows bypass Hibernate's first-level cache and refresh loaders, which
            // can reread an earlier repeatable-read view even after a locking entity query.
            @SuppressWarnings("unchecked")
            List<Object[]> values = entityManager.createNativeQuery(
                    "SELECT `id`, `provider_no`, `value` FROM `property` WHERE `name` = :name "
                            + "AND (`provider_no` IS NULL OR `provider_no` = '') ORDER BY `id` FOR UPDATE")
                    .setParameter("name", "email_footer_clinic_default").getResultList();
            return values.stream().map(value -> {
                UserProperty row = new UserProperty();
                row.setId(Math.toIntExact(((Number) value[0]).longValue()));
                row.setProviderNo((String) value[1]);
                row.setName("email_footer_clinic_default");
                row.setValue((String) value[2]);
                return row; // Fresh detached snapshots; permitted writes use scoped current UPDATE/DELETE.
            }).toList();
        } catch (jakarta.persistence.PersistenceException failure) {
            // MariaDB 11.8 defaults to snapshot isolation: SQL 1020 aborts a transaction whose
            // locking read no longer fits its read view. Give the admin action the established
            // optimistic-conflict outcome; never retry in this already-aborted transaction.
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql && sql.getErrorCode() == 1020) {
                    throw new jakarta.persistence.OptimisticLockException(
                            "Clinic email footer settings changed concurrently", failure);
                }
            }
            throw failure;
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void updateClinicEmailFooter(UserProperty current, String value) {
        if (current == null || current.getId() == null || current.getId() <= 0
                || !"email_footer_clinic_default".equals(current.getName())
                || (current.getProviderNo() != null && !current.getProviderNo().isEmpty())) {
            throw new IllegalArgumentException("A locked clinic footer row is required");
        }
        int changed = entityManager.createNativeQuery(
                "UPDATE `property` SET `value` = :value WHERE `id` = :id AND `name` = :name "
                        + "AND (`provider_no` IS NULL OR `provider_no` = '')")
                .setParameter("id", current.getId()).setParameter("name", "email_footer_clinic_default")
                .setParameter("value", value).executeUpdate();
        if (changed != 1) throw new jakarta.persistence.OptimisticLockException(
                "Clinic email footer settings changed concurrently");
        // Only this row may have been loaded before the locking scalar read. Keep that
        // managed instance coherent so a later caller flush cannot restore cached text.
        UserProperty managed = entityManager.find(UserProperty.class, current.getId());
        if (managed != null) {
            managed.setProviderNo(current.getProviderNo());
            managed.setName("email_footer_clinic_default");
            managed.setValue(value);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void deleteClinicEmailFooter(Integer id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("A locked clinic footer row is required");
        int removed = entityManager.createNativeQuery(
                "DELETE FROM `property` WHERE `id` = :id AND `name` = :name "
                        + "AND (`provider_no` IS NULL OR `provider_no` = '')")
                .setParameter("id", id).setParameter("name", "email_footer_clinic_default").executeUpdate();
        if (removed != 1) throw new jakarta.persistence.OptimisticLockException(
                "Clinic email footer settings changed concurrently");
        // Detach only a previously cached target, never clear unrelated caller entities.
        UserProperty managed = entityManager.find(UserProperty.class, id);
        if (managed != null) entityManager.detach(managed);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockClinicEmailFooterSettings() {
        // A durable row exists before the first property does. No property gap lock is assumed.
        var clinics = entityManager.createQuery("select c.id from Clinic c order by c.id", Integer.class)
                .setMaxResults(1).getResultList();
        if (clinics.isEmpty()) {
            throw new IllegalStateException("Clinic configuration is missing");
        }
        entityManager.find(io.github.carlos_emr.carlos.commn.model.Clinic.class, clinics.get(0),
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockPersonalEmailFooterOwner(String providerNo) {
        personalCurrentRead(() -> {
            var owner = entityManager.find(io.github.carlos_emr.carlos.commn.model.Provider.class, providerNo,
                    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (owner == null) {
                throw new IllegalArgumentException("Personal footer owner is missing");
            }
            return owner;
        });
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<UserProperty> findPersonalEmailFooterForUpdate(String providerNo) {
        return personalCurrentRead(() -> {
            // Scalar current reads bypass entity-cache hydration and refresh's old RR view.
            @SuppressWarnings("unchecked")
            List<Object[]> values = entityManager.createNativeQuery(
                    "SELECT `id`, `value` FROM `property` WHERE `name` = :name "
                            + "AND `provider_no` = :provider ORDER BY `id` FOR UPDATE")
                    .setParameter("name", "email_footer").setParameter("provider", providerNo).getResultList();
            return values.stream().map(value -> {
                var row = new UserProperty();
                row.setId(Math.toIntExact(((Number) value[0]).longValue()));
                row.setName("email_footer"); row.setProviderNo(providerNo); row.setValue((String) value[1]);
                return row;
            }).toList();
        });
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void savePersonalEmailFooterRow(String providerNo, UserProperty property) {
        if (providerNo == null || providerNo.isBlank() || property == null
                || !"email_footer".equals(property.getName()) || !providerNo.equals(property.getProviderNo())) {
            throw new IllegalArgumentException("Personal footer row belongs to another setting or owner");
        }
        if (property.getId() == null) {
            // IDENTITY creation has no historical entity to hydrate from the caller's RR view.
            persist(property);
            return;
        }
        personalCurrentRead(() -> {
            var managed = entityManager.find(UserProperty.class, property.getId());
            if (managed != null && (!"email_footer".equals(managed.getName())
                    || !providerNo.equals(managed.getProviderNo()))) {
                throw new IllegalArgumentException("Personal footer row belongs to another setting or owner");
            }
            int updated = entityManager.createNativeQuery("UPDATE `property` SET `value` = :value "
                    + "WHERE `id` = :id AND `name` = :name AND `provider_no` = :provider")
                    .setParameter("value", property.getValue()).setParameter("id", property.getId())
                    .setParameter("name", "email_footer").setParameter("provider", providerNo).executeUpdate();
            if (updated != 1) {
                throw new jakarta.persistence.OptimisticLockException("Personal footer row changed concurrently");
            }
            // Keep an earlier managed instance consistent without refreshing or clearing other data.
            if (managed != null) managed.setValue(property.getValue());
            return updated;
        });
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void deletePersonalEmailFooterRow(String providerNo, Integer propertyId) {
        if (providerNo == null || providerNo.isBlank() || propertyId == null || propertyId <= 0) {
            throw new IllegalArgumentException("Personal footer owner and row are required");
        }
        personalCurrentRead(() -> {
            // Evict only the entity being deleted if an earlier caller read already manages it.
            // A prior RR view may not see this ID; absence never suppresses the current SQL delete.
            var managed = entityManager.find(UserProperty.class, propertyId);
            if (managed != null) {
                if (!"email_footer".equals(managed.getName()) || !providerNo.equals(managed.getProviderNo())) {
                    throw new IllegalArgumentException("Personal footer row belongs to another setting or owner");
                }
                entityManager.detach(managed);
            }
            int deleted = entityManager.createNativeQuery("DELETE FROM `property` WHERE `id` = :id "
                    + "AND `name` = :name AND `provider_no` = :provider")
                    .setParameter("id", propertyId).setParameter("name", "email_footer")
                    .setParameter("provider", providerNo).executeUpdate();
            if (deleted != 1) {
                throw new jakarta.persistence.OptimisticLockException("Personal footer row changed concurrently");
            }
            return deleted;
        });
    }

    private static <T> T personalCurrentRead(java.util.function.Supplier<T> read) {
        try {
            return read.get();
        } catch (RuntimeException failure) {
            // MariaDB strict snapshot mode aborts this transaction on a changed row. Preserve the
            // winner and surface a retryable conflict; never retry inside the aborted transaction.
            var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
            for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql && sql.getErrorCode() == 1020) {
                    throw new jakarta.persistence.OptimisticLockException("Personal footer changed concurrently", failure);
                }
            }
            throw failure;
        }
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
