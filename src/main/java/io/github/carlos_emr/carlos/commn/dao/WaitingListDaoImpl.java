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

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link WaitingListDao} for waiting list data access.
 *
 * @since 2001
 */

public class WaitingListDaoImpl extends AbstractDaoImpl<WaitingList> implements WaitingListDao {

    /** Constructs this DAO for the {@link WaitingList} entity class. */

    public WaitingListDaoImpl() {
        super(WaitingList.class);
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findByDemographic(Integer demographicNo) {
        Query query = entityManager.createQuery("SELECT wn, w FROM WaitingListName wn, WaitingList w WHERE wn.id = w.listId AND w.demographicNo = ?1 AND w.isHistory != 'Y'");
        query.setParameter(1, demographicNo);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findWaitingListsAndDemographics(Integer listId) {
        Query query = entityManager.createQuery("SELECT w, d FROM WaitingList w, Demographic d WHERE w.demographicNo = d.DemographicNo AND  w.listId = ?1 AND w.isHistory = 'N' ORDER BY w.position");
        query.setParameter(1, listId);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<WaitingList> findByWaitingListId(Integer listId) {
        Query query = entityManager.createQuery("FROM WaitingList w WHERE w.listId = ?1 AND w.isHistory = 'N' ORDER BY w.onListSince");
        query.setParameter(1, listId);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Appointment> findAppointmentFor(WaitingList waitingList) {
        Query query = entityManager.createQuery("From Appointment a where a.appointmentDate >= ?1 AND a.demographicNo = ?2");
        query.setParameter(1, waitingList.getOnListSince());
        query.setParameter(2, waitingList.getDemographicNo());
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<WaitingList> findByWaitingListIdAndDemographicId(Integer waitingListId, Integer demographicId) {
        Query query = createQuery("wl", "wl.demographicNo = ?1 AND wl.listId = ?2");
        query.setParameter(1, demographicId);
        query.setParameter(2, waitingListId);
        return query.getResultList();
    }

    public Integer getMaxPosition(Integer listId) {
        Query query = entityManager.createQuery("select max(w.position) from WaitingList w where w.listId = ?1 AND w.isHistory = 'N'");
        query.setParameter(1, listId);
        Long result = (Long) query.getSingleResult();
        if (result == null) {
            return 0;
        }
        return result.intValue();
    }

    @SuppressWarnings("unchecked")
    public List<WaitingList> search_wlstatus(Integer demographicId) {
        Query query = entityManager.createQuery("select wl from WaitingList wl where wl.demographicNo = ?1 AND wl.isHistory = 'N' order BY wl.onListSince desc");
        query.setParameter(1, demographicId);
        return query.getResultList();
    }
}
