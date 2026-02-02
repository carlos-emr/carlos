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
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

@Entity
@IdClass(CompositeFormBCAR2020TextId.class)
@Table(name = "formBCAR2020Text")
public class FormBCAR2020Text extends AbstractModel<CompositeFormBCAR2020TextId> implements Serializable {
    @Id
    @Column(name = "form_id")
    Integer formId;

    @Id
    @Column(name = "page_no")
    Integer pageNo;

    @Id
    @Column(name = "field")
    String field;

    @NotNull
    @Column(name = "provider_no")
    String providerNo;

    @Column(name = "val")
    String value = "";

    @Column(name = "field_edited")
    Date fieldEdited;

    public FormBCAR2020Text() {
    }

    public FormBCAR2020Text(Integer formId, String providerNo, Integer pageNo, String field, String value) {
        this.formId = formId;
        this.providerNo = providerNo;
        this.pageNo = pageNo;
        this.field = field;
        this.value = value;
    }

    @Override
    public CompositeFormBCAR2020TextId getId() {
        return new CompositeFormBCAR2020TextId(formId, pageNo, field);
    }

    @PrePersist
    public void prePersist() {
        if (fieldEdited == null) {
            setFieldEdited(new Date());
        }
    }

    public Integer getFormId() {
        return formId;
    }

    public void setFormId(Integer formId) {
        this.formId = formId;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Date getFieldEdited() {
        return fieldEdited;
    }

    public void setFieldEdited(Date fieldEdited) {
        this.fieldEdited = fieldEdited;
    }
}
