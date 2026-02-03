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
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import org.springframework.stereotype.Repository;

@Repository
public class BillingPaymentTypeDaoImpl extends AbstractDaoImpl<BillingPaymentType> implements BillingPaymentTypeDao {

    public BillingPaymentTypeDaoImpl() {
        super(BillingPaymentType.class);
    }

    @SuppressWarnings("unchecked")
    public List<BillingPaymentType> findAll() {
        Query query = entityManager.createQuery("SELECT x FROM " + modelClass.getSimpleName() + " x");
        List<BillingPaymentType> results = query.getResultList();
        return results;
    }

    public Integer findIdByName(String name) {
        Query query = entityManager.createQuery("SELECT x.id FROM " + modelClass.getSimpleName() + " x WHERE x.paymentType = ?1");
        query.setParameter(1, name);
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();
        if (results.size() == 1) return (results.get(0));
        else if (results.size() == 0) return (null);

        return null;
    }

    public BillingPaymentType getPaymentTypeByName(String typeName) {
        Query query = entityManager.createQuery("from BillingPaymentType bpt where bpt.paymentType = :typeName");
        query.setParameter("typeName", typeName);
        return this.getSingleResultOrNull(query);
    }

}
