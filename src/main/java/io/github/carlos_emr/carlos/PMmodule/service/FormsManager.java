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

package io.github.carlos_emr.carlos.PMmodule.service;

import java.util.List;

/**
 * Service interface for managing clinical forms within the CARLOS EMR Program Management module.
 *
 * <p>Provides generic operations for saving and retrieving clinical assessment forms
 * associated with clients. Forms are stored and retrieved by their entity class type.</p>
 *
 * @see io.github.carlos_emr.carlos.PMmodule.service.impl.FormsManagerImpl
 * @since 2005
 */
public interface FormsManager {

    /**
     * Persists a clinical form entity.
     *
     * @param o Object the form entity to save
     */
    public void saveForm(Object o);

    /**
     * Retrieves the most recent version of a form for a client.
     *
     * @param clientId String the client demographic number
     * @param clazz Class the form entity class type
     * @return Object the current form entity, or {@code null} if not found
     */
    public Object getCurrentForm(String clientId, Class clazz);

    /**
     * Retrieves form metadata for a client, typically used to list available form versions.
     *
     * @param clientId String the client demographic number
     * @param clazz Class the form entity class type
     * @return List list of form info records
     */
    public List getFormInfo(String clientId, Class clazz);
}
