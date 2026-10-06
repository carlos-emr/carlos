/**
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.model;

import io.github.carlos_emr.carlos.commons.KeyConstants;

@jakarta.persistence.Entity
@org.hibernate.annotations.Immutable
@jakarta.persistence.Table(name = "app_lookuptable_fields")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
@jakarta.persistence.IdClass(FieldDefValue.JpaId.class)
public class FieldDefValue extends BaseObject {
    private String tableId;
    private String fieldName;
    private String fieldDesc;
    private String fieldType;
    private String lookupTable;
    private String fieldSQL;
    private boolean editable;
    private boolean auto;
    private boolean unique;
    private int genericIdx;
    private int fieldIndex;
    private Integer fieldLength;

    private String val = "";
    private String valDesc = "";
    @jakarta.persistence.Transient

    public String getValDesc() {
        return valDesc;
    }

    public void setValDesc(String valDesc) {
        this.valDesc = valDesc;
    }
    @jakarta.persistence.Transient

    public String getVal() {
        return val;
    }

    public void setVal(String val) {
        this.val = val;
    }

    public FieldDefValue() {
    }
    @jakarta.persistence.Column(name = "fielddesc")

    public String getFieldDesc() {
        return fieldDesc;
    }

    public void setFieldDesc(String fieldDesc) {
        this.fieldDesc = fieldDesc;
    }
    @jakarta.persistence.Id

    @jakarta.persistence.Column(name = "fieldname")

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    @jakarta.persistence.Column(name = "fieldsql")

    public String getFieldSQL() {
        return fieldSQL;
    }

    public void setFieldSQL(String fieldSQL) {
        this.fieldSQL = fieldSQL;
    }
    @jakarta.persistence.Column(name = "fieldtype")

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }
    @jakarta.persistence.Column(name = "lookuptable")

    public String getLookupTable() {
        return lookupTable;
    }

    public void setLookupTable(String lookupTable) {
        this.lookupTable = lookupTable;
    }
    @jakarta.persistence.Id

    @jakarta.persistence.Column(name = "tableid")

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }
    @jakarta.persistence.Column(name = "edityn")

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }
    @jakarta.persistence.Column(name = "fieldindex")

    public int getFieldIndex() {
        return fieldIndex;
    }

    public void setFieldIndex(int fieldIndex) {
        this.fieldIndex = fieldIndex;
    }
    @jakarta.persistence.Column(name = "autoyn")

    public boolean isAuto() {
        return auto;
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
    }
    @jakarta.persistence.Column(name = "genericidx")

    public int getGenericIdx() {
        return genericIdx;
    }

    public void setGenericIdx(int genericIdx) {
        this.genericIdx = genericIdx;
    }
    @jakarta.persistence.Column(name = "uniqueyn")

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }
    @jakarta.persistence.Column(name = "fieldlength")

    public Integer getFieldLength() {
        return fieldLength;
    }

    public void setFieldLength(Integer fieldLength) {
        this.fieldLength = fieldLength;
    }
    @jakarta.persistence.Transient

    public String getFieldLengthStr() {
        String result;
        if (fieldLength == null) {
            result = KeyConstants.DEFAULT_FIELD_LENGTH_STRING;
        } else {
            result = fieldLength.toString();
        }
        return result;
    }

    public static class JpaId implements java.io.Serializable {
        public String tableId;
        public String fieldName;

        public JpaId() {
        }

        public String getTableId() {
            return tableId;
        }

        public void setTableId(String tableId) {
            this.tableId = tableId;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JpaId other)) return false;
            return java.util.Objects.equals(tableId, other.tableId) && java.util.Objects.equals(fieldName, other.fieldName);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tableId, fieldName);
        }
    }
}
