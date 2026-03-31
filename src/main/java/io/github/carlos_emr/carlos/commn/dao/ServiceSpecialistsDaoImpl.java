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

import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialists;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceSpecialistsDaoImpl extends AbstractDaoImpl<ServiceSpecialists> implements ServiceSpecialistsDao {

    public ServiceSpecialistsDaoImpl() {
        super(ServiceSpecialists.class);
    }

    @Override
    public List<ServiceSpecialists> findByServiceId(int serviceId) {
        Query q = entityManager.createQuery("select x from ServiceSpecialists x where x.id.serviceId = ?1");
        q.setParameter(1, serviceId);

        @SuppressWarnings("unchecked")
        List<ServiceSpecialists> results = q.getResultList();

        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Object[]> findSpecialists(Integer servId) {
        String sql = "SELECT ser, pro FROM ServiceSpecialists ser, " + ProfessionalSpecialist.class.getSimpleName() + " pro WHERE pro.id = ser.id.specId and ser.id.serviceId = ?1 ORDER BY pro.lastName";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, servId);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    /**
     * Retrieves a list of specialists along with their associated services.
     */
    public List<Object[]> findAllSpecialistsWithService() {
        Query query = entityManager.createQuery("SELECT ser, pro, cs FROM ServiceSpecialists ser, ProfessionalSpecialist pro, ConsultationServices cs WHERE pro.id = ser.id.specId AND cs.serviceId = ser.id.serviceId ORDER BY pro.lastName, cs.serviceDesc");
        return query.getResultList();
    }
}
