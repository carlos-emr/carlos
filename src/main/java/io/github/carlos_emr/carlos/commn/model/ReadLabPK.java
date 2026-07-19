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

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class ReadLabPK implements Serializable {

    @Column(name = "provider_no")
    private String providerNo;
    @Column(name = "lab_type")
    private String labType;
    @Column(name = "lab_id")
    private Integer labId;

    public ReadLabPK() {
    }

    public ReadLabPK(String providerNo, String labType, Integer labId) {
        this.providerNo = providerNo;
        this.labType = labType;
        this.labId = labId;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getLabType() {
        return labType;
    }

    public void setLabType(String labType) {
        this.labType = labType;
    }

    public Integer getLabId() {
        return labId;
    }

    public void setLabId(Integer labId) {
        this.labId = labId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (providerNo == null ? 0 : providerNo.hashCode());
        result = prime * result + (labType == null ? 0 : labType.hashCode());
        result = prime * result + (labId == null ? 0 : labId.hashCode());

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        }
        ReadLabPK other = (ReadLabPK) obj;
        if (providerNo == null && other.getProviderNo() != null) {
            return false;
        } else if (!providerNo.equals(other.getProviderNo())) {
            return false;
        }

        if (labType == null && other.getLabType() != null) {
            return false;
        } else if (!labType.equals(other.getLabType())) {
            return false;
        }

        if (labId == null && other.getLabId() != null) {
            return false;
        } else if (!labId.equals(other.getLabId())) {
            return false;
        }

        return true;
    }
}
