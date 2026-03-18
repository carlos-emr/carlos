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

import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.RaHeader;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class RaHeaderDaoImpl extends AbstractDaoImpl<RaHeader> implements RaHeaderDao {

    public RaHeaderDaoImpl() {
        super(RaHeader.class);
    }

    @Override
    public List<RaHeader> findCurrentByFilenamePaymentDate(String filename, String paymentDate) {
        Query query = entityManager.createQuery("SELECT r from RaHeader r WHERE r.filename = ?1 and r.paymentDate = ?2 and status != ?3 ORDER BY r.paymentDate");
        query.setParameter(1, filename);
        query.setParameter(2, paymentDate);
        query.setParameter(3, "D");
        return query.getResultList();
    }

    @Override
    public List<RaHeader> findByFilenamePaymentDate(String filename, String paymentDate) {
        Query query = entityManager.createQuery("SELECT r from RaHeader r WHERE r.filename = ?1 and r.paymentDate = ?2 ORDER BY r.paymentDate");
        query.setParameter(1, filename);
        query.setParameter(2, paymentDate);
        return query.getResultList();
    }

    @Override
    public List<RaHeader> findAllExcludeStatus(String status) {
        Query query = entityManager.createQuery("SELECT r FROM RaHeader r WHERE r.status != ?1 ORDER BY r.paymentDate DESC, r.readDate DESC");
        query.setParameter(1, status);
        return query.getResultList();
    }

    @Override
    public List<RaHeader> findByHeaderDetailsAndProviderMagic(String status, String providerNo) {
        String sql =
                "SELECT r " +
                        "FROM RaHeader r, RaDetail t, Provider p " +
                        "WHERE r.id = t.raHeaderNo " +
                        "AND p.OhipNo = t.providerOhipNo " +
                        "AND r.status <> ?1 " +
                        "AND (" +
                        "   p.ProviderNo = ?2" +
                        "   OR p.Team = (" +
                        "       SELECT pp.Team FROM Provider pp WHERE pp.ProviderNo = ?2 " +
                        "   ) " +
                        ") GROUP BY r.id" +
                        " ORDER BY r.paymentDate DESC, r.readDate DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, status);
        query.setParameter(2, providerNo);
        return query.getResultList();
    }

    @Override
    public List<RaHeader> findByStatusAndProviderMagic(String status, String providerNo) {
        String sql = "SELECT r " +
                "FROM RaHeader r, RaDetail t, Provider p " +
                "WHERE r.id = t.raHeaderNo " +
                "AND p.OhipNo = t.providerOhipNo " +
                "AND r.status <> ?1 " +
                "AND EXISTS (" +
                "   FROM ProviderSite s " +
                "   WHERE p.ProviderNo = s.id.providerNo " +
                "   AND s.id.siteId IN (" +
                "       SELECT ss.id.siteId FROM ProviderSite ss WHERE ss.id.providerNo = ?2 " +
                "   ) " +
                ") " +
                "GROUP BY r.id " +
                "ORDER BY r.paymentDate DESC, r.readDate DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, status);
        query.setParameter(2, providerNo);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findHeadersAndProvidersById(Integer id) {
        String sql = "FROM RaDetail r, Provider p " +
                "WHERE p.OhipNo = r.providerOhipNo " +
                "AND r.raHeaderNo = ?1 " +
                "GROUP BY r.providerOhipNo";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        return query.getResultList();
    }
}
