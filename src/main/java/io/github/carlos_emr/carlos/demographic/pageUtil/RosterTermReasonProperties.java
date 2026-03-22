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


package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Singleton properties loader for roster termination reason codes.
 *
 * <p>Loads reason codes and descriptions from the {@code roster_termination_reasons.properties}
 * classpath resource. Provides lookup by code and a sorted set of all available codes
 * for use in UI dropdown menus.</p>
 *
 * @since 2026-03-17
 */
public class RosterTermReasonProperties extends Properties {
    private static RosterTermReasonProperties rosterTermReasonProperties = new RosterTermReasonProperties();
    private static SortedSet<String> termReasons = new TreeSet<String>();

    private static Logger logger = MiscUtils.getLogger();

    static {
        String propFile = "/roster_termination_reasons.properties";
        InputStream is = RosterTermReasonProperties.class.getResourceAsStream(propFile);
        if (is == null) try {
            is = new FileInputStream(propFile);
        } catch (FileNotFoundException e) {
            logger.error("Roster Termination Reaons file not found!", e);
        }

        try {
            rosterTermReasonProperties.load(is);
        } catch (IOException e) {
            logger.error("Error loading Roster Termination Reasons!", e);
        }
    }

    /**
     * Returns the singleton instance of RosterTermReasonProperties.
     *
     * @return RosterTermReasonProperties the shared instance
     */
    public static RosterTermReasonProperties getInstance() {
        return rosterTermReasonProperties;
    }

    /**
     * Returns the termination reason description for the given code.
     *
     * @param code String the roster termination reason code
     * @return String the reason description, or null if not found
     */
    public String getReasonByCode(String code) {
        return rosterTermReasonProperties.getProperty(code);
    }

    /**
     * Returns a sorted set of all available termination reason codes.
     *
     * @return SortedSet&lt;String&gt; the sorted reason codes
     */
    public SortedSet<String> getTermReasonCodes() {
        if (termReasons.isEmpty()) {

            Set<Object> kset = rosterTermReasonProperties.keySet();
            for (Object key : kset) {
                termReasons.add((String) key);
            }
        }
        return termReasons;

    }
}
