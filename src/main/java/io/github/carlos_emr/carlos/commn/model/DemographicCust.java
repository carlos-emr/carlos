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
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "demographiccust")
public class DemographicCust extends AbstractModel<Integer> {

    /** cust1 (nurse) is VARCHAR(255). */
    public static final int NURSE_MAX_LENGTH = 255;
    /** cust2 (resident) is VARCHAR(255). */
    public static final int RESIDENT_MAX_LENGTH = 255;
    /** cust3 (the patient alert) is VARCHAR(255). */
    public static final int ALERT_MAX_LENGTH = 255;
    /** cust4 (midwife) is VARCHAR(255). */
    public static final int MIDWIFE_MAX_LENGTH = 255;

    @Id
    @Column(name = "demographic_no")
    private Integer id;

    @Column(name = "cust1")
    private String nurse;

    @Column(name = "cust2")
    private String resident;

    @Column(name = "cust3")
    private String alert;

    @Column(name = "cust4")
    private String midwife;

    @Column(name = "content")
    private String notes;

    public Integer getId() {
        return id;
    }

    public String getNurse() {
        return nurse;
    }

    public void setNurse(String nurse) {
        this.nurse = nurse;
    }

    public String getResident() {
        return resident;
    }

    public void setResident(String resident) {
        this.resident = resident;
    }

    public String getAlert() {
        return alert;
    }

    public void setAlert(String alert) {
        this.alert = alert;
    }

    public String getMidwife() {
        return midwife;
    }

    public void setMidwife(String midwife) {
        this.midwife = midwife;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Checks the four VARCHAR(255) custom fields against their columns (issue #5869).
     *
     * <p>Packaged installs run MariaDB with an empty {@code sql_mode}, where a longer value is cut
     * to 255 characters with only a warning and the save still reports success. That silently
     * dropped the end of a long patient alert. The add, edit and REST saves call this before they
     * write anything and refuse the whole save when it returns errors. The notes ({@code content})
     * column is MEDIUMTEXT and is not checked here.</p>
     *
     * @return one message per field over its limit, naming the field and the limit but never the
     *         value; empty when every field fits
     */
    public List<String> validateFieldLengths() {
        List<String> errors = new ArrayList<>(validateAlertLength(alert));
        Demographic.addFieldLengthError(errors, "Nurse", nurse, NURSE_MAX_LENGTH);
        Demographic.addFieldLengthError(errors, "Resident", resident, RESIDENT_MAX_LENGTH);
        Demographic.addFieldLengthError(errors, "Midwife", midwife, MIDWIFE_MAX_LENGTH);
        return errors;
    }

    /**
     * The alert check of {@link #validateFieldLengths()} alone, for a writer that changes only the
     * alert, such as the Ontario OBEC eligibility apply (#6140).
     *
     * @param alert the alert as it would be stored; may be {@code null}
     * @return a message naming the field and the limit, never the value; empty when it fits
     */
    public static List<String> validateAlertLength(String alert) {
        List<String> errors = new ArrayList<>();
        Demographic.addFieldLengthError(errors, "Alert", alert, ALERT_MAX_LENGTH);
        return errors;
    }

    /** Legacy unotes is plain-text framing, not an XML document; nested tag text is clinical content. */
    public static String wrapPatientNotes(String value) {
        return "<unotes>" + (value == null ? "" : value) + "</unotes>";
    }

    /** Read the complete outer frame so a literal closing tag in the note never truncates it. */
    public static String patientNotesText(String stored) {
        if (stored == null) return "";
        if (stored.startsWith("<unotes>") && stored.endsWith("</unotes>"))
            return stored.substring(8, stored.length() - 9);
        int first = stored.indexOf("<unotes>");
        int last = first < 0 ? -1 : stored.indexOf("</unotes>", first + 8);
        if (first >= 0 && last >= first + 8) return stored.substring(first + 8, last);
        return stored;
    }

    public void setId(Integer id) {
        this.id = id;
    }


}
