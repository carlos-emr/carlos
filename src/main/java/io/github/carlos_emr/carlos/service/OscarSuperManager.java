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
package io.github.carlos_emr.carlos.service;

import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.dao.OscarSuperDao;

/**
 * Super manager interface providing generic DAO-based data access through named queries.
 *
 * <p>Provides a registry of named {@link OscarSuperDao} instances that can be accessed
 * by name to execute select queries and row-mapped queries. Used as a flexible data
 * access abstraction in the CARLOS EMR system.</p>
 *
 * @since 2005-01-01
 * @see OscarSuperDao
 * @see OscarSuperManagerImpl
 */
public interface OscarSuperManager {

    /**
     * Sets the provider DAO for data access operations.
     *
     * @param providerDao OscarSuperDao the provider data access object
     */
    void setProviderSuperDao(OscarSuperDao providerDao);

    /**
     * Initializes the manager by registering all DAOs and validating their injection.
     *
     * @throws IllegalStateException if any required DAO has not been injected
     */
    void init();

    /**
     * Executes a named query on the specified DAO and returns results as a list of maps.
     *
     * @param daoName String the registered name of the DAO to query
     * @param queryName String the name of the query to execute
     * @param params Object[] the query parameters
     * @return List of Map objects where each map represents a result row with column name keys
     * @throws IllegalArgumentException if no DAO is registered with the given name
     */
    List<Map<String, Object>> find(String daoName, String queryName, Object[] params);

    /**
     * Executes a named query on the specified DAO and returns results as mapped objects.
     *
     * @param daoName String the registered name of the DAO to query
     * @param queryName String the name of the query to execute
     * @param params Object[] the query parameters
     * @return List of mapped result objects
     * @throws IllegalArgumentException if no DAO is registered with the given name
     */
    List<Object> populate(String daoName, String queryName, Object[] params);
}
