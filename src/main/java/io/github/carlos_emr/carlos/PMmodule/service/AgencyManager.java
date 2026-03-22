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
package io.github.carlos_emr.carlos.PMmodule.service;

import io.github.carlos_emr.carlos.PMmodule.model.Agency;

/**
 * Service interface for managing agency information within the CARLOS EMR Program Management module.
 *
 * <p>Provides operations for retrieving and persisting the local agency configuration.
 * An agency represents the healthcare organization operating the EMR instance.</p>
 *
 * @see AgencyManagerImpl
 * @see Agency
 * @since 2005
 */
public interface AgencyManager {

    /**
     * Retrieves the local agency record for the current EMR installation.
     *
     * @return Agency the local agency configuration
     */
    Agency getLocalAgency();

    /**
     * Persists an agency record to the database.
     *
     * @param agency Agency the agency record to save
     */
    void saveAgency(Agency agency);
}
