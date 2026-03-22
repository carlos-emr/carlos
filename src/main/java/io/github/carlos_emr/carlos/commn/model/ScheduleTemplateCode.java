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

import java.util.Comparator;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity representing an appointment type code used in schedule templates.
 *
 * <p>Maps to the {@code scheduletemplatecode} table. Each code is a single character
 * that appears in a {@link ScheduleTemplate}'s timecode string. The code defines
 * the type of appointment slot (e.g., regular visit, phone call, procedure),
 * along with its display color, duration, booking limit, and confirmation requirements.</p>
 *
 * @since 2026-03-17
 */
@Entity
@Table(name = "scheduletemplatecode")
public class ScheduleTemplateCode extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Character code;
    private String description;
    private String duration;
    private String color;
    private String confirm;
    private int bookinglimit;

    /**
     * Gets the unique identifier.
     *
     * @return Integer the auto-generated primary key
     */
    @Override
    public Integer getId() {
        return id;
    }

    /**
     * Gets the human-readable description of this schedule code.
     *
     * @return String the code description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the human-readable description of this schedule code.
     *
     * @param description String the code description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the duration associated with this code.
     *
     * @return String the duration value
     */
    public String getDuration() {
        return duration;
    }

    /**
     * Sets the duration associated with this code.
     *
     * @param duration String the duration value to set
     */
    public void setDuration(String duration) {
        this.duration = duration;
    }

    /**
     * Gets the display color for this schedule code in the UI.
     *
     * @return String the color value (e.g., hex color code)
     */
    public String getColor() {
        return color;
    }

    /**
     * Sets the display color for this schedule code.
     *
     * @param color String the color value to set
     */
    public void setColor(String color) {
        this.color = color;
    }

    /**
     * Gets the confirmation flag for appointments of this type.
     *
     * @return String the confirmation setting
     */
    public String getConfirm() {
        return confirm;
    }

    /**
     * Sets the confirmation flag for appointments of this type.
     *
     * @param confirm String the confirmation setting to set
     */
    public void setConfirm(String confirm) {
        this.confirm = confirm;
    }

    /**
     * Gets the maximum number of bookings allowed for this slot type.
     *
     * @return int the booking limit
     */
    public int getBookinglimit() {
        return bookinglimit;
    }

    /**
     * Sets the maximum number of bookings allowed for this slot type.
     *
     * @param bookinglimit int the booking limit to set
     */
    public void setBookinglimit(int bookinglimit) {
        this.bookinglimit = bookinglimit;
    }

    /**
     * Gets the single-character code used in schedule template timecodes.
     *
     * @return Character the template code character
     */
    public Character getCode() {
        return code;
    }

    /**
     * Sets the single-character code used in schedule template timecodes.
     *
     * @param code Character the template code character to set
     */
    public void setCode(Character code) {
        this.code = code;
    }

    /** Comparator that orders {@link ScheduleTemplateCode} instances by their code character. */
    public static final Comparator<ScheduleTemplateCode> CodeComparator = new Comparator<ScheduleTemplateCode>() {
        public int compare(ScheduleTemplateCode o1, ScheduleTemplateCode o2) {
            return o1.getCode().compareTo(o2.getCode());
        }
    };
}
