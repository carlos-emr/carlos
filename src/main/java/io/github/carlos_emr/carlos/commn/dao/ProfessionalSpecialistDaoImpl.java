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
// The content of this file will be the same as the original ProfessionalSpecialistDao.java file
// but with the class name changed to ProfessionalSpecialistDaoImpl and implementing the ProfessionalSpecialistDao interface.

package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import jakarta.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link ProfessionalSpecialistDao} for professional contact data access.
 *
 * @since 2001
 */

public class ProfessionalSpecialistDaoImpl extends AbstractDaoImpl<ProfessionalSpecialist> implements ProfessionalSpecialistDao {

    public ProfessionalSpecialistDaoImpl() {
        super(ProfessionalSpecialist.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<ProfessionalSpecialist> findAll() {
        Query query = entityManager.createQuery("select x from ProfessionalSpecialist x where x.deleted = false order by x.lastName,x.firstName");

        @SuppressWarnings("unchecked")
        List<ProfessionalSpecialist> results = query.getResultList();

        return (results);
    }

    /**
    /**
     * Retrieves a list of ProfessionalSpecialist entities with a non-null eDataUrl, sorted by lastname and firstname.
     */
    public List<ProfessionalSpecialist> findByEDataUrlNotNull() {
        Query query = entityManager.createQuery("select x from ProfessionalSpecialist x where x.deleted = false and x.eDataUrl is not null order by x.lastName,x.firstName");

        @SuppressWarnings("unchecked")
        List<ProfessionalSpecialist> results = query.getResultList();

        return (results);
    }

    /** {@inheritDoc} */

    @Override
    /**
     * Retrieves a list of ProfessionalSpecialist entities by their full name.
     */
    public List<ProfessionalSpecialist> findByFullName(String lastName, String firstName) {
        Query query = entityManager.createQuery("select x from ProfessionalSpecialist x WHERE x.deleted = false and x.lastName like ?1 and x.firstName like ?2 order by x.lastName");
        query.setParameter(1, "%" + lastName + "%");
        query.setParameter(2, "%" + firstName + "%");

        @SuppressWarnings("unchecked")
        List<ProfessionalSpecialist> cList = query.getResultList();

        if (cList != null && cList.size() > 0) {
            return cList;
        }

        return null;
    }

    /** {@inheritDoc} */

    @Override
    public List<ProfessionalSpecialist> findByLastName(String lastName) {
        return findByFullName(lastName, "");
    }

    /** {@inheritDoc} */

    @Override
    /**
     * Retrieves a list of ProfessionalSpecialist by specialty type.
     */
    public List<ProfessionalSpecialist> findBySpecialty(String specialty) {
        Query query = entityManager.createQuery("select x from ProfessionalSpecialist x WHERE x.deleted = false and x.specialtyType like ?1 order by x.lastName");
        query.setParameter(1, "%" + specialty + "%");

        @SuppressWarnings("unchecked")
        List<ProfessionalSpecialist> cList = query.getResultList();

        if (cList != null && cList.size() > 0) {
            return cList;
        }

        return null;

    }

    /** {@inheritDoc} */

    @Override
    /**
     * Retrieves a list of ProfessionalSpecialist entities by referral number.
     */
    public List<ProfessionalSpecialist> findByReferralNo(String referralNo) {
        if (StringUtils.isBlank(referralNo)) {
            return null;
        }

        // referral numbers often have zeros prepended and are stored as varchar.
        Query query = entityManager.createQuery("select x from ProfessionalSpecialist x WHERE x.deleted = false and x.referralNo LIKE ?1 order by x.lastName");
        query.setParameter(1, referralNo);

        @SuppressWarnings("unchecked")
        List<ProfessionalSpecialist> cList = query.getResultList();

        if (cList != null && cList.size() > 0) {
            return cList;
        }

        return null;

    }

    /** {@inheritDoc} */

    @Override
    public ProfessionalSpecialist getByReferralNo(String referralNo) {
        List<ProfessionalSpecialist> cList = findByReferralNo(referralNo);

        if (cList != null && cList.size() > 0) {
            return cList.get(0);
        }

        return null;

    }

    /** {@inheritDoc} */

    @Override
    public boolean hasRemoteCapableProfessionalSpecialists() {
        return (findByEDataUrlNotNull().size() > 0);
    }
    /** {@inheritDoc} */

    @Override
    public List<ProfessionalSpecialist> search(String keyword) {
        String[] temp = keyword.split("\\,\\p{Space}*");

        Query query;
        if (temp.length > 1) {
            query = entityManager.createQuery("SELECT c from ProfessionalSpecialist c where c.deleted = false and c.lastName like :lastName and c.firstName like :firstName order by c.lastName,c.firstName");
            query.setParameter("lastName", temp[0] + "%");
            query.setParameter("firstName", temp[1] + "%");
        } else {
            query = entityManager.createQuery("SELECT c from ProfessionalSpecialist c where c.deleted = false and c.lastName like :lastName order by c.lastName,c.firstName");
            query.setParameter("lastName", temp[0] + "%");
        }

        @SuppressWarnings("unchecked")
        List<ProfessionalSpecialist> contacts = query.getResultList();
        return contacts;
    }

    /** {@inheritDoc} */

    @Override
    /**
     * Retrieves a list of ProfessionalSpecialist entities based on the provided full name, specialty, and address.
     *
     * This method constructs a dynamic SQL query to filter ProfessionalSpecialist records that match the given
     * last name and first name, while optionally including filters for specialty and address. It also considers
     * whether to include hidden records based on the showHidden parameter. The results are ordered by last name.
     *
     * @param lastName the last name of the professional specialist to search for
     * @param firstName the first name of the professional specialist to search for
     * @param specialty the specialty type to filter by (optional)
     * @param address the street address to filter by (optional)
     * @param showHidden flag indicating whether to include hidden records
     */
    public List<ProfessionalSpecialist> findByFullNameAndSpecialtyAndAddress(String lastName, String firstName, String specialty, String address, Boolean showHidden) {
        String sql = "select x from ProfessionalSpecialist x WHERE x.deleted = false AND (x.lastName like ?1 and x.firstName like ?2) ";
        int paramIndex = 3;
        if (!StringUtils.isEmpty(specialty)) {
            sql += " AND x.specialtyType LIKE ?" + paramIndex++ + " ";
        }

        if (!StringUtils.isEmpty(address)) {
            sql += " AND x.streetAddress LIKE ?" + paramIndex++ + " ";
        }

        if (showHidden == null || !showHidden) {
            sql += " AND x.hideFromView=false ";
        }
        sql += " order by x.lastName";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "%" + lastName + "%");
        query.setParameter(2, "%" + firstName + "%");

        paramIndex = 3;
        if (!StringUtils.isEmpty(specialty)) {
            query.setParameter(paramIndex++, "%" + specialty + "%");
        }
        if (!StringUtils.isEmpty(address)) {
            query.setParameter(paramIndex++, "%" + address + "%");
        }

        @SuppressWarnings("unchecked")
        List<ProfessionalSpecialist> cList = query.getResultList();

        return cList;
    }

    /** {@inheritDoc} */

    @Override
    /**
     * Retrieves a list of ProfessionalSpecialist entities by the specified service name.
     */
    public List<ProfessionalSpecialist> findByService(String serviceName) {
        Query query = entityManager.createQuery("select x from ProfessionalSpecialist x, ConsultationServices cs, ServiceSpecialists ss WHERE x.deleted = false and x.id = ss.id.specId and ss.id.serviceId = cs.serviceId and cs.serviceDesc = ?1");
        query.setParameter(1, serviceName);

        @SuppressWarnings("unchecked")
        List<ProfessionalSpecialist> cList = query.getResultList();


        return cList;
    }

    /** {@inheritDoc} */

    @Override
    /**
     * Retrieves a list of ProfessionalSpecialist entities by the given service ID.
     */
    public List<ProfessionalSpecialist> findByServiceId(Integer serviceId) {
        Query query = entityManager.createQuery("select x from ProfessionalSpecialist x, ServiceSpecialists ss WHERE x.deleted = false and x.id = ss.id.specId and ss.id.serviceId = ?1");
        query.setParameter(1, serviceId);

        @SuppressWarnings("unchecked")
        List<ProfessionalSpecialist> cList = query.getResultList();


        return cList;
    }
}
