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

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplate;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class ScheduleTemplateDaoImpl extends AbstractDaoImpl<ScheduleTemplate> implements ScheduleTemplateDao {

    public ScheduleTemplateDaoImpl() {
        super(ScheduleTemplate.class);
    }

    @Override
    public List<ScheduleTemplate> findBySummary(String summary) {
        Query query = entityManager.createQuery("SELECT e FROM ScheduleTemplate e WHERE e.summary=?1");
        query.setParameter(1, summary);

        List<ScheduleTemplate> results = query.getResultList();
        return results;
    }

    @Override
    public List<Object[]> findSchedules(Date date_from, Date date_to, String provider_no) {
        String sql = "FROM ScheduleTemplate st, ScheduleDate sd " +
                "WHERE st.id.name = sd.hour " +
                "AND sd.date >= ?1 " +
                "AND sd.date <= ?2 " +
                "AND sd.providerNo = ?3 " +
                "AND sd.status = 'A' " +
                "AND (" +
                "	st.id.providerNo = sd.providerNo " +
                "	OR st.id.providerNo = 'Public' " +
                ") ORDER BY sd.date";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, date_from);
        query.setParameter(2, date_to);
        query.setParameter(3, provider_no);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findSchedules(Date dateFrom, List<String> providerIds) {
        String sql = "FROM ScheduleTemplate st, ScheduleDate sd " +
                "WHERE st.id.name = sd.hour " +
                "AND sd.date >= ?1 " +
                "AND sd.providerNo in ( ?2 ) " +
                "AND sd.status = 'A' " +
                "AND (" +
                "	st.providerNo = sd.providerNo " +
                "	OR st.providerNo = 'Public' " +
                ") ORDER BY sd.date";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, dateFrom);
        query.setParameter(2, providerIds);
        return query.getResultList();
    }

    @Override
    public List<ScheduleTemplate> findByProviderNoAndName(String providerNo, String name) {
        Query query = entityManager.createQuery("SELECT e FROM ScheduleTemplate e WHERE e.id.providerNo=?1 and e.id.name=?2 ");
        query.setParameter(1, providerNo);
        query.setParameter(2, name);

        List<ScheduleTemplate> results = query.getResultList();
        return results;
    }

    @Override
    public List<ScheduleTemplate> findByProviderNo(String providerNo) {
        Query query = entityManager.createQuery("SELECT e FROM ScheduleTemplate e WHERE e.id.providerNo=?1 order by e.id.name");
        query.setParameter(1, providerNo);

        List<ScheduleTemplate> results = query.getResultList();
        return results;
    }

    @Override
    @NativeSql({"scheduletemplate", "scheduledate"})
    public List<Object> findTimeCodeByProviderNo(String providerNo, Date date) {
        String sql = "select timecode from scheduletemplate, (select hour from (select provider_no, hour, status from scheduledate where sdate = ?1) as df where status = 'A' and provider_no= ?2) as hf where scheduletemplate.name=hf.hour and (scheduletemplate.provider_no= ?2 or scheduletemplate.provider_no='Public')";
        Query query = entityManager.createNativeQuery(sql, modelClass);
        query.setParameter(1, date);
        query.setParameter(2, providerNo);
        return query.getResultList();
    }

    @Override
    @NativeSql({"scheduletemplate", "scheduledate"})
    public List<Object> findTimeCodeByProviderNo2(String providerNo, Date date) {
        String sql = "select timecode from scheduletemplate, (select hour from (select provider_no, hour, status from scheduledate where sdate = ?1) as df where status = 'A' and provider_no= ?2) as hf where scheduletemplate.name=hf.hour and (scheduletemplate.provider_no= ?2 or scheduletemplate.provider_no='Public')";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, date);
        query.setParameter(2, providerNo);
        return query.getResultList();
    }
}
