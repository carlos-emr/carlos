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

import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceAccessTokenDaoImpl extends AbstractDaoImpl<ServiceAccessToken> implements ServiceAccessTokenDao {


    public ServiceAccessTokenDaoImpl() {
        super(ServiceAccessToken.class);
    }

    @Override
    public void persist(ServiceAccessToken token) {
        this.entityManager.persist(token);
    }

    @Override
    public void remove(ServiceAccessToken token) {
        this.entityManager.remove(token);
    }

    // @Override
// public ServiceRequestToken merge(ServiceAccessTokenDao token) {
//     return this.entityManager.merge(token);
// }
    @Override
    @SuppressWarnings("unchecked")
    public List<ServiceAccessToken> findAll() {
        Query query = createQuery("x", null);
        return query.getResultList();
    }

    @Override
    public ServiceAccessToken findByTokenId(String token) {
        Query query = entityManager.createQuery("SELECT x FROM ServiceAccessToken x WHERE x.tokenId=?1");
        query.setParameter(1, token);

        return this.getSingleResultOrNull(query);
    }

}
