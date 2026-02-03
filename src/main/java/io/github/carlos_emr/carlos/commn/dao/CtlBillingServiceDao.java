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

package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.CtlBillingService;

public interface CtlBillingServiceDao {
    public static final String DEFAULT_STATUS = "A";

    public List<CtlBillingService> findAll();

    /**
     * Gets distinct service type for services with the specific service status
     *
     * @param serviceStatus Status of the service to be retrieved
     * @return Returns list containing arrays of strings, where the first element represents the service type and the second element is the service type name.
     */
    public List<Object[]> getUniqueServiceTypes(String serviceStatus);

    /**
     * Gets distinct service type for services with {@link #DEFAULT_STATUS}
     *
     * @return Returns list containing arrays of strings, where the first element represents the service type code and the second element is the service type name.
     */
    public List<Object[]> getUniqueServiceTypes();

    public List<CtlBillingService> findByServiceTypeId(String serviceTypeId);

    public List<CtlBillingService> findByServiceGroupAndServiceTypeId(String serviceGroup, String serviceTypeId);

    public List<CtlBillingService> findByServiceType(String serviceTypeId);

    public List<CtlBillingService> getServiceTypeList();

    /**
     * Gets all service type names and service types from the {@link CtlBillingService} instances.
     */

    public List<Object[]> getAllServiceTypes();

    /**
     * Gets all {@link CtlBillingService} instance with the specified service group
     *
     * @param serviceGroup Service group to ge services for
     * @return Returns all persistent services found
     */
    public List<CtlBillingService> findByServiceGroup(String serviceGroup);

    /**
     * Gets all {@link CtlBillingService} instance with the specified service group
     *
     * @param serviceGroup Service group to ge services for
     * @return Returns all persistent services found
     */

    public List<CtlBillingService> findByServiceGroupAndServiceType(String serviceGroup, String serviceType);


    public List<Object[]> findUniqueServiceTypesByCode(String serviceCode);

    public List<Object[]> findServiceTypes();

    public List<Object[]> findServiceTypesByStatus(String status);

    public List<Object> findServiceCodesByType(String serviceType);

    void remove(Integer id);

    void persist(CtlBillingService ctlBillingService);
}
 
