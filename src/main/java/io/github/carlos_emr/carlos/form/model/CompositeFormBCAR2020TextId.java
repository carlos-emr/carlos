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
package io.github.carlos_emr.carlos.form.model;

import jakarta.persistence.Column;
import java.io.Serializable;

public class CompositeFormBCAR2020TextId implements Serializable {
    @Column(name = "form_id")
    Integer formId;
    @Column(name = "page_no")
    Integer pageNo;
    @Column(name = "field")
    String field;

    public CompositeFormBCAR2020TextId() {
    }

    public CompositeFormBCAR2020TextId(Integer formId, Integer pageNo, String field) {
        this.formId = formId;
        this.pageNo = pageNo;
        this.field = field;
    }

    public Integer getFormId() {
        return formId;
    }

    public void setFormId(Integer formId) {
        this.formId = formId;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (formId == null ? 0 : formId.hashCode());
        result = prime * result + (pageNo == null ? 0 : pageNo.hashCode());
        result = prime * result + (field == null ? 0 : field.hashCode());

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
        CompositeFormBCAR2020TextId other = (CompositeFormBCAR2020TextId) obj;
        if (formId == null && other.getFormId() != null) {
            return false;
        } else if (!formId.equals(other.getFormId())) {
            return false;
        }

        if (pageNo == null && other.getPageNo() != null) {
            return false;
        } else if (!pageNo.equals(other.getPageNo())) {
            return false;
        }

        if (field == null && other.getField() != null) {
            return false;
        } else if (!field.equals(other.getField())) {
            return false;
        }

        return true;
    }
}
