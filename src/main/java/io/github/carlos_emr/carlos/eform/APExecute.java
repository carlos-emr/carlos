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


package io.github.carlos_emr.carlos.eform;

import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.eform.data.DatabaseAP;

/**
 * Executes eForm database access point (AP) queries to populate eForm fields
 * with data from the database. APs are named SQL queries configured in
 * {@code apconfig.xml} that map database column values into eForm template
 * placeholders using {@code ${variable}} syntax.
 *
 * <p>Supports both plain-text and JSON output modes. In JSON mode, the raw
 * {@link com.fasterxml.jackson.databind.node.ArrayNode} is returned for
 * client-side JavaScript processing.</p>
 *
 * @see DatabaseAP
 * @see EFormLoader
 * @see EFormUtil#getValues(java.util.ArrayList, String)
 * @since 2006-05-25
 */
public class APExecute {

    /**
     * Default constructor.
     */
    public APExecute() {
    }


    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes the named AP query for a given demographic, replacing template
     * placeholders in the AP output with the values returned by the SQL query.
     *
     * @param ap String the AP name as defined in {@code apconfig.xml}
     * @param demographicNo String the demographic number to bind into the query
     * @return String the populated output template, or an empty string if the AP
     *         is not found or the query returns a mismatched number of columns
     */
    public String execute(String ap, String demographicNo) {
        EFormLoader.getInstance();
        DatabaseAP dap = EFormLoader.getAP(ap);
        
        if (dap == null) {
            MiscUtils.getLogger().error("DatabaseAP not found for ap: " + ap);
            return "";
        }
        
        String sql = DatabaseAP.parserReplace("demographic", demographicNo, dap.getApSQL());
        String output = dap.getApOutput();
        MiscUtils.getLogger().debug("SQL----" + sql);
        ArrayList<String> names = DatabaseAP.parserGetNames(output); //a list of ${apName} --> apName
        sql = DatabaseAP.parserClean(sql);  //replaces all other ${apName} expressions with 'apName'

        if (dap.isJsonOutput()) {
            ArrayNode values = EFormUtil.getJsonValues(names, sql);
            output = values.toString(); //in case of JsonOutput, return the whole JSONArray and let the javascript deal with it
        } else {
            ArrayList<String> values = EFormUtil.getValues(names, sql);
            if (values.size() != names.size()) {
                output = "";
            } else {
                for (int i = 0; i < names.size(); i++) {
                    output = DatabaseAP.parserReplace(names.get(i), values.get(i), output);
                }
            }
        }
        return output;
    }

    /**
     * Executes the named AP query for a given demographic and invoice number,
     * replacing template placeholders with database values. This overload
     * additionally binds the {@code invoiceNo} parameter into the SQL query.
     *
     * @param ap String the AP name as defined in {@code apconfig.xml}
     * @param demographicNo String the demographic number to bind into the query
     * @param invoiceNo Integer the invoice number to bind into the query
     * @return String the populated output template, or an empty string if the AP
     *         is not found or the query returns a mismatched number of columns
     */
    public String execute(String ap, String demographicNo, Integer invoiceNo) {
        EFormLoader.getInstance();
        DatabaseAP dap = EFormLoader.getAP(ap);
        
        if (dap == null) {
            MiscUtils.getLogger().error("DatabaseAP not found for ap: " + ap);
            return "";
        }
        
        MiscUtils.getLogger().debug("AP:" + ap);
        String sql = DatabaseAP.parserReplace("invoiceNo", String.valueOf(invoiceNo), dap.getApSQL());
        sql = DatabaseAP.parserReplace("demographic", demographicNo, sql);

        String output = dap.getApOutput();
        MiscUtils.getLogger().debug("SQL----" + sql);

        ArrayList<String> names = DatabaseAP.parserGetNames(output);
        sql = DatabaseAP.parserClean(sql);

        ArrayList<String> values = EFormUtil.getValues(names, sql);
        if (values.size() != names.size()) {
            output = "";
        } else {
            for (int i = 0; i < names.size(); i++) {
                output = DatabaseAP.parserReplace(names.get(i), values.get(i), output);
            }
        }

        return output;
    }
}
