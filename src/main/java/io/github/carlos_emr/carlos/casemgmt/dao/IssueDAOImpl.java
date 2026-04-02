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

package io.github.carlos_emr.carlos.casemgmt.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

@Transactional
public class IssueDAOImpl extends AbstractHibernateDao implements IssueDAO {
    private static Logger logger = MiscUtils.getLogger();

    @Override
    public Issue getIssue(Long id) {
        return currentSession().find(Issue.class, id);
    }

    @Override
    public List<Issue> getIssues() {
        return (List<Issue>) HqlQueryHelper.find(currentSession(), "from Issue");
    }

    @Override
    public List<Issue> findIssueByCode(String[] codes) {
        if (codes == null || codes.length == 0) {
            return new ArrayList<>();
        }
        String hql = "from Issue i where i.code in (:codes)";
        Map<String, Object> params = new HashMap<>();
        params.put("codes", Arrays.asList(codes));
        return (List<Issue>) HqlQueryHelper.find(currentSession(), hql, params);
    }

    @Override
    public Issue findIssueByCode(String code) {
        List<Issue> list = (List<Issue>) HqlQueryHelper.find(currentSession(), "from Issue i where i.code = ?1",
                code);
        if (list.size() > 0)
            return list.get(0);

        return null;
    }

    @Override
    public Issue findIssueByTypeAndCode(String type, String code) {
        List<Issue> list = (List<Issue>) HqlQueryHelper.find(currentSession(), "from Issue i where i.type=?1 and i.code = ?2",
                type, code);
        if (list.size() > 0)
            return list.get(0);

        return null;
    }

    @Override
    public void saveIssue(Issue issue) {
        if (issue.getId() == null) {
            currentSession().persist(issue);
        } else {
            currentSession().merge(issue);
        }
    }

    @Deprecated
    @Override
    public void delete(Long issueId) {
        currentSession().remove(getIssue(issueId));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Issue> findIssueBySearch(String search) {
        search = "%" + search + "%";
        search = search.toLowerCase();
        String sql = "from Issue i where lower(i.code) like ?1 or lower(i.description) like ?2";
        return (List<Issue>) HqlQueryHelper.find(currentSession(), sql, search, search);
    }

    @Override
    public List<Long> getIssueCodeListByRoles(List<Secrole> roles) {
        if (roles.size() == 0) {
            return new ArrayList<Long>();
        }

        List<String> roleNames = new ArrayList<>();
        for (Secrole role : roles) {
            roleNames.add(role.getName());
        }

        String sql = "select i.id from Issue i where i.role in (:roleNames) order by sortOrderId";
        Map<String, Object> params = new HashMap<>();
        params.put("roleNames", roleNames);
        return (List<Long>) HqlQueryHelper.find(currentSession(), sql, params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Issue> search(String search, List<Secrole> roles, final int startIndex, final int numToReturn) {
        if (roles.size() == 0) {
            return new ArrayList<Issue>();
        }

        List<String> roleNames = new ArrayList<>();
        for (Secrole role : roles) {
            roleNames.add(role.getName());
        }

        search = "%" + search + "%";
        search = search.toLowerCase();
        String hql = "from Issue i where (lower(i.code) like :search or lower(i.description) like :search or lower(i.role) like :search) and i.role in (:roleNames) order by sortOrderId";
        logger.debug(hql);
        Map<String, Object> params = new HashMap<>();
        params.put("search", search);
        params.put("roleNames", roleNames);
        return (List<Issue>) HqlQueryHelper.findWithPagination(currentSession(), hql, startIndex,
                Math.min(numToReturn, AbstractDaoImpl.MAX_LIST_RETURN_SIZE), params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Integer searchCount(String search, List<Secrole> roles) {
        if (roles.size() == 0) {
            return 0;
        }

        List<String> roleNames = new ArrayList<String>();
        for (Secrole role : roles) {
            roleNames.add(role.getName());
        }

        search = "%" + search + "%";
        search = search.toLowerCase();

        String hql = "select count(i) from Issue i where (lower(i.code) like :search or lower(i.description) like :search or lower(i.role) like :search) and i.role in (:roleNames)";
        logger.debug(hql);
        Map<String, Object> params = new HashMap<>();
        params.put("search", search);
        params.put("roleNames", roleNames);
        List<Long> result = (List<Long>) HqlQueryHelper.find(currentSession(), hql, params);

        if (result.size() > 0) {
            return result.get(0).intValue();
        }

        return 0;
    }

    @Override
    public List searchNoRolesConcerned(String search) {
        search = "%" + search + "%";
        search = search.toLowerCase();
        String sql = "from Issue i where (lower(i.code) like ?1 or lower(i.description) like ?2)";
        logger.debug(sql);
        return HqlQueryHelper.find(currentSession(), sql, search, search);
    }

    /**
     * Retrieves a list of Issue codes that have a type matching what is configured
     * in carlos.properties as COMMUNITY_ISSUE_CODETYPE,
     * or an empty list if this property is not found.
     *
     * @param type
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<String> getLocalCodesByCommunityType(String type) {
        List<String> codes;
        if (type == null || type.equals("")) {
            codes = new ArrayList<String>();
        } else {
            codes = (List<String>) HqlQueryHelper.find(currentSession(), "SELECT i.code FROM Issue i WHERE i.type = ?1",
                    type.toLowerCase());
        }
        return codes;
    }
}
