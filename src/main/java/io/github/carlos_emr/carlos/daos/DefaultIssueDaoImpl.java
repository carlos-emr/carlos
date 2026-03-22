/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.daos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.model.DefaultIssue;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;

/**
 * JPA-based implementation of {@link DefaultIssueDao} for default clinical issue persistence.
 *
 * @since 2005-01-01
 * @see DefaultIssueDao
 */
public class DefaultIssueDaoImpl extends AbstractDaoImpl<DefaultIssue> implements DefaultIssueDao {

    public DefaultIssueDaoImpl() {
        super(DefaultIssue.class);
    }

    public DefaultIssue findDefaultIssue(Integer id) {
        Query query = entityManager.createQuery("select x from DefaultIssue x where x.id = ?1");
        query.setParameter(1, id);
        return getSingleResultOrNull(query);
    }

    @SuppressWarnings("unchecked")
    public DefaultIssue getLastestDefaultIssue() {
        Query query = entityManager.createQuery("select x from DefaultIssue x order by x.assignedtime desc");
        query.setMaxResults(1);
        List<DefaultIssue> issueList = query.getResultList();
        if (issueList == null || issueList.size() == 0) {
            return null;
        }
        return issueList.get(0);
    }

    @SuppressWarnings("unchecked")
    public List<DefaultIssue> findAll() {
        Query query = entityManager.createQuery("select x from DefaultIssue x order by x.assignedtime desc");
        return query.getResultList();
    }

    public void saveDefaultIssue(DefaultIssue issue) {
        if (issue.getId() != null && issue.getId() > 0) {
            merge(issue);
        } else {
            persist(issue);
        }
    }

    @SuppressWarnings("unchecked")
    public String[] findAllDefaultIssueIds() {
        Query query = entityManager.createQuery("select x.issueIds from DefaultIssue x order by x.assignedtime");
        query.setMaxResults(1);
        List<String> issueIdsList = query.getResultList();
        if (issueIdsList.size() == 0) {
            return new String[0];
        }
        Set<String> issueIdsSet = new HashSet<String>();
        for (String ids : issueIdsList) {
            String[] idsArr = ids.split(",");
            for (String id : idsArr) {
                if (id.length() > 0) {
                    issueIdsSet.add(id);
                }
            }
        }

        return issueIdsSet.toArray(new String[issueIdsSet.size()]);
    }
}
