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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDao.codingSystem;

/**
 * Represents a diagnostic code entry in the CARLOS EMR system.
 *
 * <p>Maps to the {@code diagnosticcode} table and stores medical diagnosis codes used
 * for clinical documentation and billing purposes. Extends {@link AbstractCodeSystemModel}
 * to participate in the unified coding system framework alongside ICD-9, ICD-10, and
 * other classification systems.</p>
 *
 * <p>Each diagnostic code has a code string, description, status (active/inactive),
 * and region (for province-specific code sets). The coding system defaults to
 * MSP (Medical Services Plan) for BC billing integration.</p>
 *
 * @see AbstractCodeSystemModel
 * @since 2001-01-01
 */
@Entity
@Table(name = "diagnosticcode")
public class DiagnosticCode extends AbstractCodeSystemModel<Integer> implements java.io.Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diagnosticcode_no")
    private Integer id;

    @Column(name = "diagnostic_code")
    private String diagnosticCode;

    private String description;

    private String status;

    private String region;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDiagnosticCode() {
        return diagnosticCode;
    }

    public void setDiagnosticCode(String diagnosticCode) {
        this.diagnosticCode = diagnosticCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    @Override
    @Transient
    public String getCode() {
        return this.diagnosticCode;
    }

    @Override
    @Transient
    public String getCodingSystem() {
        return codingSystem.msp.name();
    }

    @Override
    @Transient
    public void setCode(String code) {
        setDiagnosticCode(code);
    }

}
