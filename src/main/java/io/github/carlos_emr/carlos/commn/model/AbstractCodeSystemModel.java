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

/**
 * Abstract base class for entities that represent entries in medical coding systems.
 *
 * <p>Provides a uniform interface for accessing code, description, and coding system
 * information across different medical classification systems such as ICD-9, ICD-10,
 * MSP diagnostic codes, and other standardized healthcare coding schemes.</p>
 *
 * <p>Subclasses must implement the code, description, and coding system accessors
 * to map to their specific table structures.</p>
 *
 * @param <T> the type of the primary key
 * @see DiagnosticCode
 * @since 2001-01-01
 */
public abstract class AbstractCodeSystemModel<T> extends AbstractModel<T> {

    /**
     * Returns the code value for this coding system entry.
     *
     * @return String the code (e.g., ICD-9 code, MSP diagnostic code)
     */
    public abstract String getCode();

    /**
     * Returns the human-readable description for this code.
     *
     * @return String the code description
     */
    public abstract String getDescription();

    /**
     * Returns the identifier of the coding system this code belongs to.
     *
     * @return String the coding system name (e.g., "msp", "icd9", "icd10")
     */
    public abstract String getCodingSystem();

    /**
     * Sets the code value for this coding system entry.
     *
     * @param code String the code to set
     */
    public abstract void setCode(String code);

    public abstract void setDescription(String description);
}
