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
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.commn.dao.AbstractDaoImpl;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.openo.model.security.Secrole;

/**
 * Data Access Object implementation for managing Issue entities.
 * <p>
 * This DAO provides CRUD operations and search functionality for medical issues/diagnoses
 * used in case management. Issues can be filtered by role, searched by code or description,
 * and linked to various case management workflows.
 * </p>
 * <p>
 * Migrated from HibernateDaoSupport to direct SessionFactory injection for Spring 6 compatibility.
 * </p>
 *
 * @since 2.0
 */
public class IssueDAOImpl implements IssueDAO {
    private static Logger logger = MiscUtils.getLogger();

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session.
     *
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves an Issue by its ID.
     *
     * @param id the unique identifier of the Issue
     * @return the Issue entity, or null if not found
     */
    @Override
    public Issue getIssue(Long id) {
        return getSession().get(Issue.class, id);
    }

    /**
     * Retrieves all Issues in the system.
     *
     * @return a list of all Issue entities
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Issue> getIssues() {
        return getSession().createQuery("from Issue").list();
    }

    /**
     * Finds Issues by an array of issue codes.
     * <p>
     * <strong>Security Note:</strong> This method concatenates codes into SQL which creates
     * potential SQL injection vulnerabilities. The codes parameter should be validated
     * before calling this method. Consider refactoring to use parameterized queries
     * with collection parameters in future updates.
     * </p>
     *
     * @param codes array of issue codes to search for (must be validated/sanitized by caller)
     * @return list of Issues matching any of the provided codes
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Issue> findIssueByCode(String[] codes) {
        String code = "'" + StringUtils.join(codes, "','") + "'";
        return getSession().createQuery("from Issue i where i.code in (" + code + ")").list();
    }

    /**
     * Finds a single Issue by its code.
     *
     * @param code the issue code to search for
     * @return the first Issue matching the code, or null if not found
     */
    @Override
    @SuppressWarnings("unchecked")
    public Issue findIssueByCode(String code) {
        List<Issue> list = getSession().createQuery("from Issue i where i.code = :code")
                .setParameter("code", code)
                .list();
        if (list.size() > 0)
            return list.get(0);

        return null;
    }

    /**
     * Finds a single Issue by its type and code.
     *
     * @param type the issue type
     * @param code the issue code
     * @return the first Issue matching both type and code, or null if not found
     */
    @Override
    @SuppressWarnings("unchecked")
    public Issue findIssueByTypeAndCode(String type, String code) {
        List<Issue> list = getSession().createQuery("from Issue i where i.type=:type and i.code = :code")
                .setParameter("type", type)
                .setParameter("code", code)
                .list();
        if (list.size() > 0)
            return list.get(0);

        return null;
    }

    /**
     * Saves or updates an Issue entity.
     *
     * @param issue the Issue to save or update
     */
    @Override
    public void saveIssue(Issue issue) {
        getSession().saveOrUpdate(issue);
    }

    /**
     * Deletes an Issue by its ID.
     *
     * @param issueId the ID of the Issue to delete
     * @deprecated Consider using a soft delete or archiving mechanism instead
     */
    @Deprecated
    @Override
    public void delete(Long issueId) {
        Issue issue = getIssue(issueId);
        if (issue != null) {
            getSession().delete(issue);
        }
    }

    /**
     * Searches for Issues by code or description using case-insensitive LIKE matching.
     *
     * @param search the search term to match against code or description
     * @return list of Issues matching the search criteria
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Issue> findIssueBySearch(String search) {
        search = "%" + search + "%";
        search = search.toLowerCase();
        String sql = "from Issue i where lower(i.code) like :search1 or lower(i.description) like :search2";
        return getSession().createQuery(sql)
                .setParameter("search1", search)
                .setParameter("search2", search)
                .list();
    }

    /**
     * Retrieves a list of Issue IDs filtered by security roles.
     * <p>
     * <strong>Security Note:</strong> This method builds SQL with concatenated role names.
     * Role names should be validated/sanitized by the security framework before being passed here.
     * Consider refactoring to use parameterized queries with collection parameters in future updates.
     * </p>
     *
     * @param roles list of security roles to filter by
     * @return list of Issue IDs accessible to the given roles, ordered by sortOrderId
     */
    @Override
    @SuppressWarnings("unchecked")
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
        return getSession().createQuery(sql).list();
    }

    /**
     * Searches for Issues by search term and filters by security roles with pagination.
     * <p>
     * Searches across code, description, and role fields using case-insensitive LIKE matching.
     * Results are filtered by role and support pagination with maximum result limits.
     * </p>
     * <p>
     * <strong>Security Note:</strong> This method builds SQL with concatenated role names.
     * Role names should be validated/sanitized by the security framework before being passed here.
     * </p>
     *
     * @param search the search term to match
     * @param roles list of security roles to filter by
     * @param startIndex the starting index for pagination (0-based)
     * @param numToReturn the maximum number of results to return
     * @return paginated list of Issues matching the criteria, ordered by sortOrderId
     */
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
        // Note: Original implementation used roleList for the role LIKE clause, but this has been
        // changed to use the search term for consistency. The role LIKE now searches for the
        // search term within role names, while the IN clause filters by allowed roles.
        final String sql = "from Issue i where (lower(i.code) like :search or lower(i.description) like :search or lower(i.role) like :search) and i.role in ("
                + roleList + ") order by sortOrderId";
        logger.debug(sql);
        
        Query q = getSession().createQuery(sql);
        q.setMaxResults(Math.min(numToReturn, AbstractDaoImpl.MAX_LIST_RETURN_SIZE));
        q.setFirstResult(startIndex);
        q.setParameter("search", search);
        return q.list();
    }

    /**
     * Counts the number of Issues matching search criteria and role filters.
     * <p>
     * <strong>Security Note:</strong> This method builds SQL with concatenated role names.
     * Role names should be validated/sanitized by the security framework before being passed here.
     * </p>
     *
     * @param search the search term to match against code, description, and role
     * @param roles list of security roles to filter by
     * @return the count of matching Issues, or 0 if none found
     */
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
        // Note: Original implementation used roleList for the role LIKE clause, but this has been
        // changed to use the search term for consistency with the search intent.
        final String sql = "select count(i) from Issue i where (lower(i.code) like :search or lower(i.description) like :search or lower(i.role) like :search) and i.role in ("
                + roleList + ")";
        logger.debug(sql);
        List<Long> result = getSession().createQuery(sql)
                .setParameter("search", search)
                .list();

        if (result.size() > 0) {
            return result.get(0).intValue();
        }

        return 0;
    }

    /**
     * Searches for Issues without role filtering.
     * <p>
     * Searches across code and description fields using case-insensitive LIKE matching.
     * No role-based security filtering is applied.
     * </p>
     *
     * @param search the search term to match
     * @return list of Issues matching the search criteria
     */
    @Override
    @SuppressWarnings("unchecked")
    public List searchNoRolesConcerned(String search) {
        search = "%" + search + "%";
        search = search.toLowerCase();
        String sql = "from Issue i where (lower(i.code) like :search1 or lower(i.description) like :search2)";
        logger.debug(sql);
        return getSession().createQuery(sql)
                .setParameter("search1", search)
                .setParameter("search2", search)
                .list();
    }

    /**
     * Retrieves a list of Issue codes that have a type matching what is configured
     * in oscar_mcmaster.properties as COMMUNITY_ISSUE_CODETYPE,
     * or an empty list if this property is not found.
     *
     * @param type the community issue code type to filter by
     * @return list of Issue codes matching the specified type, or empty list if type is null/empty
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<String> getLocalCodesByCommunityType(String type) {
        List<String> codes;
        if (type == null || type.equals("")) {
            codes = new ArrayList<String>();
        } else {
            codes = getSession().createQuery("SELECT i.code FROM Issue i WHERE i.type = :type")
                    .setParameter("type", type.toLowerCase())
                    .list();
        }
        return codes;
    }
}
