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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentStatusDaoImpl extends AbstractDaoImpl<AppointmentStatus> implements AppointmentStatusDao {

    public AppointmentStatusDaoImpl() {
        super(AppointmentStatus.class);
    }

    @Cacheable(value = "appointmentStatuses", key = "'all'")
    @SuppressWarnings("unchecked")
    @Override
    public List<AppointmentStatus> findAll() {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName());
        return Collections.unmodifiableList(new ArrayList<>(query.getResultList()));
    }

    @Cacheable(value = "appointmentStatuses", key = "'active'")
    @Override
    public List<AppointmentStatus> findActive() {
        Query q = entityManager.createQuery("select a from AppointmentStatus a where a.active=?1 order by a.id");
        q.setParameter(1, 1);

        @SuppressWarnings("unchecked")
        List<AppointmentStatus> results = q.getResultList();

        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    @Cacheable(value = "appointmentStatuses", key = "'status:' + #status",
               condition = "#status != null && !#status.isEmpty()")
    @Override
    public AppointmentStatus findByStatus(String status) {
        if (status == null || status.length() == 0) {
            return null;
        }

        Query q = entityManager.createQuery("select a from AppointmentStatus a where a.status like ?1");
        q.setParameter(1, status.substring(0, 1) + "%");

        @SuppressWarnings("unchecked")
        List<AppointmentStatus> results = q.getResultList();

        for (AppointmentStatus r : results) {
            if (r.getStatus() != null && r.getStatus().length() > 0 && r.getStatus().charAt(0) == status.charAt(0)) {
                return r;
            }
        }

        return null;
    }

    @CacheEvict(value = "appointmentStatuses", allEntries = true)
    @Override
    public void modifyStatus(int ID, String strDesc, String strColor) {
        AppointmentStatus appts = find(ID);
        if (appts != null) {
            appts.setDescription(strDesc);
            appts.setColor(strColor);
        }
    }

    @CacheEvict(value = "appointmentStatuses", allEntries = true)
    public void changeStatus(int ID, int iActive) {
        AppointmentStatus appts = find(ID);
        if (appts != null) {
            appts.setActive(iActive);
        }
    }

    @CacheEvict(value = "appointmentStatuses", allEntries = true)
    @Override
    public void persist(AbstractModel<?> o) { super.persist(o); }

    @CacheEvict(value = "appointmentStatuses", allEntries = true)
    @Override
    public void merge(AbstractModel<?> o) { super.merge(o); }

    @CacheEvict(value = "appointmentStatuses", allEntries = true)
    @Override
    public void remove(AbstractModel<?> o) { super.remove(o); }

    @CacheEvict(value = "appointmentStatuses", allEntries = true)
    @Override
    public boolean remove(Object id) { return super.remove(id); }

    @CacheEvict(value = "appointmentStatuses", allEntries = true)
    @Override
    public AppointmentStatus saveEntity(AppointmentStatus entity) { return super.saveEntity(entity); }

    /**
     * I don't know about this one...but i'm just converting it to a JPA entity for
     * now.
     *
     * @param allStatus
     * @return int
     */
    @Override
    public int checkStatusUsuage(List<AppointmentStatus> allStatus) {
        int iUsuage = 0;
        AppointmentStatus apptStatus = null;
        String sql = null;
        for (int i = 0; i < allStatus.size(); i++) {
            apptStatus = allStatus.get(i);
            if (apptStatus.getActive() == 1)
                continue;
            sql = "select count(*) as total from appointment a where a.status like ?1 ";
            // sql = sql + "collate latin1_general_cs";

            Query q = entityManager.createNativeQuery(sql);
            q.setParameter(1, apptStatus.getStatus() + "%");
            Object result = q.getSingleResult();

            iUsuage = ((Number) result).intValue();
            if (iUsuage > 0) {
                iUsuage = i;
                break;
            }
        }
        return iUsuage;
    }
}
