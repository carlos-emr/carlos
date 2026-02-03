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

import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingFavorite;

import org.springframework.stereotype.Repository;

import java.util.List;

import javax.persistence.Query;

@Repository
public class ProviderLabRoutingFavoritesDaoImpl extends AbstractDaoImpl<ProviderLabRoutingFavorite> implements ProviderLabRoutingFavoritesDao {

    public ProviderLabRoutingFavoritesDaoImpl() {
        super(ProviderLabRoutingFavorite.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ProviderLabRoutingFavorite> findFavorites(String provider_no) {
        String sql = "select fav from ProviderLabRoutingFavorite fav where fav.provider_no = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, provider_no);
        return query.getResultList();
    }
}
