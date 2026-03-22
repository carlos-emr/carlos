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
import java.util.Hashtable;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.db.DBHandler;

/**
 * SQL-based clinical report numerator that evaluates a patient by executing a
 * SQL query against the database. Returns {@code true} if the query returns any
 * rows for the given patient's demographic number.
 *
 * <p>The SQL statement may contain the placeholder {@code ${demographic_no}} which
 * is replaced with the actual patient demographic number at evaluation time. If
 * output fields are configured, the first result row's values are captured into
 * the {@link #outputValues} map.</p>
 *
 * @see Numerator
 * @see SQLDenominator
 * @see ReportEvaluator
 * @since 2006-06-17
 */
public class SQLNumerator implements Numerator {
    String sql = null;
    String identifier = "count";
    String name = null;
    String id = null;
    String[] outputfields = null;
    Hashtable outputValues = null;

    String processString = "demographic_no";

    /**
     * Creates a new instance of SQLNumerator
     */
    public SQLNumerator() {

    }

    /**
     * Sets the SQL query string to execute during evaluation. May contain
     * {@code ${demographic_no}} placeholders.
     *
     * @param sql String the SQL query
     */
    public void setSQL(String sql) {
        this.sql = sql;
    }

    /**
     * Sets the result column identifier used for count-based evaluation (legacy).
     *
     * @param identifier String the column name to check for count
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Parses a comma-separated string of output field names into the output fields array.
     *
     * @param str String comma-separated field names, or {@code null} to skip
     */
    public void parseOutputFields(String str) {
        if (str != null) {
            try {
                if (str.indexOf(",") != -1) {
                    outputfields = str.split(",");
                } else {
                    outputfields = new String[1];
                    outputfields[0] = str;
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }
    }

    /** {@inheritDoc} */
    public String[] getOutputFields() {

        return outputfields;
    }

    /** {@inheritDoc} */
    public Hashtable getOutputValues() {
        return outputValues;
    }


    //TODO:Do i change this to pull fields out of the query?
    public boolean evaluateOLD(String demographicNo) {
        boolean evalTrue = false;

        try {

            ResultSet rs = DBHandler.GetSQL(sql.replaceAll("\\$\\{" + processString + "\\}", demographicNo));
            MiscUtils.getLogger().debug("SQL Statement: " + sql);
            while (rs.next()) {
                int count = rs.getInt(identifier);
                if (count > 0) {

                    evalTrue = true;
                }
            }
            MiscUtils.getLogger().debug("demo " + demographicNo + " eval: " + evalTrue);
            rs.close();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return evalTrue;
    }


    //The difference between this version of evaluate is that it evaluates true if there are any rows returned from the query.
    //as apposed to looking for the value of count(*).
    // change to get a list of params 
    public boolean evaluate(LoggedInInfo loggedInInfo, String demographicNo) {
        boolean evalTrue = false;

        outputValues = null;
        try {

            ResultSet rs = DBHandler.GetSQL(sql.replaceAll("\\$\\{" + processString + "\\}", demographicNo));
            MiscUtils.getLogger().debug("SQL Statement: " + sql);
            if (rs.next()) {
                evalTrue = true;
                if (outputfields != null) {
                    outputValues = new Hashtable();
                    for (int i = 0; i < outputfields.length; i++) {
                        outputValues.put(outputfields[i], Misc.getString(rs, outputfields[i]));
                    }
                    outputValues.put("_evaluation", Boolean.valueOf(evalTrue));
                }
                //for 

            }
            MiscUtils.getLogger().debug("demo " + demographicNo + " eval: " + evalTrue);
            rs.close();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return evalTrue;
    }


    public String getId() {
        return id;
    }

    public String getNumeratorName() {
        return name;
    }

    public void setNumeratorName(String name) {
        this.name = name;
    }

    public void setId(String id) {
        this.id = id;
    }

    String[] replaceKeys = null;
    Hashtable replaceableValues = null;

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
