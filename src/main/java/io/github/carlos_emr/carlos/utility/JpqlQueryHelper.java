/**
 * Copyright (c) 2026. CARLOS EMR. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package io.github.carlos_emr.carlos.utility;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;

/**
 * JPA counterpart to {@link HqlQueryHelper} that uses {@link EntityManager}
 * instead of Hibernate {@code Session}.
 *
 * <p>Provides the same six method signatures as {@code HqlQueryHelper},
 * enabling a mechanical migration: replace
 * {@code HqlQueryHelper.find(currentSession(), hql, params)} with
 * {@code JpqlQueryHelper.find(entityManager(), hql, params)}.</p>
 *
 * <p>All JPQL strings from the Hibernate-based DAOs work unchanged because
 * Hibernate is the JPA provider. JPA exceptions are translated to Spring's
 * {@code DataAccessException} hierarchy to preserve the same contract.</p>
 *
 * @since 2026-04-11
 * @see HqlQueryHelper
 * @see io.github.carlos_emr.carlos.dao.AbstractJpaDao
 */
public final class JpqlQueryHelper {

    private JpqlQueryHelper() {
    }

    /**
     * Execute a JPQL query with 1-based positional parameter binding.
     *
     * <p>Returns {@code List<?>} to match {@code HqlQueryHelper.find()} signature,
     * allowing existing {@code (List<SomeType>)} casts to work unchanged.</p>
     *
     * @param em the EntityManager from {@code entityManager()}
     * @param jpql the JPQL query string using 1-based positional parameters ({@code ?1}, {@code ?2}, etc.)
     * @param params the parameter values, bound to {@code ?1}, {@code ?2}, etc. in order
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any positional parameter value is null
     */
    public static List<?> find(EntityManager em, String jpql, Object... params) {
        Objects.requireNonNull(em, "EntityManager must not be null — is there an active @Transactional?");
        try {
            // nosemgrep: hibernate-sqli — this IS the parameterization utility; params bound via bindPositionalParams below
            Query query = em.createQuery(jpql);
            bindPositionalParams(query, params);
            return query.getResultList();
        } catch (PersistenceException e) {
            throw translatePersistenceException(e);
        }
    }

    /**
     * Execute a JPQL query with 1-based positional parameter binding and a row limit.
     *
     * <p>Use this method when the query needs {@code setMaxResults()} (e.g., "top N" queries).
     * Pass {@code -1} for {@code maxResults} to return all rows.</p>
     *
     * @param em the EntityManager from {@code entityManager()}
     * @param jpql the JPQL query string using 1-based positional parameters ({@code ?1}, {@code ?2}, etc.)
     * @param maxResults maximum number of rows to return, or {@code -1} for unlimited
     * @param params the parameter values, bound to {@code ?1}, {@code ?2}, etc. in order
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any positional parameter value is null
     */
    public static List<?> findWithLimit(EntityManager em, String jpql, int maxResults, Object... params) {
        Objects.requireNonNull(em, "EntityManager must not be null — is there an active @Transactional?");
        try {
            // nosemgrep: hibernate-sqli — this IS the parameterization utility; params bound below
            Query query = em.createQuery(jpql);
            bindPositionalParams(query, params);
            if (maxResults >= 0) {
                query.setMaxResults(maxResults);
            }
            return query.getResultList();
        } catch (PersistenceException e) {
            throw translatePersistenceException(e);
        }
    }

    /**
     * Execute a JPQL query with named parameter binding.
     *
     * <p>Use this method for queries with named parameters ({@code :paramName}).
     * Collection values are bound with {@code setParameter} for IN clauses
     * (Hibernate 7 JPA mode supports Collection binding via {@code setParameter}).</p>
     *
     * @param em the EntityManager from {@code entityManager()}
     * @param jpql the JPQL query string using named parameters ({@code :param1}, {@code :param2}, etc.)
     * @param namedParams map of parameter names to values
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any named parameter value is null or a Collection parameter is empty
     */
    public static List<?> find(EntityManager em, String jpql, Map<String, Object> namedParams) {
        Objects.requireNonNull(em, "EntityManager must not be null — is there an active @Transactional?");
        try {
            // nosemgrep: hibernate-sqli — this IS the parameterization utility; named params bound below
            Query query = em.createQuery(jpql);
            bindNamedParams(query, namedParams);
            return query.getResultList();
        } catch (PersistenceException e) {
            throw translatePersistenceException(e);
        }
    }

    /**
     * Execute a JPQL query with named parameter binding and a row limit.
     *
     * <p>Pass {@code -1} for {@code maxResults} to return all rows.</p>
     *
     * @param em the EntityManager from {@code entityManager()}
     * @param jpql the JPQL query string using named parameters ({@code :param1}, {@code :param2}, etc.)
     * @param maxResults maximum number of rows to return, or {@code -1} for unlimited
     * @param namedParams map of parameter names to values
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any named parameter value is null or a Collection parameter is empty
     */
    public static List<?> findWithLimit(EntityManager em, String jpql, int maxResults, Map<String, Object> namedParams) {
        Objects.requireNonNull(em, "EntityManager must not be null — is there an active @Transactional?");
        try {
            // nosemgrep: hibernate-sqli — this IS the parameterization utility; named params bound below
            Query query = em.createQuery(jpql);
            bindNamedParams(query, namedParams);
            if (maxResults >= 0) {
                query.setMaxResults(maxResults);
            }
            return query.getResultList();
        } catch (PersistenceException e) {
            throw translatePersistenceException(e);
        }
    }

    /**
     * Execute a JPQL bulk update/delete with 1-based positional parameter binding.
     *
     * <p>Drop-in replacement for {@code HqlQueryHelper.bulkUpdate()}.</p>
     *
     * @param em the EntityManager from {@code entityManager()}
     * @param jpql the JPQL update/delete string using 1-based positional parameters ({@code ?1}, {@code ?2}, etc.)
     * @param params the parameter values, bound to {@code ?1}, {@code ?2}, etc. in order
     * @return the number of entity instances updated or deleted (&gt;= 0)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any positional parameter value is null
     */
    public static int bulkUpdate(EntityManager em, String jpql, Object... params) {
        Objects.requireNonNull(em, "EntityManager must not be null — is there an active @Transactional?");
        try {
            // nosemgrep: hibernate-sqli — this IS the parameterization utility; params bound below
            Query query = em.createQuery(jpql);
            bindPositionalParams(query, params);
            return query.executeUpdate();
        } catch (PersistenceException e) {
            throw translatePersistenceException(e);
        }
    }

    /**
     * Execute a JPQL query with named parameter binding, pagination, and a row limit.
     *
     * <p>Use this method when the query needs both {@code setFirstResult()} and
     * {@code setMaxResults()} (e.g., paginated search results).</p>
     *
     * @param em the EntityManager from {@code entityManager()}
     * @param jpql the JPQL query string using named parameters ({@code :param1}, {@code :param2}, etc.)
     * @param firstResult the 0-based index of the first result to return, or {@code -1} to start from the beginning
     * @param maxResults maximum number of rows to return, or {@code -1} for unlimited
     * @param namedParams map of parameter names to values
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any named parameter value is null or a Collection parameter is empty
     */
    public static List<?> findWithPagination(EntityManager em, String jpql, int firstResult, int maxResults, Map<String, Object> namedParams) {
        Objects.requireNonNull(em, "EntityManager must not be null — is there an active @Transactional?");
        try {
            // nosemgrep: hibernate-sqli — this IS the parameterization utility; named params bound below
            Query query = em.createQuery(jpql);
            bindNamedParams(query, namedParams);
            if (firstResult >= 0) {
                query.setFirstResult(firstResult);
            }
            if (maxResults >= 0) {
                query.setMaxResults(maxResults);
            }
            return query.getResultList();
        } catch (PersistenceException e) {
            throw translatePersistenceException(e);
        }
    }

    private static void bindNamedParams(Query query, Map<String, Object> namedParams) {
        if (namedParams == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : namedParams.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                throw new IllegalArgumentException(
                        "Null value for named parameter '" + entry.getKey()
                        + "'. Use IS NULL in JPQL instead of binding a null value.");
            }
            if (value instanceof Collection) {
                Collection<?> coll = (Collection<?>) value;
                if (coll.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Empty collection for named parameter '" + entry.getKey()
                            + "'. IN clause requires at least one value.");
                }
            }
            query.setParameter(entry.getKey(), value);
        }
    }

    /**
     * Translates a {@link PersistenceException} to Spring's {@link DataAccessException}
     * hierarchy, preserving the same contract as {@link HqlQueryHelper}.
     *
     * <p>Checks JPA-standard subtypes first, then unwraps Hibernate-specific
     * exceptions from the immediate cause ({@code e.getCause()} — one level only)
     * for finer-grained translation.</p>
     */
    private static DataAccessException translatePersistenceException(PersistenceException e) {
        // JPA-standard exception subtypes
        if (e instanceof jakarta.persistence.EntityExistsException) {
            return new DataIntegrityViolationException(e.getMessage(), e);
        }
        if (e instanceof jakarta.persistence.NonUniqueResultException) {
            return new IncorrectResultSizeDataAccessException(e.getMessage(), 1, e);
        }
        if (e instanceof jakarta.persistence.OptimisticLockException) {
            return new OptimisticLockingFailureException(e.getMessage(), e);
        }
        if (e instanceof jakarta.persistence.PessimisticLockException) {
            return new CannotAcquireLockException(e.getMessage(), e);
        }
        if (e instanceof jakarta.persistence.QueryTimeoutException) {
            return new QueryTimeoutException(e.getMessage(), e);
        }
        if (e instanceof jakarta.persistence.TransactionRequiredException) {
            return new InvalidDataAccessApiUsageException(e.getMessage(), e);
        }

        // Unwrap Hibernate-specific exceptions from the immediate cause (e.getCause() — one level only)
        Throwable cause = e.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
            return new DataIntegrityViolationException(e.getMessage(), e);
        }
        if (cause instanceof org.hibernate.exception.DataException) {
            return new DataIntegrityViolationException(e.getMessage(), e);
        }
        if (cause instanceof org.hibernate.exception.LockAcquisitionException) {
            return new CannotAcquireLockException(e.getMessage(), e);
        }
        if (cause instanceof org.hibernate.exception.SQLGrammarException) {
            return new InvalidDataAccessResourceUsageException(e.getMessage(), e);
        }
        if (cause instanceof org.hibernate.exception.JDBCConnectionException) {
            return new DataAccessResourceFailureException(e.getMessage(), e);
        }
        if (cause instanceof org.hibernate.QueryException) {
            return new InvalidDataAccessResourceUsageException(e.getMessage(), e);
        }

        return new InvalidDataAccessResourceUsageException(e.getMessage(), e);
    }

    private static void bindPositionalParams(Query query, Object[] params) {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] == null) {
                    throw new IllegalArgumentException(
                            "Null value for positional parameter ?" + (i + 1)
                            + ". Use IS NULL in JPQL instead of binding a null value.");
                }
                query.setParameter(i + 1, params[i]);
            }
        }
    }
}
