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

package ca.openosp.openo.commn.dao;

import java.math.BigInteger;
import java.util.Date;
import java.util.List;

import javax.persistence.Query;

import org.apache.commons.lang3.time.DateFormatUtils;
import ca.openosp.openo.commn.NativeSql;
import ca.openosp.openo.commn.model.ConsultationRequest;

@SuppressWarnings("unchecked")
public class ConsultationRequestDaoImpl extends AbstractDaoImpl<ConsultationRequest> implements ConsultationRequestDao {

    public ConsultationRequestDaoImpl() {
        super(ConsultationRequest.class);
    }

    public int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff) {
        Query query = entityManager.createNativeQuery("select count(*) from consultationRequests where referalDate < ?1 and status != 4");
        query.setParameter(1, referralDateCutoff);

        return ((BigInteger) query.getSingleResult()).intValue();
    }

    public int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff, String sendto) {
        Query query = entityManager.createNativeQuery("select count(*) from consultationRequests where referalDate < ?1 and status != 4 and sendto = ?2");
        query.setParameter(1, referralDateCutoff);
        query.setParameter(2, sendto);

        return ((BigInteger) query.getSingleResult()).intValue();
    }

    public List<ConsultationRequest> getConsults(Integer demoNo) {
        StringBuilder sql = new StringBuilder("select cr from ConsultationRequest cr, Demographic d, Provider p where d.DemographicNo = cr.demographicId and p.ProviderNo = cr.providerNo and cr.demographicId = ?1");
        Query query = entityManager.createQuery(sql.toString());
        query.setParameter(1, demoNo);

        List<ConsultationRequest> results = query.getResultList();
        return results;
    }


    public List<ConsultationRequest> getConsults(String team, boolean showCompleted, Date startDate, Date endDate, String orderby, String desc, String searchDate, Integer offset, Integer limit) {

        	StringBuilder sql = new StringBuilder("SELECT cr " +
					"FROM ConsultationRequest cr " +
                    "LEFT JOIN cr.professionalSpecialist specialist " +
                    "LEFT JOIN ConsultationServices service ON cr.serviceId = service.serviceId " +
                    "LEFT JOIN ConsultationRequestExt ext ON cr.id = ext.requestId AND ext.key = 'ereferral_service' " +
					"LEFT JOIN Demographic d on cr.demographicId = d.DemographicNo " +
					"LEFT JOIN Provider p on d.ProviderNo = p.ProviderNo WHERE 1=1 ");

        if (!showCompleted) {
            sql.append("and cr.status != 4 ");
        }

        if (!team.isEmpty()) {
            sql.append("and cr.sendTo = '" + team + "' ");
        }

        if (startDate != null) {
            if (searchDate != null && searchDate.equals("1")) {
                sql.append("and cr.appointmentDate >= '" + DateFormatUtils.ISO_DATETIME_FORMAT.format(startDate) + "' ");
            } else {
                sql.append("and cr.referralDate >= '" + DateFormatUtils.ISO_DATETIME_FORMAT.format(startDate) + "' ");
            }
        }

        if (endDate != null) {
            if (searchDate != null && searchDate.equals("1")) {
                sql.append("and cr.appointmentDate <= '" + DateFormatUtils.ISO_DATETIME_FORMAT.format(endDate) + "' ");
            } else {
                sql.append("and cr.referralDate <= '" + DateFormatUtils.ISO_DATETIME_FORMAT.format(endDate) + "' ");
            }
        }

        String orderDesc = desc != null && desc.equals("1") ? "DESC" : "";
        String service = ", service.serviceDesc";
        if (orderby == null) {
            sql.append("order by cr.referralDate desc ");
        } else if (orderby.equals("1")) {               //1 = msgStatus
            sql.append("order by cr.status " + orderDesc + service);
        } else if (orderby.equals("2")) {               //2 = msgTeam
            sql.append("order by cr.sendTo " + orderDesc + service);
        } else if (orderby.equals("3")) {               //3 = msgPatient
            sql.append("order by d.LastName " + orderDesc + service);
        } else if (orderby.equals("4")) {               //4 = msgProvider
            sql.append("order by p.LastName " + orderDesc + service);
        } else if (orderby.equals("5")) {               //5 = msgService Desc
            sql.append("order by service.serviceDesc " + orderDesc);
        } else if (orderby.equals("6")) {               //6 = msgSpecialist Name
            sql.append("order by specialist.lastName " + orderDesc + service);
        } else if (orderby.equals("7")) {               //7 = msgRefDate
            sql.append("order by cr.referralDate " + orderDesc);
        } else if (orderby.equals("8")) {               //8 = Appointment Date
            sql.append("order by cr.appointmentDate " + orderDesc);
        } else if (orderby.equals("9")) {               //9 = FollowUp Date
            sql.append("order by cr.followUpDate " + orderDesc);
        } else {
            sql.append("order by cr.referralDate desc");
        }


        Query query = entityManager.createQuery(sql.toString());
        query.setFirstResult(offset != null ? offset : 0);

        //need to never send more than MAX_LIST_RETURN_SIZE
        int myLimit = limit != null ? limit : DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT;
        query.setMaxResults(Math.min(myLimit, MAX_LIST_RETURN_SIZE));

        return query.getResultList();
    }


    public List<ConsultationRequest> getConsultationsByStatus(Integer demographicNo, String status) {
        Query query = entityManager.createQuery("SELECT c FROM ConsultationRequest c where c.demographicId = ?1 and c.status = ?2");
        query.setParameter(1, demographicNo);
        query.setParameter(2, status);


        List<ConsultationRequest> results = query.getResultList();
        return results;
    }

    public ConsultationRequest getConsultation(Integer requestId) {
        return this.find(requestId);
    }


    public List<ConsultationRequest> getReferrals(String providerId, Date cutoffDate) {
        Query query = createQuery("cr", "cr.referralDate <= ?1 AND cr.status = '1' and cr.providerNo = ?2");
        query.setParameter(1, cutoffDate);
        query.setParameter(2, providerId);
        return query.getResultList();
    }

    public List<Object[]> findRequests(Date timeLimit, String providerNo) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT d.LastName, c.demographicId FROM ConsultationRequest c, Demographic d " +
                "WHERE c.referralDate >= ?1" +
                "AND c.demographicId = d.DemographicNo");
        if (providerNo != null) {
            sql.append(" AND d.ProviderNo = ?2");
        }
        sql.append(" ORDER BY d.LastName");

        Query query = entityManager.createQuery(sql.toString());
        query.setParameter(1, timeLimit);
        if (providerNo != null) {
            query.setParameter(2, providerNo);
        }
        return query.getResultList();
    }

    public List<ConsultationRequest> findRequestsByDemoNo(Integer demoId, Date cutoffDate) {
        Query query = createQuery("cr", "cr.referralDate <= ?1 AND cr.demographicId = ?2");
        query.setParameter(1, cutoffDate);
        query.setParameter(2, demoId);
        return query.getResultList();
    }

    public List<ConsultationRequest> findByDemographicAndService(Integer demographicNo, String serviceName) {
        String sql = "SELECT cr FROM ConsultationRequest cr, ConsultationServices cs WHERE cr.serviceId = cs.serviceId and cr.demographicId = ?1 and cs.serviceDesc = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, serviceName);

        return query.getResultList();
    }

    public List<ConsultationRequest> findByDemographicAndServices(Integer demographicNo, List<String> serviceNameList) {
        String sql = "SELECT cr FROM ConsultationRequest cr, ConsultationServices cs WHERE cr.serviceId = cs.serviceId and cr.demographicId = ?1 and cs.serviceDesc IN (?2)";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, serviceNameList);

        return query.getResultList();
    }

    @NativeSql("consultationRequests")
    public List<Integer> findNewConsultationsSinceDemoKey(String keyName) {

        String sql = "select distinct dr.demographicNo from consultationRequests dr,demographic d,demographicExt e where dr.demographicNo = d.demographic_no and d.demographic_no = e.demographic_no and e.key_val=?1 and dr.lastUpdateDate > e.value";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, keyName);
        return query.getResultList();
    }
}
