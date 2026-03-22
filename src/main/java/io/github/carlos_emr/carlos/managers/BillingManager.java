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
package io.github.carlos_emr.carlos.managers;

import java.util.List;

import io.github.carlos_emr.carlos.managers.model.ServiceType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Service interface for managing billing service types in the CARLOS EMR system.
 *
 * <p>Provides access to unique billing service type records used in the
 * Canadian provincial healthcare billing subsystem (BC Teleplan, Ontario OHIP).</p>
 *
 * @see BillingManagerImpl
 * @see io.github.carlos_emr.carlos.managers.model.ServiceType
 * @since 2026-03-17
 */
public interface BillingManager {

    /**
     * Retrieves all unique billing service types.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @return List of ServiceType representing all unique service types
     */
    public List<ServiceType> getUniqueServiceTypes(LoggedInInfo loggedInInfo);

    /**
     * Retrieves unique billing service types filtered by type category.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param type String the service type category to filter by
     * @return List of ServiceType matching the specified type
     */
    public List<ServiceType> getUniqueServiceTypes(LoggedInInfo loggedInInfo, String type);
}


