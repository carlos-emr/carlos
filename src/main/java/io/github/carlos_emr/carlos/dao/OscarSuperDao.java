/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.dao;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.JdbcDaoSupport;

/**
 * Abstract base DAO that centralizes JDBC query execution for provider-related
 * database operations originally embedded in JSP files.
 *
 * <p>Subclasses (e.g., {@link ProviderDao}) supply a registry of named SQL queries
 * via {@link #getDbQueries()} and optional {@link RowMapper} instances via
 * {@link #getRowMappers()}. This class provides two execution methods:
 * {@link #executeSelectQuery(String, Object[])} for generic {@code Map}-based results,
 * and {@link #executeRowMappedSelectQuery(String, Object[])} for typed object mapping.</p>
 *
 * <p>This class should not be accessed directly from JSP or action classes.
 * Use {@code OscarSuperManager} methods instead to maintain proper layering.</p>
 *
 * @author Eugene Petruhin
 * @since 2012-01-18 (OSCAR McMaster heritage)
 */
public abstract class OscarSuperDao extends JdbcDaoSupport {

    protected static final Logger logger = MiscUtils.getLogger();

    /**
     * Returns the subclass-specific SQL query registry.
     *
     * <p>Each entry is a two-element {@code String} array where index 0 is
     * the query name (used as a lookup key) and index 1 is the parameterized
     * SQL statement.</p>
     *
     * @return String[][] array of {@code [queryName, sqlStatement]} pairs
     */
    protected abstract String[][] getDbQueries();

    /**
     * Returns the subclass-specific row mapper registry.
     *
     * <p>Keys correspond to query names from {@link #getDbQueries()}. When a
     * query is executed via {@link #executeRowMappedSelectQuery(String, Object[])},
     * the matching {@link RowMapper} transforms each result set row into a
     * domain object.</p>
     *
     * @return Map of query names to their corresponding {@link RowMapper} instances
     */
    protected abstract Map<String, RowMapper> getRowMappers();

    /**
     * Executes a parameterized select query identified by a key.<br>
     * Returned collection item is an automatically populated Map.
     *
     * @param queryName sql query key
     * @param params    sql query parameters
     * @return List of Map objects created for each result set row
     */
    public List<Map<String, Object>> executeSelectQuery(String queryName, Object[] params) {
        return getJdbcTemplate().queryForList(getSqlQueryByKey(queryName), params);
    }

    /**
     * Executes a parameterized select query identified by a key.<br>
     * Returned collection item is a value object populated by a row mapper identified by the same key.
     *
     * @param queryName sql query key
     * @param params    sql query parameters
     * @return List of value objects created for each result set row by a row mapper
     */
    @SuppressWarnings("unchecked")
    public List<Object> executeRowMappedSelectQuery(String queryName, Object[] params) {
        return getJdbcTemplate().query(getSqlQueryByKey(queryName), params, getRowMapperByKey(queryName));
    }

    /**
     * Retrieves a sql query associated with a query name or reports an error.
     *
     * @param key query name
     * @return sql query
     */
    private String getSqlQueryByKey(String key) {
        logger.debug("Calling query " + key);
        for (String[] query : getDbQueries()) {
            if (query[0].equals(key)) {
                return query[1];
            }
        }
        throw new IllegalArgumentException("dbQueries array contains no query with specified name: " + key);
    }

    /**
     * Retrieves a row mapper associated with a query name or reports an error.
     *
     * @param key query name
     * @return row mapper
     */
    private RowMapper getRowMapperByKey(String key) {
        RowMapper rowMapper = getRowMappers().get(key);
        if (rowMapper != null) {
            return rowMapper;
        }
        throw new IllegalArgumentException("rowMappers map contains no row mapper with specified name: " + key);
    }
}
