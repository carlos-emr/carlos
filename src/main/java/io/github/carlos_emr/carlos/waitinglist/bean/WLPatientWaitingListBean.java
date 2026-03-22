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
 * Data transfer object representing a patient's entry on a waiting list.
 *
 * <p>Holds display-ready information about a patient's waiting list placement,
 * including their demographic number, position, associated waiting list name,
 * contact phone number, and the date they were placed on the list.</p>
 *
 * @since 2026-03-17
 */
public class WLPatientWaitingListBean {

    String demographicNo;
    String waitingList;
    String waitingListID;
    String position;
    String onListSince;
    String phoneNumber;
    String patientName;
    String note;

    /**
     * Constructs a bean with waiting list name information (used for patient-centric views).
     *
     * @param demographicNo String the patient demographic number
     * @param waitingListID String the unique identifier of the waiting list
     * @param waitingList   String the name of the waiting list
     * @param position      String the patient's position on the list
     * @param note          String any notes associated with this waiting list entry
     * @param onListSince   String the date the patient was added to the list
     */
    public WLPatientWaitingListBean(String demographicNo, String waitingListID, String waitingList, String position, String note, String onListSince) {
        this.demographicNo = demographicNo;
        this.waitingListID = waitingListID;
        this.waitingList = waitingList;
        this.note = note;
        this.position = position;
        this.onListSince = onListSince;
    }

    /**
     * Constructs a bean with patient contact information (used for list-centric views).
     *
     * @param demographicNo String the patient demographic number
     * @param waitingListID String the unique identifier of the waiting list
     * @param position      String the patient's position on the list
     * @param patientName   String the patient's full name
     * @param phoneNumber   String the patient's contact phone number
     * @param note          String any notes associated with this waiting list entry
     * @param onListSince   String the date the patient was added to the list
     */
    public WLPatientWaitingListBean(String demographicNo, String waitingListID, String position, String patientName, String phoneNumber, String note, String onListSince) {
        this.demographicNo = demographicNo;
        this.waitingListID = waitingListID;
        this.position = position;
        this.patientName = patientName;
        this.phoneNumber = phoneNumber;
        this.onListSince = onListSince;
        this.note = note;
    }

    /**
     * Returns the patient demographic number.
     *
     * @return String the demographic number
     */
    public String getDemographicNo() {
        return demographicNo;
    }

    /**
     * Returns the waiting list identifier.
     *
     * @return String the waiting list ID
     */
    public String getWaitingListID() {
        return waitingListID;
    }

    /**
     * Returns the name of the waiting list.
     *
     * @return String the waiting list name
     */
    public String getWaitingList() {
        return waitingList;
    }

    /**
     * Returns the patient's position on the waiting list.
     *
     * @return String the position number
     */
    public String getPosition() {
        return position;
    }

    /**
     * Returns the date the patient was placed on the waiting list.
     *
     * @return String the date in string format
     */
    public String getOnListSince() {
        return onListSince;
    }

    /**
     * Returns the patient's phone number.
     *
     * @return String the phone number, or {@code null} if not set
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Returns the patient's full name.
     *
     * @return String the patient name, or {@code null} if not set
     */
    public String getPatientName() {
        return patientName;
    }

    /**
     * Returns the note associated with this waiting list entry.
     *
     * @return String the note text
     */
    public String getNote() {
        return note;
    }
}
