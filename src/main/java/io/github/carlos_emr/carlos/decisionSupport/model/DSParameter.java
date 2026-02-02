//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */


package io.github.carlos_emr.carlos.decisionSupport.model;

/**
 * Represents a parameter definition used in decision support guidelines for dynamic class instantiation.
 * <p>
 * DSParameter defines named parameters that can be referenced within decision support guidelines
 * to create instances of specific Java classes. This enables guidelines to dynamically work with
 * different data types and objects during evaluation without hardcoding class dependencies.
 * </p>
 * <p>
 * Parameters are typically defined in guideline XML with an identifier (alias) and a fully-qualified
 * class name, allowing the rules engine to instantiate the appropriate objects during guideline evaluation.
 * </p>
 *
 * @author rjonasz
 * @since 2006-12-16
 * @see DSGuideline for guideline composition using parameters
 * @see DSGuidelineFactory for parameter parsing from XML
 */
public class DSParameter {
    private String strClass;
    private String strAlias;

    /**
     * Gets the fully-qualified Java class name for this parameter.
     * This class name is used by the rules engine to dynamically instantiate
     * objects during guideline evaluation.
     *
     * @return String containing the complete class name (e.g., "java.util.ArrayList")
     */
    public String getStrClass() {
        return strClass;
    }

    /**
     * Sets the fully-qualified Java class name for this parameter.
     *
     * @param strClass String containing the complete Java class name for instantiation
     */
    public void setStrClass(String strClass) {
        this.strClass = strClass;
    }

    /**
     * Gets the alias identifier for this parameter.
     * The alias serves as a symbolic name that can be referenced within
     * guideline logic to refer to instances of the associated class.
     *
     * @return String identifier used to reference this parameter in guidelines
     */
    public String getStrAlias() {
        return strAlias;
    }

    /**
     * Sets the alias identifier for this parameter.
     *
     * @param strAlias String identifier for referencing this parameter in guideline logic
     */
    public void setStrAlias(String strAlias) {
        this.strAlias = strAlias;
    }
}
