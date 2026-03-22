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

import io.github.carlos_emr.carlos.commn.model.RSchedule;
import org.springframework.stereotype.Repository;

@Repository(value = "rScheduleDao")
@SuppressWarnings("unchecked")
/**
 * JPA implementation of {@link RScheduleDao} for recurring schedule data access.
 *
 * @since 2001
 */

public class RScheduleDaoImpl extends AbstractDaoImpl<RSchedule> implements RScheduleDao {

    /** Constructs this DAO for the {@link RSchedule} entity class. */

    public RScheduleDaoImpl() {
        super(RSchedule.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<RSchedule> findByProviderAvailableAndDate(String providerNo, String available, Date sdate) {
        Query query = entityManager.createQuery("select s from RSchedule s where s.providerNo=?1 and s.available=?2 and s.sDate=?3");
        query.setParameter(1, providerNo);
        query.setParameter(2, available);
        query.setParameter(3, sdate);

        List<RSchedule> results = query.getResultList();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public Long search_rschedule_overlaps(String providerNo, Date d1, Date d2, Date d3, Date d4, Date d5, Date d6, Date d7, Date d8, Date d9, Date d10, Date d11, Date d12, Date d13, Date d14) {
        Query query = entityManager.createQuery("select count(r.id) from RSchedule r where r.providerNo=?1 and ((r.sDate <?2 and r.eDate >=?3) or (?4 < r.sDate and r.sDate < ?5) or (?6 < r.eDate and r.eDate <= ?7) or ( ?8 < r.sDate and r.eDate <= ?9) or (r.sDate = ?10 and r.sDate = ?11) or (r.eDate = ?12 and r.eDate <= ?13) or (r.sDate = ?14 and r.eDate != ?15)) and r.status = 'A'");
        query.setParameter(1, providerNo);
        query.setParameter(2, d1);
        query.setParameter(3, d2);
        query.setParameter(4, d3);
        query.setParameter(5, d4);
        query.setParameter(6, d5);
        query.setParameter(7, d6);
        query.setParameter(8, d7);
        query.setParameter(9, d8);
        query.setParameter(10, d9);
        query.setParameter(11, d10);
        query.setParameter(12, d11);
        query.setParameter(13, d12);
        query.setParameter(14, d13);
        query.setParameter(15, d14);

        Long results = (Long) query.getSingleResult();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public Long search_rschedule_exists(String providerNo, Date d1, Date d2) {
        Query query = entityManager.createQuery("select count(r.id) from RSchedule r where r.providerNo=?1 and r.sDate =?2 and r.eDate =?3 and r.status = 'A'");
        query.setParameter(1, providerNo);
        query.setParameter(2, d1);
        query.setParameter(3, d2);

        Long results = (Long) query.getSingleResult();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public RSchedule search_rschedule_current(String providerNo, String available, Date sdate) {
        Query query = entityManager.createQuery("select s from RSchedule s where s.providerNo=?1 and s.available=?2 and s.sDate <= ?3 and s.status='A' order by s.sDate desc");
        query.setParameter(1, providerNo);
        query.setParameter(2, available);
        query.setParameter(3, sdate);
        query.setMaxResults(1);

        RSchedule result = this.getSingleResultOrNull(query);
        return result;
    }

    /** {@inheritDoc} */

    @Override
    public List<RSchedule> search_rschedule_future(String providerNo, String available, Date sdate) {
        Query query = entityManager.createQuery("select s from RSchedule s where s.providerNo=?1 and s.available=?2 and s.sDate > ?3 and s.status='A' order by s.sDate");
        query.setParameter(1, providerNo);
        query.setParameter(2, available);
        query.setParameter(3, sdate);

        @SuppressWarnings("unchecked")
        List<RSchedule> results = query.getResultList();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public RSchedule search_rschedule_current1(String providerNo, Date sdate) {
        Query query = entityManager.createQuery("select s from RSchedule s where s.providerNo=?1 and s.sDate <= ?2 and s.status='A' order by s.sDate desc");
        query.setParameter(1, providerNo);
        query.setParameter(2, sdate);
        query.setMaxResults(1);

        RSchedule result = this.getSingleResultOrNull(query);
        return result;
    }

    /** {@inheritDoc} */

    @Override
    public RSchedule search_rschedule_current2(String providerNo, Date sdate) {
        Query query = entityManager.createQuery("select s from RSchedule s where s.providerNo=?1 and s.sDate >= ?2 and s.status='A' order by s.sDate");
        query.setParameter(1, providerNo);
        query.setParameter(2, sdate);
        query.setMaxResults(1);

        RSchedule result = this.getSingleResultOrNull(query);
        return result;
    }

    /** {@inheritDoc} */

    @Override
    public List<RSchedule> search_rschedule_future1(String providerNo, Date sdate) {
        Query query = entityManager.createQuery("select s from RSchedule s where s.providerNo=?1 and s.sDate > ?2 and s.status='A' order by s.sDate");
        query.setParameter(1, providerNo);
        query.setParameter(2, sdate);

        List<RSchedule> results = query.getResultList();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public List<RSchedule> findByProviderNoAndDates(String providerNo, Date apptDate) {
        Query query = createQuery("r", "r.providerNo = ?1 AND r.sDate <= ?2 AND r.eDate >= ?2");
        query.setParameter(1, providerNo);
        query.setParameter(2, apptDate);
        return query.getResultList();
    }

}
