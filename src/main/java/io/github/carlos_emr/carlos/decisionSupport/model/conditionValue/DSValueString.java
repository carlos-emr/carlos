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


package io.github.carlos_emr.carlos.decisionSupport.model.conditionValue;

import io.github.carlos_emr.carlos.decisionSupport.model.DecisionSupportException;

/**
 * Represents a string-based condition value for exact text matching in clinical decision support guidelines.
 * <p>
 * DSValueString extends DSValue to support exact string equality comparisons.
 * This is used for matching categorical data such as gender, medication names,
 * diagnosis descriptions, and other text-based clinical information.
 * </p>
 * <p>
 * Unlike {@link DSValueStatement} which performs numeric comparisons,
 * DSValueString uses simple string equality matching. It is commonly used for:
 * </p>
 * <ul>
 * <li>Gender matching (e.g., "M", "F")</li>
 * <li>Medical code string matching (e.g., "icd9:250.0")</li>
 * <li>Text search in clinical notes (e.g., "'diabetes mellitus'")</li>
 * <li>Categorical value matching (e.g., "'active'", "'inactive'")</li>
 * </ul>
 *
 * @author apavel
 * @since 2009-07-06
 * @see DSValue for base value functionality
 * @see DSValueStatement for numeric comparison values
 */
public class DSValueString extends DSValue {

    /**
     * Default constructor for DSValueString.
     */
    public DSValueString() {
    }

    /**
     * Tests whether a given string value exactly matches this string value.
     * <p>
     * Performs exact string equality comparison using String.equals().
     * Note that this method may not always be called directly, as some
     * evaluation logic in DSDemographicAccess performs manual string matching
     * for performance or context-specific reasons.
     * </p>
     *
     * @param value String to test for exact equality with this value
     * @return boolean true if the provided value exactly equals this value, false otherwise
     * @throws DecisionSupportException typically not thrown by this implementation
     */
    public boolean testValue(String value) throws DecisionSupportException {
        return this.getValue().equals(value);
    }

    /**
     * Returns a string representation of this DSValueString.
     * <p>
     * For string values without units, the value is quoted (e.g., "type:'value'").
     * For values with units, standard formatting is used (e.g., "type:value unit").
     * </p>
     *
     * @return String representation with appropriate formatting and quoting
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (this.getValueType() != null) result.append(this.getValueType() + ":");
        // String values without units are quoted for clarity
        if (this.getValueUnit() == null) {
            result.append("'" + this.getValue() + "'");
        } else {
            // Values with units are treated as numeric and not quoted
            result.append(this.getValue());
            result.append(" " + this.getValueUnit());
        }
        return result.toString();
    }
}
