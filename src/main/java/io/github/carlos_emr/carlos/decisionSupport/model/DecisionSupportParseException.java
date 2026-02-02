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


package io.github.carlos_emr.carlos.decisionSupport.model;

/**
 * Exception thrown when errors occur during parsing of clinical decision support guideline XML configurations.
 * <p>
 * DecisionSupportParseException is a specialized exception for handling XML parsing errors
 * when loading and configuring clinical decision support guidelines. This exception provides
 * specific context about which guideline failed to parse and what parsing error occurred.
 * </p>
 * <p>
 * Common scenarios that trigger this exception include:
 * </p>
 * <ul>
 * <li>Malformed XML structure in guideline definitions</li>
 * <li>Missing required attributes or elements in guideline XML</li>
 * <li>Invalid condition types, operators, or consequence types</li>
 * <li>Unrecognized parameter class names or identifiers</li>
 * <li>XML schema validation failures for guideline structure</li>
 * </ul>
 *
 * @author apavel
 * @since 2009-07-06
 * @see DecisionSupportException for general guideline evaluation errors
 * @see DSGuidelineFactory for XML parsing that may throw this exception
 * @see DSGuideline for guideline XML structure requirements
 */
public class DecisionSupportParseException extends DecisionSupportException {

    /**
     * Constructs a new DecisionSupportParseException for a specific guideline with a detailed message.
     *
     * @param guidelineTitle String identifying the guideline that failed to parse
     * @param message String describing the specific parsing error that occurred
     */
    public DecisionSupportParseException(String guidelineTitle, String message) {
        super("Error parsing decision support guideline titled '" + guidelineTitle + "'.  " + message);
    }

    /**
     * Constructs a new DecisionSupportParseException for a specific guideline with a detailed message and cause.
     *
     * @param guidelineTitle String identifying the guideline that failed to parse
     * @param message String describing the specific parsing error that occurred
     * @param e Throwable that caused this parsing exception to be thrown
     */
    public DecisionSupportParseException(String guidelineTitle, String message, Throwable e) {
        super("Error parsing decision support guideline titled '" + guidelineTitle + "'.  " + message, e);
    }

    /**
     * Constructs a new DecisionSupportParseException with a general parsing error message.
     *
     * @param message String describing the parsing error that occurred
     */
    public DecisionSupportParseException(String message) {
        super("Error parsing decision support guideline. " + message);
    }

    /**
     * Constructs a new DecisionSupportParseException with a message and cause.
     *
     * @param message String describing the parsing error that occurred
     * @param e Throwable that caused this parsing exception to be thrown
     */
    public DecisionSupportParseException(String message, Throwable e) {
        super(message, e);
    }
}
