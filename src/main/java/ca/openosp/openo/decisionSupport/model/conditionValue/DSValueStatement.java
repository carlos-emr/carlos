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


package ca.openosp.openo.decisionSupport.model.conditionValue;

import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.decisionSupport.model.DecisionSupportException;

/**
 * Represents a condition value with numeric comparison operators for clinical decision support guidelines.
 * <p>
 * DSValueStatement extends DSValue to support numeric comparisons using mathematical operators.
 * This enables clinical guidelines to evaluate numeric patient data such as age, vital signs,
 * lab values, and other quantitative measurements against specific thresholds or ranges.
 * </p>
 * <p>
 * Supported comparison operators include:
 * </p>
 * <ul>
 * <li>&lt; (less than)</li>
 * <li>&lt;= (less than or equal to)</li>
 * <li>&gt; (greater than)</li>
 * <li>&gt;= (greater than or equal to)</li>
 * <li>= (equal to)</li>
 * </ul>
 * <p>
 * Example usage in guidelines:
 * </p>
 * <ul>
 * <li>"&gt;=18 y" - Age 18 years or older</li>
 * <li>"&lt;140 mmHg" - Systolic blood pressure under 140</li>
 * <li>"=F" - Gender equals female</li>
 * </ul>
 *
 * @author apavel
 * @since 2009-07-06
 * @see DSValue for base value functionality
 * @see DSValueString for string-based value matching
 */
public class DSValueStatement extends DSValue {

    /**
     * Enumeration of supported comparison operators for numeric value evaluation.
     *
     * @since 2009-07-06
     */
    private enum Operator {
        /** Less than comparison (&lt;) */
        lessThan("<"),
        /** Less than or equal to comparison (&lt;=) */
        lessThanEqualTo("<="),
        /** Greater than comparison (&gt;) */
        greaterThan(">"),
        /** Greater than or equal to comparison (&gt;=) */
        greaterThanEqualTo(">="),
        /** Equality comparison (=) */
        equal("="),
        /** Range between two values (-) - used in parsing but converted to >= and <= operations */
        between("-");

        String symbol;

        /**
         * Constructs an Operator with the specified symbol.
         *
         * @param symbol String representation of the operator
         */
        private Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * Gets the Operator enum value corresponding to the given symbol string.
         *
         * @param symbol String representation of the operator to find
         * @return Operator enum value matching the symbol
         * @throws IllegalArgumentException if no operator matches the given symbol
         */
        private static Operator getOperator(String symbol) throws IllegalArgumentException {
            for (Operator currentOperator : Operator.values()) {
                if (currentOperator.getSymbol().equals(symbol)) return currentOperator;
            }
            throw new IllegalArgumentException("Cannot get enum Operator from '" + symbol + "'");
        }

        /**
         * Gets an array of all supported operator symbols.
         *
         * @return String array containing all operator symbols
         */
        private static String[] getOperatorSymbols() {
            ArrayList<String> operatorSymbols = new ArrayList<String>();
            for (Operator currentOperator : Operator.values()) {
                operatorSymbols.add(currentOperator.getSymbol());
            }
            return (String[]) operatorSymbols.toArray();
        }

        /**
         * Gets the string symbol for this operator.
         *
         * @return String representation of this operator
         */
        private String getSymbol() {
            return symbol;
        }
    }

    private Operator operator;

    /**
     * Tests whether a given numeric value matches this statement's comparison criteria.
     * <p>
     * Performs numeric comparison by parsing both the test value and this statement's value
     * as integers, then applying the configured operator. This method is used during
     * clinical guideline evaluation to determine if patient data meets the specified criteria.
     * </p>
     *
     * @param testValue String representation of the numeric value to test (e.g., "45", "130")
     * @return boolean true if the test value satisfies the comparison criteria, false otherwise
     * @throws DecisionSupportException if either value cannot be parsed as an integer or operator is undefined
     */
    public boolean testValue(String testValue) throws DecisionSupportException {
        try {
            int testValueInt = Integer.parseInt(testValue);
            int valueInt = Integer.parseInt(this.getValue());
            switch (operator) {
                case lessThan:
                    return testValueInt < valueInt;
                case lessThanEqualTo:
                    return testValueInt <= valueInt;
                case greaterThan:
                    return testValueInt > valueInt;
                case greaterThanEqualTo:
                    return testValueInt >= valueInt;
                case equal:
                    return testValueInt == valueInt;
            }
        } catch (NumberFormatException nfe) {
            throw new DecisionSupportException("cannot parse value '" + testValue + "' or '" + this.getValue() + "' to int to evaluate a compare statement)", nfe);
        }
        throw new DecisionSupportException("One of the operators is not defined");
    }

    /**
     * Gets the comparison operator symbol for this statement.
     *
     * @return String representation of the comparison operator (e.g., "&gt;=", "&lt;", "=")
     */
    public String getOperator() {
        return operator.getSymbol();
    }

    /**
     * Sets the comparison operator for this statement from a string symbol.
     *
     * @param operator String representation of the comparison operator (e.g., "&gt;=", "&lt;", "=")
     * @throws IllegalArgumentException if the operator string is not recognized as a valid operator
     */
    public void setOperator(String operator) throws IllegalArgumentException {
        try {
            this.operator = Operator.getOperator(operator);
        } catch (IllegalArgumentException iae) {
            String allowedOperators = StringUtils.join(Operator.getOperatorSymbols(), ",");
            throw new IllegalArgumentException("Operator '" + operator + "' not supported.  Supported operators; " + allowedOperators, iae);
        }
    }

    /**
     * Returns a string representation of this DSValueStatement.
     * Format includes type prefix, operator, value, and unit if present (e.g., "age:&gt;=18 y").
     *
     * @return String representation in format "type:operator:value unit"
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (this.getValueType() != null) result.append(this.getValueType() + ":");
        if (this.getOperator() != null) result.append(this.getOperator());
        result.append(this.getValue());
        if (this.getValueUnit() != null) result.append(" " + this.getValueUnit());
        return result.toString();
    }


}
