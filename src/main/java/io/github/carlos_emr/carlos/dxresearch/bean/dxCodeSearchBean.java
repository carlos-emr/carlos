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


package io.github.carlos_emr.carlos.dxresearch.bean;


/**
 * Serializable bean representing a single diagnosis code search result.
 *
 * <p>Holds the description, diagnosis code, coding system type, and whether
 * the code is an exact match for the search query. Used by
 * {@link dxCodeSearchBeanHandler} to transport search results from the
 * DAO layer to the JSP view layer.</p>
 *
 * @since 2026-03-17
 */
public class dxCodeSearchBean implements java.io.Serializable {

    String description;
    String dxSearchCode;
    String type;
    String exactMatch;


    /**
     * Default no-argument constructor.
     */
    public dxCodeSearchBean() {
    }

    /**
     * Constructs a search result bean with a description and diagnosis code.
     *
     * @param description String the human-readable description of the diagnosis code
     * @param dxSearchCode String the diagnosis code value (e.g. ICD-9, ICD-10)
     */
    public dxCodeSearchBean(String description,
                            String dxSearchCode) {
        this.description = description;
        this.dxSearchCode = dxSearchCode;
    }

    /**
     * Returns the human-readable description of the diagnosis code.
     *
     * @return String the diagnosis code description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the human-readable description of the diagnosis code.
     *
     * @param description String the diagnosis code description
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * Returns the diagnosis search code value.
     *
     * @return String the diagnosis code (e.g. ICD-9 or ICD-10 code)
     */
    public String getDxSearchCode() {
        return dxSearchCode;
    }

    /**
     * Sets the diagnosis search code value.
     *
     * @param dxSearchCode String the diagnosis code
     */
    public void setDxSearchCode(String dxSearchCode) {
        this.dxSearchCode = dxSearchCode;
    }

    /**
     * Returns the coding system type (e.g. "icd9", "icd10").
     *
     * @return String the coding system type identifier
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the coding system type.
     *
     * @param type String the coding system type identifier
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the exact match indicator.
     *
     * @return String "checked" if this code exactly matches the search query, {@code null} otherwise
     */
    public String getExactMatch() {
        return exactMatch;
    }

    /**
     * Sets the exact match indicator.
     *
     * @param exactMatch String "checked" to indicate an exact match
     */
    public void setExactMatch(String exactMatch) {
        this.exactMatch = exactMatch;
    }

    /**
     * Checks equality by comparing diagnosis code and coding system type
     * against a {@link dxResearchBean} instance.
     *
     * @param o Object the object to compare with
     * @return boolean {@code true} if the object is a {@link dxResearchBean} with matching code and type
     */
    public boolean equals(Object o) {
        if (o instanceof dxResearchBean) {
            dxResearchBean bean = (dxResearchBean) o;
            return (dxSearchCode.equals(bean.getDxSearchCode()) && type.equals(bean.getType()));
        } else
            return super.equals(o);
    }

}
