/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

package io.github.carlos_emr.carlos.billing.CA.dao;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class BillingDetailDaoImpl extends AbstractDaoImpl<BillingDetail> implements BillingDetailDao {

    public BillingDetailDaoImpl() {
        super(BillingDetail.class);
    }

    @Override
    public List<BillingDetail> findAllIncludingDeletedByBillingNo(int billingNo) {
        Query q = entityManager.createQuery("select x from BillingDetail x where x.billingNo=?1");
        q.setParameter(1, billingNo);
        List<BillingDetail> results = q.getResultList();
        return results;
    }

    @Override
    public List<BillingDetail> findByBillingNoAndStatus(Integer billingNo, String status) {
        Query query = createQuery("bd", "bd.billingNo = :billingNo AND bd.status = :status");
        query.setParameter("billingNo", billingNo);
        query.setParameter("status", status);
        return query.getResultList();
    }

    @Override
    public List<BillingDetail> findByBillingNo(Integer billingNo) {
        Query query = createQuery("bd", "bd.billingNo = :billingNo AND bd.status <> 'D' ORDER BY bd.serviceCode");
        query.setParameter("billingNo", billingNo);
        return query.getResultList();
    }

    @Override
    public List<BillingDetail> findByBillingNos(Collection<Integer> billingNos) {
        if (billingNos == null || billingNos.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Query query = entityManager.createQuery(
                "select bd from BillingDetail bd where bd.billingNo in (:billingNos) order by bd.billingNo, bd.serviceCode");
        query.setParameter("billingNos", billingNos);
        return query.getResultList();
    }
}
