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
package io.github.carlos_emr.carlos.managers;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerCategory;
import io.github.carlos_emr.carlos.commn.model.TicklerLink;
import io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest;
import io.github.carlos_emr.carlos.tickler.dto.TicklerListDTO;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;


public interface TicklerManager {

    public static String DEMOGRAPHIC_NAME = "demographic_name";
    public static String CREATOR = "creator";
    public static String SERVICE_DATE = "service_date";
    public static String CREATION_DATE = "creation_date";
    public static String PRIORITY = "priority";
    public static String TASK_ASSIGNED_TO = "task_assigned_to";
    public static String STATUS = "status";
    public static String SORT_ASC = "asc";
    public static String SORT_DESC = "desc";

    public List<TicklerCategory> getActiveTicklerCategories(LoggedInInfo loggedInInfo);

    public boolean validateTicklerIsValid(Tickler tickler);

    public boolean addTicklerLink(LoggedInInfo loggedInInfo, TicklerLink ticklerLink);

    public boolean addTickler(LoggedInInfo loggedInInfo, Tickler tickler);

    public boolean updateTickler(LoggedInInfo loggedInInfo, Tickler tickler);

    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter, String providerNo,
                                     String programId);

    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter);

    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter, int offset, int limit);

    public List<Tickler> getTicklerByLabId(LoggedInInfo loggedInInfo, int labId, Integer demoNo);

    public List<Tickler> getTicklerByLabIdAnyProvider(LoggedInInfo loggedInInfo, int labId, Integer demoNo);

    public List<Tickler> ticklerFacilityFiltering(LoggedInInfo loggedInInfo, List<Tickler> ticklers);

    public List<Tickler> filterTicklersByAccess(List<Tickler> ticklers, String providerNo, String programNo);

    public int getActiveTicklerCount(LoggedInInfo loggedInInfo, String providerNo);

    public int getActiveTicklerByDemoCount(LoggedInInfo loggedInInfo, Integer demographicNo);

    public int getNumTicklers(LoggedInInfo loggedInInfo, CustomFilter filter);

    public Tickler getTickler(LoggedInInfo loggedInInfo, String tickler_no);

    public Tickler getTickler(LoggedInInfo loggedInInfo, Integer id);

    public void addComment(LoggedInInfo loggedInInfo, Integer tickler_id, String provider, String message);

    public void reassign(LoggedInInfo loggedInInfo, Integer tickler_id, String provider, String task_assigned_to);

    public void updateStatus(LoggedInInfo loggedInInfo, Integer tickler_id, String provider, Tickler.STATUS status);

    public void sendNotification(LoggedInInfo loggedInInfo, Tickler t) throws IOException;

    public void completeTickler(LoggedInInfo loggedInInfo, Integer tickler_id, String provider);

    public void deleteTickler(LoggedInInfo loggedInInfo, Integer tickler_id, String provider);

    public void activateTickler(LoggedInInfo loggedInInfo, Integer tickler_id, String provider);

    public void resolveTicklersBySubstring(LoggedInInfo loggedInInfo, String providerNo, List<String> demographicIds,
                                           String remString);

    public List<CustomFilter> getCustomFilters();

    public List<CustomFilter> getCustomFilters(String provider_no);

    public List<CustomFilter> getCustomFilterWithShortCut(String providerNo);

    public CustomFilter getCustomFilter(String name);

    public CustomFilter getCustomFilter(String name, String providerNo);

    public CustomFilter getCustomFilterById(Integer id);

    public void saveCustomFilter(CustomFilter filter);

    public void deleteCustomFilter(String name);

    public void deleteCustomFilterById(Integer id);

    public void addTickler(String demographic_no, String message, Tickler.STATUS status, String service_date,
                           String creator, Tickler.PRIORITY priority, String task_assigned_to);

    public boolean hasTickler(String demographic, String task_assigned_to, String message);

    public void createTickler(String demoNo, String provNo, String message, String assignedTo);

    public void resolveTicklers(LoggedInInfo loggedInInfo, String providerNo, List<String> cdmPatientNos,
                                String remString);

    public List<Tickler> listTicklers(LoggedInInfo loggedInInfo, Integer demographicNo, Date beginDate, Date endDate);

    public List<Tickler> findActiveByDemographicNo(LoggedInInfo loggedInInfo, Integer demographicNo);

    public List<Tickler> search_tickler_bydemo(LoggedInInfo loggedInInfo, Integer demographicNo, String status,
                                               Date beginDate, Date endDate);

    public List<Tickler> search_tickler(LoggedInInfo loggedInInfo, Integer demographicNo, Date endDate);

    public List<TicklerTextSuggest> getActiveTextSuggestions(LoggedInInfo loggedInInfo);

    public List<TicklerTextSuggest> getAllTextSuggestions(LoggedInInfo loggedInInfo, int offset, int itemsToReturn);

    public List<Tickler> sortTicklerList(Boolean isSortAscending, String sortColumn, List<Tickler> ticklers);

    /**
     * Returns paginated TicklerListDTOs matching the filter criteria.
     *
     * @param loggedInInfo LoggedInInfo the current session info
     * @param filter CustomFilter the filter criteria
     * @param offset int the starting position for pagination (0-based)
     * @param limit int the maximum number of results, or &lt;= 0 for no limit
     * @return List&lt;TicklerListDTO&gt; matching ticklers with comments and links populated
     * @since 2026-01-30
     */
    public List<TicklerListDTO> getTicklerDTOs(LoggedInInfo loggedInInfo, CustomFilter filter, int offset, int limit);

    /**
     * Returns TicklerListDTOs matching the filter criteria with default pagination.
     *
     * @param loggedInInfo LoggedInInfo the current session info
     * @param filter CustomFilter the filter criteria
     * @return List&lt;TicklerListDTO&gt; matching ticklers with comments and links populated
     * @since 2026-01-30
     */
    public List<TicklerListDTO> getTicklerDTOs(LoggedInInfo loggedInInfo, CustomFilter filter);
}
