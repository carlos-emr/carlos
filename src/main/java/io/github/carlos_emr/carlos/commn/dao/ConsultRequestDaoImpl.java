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

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.consultations.ConsultationRequestSearchFilter;
import io.github.carlos_emr.carlos.consultations.ConsultationRequestSearchFilter.SORTMODE;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Repository
public class ConsultRequestDaoImpl extends AbstractDaoImpl<ConsultationRequest> implements ConsultRequestDao {

    public ConsultRequestDaoImpl() {
        super(ConsultationRequest.class);
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

    @Override
    public ConsultationRequest findWithAssociations(Integer id) {
        Query query = entityManager.createQuery("""
                SELECT cr
                FROM ConsultationRequest cr
                LEFT JOIN FETCH cr.professionalSpecialist
                LEFT JOIN FETCH cr.demographicContact
                LEFT JOIN FETCH cr.lookupListItem
                WHERE cr.id = :id
                """);
        query.setParameter("id", id);
        List<ConsultationRequest> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private QueryWithParams buildSearchQuery(ConsultationRequestSearchFilter filter, boolean selectCountOnly) {
        QueryWithParams queryWithParams = new QueryWithParams();
        
        StringBuilder sql = new StringBuilder(
                "select " + (selectCountOnly ? "count(*)" : "cr,specialist,cs,d,p") +
                        " from ConsultationRequest cr left outer join cr.professionalSpecialist specialist, ConsultationServices cs, Demographic d"
                        +
                        " left outer join d.provider p where d.demographicNo = cr.demographicId and cs.id = cr.serviceId ");

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
            sql.append("and cr.status!='4' and cr.status!='5' and cr.status!='7' ");
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
            sql.append("and d.providerNo = :mrpNo ");
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
            orderBy = "d.lastName " + orderDir + ",d.firstName " + orderDir;
        } else if (SORTMODE.Service.equals(filter.getSortMode())) {
            orderBy = "cs.serviceDesc " + orderDir;
        } else if (SORTMODE.Consultant.equals(filter.getSortMode())) {
            orderBy = "specialist.lastName " + orderDir + ",specialist.firstName " + orderDir;
        } else if (SORTMODE.Team.equals(filter.getSortMode())) {
            orderBy = "cr.sendTo " + orderDir;
        } else if (SORTMODE.Status.equals(filter.getSortMode())) {
            orderBy = "cr.status " + orderDir;
        } else if (SORTMODE.MRP.equals(filter.getSortMode())) {
            orderBy = "p.lastName " + orderDir + ",p.firstName " + orderDir;
        } else if (SORTMODE.FollowUpDate.equals(filter.getSortMode())) {
            orderBy = "cr.followUpDate " + orderDir;
        } else if (SORTMODE.ReferralDate.equals(filter.getSortMode())) {
            orderBy = "cr.referralDate " + orderDir;
        } else if (SORTMODE.Urgency.equals(filter.getSortMode())) {
            orderBy = "cr.urgency " + orderDir;
        }
        // Skip ORDER BY for count queries - meaningless and causes SQL errors in strict mode
        if (!selectCountOnly) {
            orderBy = " ORDER BY " + orderBy;
            sql.append(orderBy);
        }
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
