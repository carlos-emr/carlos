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
import io.github.carlos_emr.carlos.consultation.dto.ConsultantOptionDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationListFilterDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationMrpOptionDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationRequestListItemDTO;

public interface ConsultationRequestDao extends AbstractDao<ConsultationRequest> {

    public static final int DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT = 100;

    int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff);

    int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff, String sendto);

    List<ConsultationRequest> getConsults(Integer demoNo);

    List<ConsultationRequest> getConsults(String team, boolean showCompleted, Date startDate, Date endDate, String orderby, String desc, String searchDate, Integer offset, Integer limit);

    /**
     * Returns one page of the Consultations list, applying every filter in {@code filter}.
     *
     * <p>The positional {@link #getConsults(String, boolean, Date, Date, String, String, String, Integer, Integer)}
     * delegates here with no consultant and no MRP filter.</p>
     *
     * @param filter ConsultationListFilterDto the list filters; must not be null
     * @return List of matching requests, at most {@code MAX_LIST_RETURN_SIZE} rows
     * @since 2026-09-30
     */
    List<ConsultationRequest> getConsults(ConsultationListFilterDto filter);

    /**
     * Searches the specialists that at least one consultation request was sent to, for the
     * Consultant type-ahead on the Consultations list.
     *
     * <p>The keyword is split on whitespace and commas; every token (up to four) must appear,
     * case-insensitively, somewhere in "last, first". So "Smith,B", "smith b" and "brian smith"
     * all find "Smith, Brian". LIKE wildcards in the keyword are matched literally.</p>
     *
     * @param keyword    String the text typed by the user; null or blank returns an empty list
     * @param maxResults int the maximum number of rows to return; values below 1 return an empty list
     * @return List of specialists ordered by last name, first name
     * @since 2026-09-30
     */
    List<ConsultantOptionDto> searchDistinctConsultants(String keyword, int maxResults);

    /**
     * Lists the distinct most responsible providers ({@code demographic.provider_no}) of patients
     * that have at least one consultation request, for the Provider filter on the Consultations list.
     *
     * @return List of providers ordered by last name, first name
     * @since 2026-09-30
     */
    List<ConsultationMrpOptionDto> findDistinctConsultMrps();

    List<ConsultationRequest> getConsultationsByStatus(Integer demographicNo, String status);

    ConsultationRequest getConsultation(Integer requestId);

    List<ConsultationRequest> getReferrals(String providerId, Date cutoffDate);

    List<Object[]> findRequests(Date timeLimit, String providerNo);

    List<ConsultationRequest> findRequestsByDemoNo(Integer demoId, Date cutoffDate);

    List<ConsultationRequest> findByDemographicAndService(Integer demographicNo, String serviceName);

    List<ConsultationRequest> findByDemographicAndServices(Integer demographicNo, List<String> serviceNameList);

    List<Integer> findNewConsultationsSinceDemoKey(String keyName);

    /**
     * Returns lightweight consultation request DTOs for a demographic, eliminating
     * 3 EAGER entity joins. Pre-joins specialist name.
     *
     * @param demographicId Integer the patient demographic number
     * @return List of ConsultationRequestListItemDTO
     * @since 2026-04-11
     */
    List<ConsultationRequestListItemDTO> findConsultationDTOsByDemographicId(Integer demographicId);
    ConsultationRequest lockForAttachmentSync(Integer id);

}
