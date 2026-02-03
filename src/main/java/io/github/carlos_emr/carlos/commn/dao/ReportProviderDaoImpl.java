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

import io.github.carlos_emr.carlos.commn.model.ReportProvider;
import org.springframework.stereotype.Repository;

@Repository
public class ReportProviderDaoImpl extends AbstractDaoImpl<ReportProvider> implements ReportProviderDao {

    public ReportProviderDaoImpl() {
        super(ReportProvider.class);
    }

    @Override
    public List<ReportProvider> findAll() {
        Query query = createQuery("x", null);
        return query.getResultList();
    }

    @Override
    public List<ReportProvider> findByAction(String action) {
        String sql = "select x from ReportProvider x where x.action=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, action);

        List<ReportProvider> results = query.getResultList();
        return results;
    }

    @Override
    public List<ReportProvider> findByProviderNoTeamAndAction(String providerNo, String team, String action) {
        String sql = "select x from ReportProvider x where x.providerNo=?1 and x.team=?2 and x.action=?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, providerNo);
        query.setParameter(2, team);
        query.setParameter(3, action);

        List<ReportProvider> results = query.getResultList();
        return results;
    }

    @Override
    public List<Object[]> search_reportprovider(String action) {
        String sql = "from ReportProvider r, Provider p where r.providerNo=p.ProviderNo and r.status<>'D' and r.action=?1 order by r.team";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, action);

        List<Object[]> results = query.getResultList();
        return results;
    }

    @Override
    public List<Object[]> search_reportprovider(String action, String providerNo) {
        String sql = "from ReportProvider r, Provider p where r.providerNo=p.ProviderNo and r.status<>'D' and r.action=?1 and p.ProviderNo like ?2 order by r.team";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, action);
        query.setParameter(2, providerNo);

        List<Object[]> results = query.getResultList();
        return results;
    }
}
