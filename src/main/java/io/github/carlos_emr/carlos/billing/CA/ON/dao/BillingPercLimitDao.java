/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.billing.CA.ON.dao;

import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class BillingPercLimitDao extends AbstractDaoImpl<BillingPercLimit> {

    public BillingPercLimitDao() {
        super(BillingPercLimit.class);
    }


    public List<BillingPercLimit> findByServiceCode(String serviceCode) {
        String sql = "select x from BillingPercLimit x where x.service_code=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceCode);

        List<BillingPercLimit> results = query.getResultList();
        return results;
    }

    public BillingPercLimit findByServiceCodeAndEffectiveDate(String serviceCode, Date effectiveDate) {
        String sql = "select x from BillingPercLimit x where x.service_code=?1 and x.effective_date=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceCode);
        query.setParameter(2, effectiveDate);

        BillingPercLimit results = this.getSingleResultOrNull(query);
        return results;
    }


    public List<BillingPercLimit> findByServiceCodeAndLatestDate(String serviceCode, Date date) {
        String sql = "FROM BillingPercLimit b WHERE b.service_code = :serviceCode " +
                "AND b.effective_date = (" +
                "	SELECT MAX(b2.effective_date) FROM BillingPercLimit b2 " +
                "	WHERE b2.effective_date <= :date and b2.service_code = :serviceCode" +
                ")";
        Query query = entityManager.createQuery(sql);
        query.setParameter("serviceCode", serviceCode);
        query.setParameter("date", date);
        return query.getResultList();
    }
}
