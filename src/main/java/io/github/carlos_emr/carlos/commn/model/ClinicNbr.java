/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.apache.commons.lang3.StringUtils;

@Entity
@Table(name = "clinic_nbr")
public class ClinicNbr extends AbstractModel<Integer> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nbr_id")
    private Integer id;

    @Column(name = "nbr_value")
    private String nbrValue;

    @Column(name = "nbr_string")
    private String nbrString;

    @Column(name = "nbr_status")
    private String nbrStatus = "A";

    public Integer getId() {
        return this.id;
    }

    public String getNbrValue() {
        return this.nbrValue;
    }

    public void setNbrValue(String nbrValue) {
        this.nbrValue = StringUtils.trimToNull(nbrValue);
    }

    public String getNbrString() {
        return this.nbrString;
    }

    public void setNbrString(String nbrString) {
        this.nbrString = StringUtils.trimToNull(nbrString);
    }

    public String getNbrStatus() {
        return nbrStatus;
    }

    public void setNbrStatus(String nbrStatus) {
        this.nbrStatus = nbrStatus;
    }

}
