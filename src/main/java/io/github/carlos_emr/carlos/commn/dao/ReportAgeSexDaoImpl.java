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
import io.github.carlos_emr.carlos.commn.model.ReportAgeSex;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link ReportAgeSexDao} for reporting data access.
 *
 * @since 2001
 */

public class ReportAgeSexDaoImpl extends AbstractDaoImpl<ReportAgeSex> implements ReportAgeSexDao {

    /** Constructs this DAO for the {@link ReportAgeSex} entity class. */

    public ReportAgeSexDaoImpl() {
        super(ReportAgeSex.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<ReportAgeSex> findBeforeReportDate(Date reportDate) {
        String sql = "select x from ReportAgeSex x where x.reportDate=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, reportDate);

        @SuppressWarnings("unchecked")
        List<ReportAgeSex> results = query.getResultList();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public void deleteAllByDate(Date reportDate) {
        String sql = "delete from ReportAgeSex x where x.reportDate <= ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, reportDate);
        query.executeUpdate();
    }

    @NativeSql("reportagesex")
    /** {@inheritDoc} */

    @Override
    public void populateAll(String yearOfBirth) {
        String copyQuery =
                "INSERT INTO reportagesex(demographic_no, age, roster, sex, provider_no, reportdate, status, date_joined) " +
                        "SELECT d.demographic_no, FLOOR(DATEDIFF(CURRENT_DATE(), STR_TO_DATE(CONCAT(d.year_of_birth,'-',d.month_of_birth,'-',d.date_of_birth), '%Y-%m-%d' )) / 365.25), d.roster_status, d.sex, d.provider_no, CURRENT_DATE(), d.patient_status, d.date_joined " +
                        "FROM demographic d WHERE d.year_of_birth >= ?1";
        Query query = entityManager.createNativeQuery(copyQuery);
        query.setParameter(1, yearOfBirth);
        query.executeUpdate();
    }

    /** {@inheritDoc} */

    @Override
    public Long count_reportagesex_roster(String roster, String sex, String providerNo, int age, Date dateStarted, Date dateEnded) {
        String sql = "select count(x) from ReportAgeSex x where (x.status<>'OP' and x.status<>'IN' and x.status<>'DE') " +
        "and x.roster=?1 and x.sex like ?2 and x.providerNo = ?3 and x.age >= ?4 and x.dateJoined >= ?5 and x.dateJoined <= ?6";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, roster);
        query.setParameter(2, sex);
        query.setParameter(3, providerNo);
        query.setParameter(4, age);
        query.setParameter(5, dateStarted);
        query.setParameter(6, dateEnded);

        Long results = (Long) query.getSingleResult();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public Long count_reportagesex_noroster(String roster, String sex, String providerNo, int minAge, int maxAge, Date dateStarted, Date dateEnded) {
        String sql = "select count(x)  from ReportAgeSex x  where (x.status<>'OP' and x.status<>'IN' and x.status<>'DE') " +
        "and x.roster<>?1 and x.sex like ?2 and x.providerNo=?3 and x.age >= ?4 and x.age <=?5 and x.dateJoined >=?6 and x.dateJoined <=?7";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, roster);
        query.setParameter(2, sex);
        query.setParameter(3, providerNo);
        query.setParameter(4, minAge);
        query.setParameter(5, maxAge);
        query.setParameter(6, dateStarted);
        query.setParameter(7, dateEnded);

        Long results = (Long) query.getSingleResult();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public Long count_reportagesex(String roster, String sex, String providerNo, int minAge, int maxAge, Date startDate, Date endDate) {
        String sql =
                "select count(x) from ReportAgeSex x " +
                        "where " +
                        "( x.status <> 'OP' and x.status <> 'IN' and x.status <> 'DE') and " +
                        "x.roster like ?1 and " +
                        "x.sex like ?2 and " +
                        "x.providerNo=?3 and " +
                        "x.age >= ?4 and " +
                        "x.age <=?5 and " +
                        "x.dateJoined >=?6 and " +
                        "x.dateJoined <=?7";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, roster);
        query.setParameter(2, sex);
        query.setParameter(3, providerNo);
        query.setParameter(4, minAge);
        query.setParameter(5, maxAge);
        query.setParameter(6, startDate);
        query.setParameter(7, endDate);

        Long results = (Long) query.getSingleResult();
        return results;
    }
}
