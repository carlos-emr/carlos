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


package io.github.carlos_emr.carlos.report.ClinicalReports;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;

/**
 * This is for straight SQLDenominators  not sure if it should return a more specialised list
 *
 * @author jay
 */
public class SQLDenominator implements Denominator {
    String sql = null;
    String exeSql = null;
    String resultString = "demographic_no";
    String name;
    String id;
    String[] replaceKeys = null;
    Hashtable replaceableValues = null;


    /**
     * Creates a new instance of SQLDenominator
     */
    public SQLDenominator() {
    }

    public void setSQL(String sql) {
        this.sql = sql;
    }

    public void setResultString(String str) {
        this.resultString = str;
    }

    public List getDenominatorList() {
        ArrayList list = new ArrayList();
        try {

            List<Object> paramValues = new ArrayList<>();
            if (replaceableValues != null) {
                MiscUtils.getLogger().debug("has replaceablevalues {}", replaceableValues.size());
                MiscUtils.getLogger().debug("before replace \n{}", sql);
                exeSql = replaceAllParameterized(sql, replaceableValues, paramValues);
            } else {
                MiscUtils.getLogger().debug("doesn't have replaceablevalues");
                exeSql = sql;
                MiscUtils.getLogger().debug("sql {}", sql);
            }

            MiscUtils.getLogger().debug("SQL Statement: " + exeSql);
            try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(
                    LegacyJdbcQuery.trustedReportSelectSql(exeSql), paramValues.toArray())) {
                while (rs.next()) {
                    String toAdd = Misc.getString(rs, resultString);
                    list.add(toAdd);
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Clinical report denominator query failed — results may be incomplete", e);
        }
        return list;
    }

    public String getDenominatorName() {
        return this.name;
    }

    public void setDenominatorName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String replaceAll(String str, Hashtable replacers) {
        Enumeration e = replacers.keys();
        while (e.hasMoreElements()) {
            String processString = (String) e.nextElement();
            String replaceValue = (String) replacers.get(processString);
            str = str.replaceAll("\\$\\{" + processString + "\\}", replaceValue);
            MiscUtils.getLogger().debug(str);

        }
        return str;
    }

    /**
     * Replaces ${key} placeholders in the SQL template with ? parameter markers
     * and collects the corresponding values in order for parameterized query binding.
     *
     * @param template the SQL template containing ${key} placeholders
     * @param replacers Hashtable mapping placeholder keys to their values
     * @param paramValues List to collect parameter values in the order they appear
     * @return String the SQL with ${key} placeholders replaced by ?
     */
    private String replaceAllParameterized(String template, Hashtable replacers, List<Object> paramValues) {
        // Single-pass left-to-right replacement to ensure parameter order matches
        // the position of ${key} placeholders in the SQL template.
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf("${", i);
            if (start < 0) {
                result.append(template.substring(i));
                break;
            }
            int end = template.indexOf("}", start);
            if (end < 0) {
                result.append(template.substring(i));
                break;
            }
            result.append(template, i, start);
            String key = template.substring(start + 2, end);
            String value = (String) replacers.get(key);
            if (value != null) {
                result.append("?");
                paramValues.add(value);
            } else {
                result.append(template, start, end + 1);
            }
            i = end + 1;
        }
        return result.toString();
    }

    public String[] getReplaceableKeys() {
        return replaceKeys;
    }

    public void parseReplaceValues(String str) {
        if (str != null) {
            try {
                MiscUtils.getLogger().debug("parsing string " + str);
                if (str.indexOf(",") != -1) {
                    replaceKeys = str.split(",");
                } else {
                    replaceKeys = new String[1];
                    replaceKeys[0] = str;
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }
    }

    public boolean hasReplaceableValues() {
        boolean repVal = false;
        if (replaceKeys != null) {
            repVal = true;
        }
        return repVal;
    }

    public void setReplaceableValues(Hashtable vals) {
        replaceableValues = vals;
    }

    public Hashtable getReplaceableValues() {
        return replaceableValues;
    }


}
