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


package io.github.carlos_emr.carlos.util;


import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.CarlosProperties;


/**
 * JSP custom tag for conditionally including page content based on CARLOS EMR property values.
 * Compares the value of a named property from {@link CarlosProperties} against an expected value.
 * If they match (or mismatch when {@code reverse} is set to "true"), the tag body is included.
 *
 * @since 2001-01-01
 */
public class CarlosPropertiesCheck extends TagSupport {

    protected String value = null;
    protected String property = null;
    protected String defaultVal = null;
    protected String reverse = null;

    /**
     * Returns the reverse flag value.
     *
     * @return String "true" if the condition logic is inverted, null otherwise
     */
    public String getReverse() {
        return reverse;
    }

    /**
     * Sets the reverse flag. When "true", the condition logic is inverted.
     *
     * @param reverse String "true" to invert the condition
     */
    public void setReverse(String reverse) {
        this.reverse = reverse;
    }

    /**
     * Returns the expected property value to compare against.
     *
     * @return String the expected value
     */
    public String getValue() {
        return (this.value);
    }

    /**
     * Sets the expected property value to compare against.
     *
     * @param value String the expected value (trimmed on assignment)
     */
    public void setValue(String value) {
        this.value = value.trim();
    }

    /**
     * Returns the property name to look up.
     *
     * @return String the property name
     */
    public String getProperty() {
        return (this.property);
    }

    /**
     * Sets the property name to look up in {@link CarlosProperties}.
     *
     * @param property String the property name (trimmed on assignment)
     */
    public void setProperty(String property) {
        this.property = property.trim();
    }


    /**
     * Evaluates the property condition and includes or skips the tag body accordingly.
     *
     * @return int {@link TagSupport#EVAL_BODY_INCLUDE} if the condition is met,
     *         {@link TagSupport#SKIP_BODY} otherwise
     * @throws JspException if tag processing fails
     */
    public int doStartTag() throws JspException {

        boolean conditionMet = false;

        String prop = getProperty();
        String val = getValue();

        boolean rev = false;
        if (getReverse() != null && getReverse().equalsIgnoreCase("true")) {
            rev = true;
        }

        try {
            String oscarVal = CarlosProperties.getInstance().getProperty(prop);
            if (oscarVal.equals(val)) {
                conditionMet = true;
            }

            if (rev) {
                conditionMet = !conditionMet;
            }
        } catch (Exception invalidProp) {
            if (defaultVal != null && defaultVal.equalsIgnoreCase("true")) {
                conditionMet = true;
            }
            if (rev) {
                conditionMet = true;
            }
        }

        if (conditionMet)
            return (EVAL_BODY_INCLUDE);
        else
            return (SKIP_BODY);

    }

    /**
     * Completes tag processing and continues page evaluation.
     *
     * @return int {@link TagSupport#EVAL_PAGE}
     * @throws JspException if tag processing fails
     */
    public int doEndTag() throws JspException {

        return (EVAL_PAGE);

    }

    /**
     * Releases tag resources and resets state for tag reuse.
     */
    public void release() {

        super.release();
        value = null;
        property = null;
    }

    /**
     * Getter for property defaultVal.
     *
     * @return Value of property defaultVal.
     */
    public java.lang.String getDefaultVal() {
        return defaultVal;
    }

    /**
     * Setter for property defaultVal.
     *
     * @param defaultVal New value of property defaultVal.
     */
    public void setDefaultVal(java.lang.String defaultVal) {
        this.defaultVal = defaultVal;
    }


}
