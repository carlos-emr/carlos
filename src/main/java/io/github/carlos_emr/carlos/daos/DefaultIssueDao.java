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

package io.github.carlos_emr.carlos.daos;

import java.util.List;

import io.github.carlos_emr.carlos.model.DefaultIssue;
import io.github.carlos_emr.carlos.commn.dao.AbstractDao;

/**
 * Data access interface for managing default clinical issues used in case management.
 *
 * <p>Provides CRUD operations for {@link DefaultIssue} entities, which represent
 * predefined clinical issue types that can be assigned to patient encounters.</p>
 *
 * @since 2005-01-01
 * @see DefaultIssue
 */
public interface DefaultIssueDao extends AbstractDao<DefaultIssue> {

    /**
     * Finds a default issue by its unique identifier.
     *
     * @param id Integer the default issue ID
     * @return DefaultIssue the matching issue, or null if not found
     */
    public DefaultIssue findDefaultIssue(Integer id);

    /**
     * Returns the most recently created default issue.
     *
     * @return DefaultIssue the latest default issue
     */
    public DefaultIssue getLastestDefaultIssue();

    /**
     * Retrieves all default issues.
     *
     * @return List of all DefaultIssue entities
     */
    public List<DefaultIssue> findAll();

    /**
     * Persists a new or updated default issue.
     *
     * @param issue DefaultIssue the issue to save
     */
    public void saveDefaultIssue(DefaultIssue issue);

    /**
     * Returns all default issue IDs as an array of strings.
     *
     * @return String[] array of all default issue ID values
     */
    public String[] findAllDefaultIssueIds();
}
