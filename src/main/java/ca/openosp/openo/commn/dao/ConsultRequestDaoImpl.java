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
package ca.openosp.openo.commn.dao;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.commn.PaginationQuery;
import ca.openosp.openo.commn.model.ConsultationRequest;
import ca.openosp.openo.consultations.ConsultationQuery;
import ca.openosp.openo.consultations.ConsultationRequestSearchFilter;
import ca.openosp.openo.consultations.ConsultationRequestSearchFilter.SORTMODE;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.stereotype.Repository;

@Repository
public class ConsultRequestDaoImpl extends AbstractDaoImpl<ConsultationRequest> implements ConsultRequestDao {

    public ConsultRequestDaoImpl() {
        super(ConsultationRequest.class);
    }

    @Override
    public int getConsultationCount(PaginationQuery paginationQuery) {
        QueryWithParams queryWithParams = generateQueryWithParams(paginationQuery, true);
        Query query = entityManager.createQuery(queryWithParams.sql);
        setQueryParameters(query, queryWithParams);

        Long x = (Long) query.getSingleResult();
        return x.intValue();
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    @Override
    public List<ConsultationRequest> listConsultationRequests(ConsultationQuery consultationQuery) {
        QueryWithParams queryWithParams = generateQueryWithParams(consultationQuery, false);
        Query query = entityManager.createQuery(queryWithParams.sql);
        setQueryParameters(query, queryWithParams);
        query.setFirstResult(consultationQuery.getStart());
        query.setMaxResults(consultationQuery.getLimit());
        return query.getResultList();
    }

    @Deprecated
    private QueryWithParams generateQueryWithParams(PaginationQuery paginationQuery, boolean selectCountOnly) {
        ConsultationQuery consultationQuery = (ConsultationQuery) paginationQuery;
        QueryWithParams queryWithParams = new QueryWithParams();
        
        StringBuilder sql = new StringBuilder(
                "select " + (selectCountOnly ? "count(*)" : "cr") +
                        " from ConsultationRequest cr left outer join cr.professionalSpecialist specialist, ConsultationServices cs, Demographic d"
                        +
                        " left outer join d.provider p where d.DemographicNo = cr.demographicId and cs.id = cr.serviceId ");
        
        if (StringUtils.isNotBlank(consultationQuery.getProviderNo())) {
            sql.append("and cr.providerNo = :providerNo ");
            queryWithParams.addParam("providerNo", consultationQuery.getProviderNo());
        }
        if (!StringUtils.equals(consultationQuery.getComplete(), "true")) {
            sql.append("and cr.status != 4 ");
        }
        if (StringUtils.isNotBlank(consultationQuery.getStatus())) {
            sql.append("and cr.status = :status ");
            queryWithParams.addParam("status", consultationQuery.getStatus());
        }
        if (StringUtils.isNotBlank(consultationQuery.getTeam())) {
            sql.append("and cr.sendTo = :team ");
            queryWithParams.addParam("team", consultationQuery.getTeam());
        }
        if (StringUtils.isNotBlank(consultationQuery.getKeyword())) {
            String keywordParam = "%" + consultationQuery.getKeyword() + "%";
            sql.append("and (");
            sql.append("d.LastName like :keyword ");
            sql.append("or d.FirstName like :keyword ");
            sql.append("or specialist.lastName like :keyword ");
            sql.append("or specialist.firstName like :keyword ");
            sql.append("or cs.serviceDesc like :keyword");
            sql.append(") ");
            queryWithParams.addParam("keyword", keywordParam);
        }
        if (consultationQuery != null) {
            if (StringUtils.equals("true", consultationQuery.getWithOption())) {
                String dateType = consultationQuery.getDateType();
                Date startDate = consultationQuery.getStartDate();
                Date endDate = consultationQuery.getEndDate();

                if (startDate != null) {
                    if (StringUtils.equals("appointmentDate", dateType)) {
                        sql.append("and cr.appointmentDate >= :startDate ");
                    } else {
                        sql.append("and cr.referralDate >= :startDate ");
                    }
                    queryWithParams.addParam("startDate", startDate);
                }

                if (endDate != null) {
                    if (StringUtils.equals("appointmentDate", dateType)) {
                        sql.append("and cr.appointmentDate <= :endDate ");
                    } else {
                        sql.append("and cr.referralDate <= :endDate ");
                    }
                    queryWithParams.addParam("endDate", endDate);
                }
            }
            
            // Build ORDER BY clause - validate all user input first
            String sort = consultationQuery.getSort();
            String orderby = consultationQuery.getOrderby();
            
            // Sanitize and validate sort direction BEFORE any usage
            String validatedSort;
            if (sort != null) {
                String lowerSort = sort.toLowerCase().trim();
                if ("asc".equals(lowerSort)) {
                    validatedSort = "asc";
                } else if ("desc".equals(lowerSort)) {
                    validatedSort = "desc";
                } else {
                    validatedSort = "desc"; // Default to desc if invalid
                }
            } else {
                validatedSort = "desc";
            }
            
            // Build ORDER BY based on known column values using validated sort
            if (StringUtils.isBlank(orderby) || "null".equals(orderby)) {
                sql.append("order by cr.referralDate desc ");
            } else if ("serviceDesc".equals(orderby)) {
                sql.append(" order by cs.serviceDesc ").append(validatedSort);
            } else if ("patient".equals(orderby)) {
                sql.append(" order by d.LastName ").append(validatedSort);
            } else if ("providerName".equals(orderby)) {
                sql.append(" order by p.LastName ").append(validatedSort);
            } else if ("specialistName".equals(orderby)) {
                sql.append(" order by specialist.lastName ").append(validatedSort);
            } else if ("appointmentDate".equals(orderby)) {
                sql.append(" order by cr.appointmentDate ").append(validatedSort)
                    .append(", cr.appointmentTime ").append(validatedSort);
            } else {
                sql.append(" order by cr.").append(orderby).append(" ").append(validatedSort);
            }
        }
        
        queryWithParams.sql = sql.toString();
        return queryWithParams;
    }

    @Override
    public int getConsultationCount2(ConsultationRequestSearchFilter filter) {
        QueryWithParams queryWithParams = buildSearchQuery(filter, true);
        MiscUtils.getLogger().info("sql=" + queryWithParams.sql);
        Query query = entityManager.createQuery(queryWithParams.sql);
        setQueryParameters(query, queryWithParams);
        Long count = this.getCountResult(query);

        return count.intValue();
    }

    @Override
    public List<Object[]> search(ConsultationRequestSearchFilter filter) {
        QueryWithParams queryWithParams = buildSearchQuery(filter, false);
        MiscUtils.getLogger().info("sql=" + queryWithParams.sql);
        Query query = entityManager.createQuery(queryWithParams.sql);
        setQueryParameters(query, queryWithParams);
        query.setFirstResult(filter.getStartIndex());
        query.setMaxResults(filter.getNumToReturn());
        return query.getResultList();
    }

    private static class QueryWithParams {
        String sql;
        List<Object> params = new java.util.ArrayList<>();
        List<String> paramNames = new java.util.ArrayList<>();
        
        void addParam(String name, Object value) {
            paramNames.add(name);
            params.add(value);
        }
    }
    
    private void setQueryParameters(Query query, QueryWithParams queryWithParams) {
        for (int i = 0; i < queryWithParams.params.size(); i++) {
            query.setParameter(queryWithParams.paramNames.get(i), queryWithParams.params.get(i));
        }
    }

    private QueryWithParams buildSearchQuery(ConsultationRequestSearchFilter filter, boolean selectCountOnly) {
        QueryWithParams queryWithParams = new QueryWithParams();
        
        StringBuilder sql = new StringBuilder(
                "select " + (selectCountOnly ? "count(*)" : "cr,specialist,cs,d,p") +
                        " from ConsultationRequest cr left outer join cr.professionalSpecialist specialist, ConsultationServices cs, Demographic d"
                        +
                        " left outer join d.provider p where d.DemographicNo = cr.demographicId and cs.id = cr.serviceId ");

        if (filter.getAppointmentStartDate() != null) {
            sql.append("and cr.appointmentDate >= :appointmentStartDate ");
            queryWithParams.addParam("appointmentStartDate", filter.getAppointmentStartDate());
        }

        if (filter.getAppointmentEndDate() != null) {
            sql.append("and cr.appointmentDate <= :appointmentEndDate ");
            queryWithParams.addParam("appointmentEndDate", setCalender(filter.getAppointmentEndDate()).getTime());
        }

        if (filter.getReferralStartDate() != null) {
            sql.append("and cr.referralDate >= :referralStartDate ");
            queryWithParams.addParam("referralStartDate", filter.getReferralStartDate());
        }

        if (filter.getReferralEndDate() != null) {
            sql.append("and cr.referralDate <= :referralEndDate ");
            queryWithParams.addParam("referralEndDate", setCalender(filter.getReferralEndDate()).getTime());
        }

        if (filter.getStatus() != null) {
            sql.append("and cr.status = :status ");
            queryWithParams.addParam("status", String.valueOf(filter.getStatus()));
        } else {
            sql.append("and cr.status!=4 and cr.status!=5 and cr.status!=7 ");
        }

        if (StringUtils.isNotBlank(filter.getTeam())) {
            sql.append("and cr.sendTo = :team ");
            queryWithParams.addParam("team", filter.getTeam());
        }

        if (StringUtils.isNotBlank(filter.getUrgency())) {
            sql.append("and cr.urgency = :urgency ");
            queryWithParams.addParam("urgency", filter.getUrgency());
        }

        if (filter.getDemographicNo() != null && filter.getDemographicNo() > 0) {
            sql.append("and cr.demographicId = :demographicNo ");
            queryWithParams.addParam("demographicNo", filter.getDemographicNo());
        }

        if (filter.getMrpNo() != null && filter.getMrpNo() > 0) {
            sql.append("and d.ProviderNo = :mrpNo ");
            queryWithParams.addParam("mrpNo", filter.getMrpNo());
        }

        String orderBy = "cr.referralDate";
        String orderDir = "desc";

        if (filter.getSortDir() != null) {
            String sortDir = filter.getSortDir().toString().toLowerCase();
            // Validate sort direction to prevent injection
            if ("asc".equals(sortDir) || "desc".equals(sortDir)) {
                orderDir = sortDir;
            }
        }

        // Sort mode determines the order by clause - these are controlled enums, not user input
        if (SORTMODE.AppointmentDate.equals(filter.getSortMode())) {
            orderBy = "cr.appointmentDate " + orderDir + ",cr.appointmentTime " + orderDir;
        } else if (SORTMODE.Demographic.equals(filter.getSortMode())) {
            orderBy = "d.LastName " + orderDir + ",d.FirstName " + orderDir;
        } else if (SORTMODE.Service.equals(filter.getSortMode())) {
            orderBy = "cs.serviceDesc " + orderDir;
        } else if (SORTMODE.Consultant.equals(filter.getSortMode())) {
            orderBy = "specialist.lastName " + orderDir + ",specialist.firstName " + orderDir;
        } else if (SORTMODE.Team.equals(filter.getSortMode())) {
            orderBy = "cr.sendTo " + orderDir;
        } else if (SORTMODE.Status.equals(filter.getSortMode())) {
            orderBy = "cr.status " + orderDir;
        } else if (SORTMODE.MRP.equals(filter.getSortMode())) {
            orderBy = "p.LastName " + orderDir + ",p.FirstName " + orderDir;
        } else if (SORTMODE.FollowUpDate.equals(filter.getSortMode())) {
            orderBy = "cr.followUpDate " + orderDir;
        } else if (SORTMODE.ReferralDate.equals(filter.getSortMode())) {
            orderBy = "cr.referralDate " + orderDir;
        } else if (SORTMODE.Urgency.equals(filter.getSortMode())) {
            orderBy = "cr.urgency " + orderDir;
        }

        orderBy = " ORDER BY " + orderBy;

        sql.append(orderBy);

        queryWithParams.sql = sql.toString();
        return queryWithParams;
    }

    private Calendar setCalender(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        return cal;
    }
}
