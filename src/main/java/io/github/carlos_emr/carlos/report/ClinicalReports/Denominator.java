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
 * Denominator.java
 *
 * Created on June 17, 2006, 2:55 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.report.ClinicalReports;

import java.util.Hashtable;
import java.util.List;

/**
 * Defines the contract for a clinical report denominator, representing the patient
 * population (denominator set) against which numerator criteria are evaluated.
 *
 * <p>Implementations include {@link SQLDenominator} (SQL-based patient lists) and
 * {@link PatientSetDenominator} (predefined demographic sets). Denominators may
 * accept replaceable values (e.g. provider number, date ranges) that are injected
 * at runtime before the denominator list is generated.</p>
 *
 * @see Numerator
 * @see SQLDenominator
 * @see PatientSetDenominator
 * @see ReportEvaluator
 * @since 2006-06-17
 */
public interface Denominator {

    /**
     * Returns the list of patient demographic numbers that form this denominator set.
     *
     * @return List the list of demographic number strings
     */
    public List getDenominatorList();

    /**
     * Returns the human-readable display name for this denominator.
     *
     * @return String the denominator name
     */
    public String getDenominatorName();

    /**
     * Returns the unique identifier for this denominator.
     *
     * @return String the denominator identifier
     */
    public String getId();

    /**
     * Checks whether this denominator expects replaceable values to be injected
     * before generating the denominator list.
     *
     * @return boolean {@code true} if replaceable value keys have been configured
     */
    public boolean hasReplaceableValues();

    /**
     * Returns the array of replaceable value keys expected by this denominator.
     *
     * @return String[] the keys identifying expected replaceable values
     */
    public String[] getReplaceableKeys();

    /**
     * Sets the replaceable values map containing runtime parameters for this denominator.
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
