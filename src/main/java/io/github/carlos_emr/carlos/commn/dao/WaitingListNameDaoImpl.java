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

import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;

import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link WaitingListNameDao} for waiting list data access.
 *
 * @since 2001
 */

public class WaitingListNameDaoImpl extends AbstractDaoImpl<WaitingListName> implements WaitingListNameDao {

    public WaitingListNameDaoImpl() {
        super(WaitingListName.class);
    }

    public long countActiveWatingListNames() {
        Query query = entityManager.createQuery("SELECT COUNT(*) FROM WaitingListName n WHERE n.isHistory = 'N'");
        query.setMaxResults(1);
        return (Long) query.getSingleResult();
    }

    public List<WaitingListName> findCurrentByNameAndGroup(String name, String group) {
        String sql = "select x from WaitingListName x where x.name = ?1 AND x.groupNo = ?2 and x.isHistory='N'";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, name);
        query.setParameter(2, group);

        @SuppressWarnings("unchecked")
        List<WaitingListName> results = query.getResultList();
        return results;
    }

    public List<WaitingListName> findByMyGroups(String providerNo, List<MyGroup> myGroups) {
        List<String> groupIds = new ArrayList<String>();
        for (MyGroup mg : myGroups) {
            groupIds.add(mg.getId().getMyGroupNo());
        }
        if (!groupIds.contains(providerNo)) {
            groupIds.add(providerNo);
        }
        groupIds.add(".default");

        String sql = "select x from WaitingListName x where x.groupNo IN (?1) and x.isHistory='N' order by x.name ASC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, groupIds);

        @SuppressWarnings("unchecked")
        List<WaitingListName> results = query.getResultList();
        return results;
    }

    public List<WaitingListName> findCurrentByGroup(String group) {
        String sql = "select x from WaitingListName x where x.groupNo = ?1 and x.isHistory='N' order by x.name";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, group);

        @SuppressWarnings("unchecked")
        List<WaitingListName> results = query.getResultList();
        return results;
    }
}
