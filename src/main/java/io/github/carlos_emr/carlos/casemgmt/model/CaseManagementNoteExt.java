/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.casemgmt.model;

import java.util.Date;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * Persistent entity representing extended key-value metadata associated with a
 * {@link CaseManagementNote}. Each extension stores a string key, an optional string value,
 * and an optional date value for a specific note.
 *
 * <p>Common key constants are defined as static fields (e.g., {@link #STARTDATE},
 * {@link #TREATMENT}, {@link #PROBLEMSTATUS}) and date-type keys follow the naming
 * convention "XXX Date".</p>
 *
 * @since 2026-03-17
 */
public class CaseManagementNoteExt {

    // Key Value constants ***All date value key must be in format "XXX Date"
    public static String STARTDATE = "Start Date";
    public static String RESOLUTIONDATE = "Resolution Date";
    public static String PROCEDUREDATE = "Procedure Date";

    public static String AGEATONSET = "Age at Onset";
    public static String TREATMENT = "Treatment";
    public static String PROBLEMSTATUS = "Problem Status";
    public static String EXPOSUREDETAIL = "Exposure Details";
    public static String RELATIONSHIP = "Relationship";
    public static String LIFESTAGE = "Life Stage";
    public static String HIDECPP = "Hide Cpp";
    public static String PROBLEMDESC = "Problem Description";
    public static String PROCEDURE = "Procedure";


    //Class fields
    private Long id;
    private Long noteId;
    private String keyVal;
    private String value;
    private Date dateValue;

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNoteId() {
        return this.noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public String getKeyVal() {
        return this.keyVal;
    }

    public void setKeyVal(String keyVal) {
        this.keyVal = keyVal;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Date getDateValue() {
        return this.dateValue;
    }

    /**
     * Returns the date value formatted as a "yyyy-MM-dd" string.
     *
     * @return String the formatted date string, or null if the date value is null
     */
    public String getDateValueStr() {
        return UtilDateUtilities.DateToString(this.dateValue, "yyyy-MM-dd");
    }

    public void setDateValue(Date dateValue) {
        this.dateValue = dateValue;
    }

    /**
     * Parses a date string and sets the date value. Supports multiple formats based on
     * string length: "yyyy-MM-dd" (8+ chars), "yyyy-MM" (6+ chars), or "yyyy" (4 chars).
     * Sets the date value to null if the string does not match any supported format.
     *
     * @param dateValue String the date string to parse
     */
    public void setDateValue(String dateValue) {
        if (dateValue.trim().length() >= 8) {
            setDateValue(UtilDateUtilities.StringToDate(dateValue, "yyyy-MM-dd"));
        } else if (dateValue.trim().length() >= 6) {
            setDateValue(UtilDateUtilities.StringToDate(dateValue, "yyyy-MM"));
        } else if (dateValue.trim().length() == 4) {
            setDateValue(UtilDateUtilities.StringToDate(dateValue, "yyyy"));
        } else {
            this.dateValue = null;
        }
    }
}
