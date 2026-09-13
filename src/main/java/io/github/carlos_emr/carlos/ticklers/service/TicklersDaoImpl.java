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
package io.github.carlos_emr.carlos.ticklers.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.PaginationQuery;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.ticklers.web.TicklerQuery;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Repository
public class TicklersDaoImpl extends AbstractDaoImpl<Tickler> implements TicklersDao {

    public TicklersDaoImpl() {
        super(Tickler.class);
    }

    @Override
    public int getTicklersCount(PaginationQuery paginationQuery) {
        Query query = this.createQuery(paginationQuery, true);

        Long x = (Long) query.getSingleResult();
        return x.intValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Tickler> getTicklers(TicklerQuery ticklerQuery) {
        Query query = this.createQuery(ticklerQuery, false);
        query.setFirstResult(ticklerQuery.getStart());
        query.setMaxResults(ticklerQuery.getLimit());
        return query.getResultList();
    }

    private Query createQuery(PaginationQuery paginationQuery, boolean selectCountOnly) {
        QueryParameters parameters = new QueryParameters();
        StringBuilder sql = this.generateQuery(paginationQuery, selectCountOnly, parameters);
        Query query = entityManager.createQuery(sql.toString());
        parameters.apply(query);
        return query;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private StringBuilder generateQuery(PaginationQuery paginationQuery, boolean selectCountOnly, QueryParameters parameters) {
        TicklerQuery ticklerQuery = (TicklerQuery) paginationQuery;
        StringBuilder sql = new StringBuilder("select " + (selectCountOnly ? "count(*)" : "t")
                + " FROM Tickler t where "
                + " t.serviceDate >= :startDate and t.serviceDate <= :endDate ");
        parameters.add("startDate", ticklerQuery.getStartDate());
        parameters.add("endDate", ticklerQuery.getEndDate());

        if (ticklerQuery.getMrps() != null && ticklerQuery.getMrps().length > 0) {
            sql = new StringBuilder("select " + (selectCountOnly ? "count(*)" : "t")
                    + " FROM Tickler t, Demographic d where "
                    + " t.serviceDate >= :startDate and t.serviceDate <= :endDate ");
            sql.append("and d.demographicNo = cast(t.demographicNo as integer) ");
            sql.append(" and d.providerNo IN (:mrps) ");
            parameters.add("mrps", Arrays.asList(ticklerQuery.getMrps()));
        }

        if (StringUtils.isNotBlank(ticklerQuery.getStatus())) {
            Tickler.STATUS status = parseStatus(ticklerQuery.getStatus());
            if (status != null) {
                sql.append(" and t.status = :status ");
                parameters.add("status", status);
            }
        }

        if (StringUtils.isNotBlank(ticklerQuery.getKeyword())) {
            parameters.add("keyword", "%" + ticklerQuery.getKeyword() + "%");
            sql.append("and (");
            sql.append("str(t.demographicNo) like :keyword ");
            sql.append("or t.message like :keyword ");
            sql.append("or t.creator like :keyword ");
            sql.append("or t.taskAssignedTo like :keyword ");
            sql.append(") ");
        }

        if (ticklerQuery != null) {
            if (StringUtils.equals("true", ticklerQuery.getWithOption())) {

                if (StringUtils.isNotBlank(ticklerQuery.getProgramId())) {
                    sql.append(" and str(t.programId) = :programId ");
                    parameters.add("programId", ticklerQuery.getProgramId());
                }

                if (StringUtils.isNotBlank(ticklerQuery.getDemographicNo())) {
                    sql.append(" and str(t.demographicNo) = :demographicNo ");
                    parameters.add("demographicNo", ticklerQuery.getDemographicNo());
                }
                if (StringUtils.isNotBlank(ticklerQuery.getClient())) {
                    sql.append(" and str(t.demographicNo) = :client ");
                    parameters.add("client", ticklerQuery.getClient());
                }
                if (StringUtils.isNotBlank(ticklerQuery.getMessage())) {
                    sql.append(" and t.message = :message ");
                    parameters.add("message", ticklerQuery.getMessage());
                }

                if (StringUtils.isNotBlank(ticklerQuery.getProviderNo())) {
                    sql.append("and t.creator = :providerNo ");
                    parameters.add("providerNo", ticklerQuery.getProviderNo());
                }

                if (ticklerQuery.getProviders() != null && ticklerQuery.getProviders().length > 0) {
                    sql.append(" and t.creator IN (:providers) ");
                    parameters.add("providers", Arrays.asList(ticklerQuery.getProviders()));
                }

                if (ticklerQuery.getAssignees() != null && ticklerQuery.getAssignees().length > 0) {
                    sql.append(" and t.taskAssignedTo IN (:assignees) ");
                    parameters.add("assignees", Arrays.asList(ticklerQuery.getAssignees()));
                }
            }
            String sort = ticklerQuery.getSort();
            if (!StringUtils.equalsIgnoreCase(sort, "asc") && !StringUtils.equalsIgnoreCase(sort, "desc")) {
                MiscUtils.getLogger().warn("invalid sort parameter passwd for ticklers: " + sort);
                sort = "Asc";
            }

            String orderby = ticklerQuery.getOrderby();
            if (StringUtils.isBlank(orderby) || "null".equals(orderby)) {
                sql.append(" order by t.serviceDate Asc ");
            } else if (orderby.equals("serviceDate")) {
                sql.append(" order by t.serviceDate " + sort);
            } else if (orderby.equals("demographicName")) {
                sql.append(" order by t.provider " + sort);
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
                sql.append(" order by t.serviceDate " + sort);
            }
        }
        return sql;
    }

    private Tickler.STATUS parseStatus(String status) {
        try {
            return Tickler.STATUS.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final class QueryParameters {
        private final Map<String, Object> values = new LinkedHashMap<>();

        private void add(String name, Object value) {
            values.put(name, value);
        }

        private void apply(Query query) {
            values.forEach(query::setParameter);
        }
    }
}
