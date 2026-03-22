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


/**
 * Persistent entity representing a link between a {@link CaseManagementNote} and another
 * clinical data entity such as a prescription, allergy, lab test, document, eForm,
 * prevention, tickler, or appointment.
 *
 * <p>The {@code tableName} field uses integer constants (e.g., {@link #DRUGS},
 * {@link #ALLERGIES}, {@link #LABTEST}) to identify the linked table type, and
 * {@code tableId} holds the primary key of the linked record. Display name constants
 * (e.g., {@link #DISP_PRESCRIP}) provide human-readable labels for each link type.</p>
 *
 * @since 2026-03-17
 */
public class CaseManagementNoteLink {

    // Table Name constants
    public static final Integer CASEMGMTNOTE = 1;
    public static final Integer DRUGS = 2;
    public static final Integer ALLERGIES = 3;
    public static final Integer LABTEST = 4; //represents the hl7TextMessage table
    public static final Integer DOCUMENT = 5;
    public static final Integer EFORMDATA = 6;
    public static final Integer DEMOGRAPHIC = 7;
    public static final Integer PREVENTIONS = 8;
    public static final Integer LABTEST2 = 9; //repesents the labPatientPhysicianInfo table
    public static final Integer TICKLER = 10;
    public static final Integer APPOINTMENT = 11;
    public static final Integer EMAIL = 12;

    public static final String DISP_PRESCRIP = "Prescriptions";
    public static final String DISP_ALLERGY = "Allergies";
    public static final String DISP_LABTEST = "Lab Reports";
    public static final String DISP_LABTEST2 = "Lab Reports_";
    public static final String DISP_DOCUMENT = "Documents";
    public static final String DISP_DEMO = "Patient Record";
    public static final String DISP_PREV = "Preventions";
    public static final String DISP_TICKLER = "Ticklers";
    public static final String DISP_APPOINTMENT = "Appointments";
    public static final String DISP_EMAIL = "Email Logs";

    private Long id;
    private Integer tableName;
    private Long tableId;
    private Long noteId;
    private String otherId;

    /**
     * Default constructor.
     */
    public CaseManagementNoteLink() {
    }

    /**
     * Constructs a note link with the specified linked table type, table record ID, and note ID.
     *
     * @param tableName Integer the linked table type constant (e.g., {@link #DRUGS}, {@link #DOCUMENT})
     * @param tableId Long the primary key of the linked record
     * @param noteId Long the ID of the associated case management note
     */
    public CaseManagementNoteLink(Integer tableName, Long tableId, Long noteId) {
        this.tableName = tableName;
        this.tableId = tableId;
        this.noteId = noteId;
    }


    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getTableName() {
        return this.tableName;
    }

    public void setTableName(Integer tableName) {
        this.tableName = tableName;
    }

    public Long getTableId() {
        return this.tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public Long getNoteId() {
        return this.noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    /**
     * Sets the note ID from an Integer value, converting to Long.
     *
     * @param noteId Integer the note ID to convert and set
     */
    public void setNoteId(Integer noteId) {
        setNoteId(noteId.longValue());
    }

    public String getOtherId() {
        return otherId;
    }

    public void setOtherId(String otherId) {
        this.otherId = otherId;
    }


}
