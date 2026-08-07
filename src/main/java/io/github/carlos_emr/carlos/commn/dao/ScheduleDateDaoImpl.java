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

import java.util.Date;
import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class ScheduleDateDaoImpl extends AbstractDaoImpl<ScheduleDate> implements ScheduleDateDao {

    public ScheduleDateDaoImpl() {
        super(ScheduleDate.class);
    }

    @Override
    public ScheduleDate findByProviderNoAndDate(String providerNo, Date date) {
        Query query = entityManager.createQuery("select s from ScheduleDate s where s.providerNo=?1 and s.date=?2 and s.status=?3");
        query.setParameter(1, providerNo);
        query.setParameter(2, date);
        query.setParameter(3, 'A');

        return (getSingleResultOrNull(query));
    }

    @Override
    public List<ScheduleDate> findByProviderPriorityAndDateRange(String providerNo, char priority, Date date, Date date2) {
        Query query = entityManager.createQuery("select s from ScheduleDate s where s.providerNo=?1 and s.priority=?2 and s.date>=?3 and s.date <=?4");
        query.setParameter(1, providerNo);
        query.setParameter(2, priority);
        query.setParameter(3, date);
        query.setParameter(4, date2);


        List<ScheduleDate> results = query.getResultList();
        return results;
    }

    @Override
    public List<ScheduleDate> findByProviderAndDateRange(String providerNo, Date date, Date date2) {
        Query query = entityManager.createQuery("select s from ScheduleDate s where s.providerNo=?1 and s.date>=?2 and s.date <=?3");
        query.setParameter(1, providerNo);
        query.setParameter(2, date);
        query.setParameter(3, date2);


        List<ScheduleDate> results = query.getResultList();
        return results;
    }

    @Override
    public List<ScheduleDate> search_scheduledate_c(String providerNo) {
        Query query = entityManager.createQuery("select s from ScheduleDate s where s.priority='c' and s.status = 'A' and s.providerNo=?1");
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<ScheduleDate> results = query.getResultList();
        return results;
    }

    @Override
    public List<ScheduleDate> search_numgrpscheduledate(String myGroupNo, Date sDate) {
        Query query = entityManager.createQuery("select s from MyGroup m, ScheduleDate s where m.id.myGroupNo = ?1 and s.date=?2 and m.id.providerNo = s.providerNo and s.available = '1' and s.status='A'");
        query.setParameter(1, myGroupNo);
        query.setParameter(2, sDate);


        @SuppressWarnings("unchecked")
        List<ScheduleDate> results = query.getResultList();
        return results;
    }

    @Override
    public List<Object[]> search_appttimecode(Date sDate, String providerNo) {
        Query query = entityManager.createQuery("SELECT st, sd FROM ScheduleTemplate st, ScheduleDate sd WHERE st.id.name=sd.hour and sd.date=?1 and sd.providerNo=?2 and sd.status='A' and (st.id.providerNo = sd.providerNo or st.id.providerNo='Public')");
        query.setParameter(1, sDate);
        query.setParameter(2, providerNo);


        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        return results;
    }

    @Override
    public List<ScheduleDate> search_scheduledate_teamp(Date date, Date date2, Character status, List<String> providerNos) {
        Query query = entityManager.createQuery("select s from ScheduleDate s where s.date>=?1 and s.date <=?2 and s.status=?3 and s.providerNo in (?4) order by s.date");
        query.setParameter(1, date);
        query.setParameter(2, date2);
        query.setParameter(3, status);
        query.setParameter(4, providerNos);

        @SuppressWarnings("unchecked")
        List<ScheduleDate> results = query.getResultList();
        return results;
    }

    @Override
    public List<ScheduleDate> search_scheduledate_datep(Date date, Date date2, Character status) {
        Query query = entityManager.createQuery("select s from ScheduleDate s where s.date>=?1 and s.date <=?2 and s.status=?3  order by s.date");
        query.setParameter(1, date);
        query.setParameter(2, date2);
        query.setParameter(3, status);

        @SuppressWarnings("unchecked")
        List<ScheduleDate> results = query.getResultList();
        return results;
    }

    @Override
    public List<ScheduleDate> findByProviderStartDateAndPriority(String providerNo, Date apptDate, String priority) {
        Character priorityChar = null;
        if (priority != null && priority.length() == 1) {
            priorityChar = priority.charAt(0);
        }
        Query query = createQuery("sd", "sd.date = ?1 AND sd.providerNo = ?2 AND sd.priority = ?3");
        query.setParameter(1, apptDate);
        query.setParameter(2, providerNo);
        query.setParameter(3, priorityChar);
        return query.getResultList();
    }
}
