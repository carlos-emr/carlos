/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package io.github.carlos_emr.carlos.commn.model;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "read_lab")
public class ReadLab extends AbstractModel<ReadLabPK> {

    @EmbeddedId
    private ReadLabPK id;

    public ReadLab() {
    }

    public ReadLab(String providerNo, String labType, Integer labId) {
        this.id = new ReadLabPK(providerNo, labType, labId);
    }

    public ReadLabPK getId() {
        return id;
    }

    public void setId(ReadLabPK readLabPK) {
        this.id = readLabPK;
    }

    public String getProviderNo() {
        return id.getProviderNo();
    }

    public void setProviderNo(String providerNo) {
        this.id.setProviderNo(providerNo);
    }

    public String getLabType() {
        return id.getLabType();
    }

    public void setLabType(String labType) {
        this.id.setLabType(labType);
    }

    public Integer getLabId() {
        return id.getLabId();
    }

    public void setLabId(Integer labId) {
        this.id.setLabId(labId);
    }
}
