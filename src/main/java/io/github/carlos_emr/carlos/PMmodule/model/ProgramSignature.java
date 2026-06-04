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

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "programSignature")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class ProgramSignature implements Serializable {
    public Integer id;
    public Integer programId;
    public String programName;
    public String providerId;
    public String providerName;
    public String caisiRoleName;
    public java.util.Date updateDate = new Date();
    private int hashCode = Integer.MIN_VALUE;

    public ProgramSignature() {

    }
    @jakarta.persistence.Column(name = "caisiRoleName", length = 255)

    public String getCaisiRoleName() {
        return caisiRoleName;
    }

    public void setCaisiRoleName(String caisiRoleName) {
        this.caisiRoleName = caisiRoleName;
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
    @jakarta.persistence.Column(name = "programId", nullable = false)

    public Integer getProgramId() {
        return programId;
    }

    public void setProgramId(Integer programId) {
        this.programId = programId;
    }
    @jakarta.persistence.Column(name = "programName", length = 70, nullable = false)

    public String getProgramName() {
        return programName;
    }

    public void setProgramName(String programName) {
        this.programName = programName;
    }
    @jakarta.persistence.Column(name = "providerId", length = 6, nullable = false)

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
    @jakarta.persistence.Column(name = "providerName", length = 60, nullable = false)

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.TIMESTAMP)

    @jakarta.persistence.Column(name = "updateDate")

    public java.util.Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(java.util.Date updateDate) {
        this.updateDate = updateDate;
    }


    public boolean equals(Object obj) {
        if (null == obj) return false;
        if (!(obj instanceof ProgramSignature)) return false;
        else {
            ProgramSignature other = (ProgramSignature) obj;
            if (null == this.getId() || null == other.getId()) return false;
            else return (this.getId().equals(other.getId()));
        }
    }

    public int hashCode() {
        if (Integer.MIN_VALUE == this.hashCode) {
            if (null == this.getId()) return super.hashCode();
            else {
                String hashStr = this.getClass().getName() + ":" + this.getId().hashCode();
                this.hashCode = hashStr.hashCode();
            }
        }
        return this.hashCode;
    }


    public String toString() {
        return super.toString();
    }
}
