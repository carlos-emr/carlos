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

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * JPA entity representing a recurring schedule entry for a healthcare provider.
 *
 * <p>Maps to the {@code rschedule} table and stores provider availability
 * information including date ranges, day-of-week specifications, and
 * available hour configurations. Supports alternating week schedules
 * through separate {@code availHour} and {@code availHourB} fields.</p>
 *
 * @since 2026-03-17
 */
@Entity
@Table(name = "rschedule")
public class RSchedule extends AbstractModel<Integer> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "provider_no")
    private String providerNo;

    @Temporal(TemporalType.DATE)
    private Date sDate;

    @Temporal(TemporalType.DATE)
    private Date eDate;

    private String available;

    @Column(name = "day_of_week")
    private String dayOfWeek;

    @Column(name = "avail_hour")
    private String availHour;

    @Column(name = "avail_hourB")
    private String availHourB;

    private String creator;

    private String status;

    /**
     * Gets the unique identifier for this schedule entry.
     *
     * @return Integer the auto-generated primary key
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this schedule entry.
     *
     * @param id Integer the primary key to set
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Gets the provider number associated with this schedule.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number associated with this schedule.
     *
     * @param providerNo String the provider number to set
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Gets the start date of the recurring schedule.
     *
     * @return Date the schedule start date
     */
    public Date getsDate() {
        return sDate;
    }

    /**
     * Sets the start date of the recurring schedule.
     *
     * @param sDate Date the schedule start date to set
     */
    public void setsDate(Date sDate) {
        this.sDate = sDate;
    }

    /**
     * Gets the end date of the recurring schedule.
     *
     * @return Date the schedule end date
     */
    public Date geteDate() {
        return eDate;
    }

    /**
     * Sets the end date of the recurring schedule.
     *
     * @param eDate Date the schedule end date to set
     */
    public void seteDate(Date eDate) {
        this.eDate = eDate;
    }

    /**
     * Gets the availability indicator for this schedule.
     *
     * <p>Common values include "1" (available on specified days),
     * "0" (unavailable on specified days), and "A" (alternating weeks).</p>
     *
     * @return String the availability indicator
     */
    public String getAvailable() {
        return available;
    }

    /**
     * Sets the availability indicator for this schedule.
     *
     * @param available String the availability indicator to set
     */
    public void setAvailable(String available) {
        this.available = available;
    }

    /**
     * Gets the day-of-week specification for this schedule.
     *
     * <p>Contains space-separated day-of-week numbers (1=Sunday through 7=Saturday).
     * For alternating week schedules, two sets are separated by a pipe (|) character.</p>
     *
     * @return String the day-of-week specification
     */
    public String getDayOfWeek() {
        return dayOfWeek;
    }

    /**
     * Sets the day-of-week specification for this schedule.
     *
     * @param dayOfWeek String the day-of-week specification to set
     */
    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    /**
     * Gets the available hours configuration (primary week or single week).
     *
     * <p>Contains XML-formatted hour data with day-of-week tags
     * (e.g., {@code <MON>templateName</MON>}).</p>
     *
     * @return String the available hours XML configuration
     */
    public String getAvailHour() {
        return availHour;
    }

    /**
     * Sets the available hours configuration.
     *
     * @param availHour String the available hours XML configuration to set
     */
    public void setAvailHour(String availHour) {
        this.availHour = availHour;
    }

    /**
     * Gets the alternate week available hours configuration.
     *
     * <p>Used when the schedule alternates between two different weekly
     * patterns. Contains XML-formatted hour data for the alternate week.</p>
     *
     * @return String the alternate week available hours XML configuration
     */
    public String getAvailHourB() {
        return availHourB;
    }

    /**
     * Sets the alternate week available hours configuration.
     *
     * @param availHourB String the alternate week available hours to set
     */
    public void setAvailHourB(String availHourB) {
        this.availHourB = availHourB;
    }

    /**
     * Gets the creator identifier for this schedule entry.
     *
     * @return String the creator identifier
     */
    public String getCreator() {
        return creator;
    }

    /**
     * Sets the creator identifier for this schedule entry.
     *
     * @param creator String the creator identifier to set
     */
    public void setCreator(String creator) {
        this.creator = creator;
    }

    /**
     * Gets the status of this schedule entry.
     *
     * @return String the status (e.g., "A" for active)
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status of this schedule entry.
     *
     * @param status String the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }


}
