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


package io.github.carlos_emr.carlos.waitinglist.bean;


/**
 * Data transfer object representing a waiting list name definition.
 *
 * <p>Holds the metadata for a waiting list including its name, associated
 * provider group, creating provider, and creation date. Used for display
 * in the waiting list name management UI.</p>
 *
 * @since 2026-03-17
 */
public class WLWaitingListNameBean {

    String waitingListName;
    String ID;
    String groupNo;
    String providerNo;
    String createdDate;

    /**
     * Constructs a waiting list name bean with all required fields.
     *
     * @param nameId          String the unique identifier of the waiting list name
     * @param waitingListName String the display name of the waiting list
     * @param groupNo         String the provider group number this list belongs to
     * @param providerNo      String the provider number who created this list
     * @param createdDate     String the date the waiting list was created
     */
    public WLWaitingListNameBean(String nameId, String waitingListName, String groupNo,
                                 String providerNo, String createdDate) {
        this.waitingListName = waitingListName;
        this.ID = nameId;
        this.groupNo = groupNo;
        this.providerNo = providerNo;
        this.createdDate = createdDate;
    }

    /**
     * Returns the display name of this waiting list.
     *
     * @return String the waiting list name
     */
    public String getWaitingListName() {
        return waitingListName;
    }

    /**
     * Returns the unique identifier of this waiting list name.
     *
     * @return String the waiting list name ID
     */
    public String getId() {
        return ID;
    }

    /**
     * Returns the provider group number associated with this waiting list.
     *
     * @return String the group number
     */
    public String getGroupNo() {
        return groupNo;
    }

    /**
     * Returns the provider number of the creator of this waiting list.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Returns the date this waiting list was created.
     *
     * @return String the creation date in string format
     */
    public String getCreatedDate() {
        return createdDate;
    }
}
