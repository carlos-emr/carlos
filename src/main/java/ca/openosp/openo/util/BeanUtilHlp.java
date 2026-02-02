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

package ca.openosp.openo.util;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import ca.openosp.openo.utility.MiscUtils;

/**
 * <p>Title:BeanUtil </p>
 *
 * <p>Description: BeanUtil Provides a simplified interface for dynamically manipulating a Java Bean via reflection</p>
 *
 * @author Joel Legris
 * @version 1.0
 */
public class BeanUtilHlp {
    public BeanUtilHlp() {
    }

    /**
     * A convenience method used to retrieve the field value of a specified JavaBean
     *
     * @param bean
     * @param fieldName
     * @return the property value
     */
    public String getPropertyValue(Object bean, String fieldName) {

        String value = "";
        try {
            BeanWrapper beanWrapper = new BeanWrapperImpl(bean);
            Object propertyValue = beanWrapper.getPropertyValue(fieldName);
            value = propertyValue != null ? propertyValue.toString() : "null";
        } catch (Exception ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return value;
    }

}
