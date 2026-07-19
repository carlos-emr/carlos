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
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class BillingONItemDaoImpl extends AbstractDaoImpl<BillingONItem> implements BillingONItemDao {

    public BillingONItemDaoImpl() {
        super(BillingONItem.class);
    }

    public List<BillingONItem> getBillingItemByCh1Id(Integer ch1_id) {
        String queryStr = "FROM BillingONItem b WHERE b.ch1Id = ?1";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, ch1_id);
        return q.getResultList();
    }

    public List<BillingONItem> getActiveBillingItemByCh1Id(Integer ch1_id) {
        String queryStr = "FROM BillingONItem b WHERE b.ch1Id = ?1 AND b.status <> ?2";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, ch1_id);
        q.setParameter(2, "D");
        return q.getResultList();
    }

    public List<BillingONCHeader1> getCh1ByDemographicNo(Integer demographic_no) {
        String queryStr = "FROM BillingONCHeader1 b WHERE b.demographicNo = ?1 ORDER BY b.id DESC";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, demographic_no);
        return q.getResultList();
    }

    public List<BillingONItem> findByCh1Id(Integer id) {
        Query query = createQuery("bi", "bi.ch1Id = ?1 AND bi.status <> 'D' AND bi.status <> 'S'");
        query.setParameter(1, id);
        return query.getResultList();
    }

    public List<BillingONItem> findByCh1IdAndStatusNotEqual(Integer chId, String string) {
        Query query = createQuery("i", "i.ch1Id= ?1 AND i.status != 'D'");
        query.setParameter(1, chId);
        return query.getResultList();
    }

    public List<BillingONCHeader1> getCh1ByDemographicNoSince(Integer demographic_no, Date lastUpdateDate) {
        String queryStr = "FROM BillingONCHeader1 b WHERE b.demographicNo = " + demographic_no + " and b.timestamp > ?1 ORDER BY b.id";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, lastUpdateDate);
        return q.getResultList();
    }

    public List<Integer> getDemographicNoSince(Date lastUpdateDate) {
        String queryStr = "select b.demographicNo FROM BillingONCHeader1 b WHERE b.timestamp > ?1 ORDER BY b.id";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, lastUpdateDate);
        return q.getResultList();
    }
}
