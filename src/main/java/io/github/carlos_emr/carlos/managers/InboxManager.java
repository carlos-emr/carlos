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

import io.github.carlos_emr.carlos.inbox.InboxManagerQuery;
import io.github.carlos_emr.carlos.inbox.InboxManagerResponse;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Service interface for querying the provider inbox in the CARLOS EMR system.
 *
 * <p>The inbox aggregates lab results and documents for a healthcare provider,
 * supporting filtering by result status (normal, abnormal) and document type.</p>
 *
 * @see InboxManagerImpl
 * @see io.github.carlos_emr.carlos.inbox.InboxManagerQuery
 * @see io.github.carlos_emr.carlos.inbox.InboxManagerResponse
 * @since 2026-03-17
 */
public interface InboxManager {
    /** Filter constant for normal results. */
    public static final String NORMAL = "normal";
    /** Filter constant for all results. */
    public static final String ALL = "all";
    /** Filter constant for abnormal results. */
    public static final String ABNORMAL = "abnormal";
    /** Filter constant for lab results only. */
    public static final String LABS = "labs";
    /** Filter constant for documents only. */
    public static final String DOCUMENTS = "documents";

    /**
     * Queries the provider inbox based on the specified filter criteria.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param query InboxManagerQuery the search and filter parameters
     * @return InboxManagerResponse containing the matching inbox items
     */
    public InboxManagerResponse getInboxResults(LoggedInInfo loggedInInfo, InboxManagerQuery query);
}


