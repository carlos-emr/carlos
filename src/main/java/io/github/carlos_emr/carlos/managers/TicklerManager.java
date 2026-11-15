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

    /**
     * Retrieves a list of active tickler categories for the given logged-in user.
     */
    public List<TicklerCategory> getActiveTicklerCategories(LoggedInInfo loggedInInfo);

    /**
     * Validates the given tickler.
     */
    public boolean validateTicklerIsValid(Tickler tickler);

    public boolean addTickler(LoggedInInfo loggedInInfo, Tickler tickler);

    /**
     * Updates the specified tickler for the given logged-in user.
     */
    public boolean updateTickler(LoggedInInfo loggedInInfo, Tickler tickler);

    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter, String providerNo,
                                     String programId);

    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter);

    /**
     * Retrieves a list of ticklers based on the provided parameters.
     */
    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter, int offset, int limit);

    /**
     * Retrieves ticklers with optional relationship expansion for detached rendering/conversion paths.
     *
     * <p>{@code includeProvider} and {@code includeAssignee} fetch the creator and assignee
     * relationships for JSP rendering. {@code includeComments} and {@code includeUpdates}
     * initialize those collections before the entities leave the DAO transaction.</p>
     */
    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter, int offset, int limit,
                                     boolean includeComments, boolean includeUpdates, boolean includeProvider,
                                     boolean includeAssignee);

    public List<Tickler> getTicklerByLabId(LoggedInInfo loggedInInfo, int labId, Integer demoNo);

    /**
     * Retrieves a list of Tickler objects by lab ID for any provider.
     */
    public List<Tickler> getTicklerByLabIdAnyProvider(LoggedInInfo loggedInInfo, int labId, Integer demoNo);

    /**
     * Filters ticklers based on the provided facility information.
     */
    public List<Tickler> ticklerFacilityFiltering(LoggedInInfo loggedInInfo, List<Tickler> ticklers);

    /**
     * Filters ticklers based on access criteria.
     */
    public List<Tickler> filterTicklersByAccess(List<Tickler> ticklers, String providerNo, String programNo);

    /**
     * Returns the count of active ticklers for the given provider.
     */
    public int getActiveTicklerCount(LoggedInInfo loggedInInfo, String providerNo);

    /**
     * Retrieves the count of active ticklers based on the demographic number.
     */
    public int getActiveTicklerByDemoCount(LoggedInInfo loggedInInfo, Integer demographicNo);

    public int getNumTicklers(LoggedInInfo loggedInInfo, CustomFilter filter);

    /**
     * Returns the count of ticklers matching the filter criteria.
     */
    public int getNumTicklersFiltered(LoggedInInfo loggedInInfo, CustomFilter filter);

    public Tickler getTickler(LoggedInInfo loggedInInfo, String tickler_no);

    public Tickler getTickler(LoggedInInfo loggedInInfo, Integer id);

    /**
     * Resolves the patient for note authorization without loading editable demographic entities.
     * The tickler read privilege and existing lookup audit apply before disclosure.
     *
     * @param loggedInInfo LoggedInInfo current authenticated user
     * @param id Integer tickler identifier
     * @return Integer patient identifier, or {@code null} when the tickler is missing or unassigned
     * @since 2026-10-24
     */
    public Integer getTicklerDemographicNo(LoggedInInfo loggedInInfo, Integer id);

    /**
     * Requires an existing transaction and update authority, without adding a read prerequisite.
     * Locks the row and returns its stored patient. A legacy tickler stored without a patient
     * (0 or {@code null}) is returned as stored and is governed by the global right alone.
     */
    default Integer getTicklerDemographicNoForUpdate(LoggedInInfo actor, Integer id) {
        throw new UnsupportedOperationException("Tickler patient locking is not implemented");
    }

    public void addComment(LoggedInInfo loggedInInfo, Integer tickler_id, String provider, String message);

    public void reassign(LoggedInInfo loggedInInfo, Integer tickler_id, String provider, String task_assigned_to);

    public void updateStatus(LoggedInInfo loggedInInfo, Integer tickler_id, String provider, Tickler.STATUS status);

    /** What {@link #updateStatusIfCurrent} did with one tickler. */
    enum StatusChange {
        /** The status was changed and a history row written. */
        APPLIED,
        /** The tickler already had the requested status: nothing was written. */
        UNCHANGED,
        /** Someone changed the status after the caller's page was rendered: nothing was written. */
        STALE
    }

    /**
     * Changes a tickler's status only if nobody changed it since the caller's page was rendered.
     * The task lists' bulk Complete and Delete use it: a list loaded before another user completed
     * or deleted a task must not write its own status over that (a Deleted task used to come back
     * as Completed). The row is locked, its committed status is compared with
     * {@code renderedStatus}, and the change is applied in the same transaction.
     *
     * <p>Without a rendered status (an older page, a hand-built request) the change is applied as
     * {@link #updateStatus} would, except that a Deleted task is never changed to Completed.</p>
     *
     * @param loggedInInfo LoggedInInfo the acting user
     * @param ticklerId Integer the task
     * @param provider String the provider recorded in the status history
     * @param renderedStatus Tickler.STATUS the status the caller's page showed for the task, or {@code null} when unknown
     * @param status Tickler.STATUS the status to set
     * @return StatusChange what was done; {@link StatusChange#STALE} means the task was left as it is
     * @throws jakarta.ws.rs.NotFoundException when there is no such tickler
     * @since 2026-11-12
     */
    default StatusChange updateStatusIfCurrent(LoggedInInfo loggedInInfo, Integer ticklerId, String provider,
                                               Tickler.STATUS renderedStatus, Tickler.STATUS status) {
        throw new UnsupportedOperationException("Conditional tickler status changes are not implemented");
    }

    /**
     * Locks a tickler for an edit and returns it as currently stored, with its comments and
     * history. Requires an existing transaction (the lock is held until it ends) and update
     * authority, including the patient-specific rule when the tickler has a patient. Call it
     * before anything in the transaction changes the tickler: the row is read again under the
     * lock, which would discard unsaved changes to an already loaded instance.
     *
     * @param loggedInInfo LoggedInInfo the acting user
     * @param id Integer the task
     * @return Tickler the locked tickler, or {@code null} when there is no such tickler
     * @since 2026-11-12
     */
    default Tickler getTicklerForUpdate(LoggedInInfo loggedInInfo, Integer id) {
        throw new UnsupportedOperationException("Tickler edit locking is not implemented");
    }

    public void sendNotification(LoggedInInfo loggedInInfo, Tickler t) throws IOException;

    public void completeTickler(LoggedInInfo loggedInInfo, Integer tickler_id, String provider);

    /**
     * Marks the tickler Deleted while retaining the task, comments, links, attachments and
     * status history as part of the clinical record.
     *
     * @param loggedInInfo LoggedInInfo the acting user
     * @param tickler_id Integer the task to mark Deleted
     * @param provider String the provider recording the status change
     */
    public void deleteTickler(LoggedInInfo loggedInInfo, Integer tickler_id, String provider);

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

    /**
     * Returns paginated tickler data as lightweight DTOs for server-side DataTables.
     *
     * @param loggedInInfo LoggedInInfo the logged in user info
     * @param filter CustomFilter the filter criteria
     * @param offset int the starting position for pagination
     * @param limit int the maximum number of results
     * @return List of TicklerListDTO matching the filter criteria
     * @since 2026-02-27
     */
    List<TicklerListDTO> getTicklerDTOs(LoggedInInfo loggedInInfo, CustomFilter filter, int offset, int limit);

    /**
     * Returns all tickler data as lightweight DTOs, limited to MAX_LIST_RETURN_SIZE.
     *
     * @param loggedInInfo LoggedInInfo the logged in user info
     * @param filter CustomFilter the filter criteria
     * @return List of TicklerListDTO matching the filter criteria
     * @since 2026-02-27
     */
    List<TicklerListDTO> getTicklerDTOs(LoggedInInfo loggedInInfo, CustomFilter filter);
}
