/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.consultations;

import java.util.List;

import io.github.carlos_emr.carlos.commn.PaginationQuery;
import io.github.carlos_emr.carlos.commn.dao.ConsultRequestDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.log.LogAction;

/**
 * Service layer for consultation request operations including searching and counting.
 *
 * <p>Provides paginated access to consultation requests via {@link ConsultRequestDao},
 * with audit logging of all data access through {@link LogAction}. This service is
 * deprecated in favor of newer consultation management patterns.</p>
 *
 * @since 2026-03-17
 * @deprecated Use the updated consultation management APIs instead.
 */
@Component
@Deprecated
public class ConsultationService {
    @Autowired
    private ConsultRequestDao consultationDao;


    /**
     * Returns the total count of consultation requests matching the given query criteria.
     * Used for calculating pagination display in consultation list views.
     *
     * @param paginationQuery PaginationQuery the filter criteria for counting consultations
     * @return int the total number of matching consultation requests
     * @deprecated Use the updated consultation management APIs instead.
     */
    @Deprecated
    public int getConsultationCount(PaginationQuery paginationQuery) {
        return this.consultationDao.getConsultationCount(paginationQuery);
    }

    /**
     * Retrieves a paginated list of consultation requests matching the given query criteria.
     * Logs the IDs of all returned consultation requests for audit purposes.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context for audit logging
     * @param paginationQuery PaginationQuery the filter and pagination criteria, cast to {@link ConsultationQuery}
     * @return List&lt;ConsultationRequest&gt; the matching consultation requests, may be empty
     * @deprecated Use the updated consultation management APIs instead.
     */
    @Deprecated
    public List<ConsultationRequest> listConsultationRequests(LoggedInInfo loggedInInfo, PaginationQuery paginationQuery) {
        // Narrow to ConsultationQuery to access consultation-specific filter fields
        ConsultationQuery query = (ConsultationQuery) paginationQuery;

        List<ConsultationRequest> results = consultationDao.listConsultationRequests(query);
        // Log the IDs of returned results for audit trail compliance
        if (results.size() > 0) {
            String resultIds = ConsultationRequest.getIdsAsStringList(results);
            LogAction.addLogSynchronous(loggedInInfo, "ConsultationService.listConsultationRequests", "ids returned=" + resultIds);
        }


        return results;
    }
}
