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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Query;
import jakarta.persistence.LockModeType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import org.springframework.transaction.annotation.Transactional;

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


    /**
     * Serializes preference creation against the existing owner row, including when there is
     * no property row to lock yet. Deleting all matching rows also repairs historical duplicates.
     * The owner lock and replacement remain in one transaction, so Default cannot leave a stale copy.
     */
    @Override
    @Transactional
    public void replaceTicklerTaskAssignee(String providerNo, String value) {
        if (providerNo == null || entityManager.find(Provider.class, providerNo, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new IllegalArgumentException("Unknown tickler preference owner");
        }
        entityManager.createQuery("delete from UserProperty p where p.providerNo = :owner and p.name = :name")
                .setParameter("owner", providerNo)
                .setParameter("name", UserProperty.TICKLER_TASK_ASSIGNEE)
                .executeUpdate();
        if (value != null) {
            UserProperty property = new UserProperty();
            property.setProviderNo(providerNo);
            property.setName(UserProperty.TICKLER_TASK_ASSIGNEE);
            property.setValue(value);
            entityManager.persist(property);
        }
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
        // Oldest row first: legacy data can hold duplicate (provider, name) rows, and saveProp must
        // update the same row getProviderPropertiesByNames reads.
        Query query = entityManager.createQuery("select p from UserProperty p where p.providerNo = ?1 and p.name = ?2 order by p.id");
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
    public UserProperty findClinicProperty(String name) {
        List<UserProperty> rows = entityManager.createQuery("select p from UserProperty p where p.name = :name "
                + "and (p.providerNo is null or p.providerNo = '') order by p.id", UserProperty.class)
                .setParameter("name", name).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
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
            for (int depth = 0;
            cause != null && depth < 16;
            depth++, cause = cause.getCause()) {
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
    public boolean isPersonalEmailFooterRow(String providerNo, Integer propertyId) {
        if (providerNo == null || providerNo.isBlank() || propertyId == null || propertyId <= 0) return false;
        // Use the same DB predicate as footer reads: Java lower/trim cannot reproduce its collation.
        return entityManager.createQuery("select count(p.id) from UserProperty p where p.id = :id "
                        + "and p.providerNo = :provider and p.name = :name", Long.class)
                .setParameter("id", propertyId).setParameter("provider", providerNo)
                .setParameter("name", "email_footer").getSingleResult() > 0;
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
            // Discover committed IDs without a locking scan or the caller's historical RR view.
            // The provider PK mutex already serializes this owner's cooperating footer writers.
            var ids = new java.util.TreeSet<Integer>();
            try (var current = entityManager.getEntityManagerFactory().createEntityManager()) {
                @SuppressWarnings("unchecked")
                List<Number> committed = current.createNativeQuery(
                        "SELECT `id` FROM `property` WHERE `name` = :name AND `provider_no` = :provider")
                        .setParameter("name", "email_footer").setParameter("provider", providerNo).getResultList();
                committed.forEach(id -> ids.add(Math.toIntExact(id.longValue())));
            }
            // The fresh connection cannot see uncommitted inserts or deletes made through
            // this DAO. Overlay only our recorded writes; never union historical RR row IDs.
            var writes = personalFooterWrites(false);
            if (writes != null) {
                if (!writes.created.isEmpty()) {
                    // This transaction's own inserts are visible to its own connection. One read
                    // classifies them with the same database predicate (name collation, exact owner)
                    // as the committed discovery above.
                    @SuppressWarnings("unchecked")
                    List<Number> own = entityManager.createNativeQuery(
                            "SELECT `id` FROM `property` WHERE `id` IN (:ids) AND `name` = :name AND `provider_no` = :provider")
                            .setParameter("ids", writes.created).setParameter("name", "email_footer")
                            .setParameter("provider", providerNo).getResultList();
                    own.forEach(id -> ids.add(Math.toIntExact(id.longValue())));
                }
                ids.removeAll(writes.deleted);
            }
            var rows = new java.util.ArrayList<UserProperty>();
            for (Integer id : ids) {
                @SuppressWarnings("unchecked")
                List<Object[]> values = entityManager.createNativeQuery(
                        "SELECT `id`, `name`, `provider_no`, `value` FROM `property` FORCE INDEX (PRIMARY) "
                                + "WHERE `id` = :id AND `name` = :name AND `provider_no` = :provider FOR UPDATE")
                        .setParameter("id", id).setParameter("name", "email_footer")
                        .setParameter("provider", providerNo).getResultList();
                // Legacy writers outside the provider mutex can remove or repurpose a row.
                // Refuse that race rather than silently create another default or edit its owner.
                if (values.size() != 1 || !providerNo.equals(values.get(0)[2])) {
                    throw new jakarta.persistence.OptimisticLockException("Personal footer changed concurrently");
                }
                Object[] value = values.get(0);
                var row = new UserProperty();
                row.setId(id);
                row.setName((String) value[1]);
                row.setProviderNo(providerNo);
                row.setValue((String) value[3]);
                rows.add(row);
            }
            return rows;
        });
    }

    // The DAO records the ids of the property rows this transaction inserts or removes through it,
    // without reading the database: only a footer save classifies them (above), so other property
    // writes pay no extra query. The state follows Spring transaction suspension and is removed on
    // commit or rollback; no historical view or other owner leaks in.
    private final Object personalFooterWritesKey = new Object();
    private static final class PersonalFooterWrites {
        final java.util.Set<Integer> created = new java.util.HashSet<>();
        final java.util.Set<Integer> deleted = new java.util.HashSet<>();
    }

    private PersonalFooterWrites personalFooterWrites(boolean create) {
        var writes = (PersonalFooterWrites) org.springframework.transaction.support.TransactionSynchronizationManager
                .getResource(personalFooterWritesKey);
        if (writes != null || !create) return writes;
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        var transactionWrites = new PersonalFooterWrites();
        org.springframework.transaction.support.TransactionSynchronizationManager
                .bindResource(personalFooterWritesKey, transactionWrites);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void suspend() {
                        org.springframework.transaction.support.TransactionSynchronizationManager
                                .unbindResourceIfPossible(personalFooterWritesKey);
                    }
                    @Override public void resume() {
                        org.springframework.transaction.support.TransactionSynchronizationManager
                                .bindResource(personalFooterWritesKey, transactionWrites);
                    }
                    @Override public void afterCompletion(int status) {
                        org.springframework.transaction.support.TransactionSynchronizationManager
                                .unbindResourceIfPossible(personalFooterWritesKey);
                    }
                });
        return transactionWrites;
    }

    /** Records ids inserted through the property DAO so a joining footer save sees its own writes. */
    @Override
    public void persist(io.github.carlos_emr.carlos.commn.model.AbstractModel<?> model) {
        boolean newRow = model instanceof UserProperty property && property.getId() == null;
        super.persist(model);
        if (newRow && model instanceof UserProperty property && property.getId() != null) {
            var writes = personalFooterWrites(true);
            if (writes != null) writes.created.add(property.getId());
        }
    }

    /** Records DAO removals so a joining footer save does not rediscover its uncommitted deletes. */
    @Override
    public void remove(io.github.carlos_emr.carlos.commn.model.AbstractModel<?> model) {
        // Record only a removal that happens: a detached row this transaction cannot see is not
        // deleted, so a footer save must still find it. For a detached row this is the lookup the
        // base class makes anyway; its own lookup then comes from the persistence context.
        boolean present = model instanceof UserProperty property && property.getId() != null
                && (entityManager.contains(property) || entityManager.find(UserProperty.class, property.getId()) != null);
        super.remove(model);
        if (present && model instanceof UserProperty property) {
            var writes = personalFooterWrites(true);
            if (writes != null) writes.deleted.add(property.getId());
        }
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
            if (managed != null && !providerNo.equals(managed.getProviderNo())) {
                throw new IllegalArgumentException("Personal footer row belongs to another setting or owner");
            }
            int updated = entityManager.createNativeQuery("UPDATE `property` SET `name` = :name, `value` = :value "
                    + "WHERE `id` = :id AND `name` = :name AND `provider_no` = :provider")
                    .setParameter("value", property.getValue()).setParameter("id", property.getId())
                    .setParameter("name", "email_footer").setParameter("provider", providerNo).executeUpdate();
            if (updated != 1) {
                throw new jakarta.persistence.OptimisticLockException("Personal footer row changed concurrently");
            }
            // Keep an earlier managed instance consistent without refreshing or clearing other data.
            if (managed != null) { managed.setName("email_footer");
            managed.setValue(property.getValue());
            }
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
                if (!providerNo.equals(managed.getProviderNo())) {
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
            var writes = personalFooterWrites(true);
            if (writes != null) writes.deleted.add(propertyId);
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
            for (Throwable cause = failure;
            cause != null && seen.add(cause);
            cause = cause.getCause()) {
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

    @Override
    public Map<String, String> getProviderPropertiesByNames(String providerNo, Collection<String> names) {
        Map<String, String> map = new HashMap<>();
        if (providerNo == null || names == null || names.isEmpty()) {
            return map;
        }
        Query query = entityManager.createQuery(
                "select p from UserProperty p where p.providerNo = ?1 and p.name in ?2 order by p.id");
        query.setParameter(1, providerNo);
        query.setParameter(2, names);

        @SuppressWarnings("unchecked")
        List<UserProperty> list = query.getResultList();
        for (UserProperty p : list) {
            // Ordered by id: a duplicated name resolves to its oldest row, deterministically, even
            // when that row's value is NULL (putIfAbsent would let a later row replace a null).
            if (!map.containsKey(p.getName())) {
                map.put(p.getName(), p.getValue());
            }
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
