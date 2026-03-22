/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.NativeSql;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link BillingBCDao} for healthcare billing data access.
 *
 * @since 2001
 */

public class BillingBCDaoImpl extends BillingDaoImpl implements BillingBCDao {

    @NativeSql({"ctl_billingservice", "billingservice"})
    @SuppressWarnings("unchecked")
    public List<Object[]> findBillingServices(String billRegion, String serviceGroup, String serviceType) {
        Query query = entityManager.createNativeQuery("SELECT DISTINCT b.service_code, b.description, b.value, b.percentage "
            + "FROM ctl_billingservice c left outer join billingservice b on b.service_code="
            + "c.service_code where b.region = ? and c.service_group= ? and c.servicetype = ? order by c.service_order");

        query.setParameter(1, billRegion);
        query.setParameter(2, serviceGroup);
        query.setParameter(3, serviceType);

        return query.getResultList();
    }

    @NativeSql("ctl_billingservice")
    @SuppressWarnings("unchecked")
    public List<Object[]> findBillingServicesByType(String serviceType) {
        Query query = entityManager.createNativeQuery("SELECT DISTINCT service_code, servicetype, service_group, status, service_order "
            + "FROM ctl_billingservice where servicetype = ? order by service_order");

        query.setParameter(1, serviceType);

        return query.getResultList();
    }

    @NativeSql({"ctl_billingservice", "billingservice"})
    @SuppressWarnings("unchecked")
    public List<Object[]> findBillingServices(String billRegion, String serviceGroup, String serviceType, String billReferenceDate) {
        Query query = entityManager.createNativeQuery(
            "SELECT DISTINCT b.service_code, b.description, b.value, b.percentage " +
                "FROM ctl_billingservice c left outer join billingservice b on b.service_code="
                + "c.service_code where b.region = ? and c.service_group = ? and c.servicetype = ?" +
                " and b.billingservice_date in (select max(b2.billingservice_date) from billingservice b2 where b2.billingservice_date <= ? and b2.service_code = b.service_code) order by c.service_order");

        query.setParameter(1, billRegion);
        query.setParameter(2, serviceGroup);
        query.setParameter(3, serviceType);
        query.setParameter(4, billReferenceDate);

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @NativeSql("billinglocation")
    public List<Object[]> findBillingLocations(String billRegion) {
        Query query = entityManager.createNativeQuery("SELECT billinglocation,billinglocation_desc FROM billinglocation WHERE region = ?");
        query.setParameter(1, billRegion);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @NativeSql("billingvisit")
    public List<Object[]> findBillingVisits(String billRegion) {
        Query query = entityManager.createNativeQuery("SELECT visittype, visit_desc FROM billingvisit WHERE region = ? ORDER BY visittype ASC");
        query.setParameter(1, billRegion);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @NativeSql("wcb_side")
    public List<Object[]> findInjuryLocations() {
        Query query = entityManager.createNativeQuery("SELECT sidetype, sidedesc FROM wcb_side");
        return query.getResultList();
    }
}
