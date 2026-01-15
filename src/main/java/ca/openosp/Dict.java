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


package ca.openosp;

import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

/**
 * Dictionary helper class for storing and retrieving strings.
 * 
 * <p>This class extends {@link Properties} and provides case-insensitive
 * key access for string values. All keys are automatically converted to
 * uppercase for storage and retrieval, ensuring consistent access regardless
 * of the case used when setting or getting values.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * Dict dict = new Dict();
 * dict.setDef("name", "John Doe");
 * String name = dict.getDef("NAME"); // Returns "John Doe"
 * </pre>
 */
public class Dict extends Properties {
    
    /**
     * Constructs an empty dictionary.
     */
    public Dict() {
    }

    /**
     * Sets a definition (key-value pair) in the dictionary.
     * The key is automatically converted to uppercase for case-insensitive access.
     * 
     * @param name the key name (will be converted to uppercase)
     * @param val the value to associate with the key
     */
    public void setDef(String name, String val) {
        setProperty(name.toUpperCase(), val);

    }

    /**
     * Sets multiple definitions from a 2D array of key-value pairs.
     * Each inner array should contain exactly 2 elements: [key, value].
     * 
     * @param pairs 2D array where each row is [key, value]
     */
    public void setDef(String[][] pairs) {
        for (int i = 0; i < pairs.length; i++)
            setDef(pairs[i][0], pairs[i][1]);
    }

    /**
     * Sets multiple definitions from parallel arrays of names and values.
     * If the arrays are of different lengths, only processes up to the shorter length.
     * 
     * @param names array of key names
     * @param vals array of values corresponding to the keys
     */
    public void setDef(String[] names, String[] vals) {
        int len = names.length;
        if (len > vals.length) len = vals.length;
        for (int i = 0; i < len; i++) {
            setDef(names[i], vals[i]);

        }
    }

    /**
     * Populates the dictionary with all parameters from an HTTP request.
     * All request parameter names and values are added to the dictionary.
     * 
     * @param req the HTTP servlet request containing parameters to extract
     */
    public void setDef(HttpServletRequest req) {
        java.util.Enumeration num = req.getParameterNames();
        while (num.hasMoreElements()) {
            String name = (String) num.nextElement();
            String val = req.getParameter(name);
            setDef(name, val);
        }
    }

    /**
     * Gets a definition value by key name.
     * The key is converted to uppercase for lookup.
     * 
     * @param name the key name (case-insensitive)
     * @return the value associated with the key, or empty string if not found
     */
    public String getDef(String name) {
        return getDef(name, "");
    }

    /**
     * Gets a definition value by key name with a default value.
     * The key is converted to uppercase for lookup.
     * 
     * @param name the key name (case-insensitive)
     * @param dflt the default value to return if the key is not found
     * @return the value associated with the key, or the default value if not found
     */
    public String getDef(String name, String dflt) {
        String val = getProperty(name.toUpperCase(), dflt);
        return val;
    }

    /**
     * Gets a definition value by key name with a maximum length limit.
     * If the value exceeds the limit, it is truncated. The key is converted to uppercase for lookup.
     * 
     * @param name the key name (case-insensitive)
     * @param dflt the default value to return if the key is not found
     * @param nLimit the maximum length of the returned value
     * @return the value associated with the key (possibly truncated), or the default value if not found
     */
    public String getShortDef(String name, String dflt, int nLimit) {
        String val = getProperty(name.toUpperCase(), dflt);
        int nLength = val.length();
        if (nLength > nLimit) {
            val = val.substring(0, nLimit);
        }
        return val;
    }

}
