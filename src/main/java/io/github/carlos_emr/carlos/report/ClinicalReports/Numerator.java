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


/*
 * Numerator.java
 *
 * Created on June 17, 2006, 2:55 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.report.ClinicalReports;

import java.util.Hashtable;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Defines the contract for a clinical report numerator, which evaluates whether
 * a specific patient meets a clinical criterion (e.g. a lab result in range,
 * a measurement taken within a date window).
 *
 * <p>Implementations include {@link SQLNumerator} (SQL-based evaluation),
 * {@link DroolsNumerator} (file-based Drools rules), and
 * {@link DroolsNumerator2} through {@link DroolsNumerator5} (programmatic
 * Drools rules with various comparison strategies).</p>
 *
 * @see Denominator
 * @see ReportEvaluator
 * @see SQLNumerator
 * @see DroolsNumerator
 * @since 2006-06-17
 */
public interface Numerator {

    /**
     * Evaluates the clinical criterion for a specific patient.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context
     * @param demographicNo String the patient demographic number to evaluate
     * @return boolean {@code true} if the patient meets the criterion
     */
    public boolean evaluate(LoggedInInfo loggedInInfo, String demographicNo);

    /**
     * Returns the unique identifier for this numerator.
     *
     * @return String the numerator identifier
     */
    public String getId();

    /**
     * Returns the human-readable display name for this numerator.
     *
     * @return String the numerator name
     */
    public String getNumeratorName();

    /**
     * Returns the output values map produced by rule evaluation.
     *
     * @return Hashtable the output key-value pairs, or {@code null} if not populated
     */
    public Hashtable getOutputValues();

    /**
     * Returns the parsed output field names.
     *
     * @return String[] the output field name array, or {@code null} if not configured
     */
    public String[] getOutputFields();

    /**
     * Checks whether this numerator expects replaceable values before evaluation.
     *
     * @return boolean {@code true} if replaceable value keys have been configured
     */
    public boolean hasReplaceableValues();

    /**
     * Returns the array of replaceable value keys expected by this numerator.
     *
     * @return String[] the keys identifying expected replaceable values
     */
    public String[] getReplaceableKeys();

    /**
     * Sets the replaceable values map containing runtime parameters.
     *
     * @param vals Hashtable the runtime parameter map to inject
     */
    public void setReplaceableValues(Hashtable vals);

    /**
     * Returns the current replaceable values map.
     *
     * @return Hashtable the replaceable values, or {@code null} if not set
     */
    public Hashtable getReplaceableValues();

}
