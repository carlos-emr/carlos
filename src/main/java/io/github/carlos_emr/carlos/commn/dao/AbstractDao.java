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

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * Generic base DAO interface providing standard CRUD and batch operations
 * for all persistent entities in the CARLOS EMR system.
 * <p>
 * All domain-specific DAO interfaces extend this interface to inherit common
 * persistence operations such as find, persist, merge, remove, and batch variants.
 * Results from list queries are capped at {@link #MAX_LIST_RETURN_SIZE} to prevent
 * unbounded memory consumption.
 *
 * @param <T> the entity type, which must extend {@link AbstractModel}
 * @since 2005
 */
public interface AbstractDao<T extends AbstractModel<?>> {

    /** Maximum number of entities returned by list queries to prevent excessive memory usage. */
    public static final int MAX_LIST_RETURN_SIZE = 5000;

    /**
     * Merges the state of the given entity into the current persistence context (update).
     *
     * @param o the detached entity whose state should be merged
     */
    void merge(AbstractModel<?> o);

    /**
     * Persists a new entity instance to the database (create).
     *
     * @param o the transient entity to persist
     */
    void persist(AbstractModel<?> o);

    /**
     * Persists a list of entities in batch using the default batch size of 25.
     *
     * @param oList the list of entities to persist
     */
    void batchPersist(List<T> oList);

    /**
     * Persists a list of entities in batch, flushing and clearing the persistence
     * context at the specified interval to manage memory.
     *
     * @param oList     the list of entities to persist
     * @param batchSize the number of entities to persist before flushing
     */
    void batchPersist(List<T> oList, int batchSize);

    /**
     * Removes a managed entity from the database.
     * The entity must be in a managed (attached) state.
     *
     * @param o the managed entity to remove
     */
    void remove(AbstractModel<?> o);

    /**
     * Removes a list of entities in batch using the default batch size of 25.
     *
     * @param oList the list of entities to remove
     */
    void batchRemove(List<T> oList);

    /**
     * Removes a list of entities in batch, flushing and clearing the persistence
     * context at the specified interval.
     *
     * @param oList     the list of entities to remove
     * @param batchSize the number of entities to remove before flushing
     */
    void batchRemove(List<T> oList, int batchSize);

    /**
     * Refreshes the state of the given managed entity from the database.
     *
     * @param o the managed entity to refresh
     */
    void refresh(AbstractModel<?> o);

    /**
     * Finds an entity by its primary key.
     *
     * @param id the primary key of the entity
     * @return the entity, or {@code null} if not found
     */
    T find(Object id);

    /**
     * Finds an entity by its integer primary key.
     *
     * @param id the integer primary key of the entity
     * @return the entity, or {@code null} if not found
     */
    T find(int id);

    /**
     * Finds an entity by its primary key and detaches it from the persistence context.
     * Changes to the returned entity will not be automatically persisted.
     *
     * @param id the primary key of the entity
     * @return the detached entity, or {@code null} if not found
     */
    T findDetached(Object id);

    /**
     * Detaches the given entity from the persistence context.
     * Subsequent changes to the entity will not be tracked or synchronized with the database.
     *
     * @param t the entity to detach; must not be {@code null}
     */
    void detach(@Nonnull T t);

    /**
     * Checks whether the given entity is managed by the current persistence context.
     *
     * @param o the entity to check
     * @return {@code true} if the entity is managed, {@code false} otherwise
     */
    boolean contains(AbstractModel<?> o);

    /**
     * Retrieves a paginated list of all entities of this type.
     *
     * @param offset Integer the zero-based offset for the first result, or {@code null} to start from the beginning
     * @param limit  Integer the maximum number of results to return; capped at {@link #MAX_LIST_RETURN_SIZE}
     * @return List of entities matching the pagination criteria
     * @throws MaxSelectLimitExceededException if the requested limit exceeds {@link #MAX_LIST_RETURN_SIZE}
     */
    List<T> findAll(Integer offset, Integer limit);

    /**
     * Removes an entity by its primary key.
     *
     * @param id the primary key of the entity to remove
     * @return {@code true} if the entity was found and removed, {@code false} if no entity exists with the given ID
     */
    boolean remove(Object id);

    /**
     * Returns the total count of all entities of this type in the database.
     *
     * @return int the total entity count
     */
    int getCountAll();

    /**
     * Executes a parameterized native SQL query with named parameters.
     * Parameters are properly bound to prevent SQL injection.
     *
     * @param sql    String the native SQL query with named parameters (e.g., {@code :paramName})
     * @param params Map of parameter names to their values
     * @return List of Object arrays containing the query result rows
     */
    List<Object[]> runParameterizedNativeQuery(String sql, Map<String, Object> params);

    /**
     * Saves or updates the entity depending on its persistence state.
     * New entities are persisted; existing entities are merged.
     *
     * @param entity the entity to save or update
     * @return the saved or updated entity
     */
    T saveEntity(T entity);

    /**
     * Returns the entity class managed by this DAO.
     *
     * @return Class the entity model class
     */
    Class<T> getModelClass();

    /**
     * Flushes the persistence context, synchronizing all pending changes with the database.
     */
    void flush();

}
