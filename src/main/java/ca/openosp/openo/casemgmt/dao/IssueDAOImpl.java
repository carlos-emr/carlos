//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.casemgmt.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.commn.dao.AbstractDaoImpl;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.orm.hibernate5.HibernateCallback;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;

import ca.openosp.openo.model.security.Secrole;

public class IssueDAOImpl extends HibernateDaoSupport implements IssueDAO {
    private static Logger logger = MiscUtils.getLogger();

    @Override
    public Issue getIssue(Long id) {
        return getHibernateTemplate().get(Issue.class, id);
    }

    @Override
    public List<Issue> getIssues() {
        return (List<Issue>) this.getHibernateTemplate().find("from Issue");
    }

    @Override
    public List<Issue> findIssueByCode(String[] codes) {
        String code = "'" + StringUtils.join(codes, "','") + "'";
        return (List<Issue>) this.getHibernateTemplate().find("from Issue i where i.code in (" + code + ")");
    }

    @Override
    public Issue findIssueByCode(String code) {
        List<Issue> list = (List<Issue>) this.getHibernateTemplate().find("from Issue i where i.code = ?0",
                new Object[]{code});
        if (list.size() > 0)
            return list.get(0);

        return null;
    }

    @Override
    public Issue findIssueByTypeAndCode(String type, String code) {
        List<Issue> list = (List<Issue>) this.getHibernateTemplate().find("from Issue i where i.type=?0 and i.code = ?1",
                new Object[]{type, code});
        if (list.size() > 0)
            return list.get(0);

        return null;
    }

    @Override
    public void saveIssue(Issue issue) {
        this.getHibernateTemplate().saveOrUpdate(issue);
    }

    @Deprecated
    @Override
    public void delete(Long issueId) {
        this.getHibernateTemplate().delete(getIssue(issueId));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Issue> findIssueBySearch(String search) {
        search = "%" + search + "%";
        search = search.toLowerCase();
        String sql = "from Issue i where lower(i.code) like ?0 or lower(i.description) like ?1";
        return (List<Issue>) this.getHibernateTemplate().find(sql, new Object[]{search, search});
    }

    @Override
    public List<Long> getIssueCodeListByRoles(List<Secrole> roles) {
        if (roles.size() == 0) {
            return new ArrayList<Long>();
        }

        StringBuilder buf = new StringBuilder();
        for (int x = 0; x < roles.size(); x++) {
            if (x != 0) {
                buf.append(",");
            }
            buf.append("\'" + (roles.get(x).getName()) + "\'");
        }
        String roleList = buf.toString();

        String sql = "select i.id from Issue i where i.role in (" + roleList + ") order by sortOrderId";
        logger.debug(sql);
        return (List<Long>) this.getHibernateTemplate().find(sql);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Issue> search(String search, List<Secrole> roles, final int startIndex, final int numToReturn) {
        if (roles.size() == 0) {
            return new ArrayList<Issue>();
        }

        StringBuilder buf = new StringBuilder();
        for (int x = 0; x < roles.size(); x++) {
            if (x != 0) {
                buf.append(",");
            }
            buf.append("\'" + (roles.get(x).getName()) + "\'");
        }
        final String roleList = buf.toString();

        search = "%" + search + "%";
        search = search.toLowerCase();
        final String sql = "from Issue i where (lower(i.code) like ?1 or lower(i.description) like ?1  or lower(i.role) like ?2) and i.role in ("
                + roleList + ") order by sortOrderId";
        logger.debug(sql);
        final String s = search;
        return (List<Issue>) getHibernateTemplate().execute(new HibernateCallback<List<Issue>>() {
            public List<Issue> doInHibernate(Session session) throws HibernateException {
                Query q = session.createQuery(sql);
                q.setMaxResults(Math.min(numToReturn, AbstractDaoImpl.MAX_LIST_RETURN_SIZE));
                q.setFirstResult(startIndex);
                q.setParameter(1, s);
                q.setParameter(2, roleList);
                return q.list();
            }
        });

    }

    @SuppressWarnings("unchecked")
    @Override
    public Integer searchCount(String search, List<Secrole> roles) {
        if (roles.size() == 0) {
            return 0;
        }

        StringBuilder buf = new StringBuilder();
        for (int x = 0; x < roles.size(); x++) {
            if (x != 0) {
                buf.append(",");
            }
            buf.append("\'" + (roles.get(x).getName()) + "\'");
        }
        final String roleList = buf.toString();

        search = "%" + search + "%";
        search = search.toLowerCase();
        final String sql = "select count(i) from Issue i where (lower(i.code) like ?0 or lower(i.description) like ?1  or lower(i.role) like ?2) and i.role in ("
                + roleList + ") order by sortOrderId";
        logger.debug(sql);
        List<Long> result = (List<Long>) this.getHibernateTemplate().find(sql,
                new Object[]{search, search, roleList});

        if (result.size() > 0) {
            return result.get(0).intValue();
        }

        return 0;
    }

    @Override
    public List searchNoRolesConcerned(String search) {
        search = "%" + search + "%";
        search = search.toLowerCase();
        String sql = "from Issue i where (lower(i.code) like ?0 or lower(i.description) like ?1)";
        logger.debug(sql);
        return this.getHibernateTemplate().find(sql, new Object[]{search, search});
    }

    /**
     * Retrieves a list of Issue codes that have a type matching what is configured
     * in oscar_mcmaster.properties as COMMUNITY_ISSUE_CODETYPE,
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
            codes = (List<String>) this.getHibernateTemplate().find("FROM Issue i WHERE i.type = ?0",
                    new Object[]{type.toLowerCase()});
        }
        return codes;
    }
}
