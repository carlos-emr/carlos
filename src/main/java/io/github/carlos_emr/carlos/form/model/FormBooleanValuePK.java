//CHECKSTYLE:OFF
/**
 * Copyright (c) 2021 WELL EMR Group Inc.
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "gnu.org/licenses/gpl-2.0.html".
 */

package io.github.carlos_emr.carlos.form.model;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class FormBooleanValuePK implements Serializable {

    // forms table name
    @Column(name = "form_name")
    private String formName;

    // id of the form in the form table
    @Column(name = "form_id")
    private Integer formId;

    @Column(name = "field_name")
    private String fieldName;

    public FormBooleanValuePK() {
    }

    public FormBooleanValuePK(String formName, Integer formId, String fieldName) {
        this.formName = formName;
        this.formId = formId;
        this.fieldName = fieldName;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public Integer getFormId() {
        return formId;
    }

    public void setFormId(Integer formId) {
        this.formId = formId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
}