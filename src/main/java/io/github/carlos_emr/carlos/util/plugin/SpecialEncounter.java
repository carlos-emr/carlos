/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.util.plugin;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.CarlosProperties;

/**
 * JSP custom tag for conditionally rendering content based on the {@code specialencounter}
 * CARLOS property. Checks whether the configured module name appears in the property value.
 * Supports exact matching and reverse (negation) modes.
 *
 * @since 2006-01-01
 */
public class SpecialEncounter extends TagSupport {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private String moduleName;
    private boolean reverse = false;
    private boolean exactEqual = false;

    /**
     * Sets the module name to check against the {@code specialencounter} property.
     *
     * @param moduleName String the module name to match
     */
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    /**
     * Evaluates whether the module name matches the {@code specialencounter} property
     * and includes or skips the tag body accordingly.
     *
     * @return int {@link TagSupport#EVAL_BODY_INCLUDE} or {@link TagSupport#SKIP_BODY}
     * @throws JspException if property access fails
     */
    public int doStartTag() throws JspException {
        try {
            CarlosProperties proper = CarlosProperties.getInstance();

            if (!isExactEqual() && (proper.getProperty("specialencounter", "").indexOf(moduleName) >= 0)) {

                if (reverse) return SKIP_BODY;
                else return EVAL_BODY_INCLUDE;
            } else if (isExactEqual() && proper.getProperty("specialencounter", "").equalsIgnoreCase(moduleName)) {

                if (reverse) return SKIP_BODY;
                else return EVAL_BODY_INCLUDE;
            }
        } catch (Exception e) {
            throw new JspException("Failed to get module load info", e);

        }
        if (reverse) return EVAL_BODY_INCLUDE;
        else return SKIP_BODY;

    }

    /**
     * Sets the reverse flag. When "true" or "yes", the inclusion condition is inverted.
     *
     * @param reverse String the reverse flag value
     */
    public void setReverse(String reverse) {
        this.reverse = "true".equalsIgnoreCase(reverse)
                || "yes".equalsIgnoreCase(reverse);
    }


    /**
     * Returns whether exact matching is enabled (as opposed to substring matching).
     *
     * @return boolean true if exact matching is enabled
     */
    public boolean isExactEqual() {
        return exactEqual;
    }

    /**
     * Sets whether to use exact equality matching instead of substring matching.
     *
     * @param exactEqual boolean true to enable exact matching
     */
    public void setExactEqual(boolean exactEqual) {
        this.exactEqual = exactEqual;
    }
}
