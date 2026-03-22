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
 * JSP custom tag for conditionally rendering content based on one or more CARLOS properties.
 * Accepts a comma-separated list of property names and includes the tag body if any of them
 * are enabled (value is "yes", "true", or "on"). Supports a reverse mode that inverts the logic.
 *
 * @since 2006-01-01
 */
public class SpecialPlugins extends TagSupport {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private String moduleName;
    private boolean reverse = false;

    /**
     * Sets the comma-separated list of property names to check.
     *
     * @param moduleName String the property names
     */
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    /**
     * Checks whether the named property is enabled in the given properties instance.
     *
     * @param proName String the property name to check
     * @param proper CarlosProperties the properties instance to query
     * @return boolean true if the property value is "yes", "true", or "on"
     */
    public boolean propertiesOn(String proName, CarlosProperties proper) {

        if (proper.getProperty(proName, "").equalsIgnoreCase("yes")
                || proper.getProperty(proName, "").equalsIgnoreCase("true")
                || proper.getProperty(proName, "").equalsIgnoreCase("on"))
            return true;
        else
            return false;

    }

    /**
     * Evaluates whether any of the configured module properties are enabled
     * and includes or skips the tag body accordingly.
     *
     * @return int {@link TagSupport#EVAL_BODY_INCLUDE} or {@link TagSupport#SKIP_BODY}
     * @throws JspException if property access fails
     */
    public int doStartTag() throws JspException {
        String[] mNameArray = moduleName.split(",");
        boolean flag = false;
        try {
            CarlosProperties proper = CarlosProperties.getInstance();

            for (int i = 0; i < mNameArray.length; i++) {
                String mname = mNameArray[i];
                if (propertiesOn(mname, proper)) {
                    flag = true;
                }
            }

        } catch (Exception e) {
            throw new JspException("Failed to get module load info", e);

        }
        if (reverse && !flag || !reverse && flag)
            return EVAL_BODY_INCLUDE;
        else
            return SKIP_BODY;

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
}
