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

import io.github.carlos_emr.carlos.commn.model.ReportByExamplesFavorite;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class ReportByExamplesFavoriteDaoImpl extends AbstractDaoImpl<ReportByExamplesFavorite> implements ReportByExamplesFavoriteDao {

    public ReportByExamplesFavoriteDaoImpl() {
        super(ReportByExamplesFavorite.class);
    }

    @Override
    public List<ReportByExamplesFavorite> findByProviderAndQuery(String providerNo, String queryString) {
        Query query = createQuery("ex", "ex.providerNo = ?1 AND ex.query = ?2");
        query.setParameter(1, providerNo);
        query.setParameter(2, queryString);
        return query.getResultList();
    }

    @Override
    public List<ReportByExamplesFavorite> findByEverything(String providerNo, String favoriteName, String queryString) {
        Query query = createQuery("ex", "ex.providerNo = ?1 AND ex.name = ?2 AND ex.query = ?3");
        query.setParameter(1, providerNo);
        query.setParameter(2, favoriteName);
        query.setParameter(3, queryString);
        return query.getResultList();
    }

    @Override
    public List<ReportByExamplesFavorite> findByProvider(String providerNo) {
        Query query = createQuery("ex", "ex.providerNo = ?1");
        query.setParameter(1, providerNo);
        return query.getResultList();
    }
}
