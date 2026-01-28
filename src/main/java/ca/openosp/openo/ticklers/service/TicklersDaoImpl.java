//CHECKSTYLE:OFF
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
package ca.openosp.openo.ticklers.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.commn.PaginationQuery;
import ca.openosp.openo.commn.dao.AbstractDaoImpl;
import ca.openosp.openo.commn.model.Tickler;
import ca.openosp.openo.ticklers.web.TicklerQuery;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.stereotype.Repository;

@Repository
public class TicklersDaoImpl extends AbstractDaoImpl<Tickler> implements TicklersDao {

    public TicklersDaoImpl() {
        super(Tickler.class);
    }

    @Override
    public int getTicklersCount(PaginationQuery paginationQuery) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = this.generateQuery(paginationQuery, true, params);
        Query query = entityManager.createQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        Long x = (Long) query.getSingleResult();
        return x.intValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Tickler> getTicklers(TicklerQuery ticklerQuery) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = this.generateQuery(ticklerQuery, false, params);
        Query query = entityManager.createQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        query.setFirstResult(ticklerQuery.getStart());
        query.setMaxResults(ticklerQuery.getLimit());
        return query.getResultList();
    }

    private StringBuilder generateQuery(PaginationQuery paginationQuery, boolean selectCountOnly, List<Object> params) {
        TicklerQuery ticklerQuery = (TicklerQuery) paginationQuery;
        int paramIndex = 1;

        Date startDate = ticklerQuery.getStartDate();
        Date endDate = ticklerQuery.getEndDate();

        StringBuilder sql;

        if (ticklerQuery.getMrps() != null && ticklerQuery.getMrps().length > 0) {
            sql = new StringBuilder("select " + (selectCountOnly ? "count(*)" : "t")
                    + " FROM Tickler t, Demographic d WHERE"
                    + " t.serviceDate >= ?" + paramIndex++);
            params.add(startDate);
            sql.append(" and t.serviceDate <= ?" + paramIndex++);
            params.add(endDate);

            sql.append(" and d.DemographicNo = cast(t.demographicNo as integer)");

            sql.append(" and d.ProviderNo IN (");
            String[] mrps = ticklerQuery.getMrps();
            for (int x = 0; x < mrps.length; x++) {
                if (x > 0) {
                    sql.append(",");
                }
                sql.append("?" + paramIndex++);
                params.add(mrps[x]);
            }
            sql.append(")");
        } else {
            sql = new StringBuilder("select " + (selectCountOnly ? "count(*)" : "t")
                    + " FROM Tickler t WHERE"
                    + " t.serviceDate >= ?" + paramIndex++);
            params.add(startDate);
            sql.append(" and t.serviceDate <= ?" + paramIndex++);
            params.add(endDate);
        }

        if (StringUtils.isNotBlank(ticklerQuery.getStatus())) {
            sql.append(" and t.status = ?" + paramIndex++);
            params.add(ticklerQuery.getStatus());
        }

        if (StringUtils.isNotBlank(ticklerQuery.getKeyword())) {
            String kw = "%" + ticklerQuery.getKeyword() + "%";
            sql.append(" and (");
            sql.append("t.demographicNo like ?" + paramIndex++);
            params.add(kw);
            sql.append(" or t.message like ?" + paramIndex++);
            params.add(kw);
            sql.append(" or t.creator like ?" + paramIndex++);
            params.add(kw);
            sql.append(" or t.taskAssignedTo like ?" + paramIndex++);
            params.add(kw);
            sql.append(")");
        }

        if (StringUtils.equals("true", ticklerQuery.getWithOption())) {

            if (StringUtils.isNotBlank(ticklerQuery.getProgramId())) {
                sql.append(" and t.programId = ?" + paramIndex++);
                params.add(ticklerQuery.getProgramId());
            }

            if (StringUtils.isNotBlank(ticklerQuery.getDemographicNo())) {
                sql.append(" and t.demographicNo = ?" + paramIndex++);
                params.add(ticklerQuery.getDemographicNo());
            }
            if (StringUtils.isNotBlank(ticklerQuery.getClient())) {
                sql.append(" and t.demographicNo = ?" + paramIndex++);
                params.add(ticklerQuery.getClient());
            }
            if (StringUtils.isNotBlank(ticklerQuery.getMessage())) {
                sql.append(" and t.message = ?" + paramIndex++);
                params.add(ticklerQuery.getMessage());
            }

            if (StringUtils.isNotBlank(ticklerQuery.getProviderNo())) {
                sql.append(" and t.creator = ?" + paramIndex++);
                params.add(ticklerQuery.getProviderNo());
            }

            if (ticklerQuery.getProviders() != null && ticklerQuery.getProviders().length > 0) {
                sql.append(" and t.creator IN (");
                String[] providers = ticklerQuery.getProviders();
                for (int x = 0; x < providers.length; x++) {
                    if (x > 0) {
                        sql.append(",");
                    }
                    sql.append("?" + paramIndex++);
                    params.add(providers[x]);
                }
                sql.append(")");
            }

            if (ticklerQuery.getAssignees() != null && ticklerQuery.getAssignees().length > 0) {
                sql.append(" and t.taskAssignedTo IN (");
                String[] assignees = ticklerQuery.getAssignees();
                for (int x = 0; x < assignees.length; x++) {
                    if (x > 0) {
                        sql.append(",");
                    }
                    sql.append("?" + paramIndex++);
                    params.add(assignees[x]);
                }
                sql.append(")");
            }
        }

        String sort = ticklerQuery.getSort();
        if (!sort.equalsIgnoreCase("asc") && !sort.equalsIgnoreCase("desc")) {
            MiscUtils.getLogger().warn("invalid sort parameter passed for ticklers: " + sort);
            sort = "";
        }

        String orderby = ticklerQuery.getOrderby();
        if (StringUtils.isBlank(orderby) || "null".equals(orderby)) {
            sql.append(" order by t.serviceDate Asc");
        } else if (orderby.equals("serviceDate")) {
            sql.append(" order by t.serviceDate " + sort);
        } else if (orderby.equals("demographicName")) {
            sql.append(" order by t.demographicNo " + sort);
        } else if (orderby.equals("updateDate")) {
            sql.append(" order by t.updateDate " + sort);
        } else if (orderby.equals("providerName")) {
            sql.append(" order by t.creator " + sort);
        } else if (orderby.equals("assigneeName")) {
            sql.append(" order by t.taskAssignedTo " + sort);
        } else if (orderby.equals("priority")) {
            sql.append(" order by t.priority " + sort);
        } else if (orderby.equals("status")) {
            sql.append(" order by t.status " + sort);
        } else {
            sql.append(" order by t.serviceDate Asc");
        }

        return sql;
    }
}
