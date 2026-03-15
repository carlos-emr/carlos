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

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class CtlBillingServiceDaoImpl extends AbstractDaoImpl<CtlBillingService> implements CtlBillingServiceDao {


    public CtlBillingServiceDaoImpl() {
        super(CtlBillingService.class);
    }

    public List<CtlBillingService> findAll() {
        Query query = entityManager.createQuery("SELECT x FROM " + modelClass.getSimpleName() + " x");
        List<CtlBillingService> results = query.getResultList();
        return results;
    }

    /**
     * Gets distinct service type for services with the specific service status
     *
     * @param serviceStatus Status of the service to be retrieved
     * @return Returns list containing arrays of strings, where the first element represents the service type and the second element is the service type name.
     */
    public List<Object[]> getUniqueServiceTypes(String serviceStatus) {
        Query query = entityManager.createQuery("SELECT DISTINCT b.serviceType, b.serviceTypeName FROM CtlBillingService b WHERE b.status = ?1");
        query.setParameter(1, serviceStatus);


        List<Object[]> results = query.getResultList();
        return results;
    }

    /**
     * Gets distinct service type for services with {@link #DEFAULT_STATUS}
     *
     * @return Returns list containing arrays of strings, where the first element represents the service type code and the second element is the service type name.
     */
    public List<Object[]> getUniqueServiceTypes() {
        return getUniqueServiceTypes(DEFAULT_STATUS);
    }

    public List<CtlBillingService> findByServiceTypeId(String serviceTypeId) {
        Query query = entityManager.createQuery("select b from CtlBillingService b where b.status='A' and b.serviceType like ?1");
        query.setParameter(1, serviceTypeId);


        List<CtlBillingService> results = query.getResultList();

        return results;
    }

    public List<CtlBillingService> findByServiceGroupAndServiceTypeId(String serviceGroup, String serviceTypeId) {
        Query query = entityManager.createQuery("select b from CtlBillingService b where b.status='A' and b.serviceGroup = ?1 and b.serviceType like ?2");
        query.setParameter(1, serviceGroup);
        query.setParameter(2, serviceTypeId);


        List<CtlBillingService> results = query.getResultList();

        return results;
    }

    public List<CtlBillingService> findByServiceType(String serviceTypeId) {
        Query query = entityManager.createQuery("select b from CtlBillingService b where b.serviceType like ?1");
        query.setParameter(1, serviceTypeId);


        List<CtlBillingService> results = query.getResultList();

        return results;
    }

    public List<CtlBillingService> getServiceTypeList() {
        Query query = entityManager.createQuery("SELECT bs FROM " + modelClass.getSimpleName() + " bs GROUP BY bs.serviceTypeName HAVING COUNT(bs.serviceTypeName) > -1");
        List<CtlBillingService> results = query.getResultList();
        return results;
    }

    /**
     * Gets all service type names and service types from the {@link CtlBillingService} instances.
     */

    public List<Object[]> getAllServiceTypes() {
        Query query = entityManager.createQuery("SELECT bs.serviceTypeName, bs.serviceType FROM " + modelClass.getSimpleName() + " bs GROUP BY bs.serviceType, bs.serviceTypeName");
        return query.getResultList();
    }

    /**
     * Gets all {@link CtlBillingService} instance with the specified service group
     *
     * @param serviceGroup Service group to ge services for
     * @return Returns all persistent services found
     */
    public List<CtlBillingService> findByServiceGroup(String serviceGroup) {
        return findByServiceGroupAndServiceType(serviceGroup, null);
    }

    /**
     * Gets all {@link CtlBillingService} instance with the specified service group
     *
     * @param serviceGroup Service group to ge services for
     * @return Returns all persistent services found
     */

    public List<CtlBillingService> findByServiceGroupAndServiceType(String serviceGroup, String serviceType) {
        StringBuilder buf = new StringBuilder("FROM " + modelClass.getSimpleName() + " bs WHERE bs.serviceGroup = ?1");
        boolean isServiceTypeSpecified = serviceType != null;
        if (isServiceTypeSpecified)
            buf.append(" AND bs.serviceType = ?2");
        Query query = entityManager.createQuery(buf.toString());
        query.setParameter(1, serviceGroup);
        if (isServiceTypeSpecified)
            query.setParameter(2, serviceType);
        return query.getResultList();
    }


    public List<Object[]> findUniqueServiceTypesByCode(String serviceCode) {
        String sql = "SELECT DISTINCT s.serviceTypeName, s.serviceType from CtlBillingService s " +
                "WHERE s.status='A' " +
                "AND s.serviceCode = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceCode);
        return query.getResultList();
    }

    public List<Object[]> findServiceTypes() {
        String sql = "SELECT DISTINCT s.serviceType, s.serviceTypeName FROM CtlBillingService s " +
                "WHERE s.status != 'D' " +
                "AND s.serviceType IS NOT NULL " +
                "AND LENGTH(TRIM(s.serviceType)) > 0";
        Query query = entityManager.createQuery(sql);
        return query.getResultList();
    }

    public List<Object[]> findServiceTypesByStatus(String status) {
        String sql = "SELECT DISTINCT s.serviceTypeName, s.serviceType " +
                "FROM CtlBillingService s " +
                "WHERE s.status = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, status);
        return query.getResultList();
    }

    public List<Object> findServiceCodesByType(String serviceType) {
        String sql = "SELECT DISTINCT bs.serviceCode FROM CtlBillingService bs " +
                "WHERE bs.status <> 'D' " +
                "AND bs.serviceType = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceType);
        return query.getResultList();
    }

    @Override
    public void remove(Integer id) {
        CtlBillingService entity = entityManager.find(CtlBillingService.class, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    public void persist(CtlBillingService ctlBillingService) {
        if (ctlBillingService.getId() == null) {
            entityManager.persist(ctlBillingService);
        } else {
            entityManager.merge(ctlBillingService);
        }
    }
}
