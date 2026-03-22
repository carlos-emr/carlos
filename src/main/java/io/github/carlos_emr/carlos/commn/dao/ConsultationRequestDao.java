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

package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;

/**
 * DAO interface for consultation and referral operations.
 *
 * @since 2001
 */

public interface ConsultationRequestDao extends AbstractDao<ConsultationRequest> {

    public static final int DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT = 100;

    /**
     * Get Count Referrals After Cut Off Date And Not Completed.
     *
     * @param referralDateCutoff Date the referralDateCutoff
     * @return int
     */
    int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff);

    /**
     * Get Count Referrals After Cut Off Date And Not Completed.
     *
     * @param referralDateCutoff Date the referralDateCutoff
     * @param sendto String the sendto
     * @return int
     */
    int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff, String sendto);

    /**
     * Get Consults.
     *
     * @param demoNo Integer the demoNo
     * @return List<ConsultationRequest>
     */
    List<ConsultationRequest> getConsults(Integer demoNo);

    /**
     * Get Consults.
     *
     * @param team String the team
     * @param showCompleted boolean the showCompleted
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @param orderby String the orderby
     * @param desc String the desc
     * @param searchDate String the searchDate
     * @param offset Integer the offset
     * @param limit Integer the limit
     * @return List<ConsultationRequest>
     */
    List<ConsultationRequest> getConsults(String team, boolean showCompleted, Date startDate, Date endDate, String orderby, String desc, String searchDate, Integer offset, Integer limit);

    /**
     * Get Consultations By Status.
     *
     * @param demographicNo Integer the demographicNo
     * @param status String the status
     * @return List<ConsultationRequest>
     */
    List<ConsultationRequest> getConsultationsByStatus(Integer demographicNo, String status);

    /**
     * Get Consultation.
     *
     * @param requestId Integer the requestId
     * @return ConsultationRequest
     */
    ConsultationRequest getConsultation(Integer requestId);

    /**
     * Get Referrals.
     *
     * @param providerId String the providerId
     * @param cutoffDate Date the cutoffDate
     * @return List<ConsultationRequest>
     */
    List<ConsultationRequest> getReferrals(String providerId, Date cutoffDate);

    /**
     * Find Requests.
     *
     * @param timeLimit Date the timeLimit
     * @param providerNo String the providerNo
     * @return List<Object[]>
     */
    List<Object[]> findRequests(Date timeLimit, String providerNo);

    /**
     * Find Requests By Demo No.
     *
     * @param demoId Integer the demoId
     * @param cutoffDate Date the cutoffDate
     * @return List<ConsultationRequest>
     */
    List<ConsultationRequest> findRequestsByDemoNo(Integer demoId, Date cutoffDate);

    /**
     * Find By Demographic And Service.
     *
     * @param demographicNo Integer the demographicNo
     * @param serviceName String the serviceName
     * @return List<ConsultationRequest>
     */
    List<ConsultationRequest> findByDemographicAndService(Integer demographicNo, String serviceName);

    /**
     * Find By Demographic And Services.
     *
     * @param demographicNo Integer the demographicNo
     * @param serviceNameList List<String> the serviceNameList
     * @return List<ConsultationRequest>
     */
    List<ConsultationRequest> findByDemographicAndServices(Integer demographicNo, List<String> serviceNameList);

    /**
     * Find New Consultations Since Demo Key.
     *
     * @param keyName String the keyName
     * @return List<Integer>
     */
    List<Integer> findNewConsultationsSinceDemoKey(String keyName);
}
