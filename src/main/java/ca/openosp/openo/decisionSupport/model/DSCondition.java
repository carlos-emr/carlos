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


package ca.openosp.openo.decisionSupport.model;

import java.util.Hashtable;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.decisionSupport.model.conditionValue.DSValue;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Represents a clinical condition used in decision support guidelines to evaluate patient data.
 * <p>
 * A DSCondition defines criteria that are evaluated against patient demographic, clinical,
 * prescription, billing, and other healthcare data to determine if specific clinical guidelines
 * should be triggered. Conditions support various data types including diagnosis codes (ICD-9/ICD-10),
 * prescription codes (ATC), patient demographics (age, sex), clinical notes, billing codes,
 * and flowsheet data.
 * </p>
 * <p>
 * Each condition is associated with a specific module (data source) and supports logical operators
 * for evaluating multiple values: any (OR logic), all (AND logic), not (negation),
 * notany (NOT OR), and notall (NOT AND).
 * </p>
 *
 * @author apavel
 * @since 2009-07-06
 * @see DSDemographicAccess for condition evaluation logic
 * @see DSValue for condition value representation
 * @see DSGuideline for guideline composition using conditions
 */
public class DSCondition {
    private static final Logger _log = MiscUtils.getLogger();

    /**
     * Defines logical operators for evaluating condition values against patient data.
     * These operators control how multiple condition values are combined and evaluated.
     *
     * @since 2009-07-06
     */
    public enum ListOperator {
        /** Any of the condition values must match (OR logic) */
        any,
        /** All condition values must match (AND logic) */
        all,
        /** None of the condition values must match (NOT logic) */
        not,
        /** Not any of the condition values must match (NOT OR logic) */
        notany,
        /** Not all condition values must match (NOT AND logic) */
        notall
    }

    /**
     * Gets the configuration parameters for this condition.
     * Parameters provide additional context for condition evaluation,
     * such as payer types for billing conditions or time ranges for date-based conditions.
     *
     * @return Hashtable containing key-value parameter pairs, may be null
     */
    public Hashtable getParam() {
        return param;
    }

    /**
     * Sets the configuration parameters for this condition.
     *
     * @param param Hashtable containing key-value parameter pairs for condition evaluation
     */
    public void setParam(Hashtable param) {
        this.param = param;
    }

    /**
     * Gets the label identifier for this condition.
     * The label is used by the Drools rules engine to match asserted patient data
     * against condition criteria during guideline evaluation.
     *
     * @return String label identifier for rules engine matching
     */
    public String getLabel() {
        _log.debug("getting label in " + this.hashCode());
        return label;
    }

    /**
     * Sets the label identifier for this condition.
     * Label is used to match the asserted value in the drools engine during
     * clinical guideline evaluation.
     *
     * @param label String identifier for rules engine matching
     */
    public void setLabel(String label) {
        _log.debug("Setting label in " + this.hashCode());
        this.label = label;
    }

    protected DSDemographicAccess.Module module;
    protected ListOperator listOperator;
    protected List<DSValue> values;
    private Hashtable param = null;
    private String label = null;
    private String desc = null;

    /**
     * Gets the healthcare data module type for this condition.
     * The module determines which type of patient data will be evaluated,
     * such as diagnosis codes, prescriptions, demographics, or clinical notes.
     *
     * @return DSDemographicAccess.Module indicating the data source type
     * @see DSDemographicAccess.Module for available data source types
     */
    public DSDemographicAccess.Module getConditionType() {
        return module;
    }

    /**
     * Sets the healthcare data module type for this condition.
     *
     * @param conditionType DSDemographicAccess.Module indicating which patient data source to evaluate
     */
    public void setConditionType(DSDemographicAccess.Module conditionType) {
        this.module = conditionType;
    }

    /**
     * Gets the logical operator used for evaluating multiple condition values.
     *
     * @return ListOperator defining how multiple values are combined (any, all, not, notany, notall)
     */
    public ListOperator getListOperator() {
        return listOperator;
    }

    /**
     * Sets the logical operator for evaluating multiple condition values.
     *
     * @param listOperator ListOperator defining logical combination of values
     */
    public void setListOperator(ListOperator listOperator) {
        this.listOperator = listOperator;
    }

    /**
     * Sets the logical operator from a string representation.
     * Converts string values to the corresponding ListOperator enum value.
     *
     * @param listOperatorStr String representation of the list operator (any, all, not, notany, notall)
     * @throws IllegalArgumentException if the string does not match a valid ListOperator value
     */
    public void setListOperatorStr(String listOperatorStr) throws IllegalArgumentException {
        try {
            this.listOperator = ListOperator.valueOf(listOperatorStr);
        } catch (IllegalArgumentException iae) {
            String allowedListOperators = StringUtils.join(ListOperator.values(), ",");
            throw new IllegalArgumentException("Cannot recognize list operator '" + listOperatorStr + "'.  Allowed list operators: " + allowedListOperators, iae);
        }
    }

    /**
     * Gets the list of values to be evaluated against patient data.
     * Values represent the specific criteria (codes, age ranges, text patterns)
     * that will be matched against the selected data module.
     *
     * @return List of DSValue objects representing condition criteria
     */
    public List<DSValue> getValues() {
        return values;
    }

    /**
     * Sets the list of values to be evaluated against patient data.
     *
     * @param values List of DSValue objects containing condition criteria
     */
    public void setValues(List<DSValue> values) {
        this.values = values;
    }

    /**
     * Gets the human-readable description of this condition.
     * Used for documentation and display purposes in clinical interfaces.
     *
     * @return String description of the condition's purpose and criteria
     */
    public String getDesc() {
        return desc;
    }

    /**
     * Sets the human-readable description of this condition.
     *
     * @param desc String description explaining the condition's clinical purpose
     */
    public void setDesc(String desc) {
        this.desc = desc;
    }


}
