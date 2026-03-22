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

import java.io.Serializable;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.FetchType;

import java.util.List;

/**
 * Represents a consultation service category in the CARLOS EMR system.
 *
 * <p>Maps to the {@code consultationServices} table and defines the types of specialist
 * services available for consultation referrals (e.g., "Cardiology", "Orthopedics").
 * Each service category has a many-to-many relationship with
 * {@link ProfessionalSpecialist} entities through the {@code serviceSpecialists}
 * join table, allowing multiple specialists to be associated with each service.</p>
 *
 * @see ConsultationRequest
 * @see ProfessionalSpecialist
 * @since 2001-01-01
 */
@Entity
@Table(name = "consultationServices")
public class ConsultationServices extends AbstractModel<Integer> implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    private Integer serviceId;
    private String serviceDesc;
    private String active;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "serviceSpecialists", joinColumns = @JoinColumn(name = "serviceId"), inverseJoinColumns = @JoinColumn(name = "specId"))
    private List<ProfessionalSpecialist> specialists;

    public ConsultationServices() {
    }

    public ConsultationServices(String serviceDesc) {
        setServiceDesc(serviceDesc);
    }

    @Override
    public Integer getId() {
        return (serviceId);
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceDesc() {
        return serviceDesc;
    }

    public void setServiceDesc(String serviceDesc) {
        this.serviceDesc = serviceDesc;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (serviceId != null ? serviceId.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof ConsultationServices)) {
            return false;
        }
        ConsultationServices other = (ConsultationServices) object;
        if ((this.serviceId == null && other.serviceId != null) || (this.serviceId != null && !this.serviceId.equals(other.serviceId))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.carlos.commn.model.ConsultationServices[serviceId=" + serviceId + "]";
    }

    /**
     * @return the specialists
     */
    public List<ProfessionalSpecialist> getSpecialists() {
        return specialists;
    }

    /**
     * @param specialists the specialists to set
     */
    public void setSpecialists(List<ProfessionalSpecialist> specialists) {
        this.specialists = specialists;
    }

}
