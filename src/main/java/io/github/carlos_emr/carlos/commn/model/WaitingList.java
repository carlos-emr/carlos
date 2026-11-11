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


package io.github.carlos_emr.carlos.commn.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "waitingList")
public class WaitingList extends AbstractModel<Integer> {

    public static final String IS_HISTORY_YES = "Y";
    public static final String IS_HISTORY_NO = "N";

    /** waitingList.note is VARCHAR(255). */
    public static final int NOTE_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "listID")
    private int listId;

    @Column(name = "demographic_no")
    private int demographicNo;

    private String note;

    private long position;

    @Temporal(TemporalType.TIMESTAMP)
    private Date onListSince;

    @Column(name = "is_history")
    private String isHistory;

    /**
     * Checks a waiting-list note against its VARCHAR(255) column before it is written (issue #5869).
     *
     * <p>Static because the edit and REST saves hand the note to
     * {@code WLWaitingListUtil.updateWaitingListRecord} as a string, without building an entity
     * first. Under an empty {@code sql_mode} a longer note was cut to 255 characters with only a
     * warning; callers now refuse the save instead.</p>
     *
     * @param note the note as submitted, or {@code null}
     * @return one message naming the field and the limit (never the note) when it is too long;
     *         otherwise empty
     */
    public static List<String> validateNoteLength(String note) {
        List<String> errors = new ArrayList<>();
        Demographic.addFieldLengthError(errors, "Waiting list note", note, NOTE_MAX_LENGTH);
        return errors;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getListId() {
        return listId;
    }

    public void setListId(int listId) {
        this.listId = listId;
    }

    public int getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(int demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public Date getOnListSince() {
        return onListSince;
    }

    public void setOnListSince(Date onListSince) {
        this.onListSince = onListSince;
    }

    public String getIsHistory() {
        return isHistory;
    }

    public void setIsHistory(String isHistory) {
        this.isHistory = isHistory;
    }

    public void setHistory(boolean isHistory) {
        setIsHistory(isHistory ? IS_HISTORY_YES : IS_HISTORY_NO);
    }

}
