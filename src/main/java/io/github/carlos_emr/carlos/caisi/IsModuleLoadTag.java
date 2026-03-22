/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.caisi;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.CarlosProperties;

/**
 * JSP custom tag that conditionally includes its body content based on whether
 * a named CARLOS EMR module is enabled in the system properties.
 *
 * <p>Checks {@link CarlosProperties} for the module name property, treating values
 * of "yes", "true", or "on" (case-insensitive) as enabled. The tag supports a
 * {@code reverse} attribute to invert the logic, allowing content to be shown
 * only when a module is <em>disabled</em>.</p>
 *
 * <p>Usage in JSP:</p>
 * <pre>{@code
 * <caisi:isModuleLoad moduleName="CAISI">
 *     <!-- Content shown only when CAISI module is enabled -->
 * </caisi:isModuleLoad>
 * }</pre>
 *
 * @since 2005-01-19
 */
public class IsModuleLoadTag extends TagSupport {

    private String moduleName;
    private boolean reverse = false;

    /**
     * Sets the name of the module property to check in {@link CarlosProperties}.
     *
     * @param moduleName String the property key identifying the module (e.g. "CAISI")
     */
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    /**
     * Evaluates whether the configured module is enabled and determines whether
     * to include or skip the tag body.
     *
     * <p>When {@code reverse} is {@code false} (default), returns {@code EVAL_BODY_INCLUDE}
     * if the module is enabled. When {@code reverse} is {@code true}, the logic is inverted.</p>
     *
     * @return int {@code EVAL_BODY_INCLUDE} to render the body, or {@code SKIP_BODY} to skip it
     * @throws JspException if the module property cannot be read from {@link CarlosProperties}
     */
    public int doStartTag() throws JspException {
        try {

            CarlosProperties proper = CarlosProperties.getInstance();

            if (proper.getProperty(moduleName, "").equalsIgnoreCase("yes") || proper.getProperty(moduleName, "").equalsIgnoreCase("true") || proper.getProperty(moduleName, "").equalsIgnoreCase("on"))
                if (reverse)
                    return SKIP_BODY;
                else
                    return EVAL_BODY_INCLUDE;
        } catch (Exception e) {
            throw new JspException("Failed to get module load info", e);

        }
        if (reverse)
            return EVAL_BODY_INCLUDE;
        else
            return SKIP_BODY;

    }

    public void setReverse(String reverse) {
        this.reverse = "true".equalsIgnoreCase(reverse) || "yes".equalsIgnoreCase(reverse);
    }

}
