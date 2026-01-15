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
 * Bean representing a holiday entry in the schedule system.
 * 
 * <p>This simple bean stores holiday information for schedule management.
 * Holidays are used to mark days when the clinic or provider is unavailable.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * HScheduleHoliday holiday = new HScheduleHoliday("Christmas Day");
 * </pre>
 */
public class HScheduleHoliday {
    
    /** Name of the holiday */
    public String holiday_name = "";

    /**
     * Constructs a new empty HScheduleHoliday.
     */
    public HScheduleHoliday() {
    }

    /**
     * Constructs a new HScheduleHoliday with the specified holiday name.
     * 
     * @param holiday_name1 the name of the holiday
     */
    public HScheduleHoliday(String holiday_name1) {
        holiday_name = holiday_name1;
    }

    /**
     * Sets the holiday name.
     * 
     * @param holiday_name1 the name of the holiday
     */
    public void setHScheduleHoliday(String holiday_name1) {
        holiday_name = holiday_name1;
    }
}
