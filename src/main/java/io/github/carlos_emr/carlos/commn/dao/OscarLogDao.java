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

import io.github.carlos_emr.carlos.commn.model.OscarLog;

public interface OscarLogDao extends AbstractDao<OscarLog> {

    public List<OscarLog> findByDemographicId(Integer demographicId);

    public List<OscarLog> findByProviderNo(String providerNo);

    public boolean hasRead(String providerNo, String content, String contentId);

    public List<OscarLog> findByActionAndData(String action, String data);

    public List<OscarLog> findByAction(String action, int start, int length, String orderBy, String orderByDirection);

    public List<OscarLog> findByActionContentAndDemographicId(String action, String content, Integer demographicId);

    public List<Integer> getDemographicIdsOpenedSinceTime(Date value);

    public List<Integer> getRecentDemographicsAccessedByProvider(String providerNo, int startPosition,
                                                                 int itemsToReturn);

    public List<Object[]> getRecentDemographicsViewedByProvider(String providerNo, int startPosition,
                                                                int itemsToReturn);

    public List<Object[]> getRecentDemographicsViewedByProviderAfterDateIncluded(String providerNo, Date date,
                                                                                 int startPosition, int itemsToReturn);

    /**
     * Finds audit log entries for the admin log report using the supplied filters.
     *
     * @param startDate Date inclusive lower bound for the log timestamp
     * @param endDate Date inclusive upper bound for the log timestamp
     * @param content String raw SQL LIKE parameter for the content column, bound exactly as supplied by the caller;
     *                plain values such as {@code admin} and {@code login} therefore behave as exact matches,
     *                while callers may pass wildcards such as {@code %}
     * @param providerNo String specific provider number to filter by, or {@code null} for all providers
     * @param siteProviderNos List<String> provider numbers allowed by site-access privacy, or {@code null} when unrestricted
     * @return List<OscarLog> matching log entries ordered by newest first
     */
    public List<OscarLog> findForReport(Date startDate, Date endDate, String content, String providerNo,
                                        List<String> siteProviderNos);

    public int purgeLogEntries(Date maxDateToRemove);

}
