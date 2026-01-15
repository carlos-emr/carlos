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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.decisionSupport.model.DecisionSupportException;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Abstract base class representing values used in clinical decision support guideline conditions.
 * <p>
 * DSValue provides a framework for parsing and evaluating various types of condition values
 * used in clinical decision support guidelines. Values can represent medical codes (ICD-9, ICD-10, ATC),
 * numeric ranges (age, measurement values), text patterns, or other clinical criteria.
 * </p>
 * <p>
 * The class supports complex parsing of condition expressions including:
 * </p>
 * <ul>
 * <li>Type-prefixed values (e.g., "icd9:250", "atc:C09AA")</li>
 * <li>Comparison operators for numeric values (e.g., "&gt;=18 y", "&lt;140 mmHg")</li>
 * <li>Range specifications (e.g., "18 y - 65 y")</li>
 * <li>Quoted string literals (e.g., "'diabetes mellitus'")</li>
 * <li>Unit specifications for measurements (e.g., "y" for years, "mmHg" for blood pressure)</li>
 * </ul>
 * <p>
 * Subclasses implement specific value types:
 * </p>
 * <ul>
 * <li>{@link DSValueStatement} - Numeric comparisons with operators</li>
 * <li>{@link DSValueString} - String literal matching</li>
 * </ul>
 *
 * @author apavel
 * @since 2009-07-06
 * @see DSValueStatement for numeric comparison values
 * @see DSValueString for string literal values
 * @see DSValueStatement for condition evaluation using values
 */
public abstract class DSValue {
    private static final Logger _log = MiscUtils.getLogger();

    private String valueType;
    private String valueUnit;
    private String value;

    /**
     * Default constructor for DSValue.
     */
    public DSValue() {
    }

    /**
     * Gets the type classification of this value.
     * Common types include medical code systems like "icd9", "icd10", "atc",
     * or measurement types like "age", "bp", "weight".
     *
     * @return String indicating the value type classification, may be null
     */
    public String getValueType() {
        return valueType;
    }

    /**
     * Sets the type classification of this value.
     *
     * @param valueType String indicating the value type (e.g., "icd9", "atc", "age")
     */
    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    /**
     * Gets the actual value content for evaluation.
     * This is the core data that will be matched against patient information,
     * such as a medical code, age number, or text string.
     *
     * @return String containing the value to be evaluated
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the actual value content for evaluation.
     *
     * @param value String containing the value to be matched against patient data
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns a string representation of this DSValue.
     * Format includes type prefix, value, and unit if present (e.g., "icd9:250", ">=18 y").
     *
     * @return String representation in format "type:value unit"
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (this.getValueType() != null) result.append(this.getValueType() + ":");
        result.append(this.getValue());
        if (this.getValueUnit() != null) result.append(" " + this.getValueUnit());
        return result.toString();
    }

    /**
     * Creates a list of DSValue objects from a comma-separated string of value expressions.
     * <p>
     * Parses complex value expressions including:
     * </p>
     * <ul>
     * <li>Simple comma-separated values: "icd9:250,icd9:401"</li>
     * <li>Quoted string values: "'diabetes mellitus','hypertension'"</li>
     * <li>Range expressions: "18 y - 65 y" (creates &gt;=18 y and &lt;=65 y)</li>
     * <li>Mixed expressions: "icd9:250,>=18 y,'diabetes'"</li>
     * </ul>
     *
     * @param values String containing comma-separated value expressions to parse
     * @return List of DSValue objects representing the parsed expressions
     * @see #createDSValue(String) for individual value parsing
     */
    public static List<DSValue> createDSValues(String values) {
        String[] dsValuesStr = new String[0];
        boolean doHyphenSearch = true;
        Pattern stringQuotePattern = Pattern.compile("'.+?'");
        if (stringQuotePattern.matcher(values).find()) {  //if has a pair of quotes with something in it - treat as a list of strings
            Pattern stringSeparatorPattern = Pattern.compile("'[\\s]*,");
            String[] separatedValues = stringSeparatorPattern.split(values); // [ ',' ] is absolutely illegal in a quoted string
            ArrayList<String> dsValueStrArray = new ArrayList<String>();
            for (String separatedValue : separatedValues) {
                if (!separatedValue.trim().endsWith("'")) {
                    separatedValue = separatedValue.trim() + "'";
                }
                dsValueStrArray.add(separatedValue);
                _log.debug("Separated Value: " + separatedValue);
            }

            dsValuesStr = dsValueStrArray.toArray(dsValuesStr);
            doHyphenSearch = false;
        } else {
            dsValuesStr = StringUtils.split(values, ",");
        }
        ArrayList<DSValue> dsValues = new ArrayList<DSValue>();
        for (String dsValueStr : dsValuesStr) {
            int hyphenIndex = -1;
            if (doHyphenSearch)
                hyphenIndex = dsValueStr.indexOf("-");
            //if specified range, i.e. "age: 1 y - 5 y"
            if (hyphenIndex != -1) {
                int colonIndex = dsValueStr.indexOf(":");
                String type = "";
                if (colonIndex != -1)
                    type = dsValueStr.substring(0, colonIndex + 1);
                String value1 = dsValueStr.substring(colonIndex + 1, hyphenIndex);
                String value2 = dsValueStr.substring(hyphenIndex + 1);
                String dsValueStr1 = type + ">=" + value1;
                String dsValueStr2 = type + "<=" + value2;
                dsValues.add(DSValue.createDSValue(dsValueStr1));
                dsValues.add(DSValue.createDSValue(dsValueStr2));
            } else {
                dsValues.add(DSValue.createDSValue(dsValueStr));
            }
        }
        return dsValues;
    }

    /**
     * Creates a single DSValue object from a formatted value expression.
     * <p>
     * Parses individual value expressions with optional type prefix, comparison operators,
     * and units. Examples of supported formats:
     * </p>
     * <ul>
     * <li>"icd9:250" - Medical code with type</li>
     * <li>">=18 y" - Numeric comparison with unit</li>
     * <li>"'diabetes'" - Quoted string literal</li>
     * <li>"&lt;=140 mmHg" - Measurement with operator and unit</li>
     * <li>"F" - Simple string value</li>
     * </ul>
     * <p>
     * Returns either a {@link DSValueStatement} for expressions with comparison operators
     * or a {@link DSValueString} for simple string values.
     * </p>
     *
     * @param typeOperatorValueUnit String containing the formatted value expression to parse
     * @return DSValue object (DSValueStatement or DSValueString) representing the parsed expression
     */
    public static DSValue createDSValue(String typeOperatorValueUnit) {
        boolean processStatement = true;
        Pattern stringQuotePattern = Pattern.compile("'.+?'");
        if (stringQuotePattern.matcher(typeOperatorValueUnit).find()) {
            typeOperatorValueUnit = typeOperatorValueUnit.replaceAll("'", "");
            processStatement = false;
        }
        DSValue returnDsValue;
        int typeSeparatorIndex = indexOfNotQuoted(typeOperatorValueUnit, ":");
        String valueStr;
        String typeStr = null;
        String operator = null;
        String unit = null;


        //take out the type i.e. 'atc:'
        if (typeSeparatorIndex == -1) {
            valueStr = typeOperatorValueUnit.trim();
        } else {
            typeStr = typeOperatorValueUnit.substring(0, typeSeparatorIndex).trim();
            valueStr = typeOperatorValueUnit.substring(typeSeparatorIndex + 1).trim();
        }

        Matcher operatorMatcher = Pattern.compile("[<>=-]+").matcher(valueStr);

        //find operator
        if (processStatement && operatorMatcher.find()) {
            operator = operatorMatcher.group().trim();
            valueStr = operatorMatcher.replaceFirst("").trim();

            Matcher unitMatcher = Pattern.compile("([^\\s]+$)").matcher(valueStr);
            //must be trimmed
            if (valueStr.indexOf(" ") != -1) {
                unitMatcher.find();
                unit = unitMatcher.group().trim();
                valueStr = unitMatcher.replaceFirst("").trim();
            }

            DSValueStatement dsValue = new DSValueStatement();
            dsValue.setValueType(typeStr);
            dsValue.setValue(valueStr);
            dsValue.setOperator(operator);
            dsValue.setValueUnit(unit);
            returnDsValue = dsValue;
        } else {
            DSValueString dsValue = new DSValueString();
            dsValue.setValueType(typeStr);
            dsValue.setValue(valueStr);
            returnDsValue = dsValue;
        }
        _log.debug("DSValue type: " + returnDsValue.getValueType() + " operator: " + returnDsValue.getValue() + " unit: " + returnDsValue.getValueUnit() + " object type: " + returnDsValue.getClass().getName());
        return returnDsValue;
    }


    //WARNING: CANNOT SEARCH FOR SINGLE, DOUBLE, TRIPPLE, ETC.. QUOTES
    //i.e. cannot search:  '  ''  ''' '''' etc   (don't know why you'd want to anyways)
    private static int indexOfNotQuoted(String str, String query) {
        if (str.contains("'")) {
            Pattern stringQuotePattern = Pattern.compile("'.+?'");
            Pattern allCharacters = Pattern.compile(".");
            String[] quotedStrings = stringQuotePattern.split(str);
            for (String quotedString : quotedStrings) {
                String blankedString = allCharacters.matcher(quotedString).replaceAll("'");
                str = str.replace(quotedString, blankedString);
            }
        }
        return str.indexOf(query);
    }

    /**
     * Tests whether a given value matches the criteria defined by this DSValue.
     * <p>
     * This abstract method must be implemented by subclasses to provide specific
     * matching logic appropriate for the value type. For example:
     * </p>
     * <ul>
     * <li>{@link DSValueStatement} performs numeric comparisons using operators</li>
     * <li>{@link DSValueString} performs string equality matching</li>
     * </ul>
     *
     * @param value String representation of the value to test against this DSValue's criteria
     * @return boolean true if the provided value matches this DSValue's criteria, false otherwise
     * @throws DecisionSupportException if the value cannot be evaluated due to format or type errors
     */
    public abstract boolean testValue(String value) throws DecisionSupportException;

    /**
     * Gets the unit of measurement for this value.
     * Common units include "y" (years), "m" (months), "d" (days),
     * "mmHg" (blood pressure), "kg" (weight), etc.
     *
     * @return String indicating the unit of measurement, may be null
     */
    public String getValueUnit() {
        return valueUnit;
    }

    /**
     * Sets the unit of measurement for this value.
     *
     * @param valueUnit String indicating the unit of measurement for the value
     */
    public void setValueUnit(String valueUnit) {
        this.valueUnit = valueUnit;
    }

}
