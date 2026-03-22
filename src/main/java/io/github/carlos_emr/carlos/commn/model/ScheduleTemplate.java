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

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity representing a schedule template that defines time slot patterns for a provider's day.
 *
 * <p>Maps to the {@code scheduletemplate} table. Each template has a composite primary key
 * of provider number and template name ({@link ScheduleTemplatePrimaryKey}), a summary
 * description, and a timecode string that defines the schedule slot pattern for the day.</p>
 *
 * <p>The timecode string divides the day into equal-length slots, where each character
 * represents a {@link ScheduleTemplateCode}. An underscore ({@code _}) indicates an
 * unused/empty slot. The slot duration is calculated as 1440 minutes / timecode length.</p>
 *
 * @since 2026-03-17
 */
@Entity
@Table(name = "scheduletemplate")
public class ScheduleTemplate extends AbstractModel<ScheduleTemplatePrimaryKey> {

    @EmbeddedId
    private ScheduleTemplatePrimaryKey id;
    private String summary;
    private String timecode;

    /**
     * Gets the composite primary key (provider number + template name).
     *
     * @return ScheduleTemplatePrimaryKey the composite primary key
     */
    public ScheduleTemplatePrimaryKey getId() {
        return id;
    }

    /**
     * Gets the summary description of this template.
     *
     * @return String the template summary
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Sets the summary description of this template.
     *
     * @param summary String the template summary to set
     */
    public void setSummary(String summary) {
        this.summary = summary;
    }

    /**
     * Gets the timecode string defining the day's schedule slot pattern.
     *
     * <p>Each character maps to a {@link ScheduleTemplateCode}, and underscore
     * ({@code _}) represents an empty slot. The total number of characters
     * determines the slot duration (1440 / length = minutes per slot).</p>
     *
     * @return String the timecode pattern
     */
    public String getTimecode() {
        return timecode;
    }

    /**
     * Sets the timecode string defining the day's schedule slot pattern.
     *
     * @param timecode String the timecode pattern to set
     */
    public void setTimecode(String timecode) {
        this.timecode = timecode;
    }

    /**
     * Sets the composite primary key (provider number + template name).
     *
     * @param id ScheduleTemplatePrimaryKey the composite primary key to set
     */
    public void setId(ScheduleTemplatePrimaryKey id) {
        this.id = id;
    }


}
