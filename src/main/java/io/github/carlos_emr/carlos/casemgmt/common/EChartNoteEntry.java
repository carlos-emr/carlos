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
package io.github.carlos_emr.carlos.casemgmt.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Represents a single entry in the electronic chart note list. Each entry encapsulates
 * metadata about a clinical note including its date, authoring provider, associated program,
 * role, type, and linked clinical issue identifiers.
 *
 * <p>Provides comparators for sorting entries by date in ascending or descending order.</p>
 *
 * @since 2026-03-17
 */
public class EChartNoteEntry {

    private Object id;
    private Date date;
    private String providerNo;
    private int programId;
    private String role;
    private String type;


    private List<Integer> issueIds = new ArrayList<Integer>();

    /**
     * Returns the unique identifier for this note entry.
     *
     * @return Object the note entry identifier
     */
    public Object getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this note entry.
     *
     * @param id Object the note entry identifier
     */
    public void setId(Object id) {
        this.id = id;
    }

    /**
     * Returns the date associated with this note entry.
     *
     * @return Date the note entry date
     */
    public Date getDate() {
        return date;
    }

    /**
     * Sets the date associated with this note entry.
     *
     * @param date Date the note entry date
     */
    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * Returns the provider number of the authoring healthcare provider.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number of the authoring healthcare provider.
     *
     * @param providerNo String the provider number
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Returns the role associated with this note entry.
     *
     * @return String the role name
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the role associated with this note entry.
     *
     * @param role String the role name
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Returns the list of clinical issue identifiers linked to this note entry.
     *
     * @return List&lt;Integer&gt; the issue IDs
     */
    public List<Integer> getIssueIds() {
        return issueIds;
    }

    /**
     * Sets the list of clinical issue identifiers linked to this note entry.
     *
     * @param issueIds List&lt;Integer&gt; the issue IDs
     */
    public void setIssueIds(List<Integer> issueIds) {
        this.issueIds = issueIds;
    }

    /**
     * Returns the program identifier associated with this note entry.
     *
     * @return int the program ID
     */
    public int getProgramId() {
        return programId;
    }

    /**
     * Sets the program identifier associated with this note entry.
     *
     * @param programId int the program ID
     */
    public void setProgramId(int programId) {
        this.programId = programId;
    }


    /**
     * Returns the type of this note entry (e.g., clinical note, document, lab result).
     *
     * @return String the note entry type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of this note entry.
     *
     * @param type String the note entry type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns a comparator that sorts note entries by date in ascending order.
     * Null entries or null dates are treated as equal (returns 0).
     *
     * @return Comparator&lt;EChartNoteEntry&gt; ascending date comparator
     */
    public static Comparator<EChartNoteEntry> getDateComparator() {
        return new Comparator<EChartNoteEntry>() {
            public int compare(EChartNoteEntry note1, EChartNoteEntry note2) {
                if (note1 == null || note2 == null) {
                    return 0;
                }

                if (note1.getDate() == null || note2.getDate() == null) {
                    MiscUtils.getLogger().warn("note date is null during compare" + note1.getId() + ":" + note2.getId());
                    return 0;
                }

                return note1.getDate().compareTo(note2.getDate());
            }
        };

    }

    /**
     * Returns a comparator that sorts note entries by date in descending order.
     * Null entries are treated as equal (returns 0).
     *
     * @return Comparator&lt;EChartNoteEntry&gt; descending date comparator
     */
    public static Comparator<EChartNoteEntry> getDateComparatorDesc() {
        return new Comparator<EChartNoteEntry>() {
            public int compare(EChartNoteEntry note1, EChartNoteEntry note2) {
                if (note1 == null || note2 == null) {
                    return 0;
                }

                return note2.getDate().compareTo(note1.getDate());
            }
        };

    }

    /**
     * Returns a string representation of this note entry in the format
     * "NoteEntry:type:id:date".
     *
     * @return String the string representation
     */
    public String toString() {
        return "NoteEntry:" + getType() + ":" + getId() + ":" + getDate();
    }
}
