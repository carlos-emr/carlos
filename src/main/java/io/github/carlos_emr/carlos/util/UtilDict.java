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

import java.util.Enumeration;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Case-insensitive dictionary extending {@link Properties} for storing and retrieving
 * name-value pairs. All keys are converted to uppercase on storage and retrieval.
 * Provides convenience methods for populating from HTTP request parameters and arrays.
 *
 * @since 2001-01-01
 */
public final class UtilDict extends Properties {
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Retrieves the value for the given key (case-insensitive), returning an empty string if not found.
     *
     * @param name String the key to look up
     * @return String the associated value, or an empty string if not found
     */
    public String getDef(String name) {
        return getDef(name, "");
    }

    /**
     * Retrieves the value for the given key (case-insensitive), returning the default if not found.
     *
     * @param name String the key to look up
     * @param dflt String the default value to return if the key is not found
     * @return String the associated value, or the default
     */
    public String getDef(String name, String dflt) {
        String result = getProperty(name.toUpperCase(), dflt);
        logger.debug("key=" + name + ", value=" + result);
        return (result);
    }

    /**
     * Retrieves and truncates the value for the given key to a maximum length.
     *
     * @param name String the key to look up
     * @param dflt String the default value if the key is not found
     * @param nLimit int the maximum number of characters to return
     * @return String the truncated value
     */
    public String getShortDef(String name, String dflt, int nLimit) {
        String val = getProperty(name.toUpperCase(), dflt);
        int nLength = val.length();
        if (nLength > nLimit) {
            val = val.substring(0, nLimit);
        }
        return val;
    }

    /**
     * Sets a value for the given key (case-insensitive).
     *
     * @param name String the key to store
     * @param val String the value to associate with the key
     */
    public void setDef(String name, String val) {
        setProperty(name.toUpperCase(), val);
    }

    /**
     * Sets multiple values from a two-dimensional array where each element is a [key, value] pair.
     *
     * @param names String[][] the array of key-value pairs
     */
    public void setDef(String[][] names) {
        for (int i = 0; i < names.length; i++)
            setDef(names[i][0], names[i][1]);
    }

    public void setDef(String[] names, String[] vals) {
        int len = names.length;
        if (len > vals.length) len = vals.length;
        for (int i = 0; i < len; i++) {
            setDef(names[i], vals[i]);
        }
    }

    public void setDef(HttpServletRequest req) {
        Enumeration varEnum = req.getParameterNames();
        while (varEnum.hasMoreElements()) {
            String name = (String) varEnum.nextElement();
            String val = req.getParameter(name);
            setDef(name, val);
        }
    }
}
