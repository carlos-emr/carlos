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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.ticklers.service;

import java.util.List;

import io.github.carlos_emr.carlos.commn.PaginationQuery;
import io.github.carlos_emr.carlos.commn.dao.AbstractDao;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.ticklers.web.TicklerQuery;

/**
 * Data access interface for tickler (clinical reminder) operations with pagination and query support.
 *
 * <p>Extends the generic {@link AbstractDao} for Tickler entities and adds methods for
 * counting and retrieving ticklers based on flexible query criteria. Ticklers are clinical
 * reminders used by healthcare providers to track follow-up tasks for patients.</p>
 *
 * @since 2001-01-01
 * @see io.github.carlos_emr.carlos.commn.model.Tickler
 * @see io.github.carlos_emr.carlos.ticklers.web.TicklerQuery
 */
public interface TicklersDao extends AbstractDao<Tickler> {

    /**
     * Returns the count of ticklers matching the specified pagination query criteria.
     *
     * @param paginationQuery PaginationQuery the query containing filter and pagination parameters
     * @return int the number of ticklers matching the query
     */
    public int getTicklersCount(PaginationQuery paginationQuery);

    /**
     * Retrieves a list of ticklers matching the specified query criteria.
     *
     * @param ticklerQuery TicklerQuery the query containing filter, sort, and pagination parameters
     * @return List of Tickler entities matching the query
     */
    public List<Tickler> getTicklers(TicklerQuery ticklerQuery);
}
