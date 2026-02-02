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

package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;

public interface ConsultationRequestDao extends AbstractDao<ConsultationRequest> {

    public static final int DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT = 100;

    int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff);

    int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff, String sendto);

    List<ConsultationRequest> getConsults(Integer demoNo);

    List<ConsultationRequest> getConsults(String team, boolean showCompleted, Date startDate, Date endDate, String orderby, String desc, String searchDate, Integer offset, Integer limit);

    List<ConsultationRequest> getConsultationsByStatus(Integer demographicNo, String status);

    ConsultationRequest getConsultation(Integer requestId);

    List<ConsultationRequest> getReferrals(String providerId, Date cutoffDate);

    List<Object[]> findRequests(Date timeLimit, String providerNo);

    List<ConsultationRequest> findRequestsByDemoNo(Integer demoId, Date cutoffDate);

    List<ConsultationRequest> findByDemographicAndService(Integer demographicNo, String serviceName);

    List<ConsultationRequest> findByDemographicAndServices(Integer demographicNo, List<String> serviceNameList);

    List<Integer> findNewConsultationsSinceDemoKey(String keyName);
}
