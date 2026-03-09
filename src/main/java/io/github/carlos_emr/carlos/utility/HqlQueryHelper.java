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

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

/**
 * Replacement for {@code HibernateTemplate.find()} and
 * {@code HibernateTemplate.bulkUpdate()} that uses 1-based positional parameter
 * binding, compatible with both Hibernate 5.x and 6.x.
 *
 * <p>Spring's {@code HibernateTemplate.find(String, Object...)} calls
 * {@code setParameter(i, value)} where {@code i} is 0-based, which is
 * incompatible with Hibernate 6's strict requirement that positional parameter
 * placeholders ({@code ?1}, {@code ?2}) match 1-based {@code setParameter}
 * indices. This helper bridges that gap during the Hibernate 5 to 6 migration.</p>
 *
 * <p>Usage: Replace {@code getHibernateTemplate().find(hql, params)} with
 * {@code HqlQueryHelper.find(currentSession(), hql, params)}</p>
 *
 * <p>Hibernate exceptions are translated to Spring's {@code DataAccessException}
 * hierarchy to preserve the same contract as {@code HibernateTemplate}.
 * Callers must have an active Hibernate session, typically ensured by annotating
 * the DAO method or class with {@code @Transactional}.</p>
 *
 * @since 2026-02-09
 */
public final class HqlQueryHelper {

    private HqlQueryHelper() {
    }

    /**
     * Execute an HQL query with 1-based positional parameter binding.
     *
     * <p>Returns {@code List<?>} to match {@code HibernateTemplate.find()} signature,
     * allowing existing {@code (List<SomeType>)} casts to work unchanged.</p>
     *
     * @param session the Hibernate session from {@code currentSession()}
     * @param hql the HQL query string using 1-based positional parameters ({@code ?1}, {@code ?2}, etc.)
     * @param params the parameter values, bound to {@code ?1}, {@code ?2}, etc. in order
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any positional parameter value is null
     */
    public static List<?> find(Session session, String hql, Object... params) {
        Objects.requireNonNull(session, "Hibernate session must not be null — is there an active @Transactional?");
        try {
            Query<?> query = session.createQuery(hql);
            bindPositionalParams(query, params);
            return query.getResultList();
        } catch (HibernateException e) {
            throw translateHibernateException(e);
        }
    }

    /**
     * Execute an HQL query with 1-based positional parameter binding and a row limit.
     *
     * <p>Use this method when the query needs {@code setMaxResults()} (e.g., "top N" queries).
     * Pass {@code -1} for {@code maxResults} to return all rows.</p>
     *
     * @param session the Hibernate session from {@code currentSession()}
     * @param hql the HQL query string using 1-based positional parameters ({@code ?1}, {@code ?2}, etc.)
     * @param maxResults maximum number of rows to return, or {@code -1} for unlimited
     * @param params the parameter values, bound to {@code ?1}, {@code ?2}, etc. in order
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any positional parameter value is null
     */
    public static List<?> findWithLimit(Session session, String hql, int maxResults, Object... params) {
        Objects.requireNonNull(session, "Hibernate session must not be null — is there an active @Transactional?");
        try {
            Query<?> query = session.createQuery(hql);
            bindPositionalParams(query, params);
            if (maxResults >= 0) {
                query.setMaxResults(maxResults);
            }
            return query.getResultList();
        } catch (HibernateException e) {
            throw translateHibernateException(e);
        }
    }

    /**
     * Execute an HQL query with named parameter binding.
     *
     * <p>Use this method for queries with named parameters ({@code :paramName}).
     * Collection values are bound with {@code setParameterList} for IN clauses.</p>
     *
     * @param session the Hibernate session from {@code currentSession()}
     * @param hql the HQL query string using named parameters ({@code :param1}, {@code :param2}, etc.)
     * @param namedParams map of parameter names to values
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any named parameter value is null or a Collection parameter is empty
     */
    public static List<?> find(Session session, String hql, Map<String, Object> namedParams) {
        Objects.requireNonNull(session, "Hibernate session must not be null — is there an active @Transactional?");
        try {
            Query<?> query = session.createQuery(hql);
            bindNamedParams(query, namedParams);
            return query.getResultList();
        } catch (HibernateException e) {
            throw translateHibernateException(e);
        }
    }

    /**
     * Execute an HQL query with named parameter binding and a row limit.
     *
     * <p>Pass {@code -1} for {@code maxResults} to return all rows.</p>
     *
     * @param session the Hibernate session from {@code currentSession()}
     * @param hql the HQL query string using named parameters ({@code :param1}, {@code :param2}, etc.)
     * @param maxResults maximum number of rows to return, or {@code -1} for unlimited
     * @param namedParams map of parameter names to values
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any named parameter value is null or a Collection parameter is empty
     */
    public static List<?> findWithLimit(Session session, String hql, int maxResults, Map<String, Object> namedParams) {
        Objects.requireNonNull(session, "Hibernate session must not be null — is there an active @Transactional?");
        try {
            Query<?> query = session.createQuery(hql);
            bindNamedParams(query, namedParams);
            if (maxResults >= 0) {
                query.setMaxResults(maxResults);
            }
            return query.getResultList();
        } catch (HibernateException e) {
            throw translateHibernateException(e);
        }
    }

    private static void bindNamedParams(Query<?> query, Map<String, Object> namedParams) {
        if (namedParams == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : namedParams.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                throw new IllegalArgumentException(
                        "Null value for named parameter '" + entry.getKey()
                        + "'. Use IS NULL in HQL instead of binding a null value.");
            }
            if (value instanceof Collection) {
                Collection<?> coll = (Collection<?>) value;
                if (coll.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Empty collection for named parameter '" + entry.getKey()
                            + "'. IN clause requires at least one value.");
                }
                query.setParameterList(entry.getKey(), coll);
            } else {
                query.setParameter(entry.getKey(), value);
            }
        }
    }

    /**
     * Execute an HQL bulk update/delete with 1-based positional parameter binding.
     *
     * <p>Drop-in replacement for {@code HibernateTemplate.bulkUpdate(String, Object...)}.</p>
     *
     * @param session the Hibernate session from {@code currentSession()}
     * @param hql the HQL update/delete string using 1-based positional parameters ({@code ?1}, {@code ?2}, etc.)
     * @param params the parameter values, bound to {@code ?1}, {@code ?2}, etc. in order
     * @return the number of entity instances updated or deleted (&gt;= 0)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any positional parameter value is null
     */
    public static int bulkUpdate(Session session, String hql, Object... params) {
        Objects.requireNonNull(session, "Hibernate session must not be null — is there an active @Transactional?");
        try {
            Query<?> query = session.createQuery(hql);
            bindPositionalParams(query, params);
            return query.executeUpdate();
        } catch (HibernateException e) {
            throw translateHibernateException(e);
        }
    }

    /**
     * Execute an HQL query with named parameter binding, pagination, and a row limit.
     *
     * <p>Use this method when the query needs both {@code setFirstResult()} and
     * {@code setMaxResults()} (e.g., paginated search results).</p>
     *
     * @param session the Hibernate session from {@code currentSession()}
     * @param hql the HQL query string using named parameters ({@code :param1}, {@code :param2}, etc.)
     * @param firstResult the 0-based index of the first result to return, or {@code -1} to start from the beginning
     * @param maxResults maximum number of rows to return, or {@code -1} for unlimited
     * @param namedParams map of parameter names to values
     * @return a non-null list of query results (empty if no matches)
     * @throws org.springframework.dao.DataAccessException if the query execution fails
     * @throws IllegalArgumentException if any named parameter value is null or a Collection parameter is empty
     */
    public static List<?> findWithPagination(Session session, String hql, int firstResult, int maxResults, Map<String, Object> namedParams) {
        Objects.requireNonNull(session, "Hibernate session must not be null — is there an active @Transactional?");
        try {
            Query<?> query = session.createQuery(hql);
            bindNamedParams(query, namedParams);
            if (firstResult >= 0) {
                query.setFirstResult(firstResult);
            }
            if (maxResults >= 0) {
                query.setMaxResults(maxResults);
            }
            return query.getResultList();
        } catch (HibernateException e) {
            throw translateHibernateException(e);
        }
    }

    /**
     * Translates a {@link HibernateException} to Spring's {@link DataAccessException}
     * hierarchy, replacing the {@code org.springframework.orm.hibernate5.SessionFactoryUtils}
     * dependency that is removed in Spring's Hibernate 6 support.
     */
    private static DataAccessException translateHibernateException(HibernateException e) {
        if (e instanceof org.hibernate.exception.ConstraintViolationException) {
            return new DataIntegrityViolationException(e.getMessage(), e);
        }
        if (e instanceof org.hibernate.exception.DataException) {
            return new DataIntegrityViolationException(e.getMessage(), e);
        }
        if (e instanceof org.hibernate.exception.LockAcquisitionException) {
            return new CannotAcquireLockException(e.getMessage(), e);
        }
        if (e instanceof org.hibernate.exception.SQLGrammarException) {
            return new InvalidDataAccessResourceUsageException(e.getMessage(), e);
        }
        if (e instanceof org.hibernate.exception.JDBCConnectionException) {
            return new DataAccessResourceFailureException(e.getMessage(), e);
        }
        if (e instanceof org.hibernate.QueryException) {
            return new InvalidDataAccessResourceUsageException(e.getMessage(), e);
        }
        if (e instanceof org.hibernate.NonUniqueResultException) {
            return new IncorrectResultSizeDataAccessException(e.getMessage(), 1, e);
        }
        return new InvalidDataAccessResourceUsageException(e.getMessage(), e);
    }

    private static void bindPositionalParams(Query<?> query, Object[] params) {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] == null) {
                    throw new IllegalArgumentException(
                            "Null value for positional parameter ?" + (i + 1)
                            + ". Use IS NULL in HQL instead of binding a null value.");
                }
                query.setParameter(i + 1, params[i]);
            }
        }
    }
}
