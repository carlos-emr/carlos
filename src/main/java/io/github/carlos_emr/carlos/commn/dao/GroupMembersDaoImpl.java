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

import org.springframework.stereotype.Repository;
import io.github.carlos_emr.carlos.commn.model.GroupMembers;
import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;

import jakarta.persistence.Query;
import java.util.Collections;
import java.util.List;

@Repository
public class GroupMembersDaoImpl extends AbstractDaoImpl<GroupMembers> implements GroupMembersDao {

    public GroupMembersDaoImpl() {
        super(GroupMembers.class);
    }

    /**
     * Only group members with an integrated facility ID that is remote - greater
     * than zero.
     *
     * @param groupId
     * @return
     */
    @Override
    public List<GroupMembers> findRemoteByGroupId(int groupId) {
        Query q = entityManager.createQuery("SELECT x FROM GroupMembers x WHERE x.facilityId > 0 AND x.groupId=?1");
        q.setParameter(1, groupId);

        @SuppressWarnings("unchecked")
        List<GroupMembers> results = q.getResultList();

        return results;
    }

    /**
     * Only group members that have a facility id of 0 - for local.
     *
     * @param groupId
     * @return
     */
    @Override
    public List<GroupMembers> findLocalByGroupId(int groupId) {
        Query q = entityManager.createQuery("SELECT x FROM GroupMembers x WHERE x.facilityId = 0 AND x.groupId=?1");
        q.setParameter(1, groupId);

        @SuppressWarnings("unchecked")
        List<GroupMembers> results = q.getResultList();

        return results;
    }

    @Override
    public List<GroupMembers> findByGroupId(int groupId) {
        Query q = entityManager.createQuery("SELECT x FROM GroupMembers x WHERE x.groupId=?1");
        q.setParameter(1, groupId);

        @SuppressWarnings("unchecked")
        List<GroupMembers> results = q.getResultList();

        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Object[]> findMembersByGroupId(int groupId) {
        String sql = "SELECT g, p FROM GroupMembers g, Provider p WHERE g.providerNo = p.ProviderNo AND g.groupId = ?1 ORDER BY p.LastName, p.FirstName";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, groupId);
        return query.getResultList();
    }

    @Override
    public List<GroupMembers> findByProviderNumberAndFacilityId(String providerNo, Integer facilityId) {
        Query query = entityManager
                .createQuery("SELECT x FROM GroupMembers x WHERE x.providerNo LIKE ?1 AND x.facilityId=?2");
        query.setParameter(1, providerNo);
        query.setParameter(2, facilityId);

        @SuppressWarnings("unchecked")
        List<GroupMembers> results = query.getResultList();

        if (results == null) {
            results = Collections.emptyList();
        }

        return results;
    }

    @Override
    public List<GroupMembers> findGroupMember(String providerNo, int groupId) {
        Query query = entityManager.createQuery("SELECT x FROM GroupMembers x WHERE x.providerNo LIKE ?1 AND x.groupId = ?2");
        query.setParameter(1, providerNo);
        query.setParameter(2, groupId);
        @SuppressWarnings("unchecked")
        List<GroupMembers> results = query.getResultList();
        if (results == null) {
            results = Collections.emptyList();
        }
        return results;
    }

    @Override
    public List<GroupMembers> findByFacilityId(Integer facilityId) {
        Query query = entityManager.createQuery("SELECT x FROM GroupMembers x WHERE x.facilityId=?1");
        query.setParameter(1, facilityId);

        @SuppressWarnings("unchecked")
        List<GroupMembers> results = query.getResultList();

        if (results == null) {
            results = Collections.emptyList();
        }

        return results;
    }

    @Override
    public GroupMembers findByIdentity(ContactIdentifier contactIdentifier) {
        Query query = entityManager.createQuery("SELECT x FROM GroupMembers x " +
                "WHERE x.facilityId=?1 AND x.providerNo=?2 AND x.groupId=?3");
        query.setParameter(1, contactIdentifier.getFacilityId());
        query.setParameter(2, contactIdentifier.getContactId());
        query.setParameter(3, contactIdentifier.getGroupId());
        return super.getSingleResultOrNull(query);
    }

}
