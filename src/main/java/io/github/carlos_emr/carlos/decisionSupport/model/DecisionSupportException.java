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

/**
 * Exception thrown when errors occur during clinical decision support guideline evaluation or execution.
 * <p>
 * DecisionSupportException represents runtime errors that occur during the evaluation of clinical
 * decision support guidelines, such as data access failures, configuration errors, or evaluation
 * logic problems. This exception provides context for debugging guideline execution issues.
 * </p>
 * <p>
 * Common scenarios that trigger this exception include:
 * </p>
 * <ul>
 * <li>Patient data access failures during condition evaluation</li>
 * <li>Invalid or missing clinical data required for guideline logic</li>
 * <li>Unimplemented evaluation methods for specific condition operators</li>
 * <li>Database connectivity issues when accessing patient records</li>
 * <li>Configuration errors in guideline evaluation parameters</li>
 * </ul>
 *
 * @author apavel
 * @since 2009-07-06
 * @see DecisionSupportParseException for XML parsing-specific errors
 * @see DSGuideline for guideline evaluation that may throw this exception
 */
public class DecisionSupportException extends Exception {

    /**
     * Constructs a new DecisionSupportException with the specified detail message and cause.
     *
     * @param message String describing the specific error that occurred during guideline evaluation
     * @param e Throwable that caused this exception to be thrown
     */
    public DecisionSupportException(String message, Throwable e) {
        super(message, e);
    }

    /**
     * Constructs a new DecisionSupportException with the specified detail message.
     *
     * @param message String describing the specific error that occurred during guideline evaluation
     */
    public DecisionSupportException(String message) {
        super(message);
    }
}
