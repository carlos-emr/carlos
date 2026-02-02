//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.form.model;

import com.sun.istack.NotNull;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "formBCAR2020")
public class FormBCAR2020 extends AbstractModel<Integer> implements Serializable {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer formId;

    @NotNull
    @Column(name = "demographic_no")
    Integer demographicNo;

    @NotNull
    @Column(name = "provider_no")
    String providerNo;

    @Column(name = "formCreated")
    Date formCreated;

    public FormBCAR2020() {
    }

    public FormBCAR2020(Integer demographicNo, String providerNo) {
        this.demographicNo = demographicNo;
        this.providerNo = providerNo;
    }

    @Override
    public Integer getId() {
        return formId;
    }

    @PrePersist
    public void prePersist() {
        if (formCreated == null) {
            setFormCreated(new Date());
        }
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Date getFormCreated() {
        return formCreated;
    }

    public void setFormCreated(Date formCreated) {
        this.formCreated = formCreated;
    }

}
