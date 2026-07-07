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

package io.github.carlos_emr.carlos.PMmodule.model;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * Service restriction
 */
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "program_client_restriction")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class ProgramClientRestriction implements Serializable {

    private Integer id;
    private int programId;
    private int demographicNo;
    private String providerNo;
    private String commentId;
    private String comments;
    private Date startDate;
    private Date endDate;
    private boolean enabled;
    private String earlyTerminationProvider;

    private Program program;
    private Demographic client;
    private Provider provider;

    public ProgramClientRestriction() {
        id = 0;
    }

    public ProgramClientRestriction(Integer id, int programId, int demographicNo, String providerNo, String comments, Date startDate, Date endDate, boolean enabled, Program program, Demographic client) {
        this.id = id;
        this.programId = programId;
        this.demographicNo = demographicNo;
        this.providerNo = providerNo;
        this.comments = comments;
        this.startDate = startDate;
        this.endDate = endDate;
        this.enabled = enabled;
        this.program = program;
        this.client = client;
    }
    @jakarta.persistence.Column(name = "provider_no", length = 6)

    public String getProviderNo() {
        return providerNo;
    }
    @jakarta.persistence.Transient

    public long getDaysRemaining() {
        return (this.getEndDate().getTime() - this.getStartDate().getTime()) / 1000 / 60 / 60 / 24;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }
    @jakarta.persistence.Id

    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)

    @jakarta.persistence.Column(name = "id")

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    @jakarta.persistence.Column(name = "program_id")

    public int getProgramId() {
        return programId;
    }

    public void setProgramId(int programId) {
        this.programId = programId;
    }
    @jakarta.persistence.Column(name = "demographic_no")

    public int getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(int demographicNo) {
        this.demographicNo = demographicNo;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.TIMESTAMP)

    @jakarta.persistence.Column(name = "start_date", nullable = false)

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.TIMESTAMP)

    @jakarta.persistence.Column(name = "end_date", nullable = false)

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
    @jakarta.persistence.Column(name = "is_enabled", nullable = false)

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    @org.hibernate.annotations.Formula("(select a.description from lst_service_restriction a where a.id=comments)")

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
    @jakarta.persistence.Transient

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }
    @jakarta.persistence.Transient

    public Demographic getClient() {
        return client;
    }

    public void setClient(Demographic client) {
        this.client = client;
    }
    @jakarta.persistence.Transient

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProgramClientRestriction that = (ProgramClientRestriction) o;
        return Objects.equals(id, that.id);
    }

    public int hashCode() {
        return Objects.hashCode(id);
    }
    @jakarta.persistence.Column(name = "comments", length = 255, nullable = false)

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }
    @jakarta.persistence.Column(name = "early_termination_provider")

    public String getEarlyTerminationProvider() {
        return earlyTerminationProvider;
    }

    public void setEarlyTerminationProvider(String earlyTerminationProvider) {
        this.earlyTerminationProvider = earlyTerminationProvider;
    }


}
