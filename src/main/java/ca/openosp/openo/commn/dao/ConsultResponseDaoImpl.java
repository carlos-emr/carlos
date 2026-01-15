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
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.ConsultationResponse;
import ca.openosp.openo.consultations.ConsultationRequestSearchFilter;
import ca.openosp.openo.consultations.ConsultationResponseSearchFilter;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.stereotype.Repository;

@Repository
public class ConsultResponseDaoImpl extends AbstractDaoImpl<ConsultationResponse> implements ConsultResponseDao {
    private Logger logger = MiscUtils.getLogger();

    public ConsultResponseDaoImpl() {
        super(ConsultationResponse.class);
    }

    public int getConsultationCount(ConsultationResponseSearchFilter filter) {
        String sql = getSearchQuery(filter, true);
        logger.debug("sql=" + sql);

        Query query = entityManager.createQuery(sql);
        setQueryParameters(query, filter);
        
        Long count = this.getCountResult(query);


        return count.intValue();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> search(ConsultationResponseSearchFilter filter) {
        String sql = this.getSearchQuery(filter, false);
        logger.debug("sql=" + sql);

        Query query = entityManager.createQuery(sql);
        setQueryParameters(query, filter);
        
        query.setFirstResult(filter.getStartIndex());
        query.setMaxResults(filter.getNumToReturn());
        return query.getResultList();
    }

    private String getSearchQuery(ConsultationResponseSearchFilter filter, boolean selectCountOnly) {
        StringBuilder sql = new StringBuilder(
                "select " + (selectCountOnly ? "count(*)" : "cr,sp,d,p") +
                        " from ConsultationResponse cr , ProfessionalSpecialist sp, Demographic d left outer join d.provider p" +
                        " where sp.id = cr.referringDocId and d.DemographicNo = cr.demographicNo ");

        if (filter.getAppointmentStartDate() != null) {
            sql.append("and cr.appointmentDate >= :appointmentStartDate ");
        }
        if (filter.getAppointmentEndDate() != null) {
            sql.append("and cr.appointmentDate <= :appointmentEndDate ");
        }
        if (filter.getReferralStartDate() != null) {
            sql.append("and cr.referralDate >= :referralStartDate ");
        }
        if (filter.getReferralEndDate() != null) {
            sql.append("and cr.referralDate <= :referralEndDate ");
        }
        if (filter.getResponseStartDate() != null) {
            sql.append("and cr.responseDate >= :responseStartDate ");
        }
        if (filter.getResponseEndDate() != null) {
            sql.append("and cr.responseDate <= :responseEndDate ");
        }
        if (filter.getStatus() != null) {
            sql.append("and cr.status = :status ");
        } else {
            sql.append("and cr.status!=4 and cr.status!=5 ");
        }
        if (StringUtils.isNotBlank(filter.getTeam())) {
            sql.append("and cr.sendTo = :team ");
        }
        if (StringUtils.isNotBlank(filter.getUrgency())) {
            sql.append("and cr.urgency = :urgency ");
        }
        if (filter.getDemographicNo() != null && filter.getDemographicNo() > 0) {
            sql.append("and cr.demographicNo = :demographicNo ");
        }
        if (filter.getMrpNo() != null && filter.getMrpNo() > 0) {
            sql.append("and d.ProviderNo = :mrpNo ");
        }

        // Apply safe ORDER BY clause construction
        String orderByClause = getSafeOrderByClause(filter);
        if (!selectCountOnly && orderByClause != null) {
            sql.append(" ORDER BY ").append(orderByClause);
        }

        return sql.toString();
    }
    
    private void setQueryParameters(Query query, ConsultationResponseSearchFilter filter) {
        if (filter.getAppointmentStartDate() != null) {
            query.setParameter("appointmentStartDate", filter.getAppointmentStartDate());
        }
        if (filter.getAppointmentEndDate() != null) {
            query.setParameter("appointmentEndDate", setCalender(filter.getAppointmentEndDate()).getTime());
        }
        if (filter.getReferralStartDate() != null) {
            query.setParameter("referralStartDate",filter.getReferralStartDate());
        }
        if (filter.getReferralEndDate() != null) {
            query.setParameter("referralEndDate", setCalender(filter.getReferralEndDate()).getTime());
        }
        if (filter.getResponseStartDate() != null) {
            query.setParameter("responseStartDate",filter.getResponseStartDate());
        }
        if (filter.getResponseEndDate() != null) {
            query.setParameter("responseEndDate", setCalender(filter.getResponseEndDate()).getTime());
        }
        if (filter.getStatus() != null) {
            query.setParameter("status", String.valueOf(filter.getStatus()));
        }
        if (StringUtils.isNotBlank(filter.getTeam())) {
            query.setParameter("team", filter.getTeam());
        }
        if (StringUtils.isNotBlank(filter.getUrgency())) {
            query.setParameter("urgency", filter.getUrgency());
        }
        if (filter.getDemographicNo() != null && filter.getDemographicNo() > 0) {
            query.setParameter("demographicNo", filter.getDemographicNo());
        }
        if (filter.getMrpNo() != null && filter.getMrpNo() > 0) {
            query.setParameter("mrpNo", filter.getMrpNo());
        }
    }
    
    private String getSafeOrderDirection(Object sortDir) {
        if (sortDir != null) {
            String dir = sortDir.toString().toLowerCase();
            if ("asc".equals(dir)) {
                return "asc";
            }
        }
        return "desc";
    }
    
    /**
     * Constructs a safe ORDER BY clause using a whitelist approach to prevent SQL injection.
     * Only allows predefined field combinations based on the SORTMODE enum.
     * 
     * @param filter the consultation response search filter
     * @return a safe ORDER BY clause string, or null if no sorting is needed
     */
    private String getSafeOrderByClause(ConsultationResponseSearchFilter filter) {
        String orderDir = getSafeOrderDirection(filter.getSortDir());
        
        // Use whitelist approach - only allow predefined field combinations
        if (filter.getSortMode() == null) {
            return "cr.referralDate " + orderDir;
        }
        
        switch (filter.getSortMode()) {
            case AppointmentDate:
                return "cr.appointmentDate " + orderDir + ",cr.appointmentTime " + orderDir;
            case Demographic:
                return "d.LastName " + orderDir + ",d.FirstName " + orderDir;
            case ReferringDoctor:
                return "sp.lastName " + orderDir + ",sp.firstName " + orderDir;
            case Team:
                return "cr.sendTo " + orderDir;
            case Status:
                return "cr.status " + orderDir;
            case Provider:
                return "p.LastName " + orderDir + ",p.FirstName " + orderDir;
            case FollowUpDate:
                return "cr.followUpDate " + orderDir;
            case ReferralDate:
                return "cr.referralDate " + orderDir;
            case ResponseDate:
                return "cr.responseDate " + orderDir;
            case Urgency:
                return "cr.urgency " + orderDir;
            default:
                // Default to referral date if unknown sort mode
                return "cr.referralDate " + orderDir;
        }
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
