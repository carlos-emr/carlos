/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Fixed billed-provider queries. Native SQL preserves MariaDB age/date and distinct-count semantics. */
@Repository
@Transactional(readOnly = true)
public class BilledProviderReportDao {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public record ProviderRole(String providerNo, String firstName, String lastName, String role) { }
    public record ProviderDetails(String providerNo, String firstName, String lastName,
            String providerType, String specialty, String ohipNo) { }
    public record Code(String code, String description) { }
    public record Category(String code, String level1, String level2) { }
    public record MetricCount(String key, long value) { }

    /** Includes duplicate role rows and preserves provider-name ordering. */
    public List<ProviderRole> providerRoles() {
        List<?> rows = entityManager.createNativeQuery("select u.provider_no, p.first_name, p.last_name, u.role_name from secUserRole u, provider p where u.provider_no=p.provider_no order by p.first_name, p.last_name").getResultList();
        return rows.stream().map(row -> {
            Object[] r = (Object[]) row;
            return new ProviderRole(text(r[0]), text(r[1]), text(r[2]), text(r[3]));
        }).toList();
    }

    /** All providers, including inactive providers, matching the legacy role editor. */
    public List<ProviderDetails> providers() {
        List<?> rows = entityManager.createNativeQuery("select provider_no, first_name, last_name, provider_type, specialty, ohip_no from provider order by first_name, last_name").getResultList();
        return rows.stream().map(row -> {
            Object[] r = (Object[]) row;
            return new ProviderDetails(text(r[0]), text(r[1]), text(r[2]), text(r[3]), text(r[4]), text(r[5]));
        }).toList();
    }

    public List<Category> categories() {
        List<?> rows = entityManager.createNativeQuery("select dxcode, level1, level2 from dxphcpgroup order by dxcode, level1, level2").getResultList();
        return rows.stream().map(row -> {
            Object[] r = (Object[]) row;
            return new Category(text(r[0]), text(r[1]), text(r[2]));
        }).toList();
    }

    /** Codes are selected by service date; counts below use billing date, as in the existing report. */
    public List<Code> codes(boolean diagnosis, String startDate, String endDate) {
        String sql = diagnosis
                ? "select distinct(bd.diagnostic_code), dt.description from billingdetail bd, diagnosticcode dt where bd.status!='D' and bd.diagnostic_code = dt.diagnostic_code and bd.appointment_date>=?1 and bd.appointment_date<=?2 order by diagnostic_code"
                : "select distinct(service_code), service_desc from billingdetail bd where bd.status!='D' and bd.appointment_date>=?1 and bd.appointment_date<=?2 order by service_code";
        List<?> rows = entityManager.createNativeQuery(sql).setParameter(1, startDate).setParameter(2, endDate).getResultList();
        return rows.stream().map(row -> {
            Object[] r = (Object[]) row;
            return new Code(text(r[0]), text(r[1]));
        }).toList();
    }

    private record MetricDefinition(String key, String diagnosisSql, String serviceSql) { }

    // Keep the legacy MariaDB expressions verbatim; only the repeated execution structure is shared.
    private static final List<MetricDefinition> METRICS = List.of(
            new MetricDefinition("pat",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd where b.billing_no=bd.billing_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D'",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd where b.billing_no=bd.billing_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?"),
            new MetricDefinition("vis",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd  where b.billing_no=bd.billing_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D'",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd where b.billing_no=bd.billing_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?"),
            new MetricDefinition("patSexF",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and d.sex='F'",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and d.sex='F'"),
            new MetricDefinition("patSexM",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and d.sex='M'",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and d.sex='M'"),
            new MetricDefinition("visSexF",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and d.sex='F'",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and d.sex='F'"),
            new MetricDefinition("visSexM",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and d.sex='M'",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and d.sex='M'"),
            new MetricDefinition("pat0_1",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=1 ",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=1 "),
            new MetricDefinition("vis0_1",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=1 ",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=1 "),
            new MetricDefinition("pat2_11",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=11  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=2 ",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=11  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=2 "),
            new MetricDefinition("vis2_11",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=11  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=2 ",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=11  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=2 "),
            new MetricDefinition("pat12_20",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=20  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=12 ",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=20  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=12 "),
            new MetricDefinition("vis12_20",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=20  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=12 ",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=20  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=12 "),
            new MetricDefinition("pat21_34",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=34  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=21 ",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=34  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=21 "),
            new MetricDefinition("vis21_34",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=34  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=21 ",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=34  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=21 "),
            new MetricDefinition("pat35_50",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=50  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=35 ",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=50  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=35 "),
            new MetricDefinition("vis35_50",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=50  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=35 ",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=50  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=35 "),
            new MetricDefinition("pat51_64",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=64  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=51 ",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=64  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=51 "),
            new MetricDefinition("vis51_64",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=64  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=51 ",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=64  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=51 "),
            new MetricDefinition("pat65_70",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=70  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=65 ",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=70  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=65 "),
            new MetricDefinition("vis65_70",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=70  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=65 ",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) <=70  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=65 "),
            new MetricDefinition("pat71_",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=71 ",
                    "select count(distinct(b.demographic_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=?  and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=71 "),
            new MetricDefinition("vis71_",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d  where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no and bd.diagnostic_code=? and b.creator=? and b.billing_date>=? and b.billing_date<=? and b.status!='D' and bd.status!='D' and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=71 ",
                    "select count(distinct(b.billing_no)) from billing b, billingdetail bd, demographic d where b.billing_no=bd.billing_no and b.demographic_no=d.demographic_no  and b.billing_date>=? and b.billing_date<=? and b.creator=? and b.status!='D' and bd.status!='D' and bd.service_code=? and bd.service_desc=? and (YEAR(CURRENT_DATE)-YEAR(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'))) - (RIGHT(CURRENT_DATE,5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth), '-', (d.date_of_birth)), '%Y-%m-%d'),5)) >=71 "));

    /** Each count is bound to the selected provider and excludes deleted headers/details. */
    public List<MetricCount> counts(boolean diagnosis, String providerNo, String startDate, String endDate,
            String code, String description) {
        String[] parameters = diagnosis ? new String[]{code, providerNo, startDate, endDate}
                : new String[]{startDate, endDate, providerNo, code, description};
        List<MetricCount> result = new ArrayList<>();
        for (MetricDefinition metric : METRICS) {
            String sql = diagnosis ? metric.diagnosisSql() : metric.serviceSql();
            result.add(new MetricCount(metric.key(), count(sql, parameters)));
        }
        return List.copyOf(result);
    }

    private long count(String sql, String[] parameters) {
        // Only the fixed expressions in METRICS reach this sink; no caller can supply SQL.
        StringBuilder numbered = new StringBuilder();
        int position = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            numbered.append(ch);
            if (ch == '?') numbered.append(++position);
        }
        var query = entityManager.createNativeQuery(numbered.toString()); // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string -- private helper receives only fixed report SQL literals; provider, code and date values are bound below
        for (int i = 0; i < parameters.length; i++) query.setParameter(i + 1, parameters[i]);
        return ((Number) query.getSingleResult()).longValue();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
