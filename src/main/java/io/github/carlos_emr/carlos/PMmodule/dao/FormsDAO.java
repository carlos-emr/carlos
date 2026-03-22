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

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.List;

/**
 * Data access interface for managing healthcare forms within the
 * Program Management module.
 *
 * <p>Provides generic form persistence and retrieval using dynamic entity
 * class resolution, supporting various form types (e.g., intake forms,
 * assessment forms).</p>
 *
 * @since 2005-01-18
 * @see FormsDAOImpl
 */
public interface FormsDAO {

    /**
     * Saves a form entity to the database.
     *
     * @param o Object the form entity to persist
     */
    public void saveForm(Object o);

    /**
     * Retrieves the current (most recent) form for a client of a specific form type.
     *
     * @param clientId String the demographic ID of the client
     * @param clazz Class the entity class representing the form type
     * @return Object the form entity, or {@code null} if not found
     * @throws IllegalArgumentException if clientId or clazz is {@code null}
     */
    public Object getCurrentForm(String clientId, Class clazz);

    /**
     * Retrieves form metadata (ID, provider, edit date) for all forms of a client.
     *
     * @param clientId String the demographic ID of the client
     * @param clazz Class the entity class representing the form type
     * @return List list of {@link io.github.carlos_emr.carlos.PMmodule.model.FormInfo} objects
     * @throws IllegalArgumentException if clientId or clazz is {@code null}
     */
    public List getFormInfo(String clientId, Class clazz);
}
 