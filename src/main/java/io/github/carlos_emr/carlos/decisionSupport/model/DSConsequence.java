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


package io.github.carlos_emr.carlos.decisionSupport.model;

import java.util.List;

/**
 * Represents the outcome or action that should be triggered when decision support guideline conditions are met.
 * <p>
 * DSConsequence defines what happens when clinical guideline conditions evaluate to true.
 * Consequences can be warnings displayed to healthcare providers, recommendations for specific
 * clinical actions, or executable Java code for automated system actions.
 * </p>
 * <p>
 * Each consequence has a type (warning or java) and an optional strength level to indicate
 * the clinical importance of the recommendation or warning.
 * </p>
 *
 * @author apavel
 * @since 2009-07-06
 * @see DSCondition for condition evaluation that triggers consequences
 * @see DSGuideline for guideline composition using consequences
 */
public class DSConsequence {

    /**
     * Defines the type of action to be performed when guideline conditions are met.
     *
     * @since 2009-07-06
     */
    public enum ConsequenceType {
        /** Display a text-based warning or recommendation to the healthcare provider */
        warning,
        /** Execute Java code for automated system actions */
        java
    }

    /**
     * Defines the clinical importance level of the consequence.
     * Used to prioritize and categorize clinical recommendations and warnings.
     *
     * @since 2009-07-06
     */
    public enum ConsequenceStrength {
        /** General clinical warning requiring provider attention */
        warning,
        /** Specific clinical recommendation for patient care */
        recommendation
    }
    private ConsequenceType consequenceType;
    private ConsequenceStrength consequenceStrength;
    private String text;
    private List<Object> objConsequence;

    /**
     * Gets the textual content of this consequence.
     * For warning-type consequences, this contains the message displayed to healthcare providers.
     * For java-type consequences, this may contain code or configuration information.
     *
     * @return String containing the consequence message or content
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the textual content of this consequence.
     *
     * @param text String containing the consequence message, warning text, or code content
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Gets the type of action this consequence represents.
     * Determines whether the consequence displays a warning or executes Java code.
     *
     * @return ConsequenceType indicating warning or java execution
     */
    public ConsequenceType getConsequenceType() {
        return consequenceType;
    }

    /**
     * Sets the type of action this consequence represents.
     *
     * @param consequenceType ConsequenceType defining the action type (warning or java)
     */
    public void setConsequenceType(ConsequenceType consequenceType) {
        this.consequenceType = consequenceType;
    }

    /**
     * Gets the clinical importance level of this consequence.
     * Used to prioritize warnings and recommendations in the clinical interface.
     *
     * @return ConsequenceStrength indicating the clinical priority level
     */
    public ConsequenceStrength getConsequenceStrength() {
        return consequenceStrength;
    }

    /**
     * Sets the clinical importance level of this consequence.
     *
     * @param consequenceStrength ConsequenceStrength defining the priority level
     */
    public void setConsequenceStrength(ConsequenceStrength consequenceStrength) {
        this.consequenceStrength = consequenceStrength;
    }

    /**
     * Gets the list of objects associated with executable consequences.
     * Used primarily for java-type consequences that require additional data objects
     * or parameters for execution.
     *
     * @return List of Object instances for consequence execution, may be null
     */
    public List<Object> getObjConsequence() {
        return objConsequence;
    }

    /**
     * Sets the list of objects associated with executable consequences.
     *
     * @param objConsequence List of Object instances needed for consequence execution
     */
    public void setObjConsequence(List<Object> objConsequence) {
        this.objConsequence = objConsequence;
    }
}
