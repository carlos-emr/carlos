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

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "form_boolean_value")
public class FormBooleanValue extends AbstractModel<FormValuePK> implements Serializable {
    @EmbeddedId
    private FormValuePK id;

    @Column(name = "value")
    private Boolean value;

    public FormBooleanValue() {
    } // default constructor for hibernate

    public FormBooleanValue(FormValuePK id, Boolean value) {
        this.id = id;
        this.value = value;
    }

    public FormBooleanValue(String formName, Integer formId, String fieldName, Boolean value) {
        this.id = new FormValuePK(formName, formId, fieldName);
        this.value = value;
    }

    @Override
    public FormValuePK getId() {
        return id;
    }

    public void setId(FormValuePK id) {
        this.id = id;
    }

    public Boolean getValue() {
        return value;
    }

    public void setValue(Boolean value) {
        this.value = value;
    }
}