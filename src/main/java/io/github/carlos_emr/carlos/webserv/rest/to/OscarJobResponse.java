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
package io.github.carlos_emr.carlos.webserv.rest.to;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import io.github.carlos_emr.carlos.webserv.rest.to.model.OscarJobTo1;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class OscarJobResponse {

    public enum ScheduleActivation { ACTIVE, DISABLED, FAILED }

    private List<OscarJobTo1> jobs = new ArrayList<OscarJobTo1>();
    private ScheduleActivation scheduleActivation;

    @XmlElement
    public List<OscarJobTo1> getJobs() {
        return jobs;
    }

    public void setJobs(List<OscarJobTo1> jobs) {
        this.jobs = jobs;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty
    public ScheduleActivation getScheduleActivation() {
        return scheduleActivation;
    }

    public void setScheduleActivation(ScheduleActivation scheduleActivation) {
        this.scheduleActivation = scheduleActivation;
    }
}
