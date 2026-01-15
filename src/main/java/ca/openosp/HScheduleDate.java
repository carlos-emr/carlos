//CHECKSTYLE:OFF
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
 */


package ca.openosp;


/**
 * Bean representing a schedule date entry with availability and hour information.
 * 
 * <p>This bean stores schedule information for a specific date including:</p>
 * <ul>
 *   <li>Availability status</li>
 *   <li>Priority level</li>
 *   <li>Reason for schedule entry (e.g., holiday, appointment)</li>
 *   <li>Hour specification</li>
 *   <li>Creator identifier</li>
 * </ul>
 * 
 * <p>Used in conjunction with schedule management to track provider
 * availability on specific dates.</p>
 */
public class HScheduleDate {

    /** Availability status for this schedule date */
    public String available = "";
    /** Priority level */
    public String priority = "";
    /** Reason for this schedule entry */
    public String reason = "";
    /** Hour specification */
    public String hour = "";
    /** Creator identifier */
    public String creator = "";

    /**
     * Constructs a new empty HScheduleDate.
     */
    public HScheduleDate() {
    }

    /**
     * Constructs a new HScheduleDate with all properties.
     * 
     * @param available1 the availability status
     * @param priority1 the priority level
     * @param reason1 the reason for this schedule entry
     * @param hour1 the hour specification
     * @param creator1 the creator identifier
     */
    public HScheduleDate(String available1, String priority1, String reason1, String hour1, String creator1) {
        available = available1;
        priority = priority1;
        reason = reason1;
        hour = hour1;
        creator = creator1;
    }

    /**
     * Sets all schedule date properties.
     * 
     * @param available1 the availability status
     * @param priority1 the priority level
     * @param reason1 the reason for this schedule entry
     * @param hour1 the hour specification
     * @param creator1 the creator identifier
     */
    public void setHScheduleDate(String available1, String priority1, String reason1, String hour1, String creator1) {
        available = available1;
        priority = priority1;
        reason = reason1;
        hour = hour1;
        creator = creator1;
    }
}
