/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * Ported from openo-beta/Open-O PR #2268 by LiamStanziani.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Tickler;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds parameterized JPQL queries for TicklerListDTO projection.
 * <p>
 * All dynamic filter values use positional parameter placeholders (?N) that are
 * bound via {@code Query.setParameter()} in the calling DAO method. No user input
 * is ever concatenated into the query string — only static JPQL fragments and
 * integer parameter indices are appended.
 * </p>
 *
 * @since 2026-02-10
 */
final class TicklerDtoDaoQueryBuilder {

    private TicklerDtoDaoQueryBuilder() {
    }

    /** Static JPQL for batch-loading tickler comments with named parameter binding. */
    static final String COMMENT_QUERY =
            "SELECT NEW io.github.carlos_emr.carlos.tickler.dto.TicklerCommentDTO("
            + "c.id, c.ticklerNo, c.message, c.updateDate, "
            + "c.provider.LastName, c.provider.FirstName) "
            + "FROM TicklerComment c LEFT JOIN c.provider "
            + "WHERE c.ticklerNo IN (:ticklerIds) ORDER BY c.updateDate ASC";

    /** Static JPQL for batch-loading tickler links with named parameter binding. */
    static final String LINK_QUERY =
            "SELECT NEW io.github.carlos_emr.carlos.tickler.dto.TicklerLinkDTO("
            + "l.id, l.ticklerNo, l.tableName, l.tableId) "
            + "FROM TicklerLink l "
            + "WHERE l.ticklerNo IN (:ticklerIds) ORDER BY l.id ASC";

    private static final String DTO_SELECT_PREFIX =
            "SELECT NEW io.github.carlos_emr.carlos.tickler.dto.TicklerListDTO("
            + "t.id, t.message, t.serviceDate, t.createDate, t.status, t.priority, "
            + "t.demographicNo, d.LastName, d.FirstName, "
            + "creator.LastName, creator.FirstName, "
            + "assignee.LastName, assignee.FirstName) "
            + "FROM Tickler t "
            + "LEFT JOIN Demographic d ON d.DemographicNo = t.demographicNo "
            + "LEFT JOIN Provider creator ON creator.ProviderNo = t.creator "
            + "LEFT JOIN Provider assignee ON assignee.ProviderNo = t.taskAssignedTo ";

    /**
     * Builds a complete parameterized JPQL query string for DTO projection.
     *
     * @param paramList list to populate with positional parameter values
     * @param filter the filter criteria
     * @param statusConverter function to convert status string to Tickler.STATUS
     * @param priorityConverter function to convert priority string to Tickler.PRIORITY
     * @return the complete JPQL query with positional parameter placeholders
     */
    static String buildQuery(List<Object> paramList, CustomFilter filter,
                             Function<String, Tickler.STATUS> statusConverter,
                             Function<String, Tickler.PRIORITY> priorityConverter) {
        int paramIndex = 1;

        boolean includeMRPClause = filter.getMrp() != null
                && !filter.getMrp().isEmpty()
                && !"All Providers".equals(filter.getMrp());
        boolean includeProviderClause = filter.getProvider() != null
                && !filter.getProvider().isEmpty()
                && !"All Providers".equals(filter.getProvider());
        boolean includeAssigneeClause = filter.getAssignee() != null
                && !filter.getAssignee().isEmpty()
                && !"All Providers".equals(filter.getAssignee());
        boolean includeStatusClause = filter.getStatus() != null
                && !filter.getStatus().isEmpty()
                && !"Z".equals(filter.getStatus());
        boolean includePriorityClause = filter.getPriority() != null
                && !filter.getPriority().isEmpty();
        boolean includeClientClause = isValidIntegerFilter(filter.getClient())
                && !"All Clients".equals(filter.getClient());
        boolean includeDemographicClause = isValidIntegerFilter(filter.getDemographicNo())
                && !"All Clients".equalsIgnoreCase(filter.getDemographicNo());
        boolean includeProgramClause = isValidIntegerFilter(filter.getProgramId())
                && !"All Programs".equals(filter.getProgramId());
        boolean includeMessage = filter.getMessage() != null
                && !filter.getMessage().trim().isEmpty();

        String jpql = DTO_SELECT_PREFIX;

        if (includeMRPClause) {
            jpql = jpql + "WHERE d.ProviderNo = ?" + paramIndex++ + " ";
            paramList.add(filter.getMrp());
        } else {
            jpql = jpql + "WHERE 1=1 ";
        }

        if (filter.getStartDate() != null) {
            jpql = jpql + "AND t.serviceDate >= ?" + paramIndex++ + " ";
            paramList.add(filter.getStartDate());
        }

        if (filter.getEndDate() != null) {
            jpql = jpql + "AND t.serviceDate <= ?" + paramIndex++ + " ";
            Calendar cal = Calendar.getInstance();
            cal.setTime(filter.getEndDate());
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            paramList.add(new Date(cal.getTime().getTime()));
        }

        if (includeProviderClause) {
            jpql = appendProviderInClause(jpql, paramList, "t.creator", filter.getProviders(), paramIndex);
            paramIndex += filter.getProviders().size();
        }

        if (includeAssigneeClause) {
            jpql = appendProviderInClause(jpql, paramList, "t.taskAssignedTo", filter.getAssignees(), paramIndex);
            paramIndex += filter.getAssignees().size();
        }

        if (includeProgramClause) {
            jpql = jpql + "AND t.programId = ?" + paramIndex++ + " ";
            paramList.add(Integer.valueOf(filter.getProgramId()));
        }

        if (includeStatusClause) {
            jpql = jpql + "AND t.status = ?" + paramIndex++ + " ";
            paramList.add(statusConverter.apply(filter.getStatus()));
        }

        if (includePriorityClause) {
            jpql = jpql + "AND t.priority = ?" + paramIndex++ + " ";
            paramList.add(priorityConverter.apply(filter.getPriority()));
        }

        if (includeClientClause) {
            jpql = jpql + "AND t.demographicNo = ?" + paramIndex++ + " ";
            paramList.add(Integer.parseInt(filter.getClient()));
        }

        if (includeDemographicClause) {
            jpql = jpql + "AND t.demographicNo = ?" + paramIndex++ + " ";
            paramList.add(Integer.parseInt(filter.getDemographicNo()));
        }

        if (includeMessage) {
            jpql = jpql + "AND t.message = ?" + paramIndex++ + " ";
            paramList.add(filter.getMessage());
        }

        // ORDER BY for deterministic results and stable pagination
        if ("desc".equalsIgnoreCase(filter.getSort_order())) {
            jpql = jpql + "ORDER BY t.serviceDate DESC, t.id DESC ";
        } else {
            jpql = jpql + "ORDER BY t.serviceDate ASC, t.id DESC ";
        }

        return jpql;
    }

    private static boolean isValidIntegerFilter(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String appendProviderInClause(String jpql, List<Object> paramList, String fieldName,
                                                  Set<Provider> providers, int paramIndex) {
        String clause = "AND " + fieldName + " IN (";
        int i = 0;
        for (Provider provider : providers) {
            if (i > 0) {
                clause += ",";
            }
            clause += "?" + (paramIndex + i);
            paramList.add(provider.getProviderNo());
            i++;
        }
        clause += ") ";
        return jpql + clause;
    }
}
