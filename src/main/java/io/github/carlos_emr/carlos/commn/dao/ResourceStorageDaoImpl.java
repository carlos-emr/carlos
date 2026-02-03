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

import io.github.carlos_emr.carlos.commn.model.ResourceStorage;
import org.springframework.stereotype.Repository;

@Repository
public class ResourceStorageDaoImpl extends AbstractDaoImpl<ResourceStorage> implements ResourceStorageDao {

    public ResourceStorageDaoImpl() {
        super(ResourceStorage.class);
    }

    @Override
    public ResourceStorage findActive(String resourceType) {
        String sqlCommand = "FROM " + modelClass.getSimpleName() + " r WHERE r.resourceType = ?1 AND r.active = true";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, resourceType);
        return getSingleResultOrNull(query);
    }

    @Override
    public List<ResourceStorage> findActiveAll(String resourceType) {
        String sqlCommand = "FROM " + modelClass.getSimpleName() + " r WHERE r.resourceType = ?1 AND r.active = true";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, resourceType);
        return query.getResultList();
    }

    @Override
    public List<ResourceStorage> findAll(String resourceType) {
        String sqlCommand = "FROM " + modelClass.getSimpleName() + " r WHERE r.resourceType = ?1";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, resourceType);
        return query.getResultList();
    }

    @Override
    public List<ResourceStorage> findByUUID(String uuid) {
        String sqlCommand = "FROM " + modelClass.getSimpleName() + " r WHERE r.uuid = ?1";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, uuid);
        return query.getResultList();
    }

}
