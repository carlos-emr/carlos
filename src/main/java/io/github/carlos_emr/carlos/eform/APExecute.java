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
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.eform.data.DatabaseAP;

/**
 * @author jay
 */
public class APExecute {

    /**
     * Creates a new instance of APExecute
     */
    public APExecute() {
    }


    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static String requireDigitsOnly(String placeholderName, String value) {
        if (value == null || value.isEmpty()) return value;
        if (!value.matches("-?\\d+")) {
            throw new IllegalArgumentException("Non-numeric value for placeholder: " + placeholderName);
        }
        return value;
    }

    public String execute(String ap, String demographicNo) {
        EFormLoader.getInstance();
        DatabaseAP dap = EFormLoader.getAP(ap);

        if (dap == null) {
            MiscUtils.getLogger().error("DatabaseAP not found for ap: " + ap);
            return "";
        }

        try {
            requireDigitsOnly("demographic", demographicNo);
        } catch (IllegalArgumentException e) {
            MiscUtils.getLogger().error("Invalid demographic number for AP {}: {}", ap, e.getMessage());
            return "";
        }

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("demographic", demographicNo);
        ParameterizedSql query = DatabaseAP.parameterizeSql(dap.getApSQL(), replacements);
        String output = dap.getApOutput();
        ArrayList<String> names = DatabaseAP.parserGetNames(output); //a list of ${apName} --> apName

        if (dap.isJsonOutput()) {
            ArrayNode values = EFormUtil.getJsonValues(names, query);
            output = values.toString(); //in case of JsonOutput, return the whole JSONArray and let the javascript deal with it
        } else {
            ArrayList<String> values = EFormUtil.getValuesOrNull(names, query);
            if (values == null) {
                logFailedQuery(ap);
                output = "";
            } else if (!names.isEmpty() && values.size() != names.size()) {
                logUnusableResult(ap, names.size(), values.size());
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
     * Records an AP query that could not be executed or read.
     *
     * <p>Separate from {@link #logUnusableResult}: this is the case {@code EFormUtil.getValues} used
     * to erase, by reporting a failed query as an empty result — identical to a healthy query over a
     * patient with no matching data. These batch callers still render blank (there is no user to
     * prompt), but the condition now leaves a trace.</p>
     */
    private static void logFailedQuery(String ap) {
        MiscUtils.getLogger().error(
                "AP {} query could not be executed or read; the field will render blank", ap);
    }

    /**
     * Records a result the AP cannot be rendered from.
     *
     * <p>Both callers return {@code ""} here, which reaches printed records and generated patient
     * letters as a blank field indistinguishable from "this patient has no such data". That is a
     * decision worth keeping — these are batch paths with no user to prompt — but it was previously
     * taken with no log of any kind.</p>
     *
     * <p>A query that could not run is reported separately, by {@link #logFailedQuery}. This one
     * covers a shape mismatch, which the {@code getValuesOrNull} contract currently makes
     * unreachable; it is retained as the guard that a change to that contract must trip. An empty
     * result is deliberately not reported at all — a query that legitimately matched no rows is
     * data, not a defect.</p>
     */
    private static void logUnusableResult(String ap, int declaredNames, int returnedValues) {
        MiscUtils.getLogger().error(
                "AP {} returned an unusable result: output declares {} names but the query returned"
                        + " {} values; the field will render blank",
                ap, declaredNames, returnedValues);
    }

    public String execute(String ap, String demographicNo, Integer invoiceNo) {
        EFormLoader.getInstance();
        DatabaseAP dap = EFormLoader.getAP(ap);
        
        if (dap == null) {
            MiscUtils.getLogger().error("DatabaseAP not found for ap: " + ap);
            return "";
        }
        
        MiscUtils.getLogger().debug("AP:" + ap);

        try {
            requireDigitsOnly("demographic", demographicNo);
        } catch (IllegalArgumentException e) {
            MiscUtils.getLogger().error("Invalid demographic number for AP {}: {}", ap, e.getMessage());
            return "";
        }

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("invoiceNo", invoiceNo);
        replacements.put("demographic", demographicNo);
        ParameterizedSql query = DatabaseAP.parameterizeSql(dap.getApSQL(), replacements);

        String output = dap.getApOutput();
        ArrayList<String> names = DatabaseAP.parserGetNames(output);

        ArrayList<String> values = EFormUtil.getValuesOrNull(names, query);
        if (values == null) {
            logFailedQuery(ap);
            output = "";
        } else if (!names.isEmpty() && values.size() != names.size()) {
            logUnusableResult(ap, names.size(), values.size());
            output = "";
        } else {
            for (int i = 0; i < names.size(); i++) {
                output = DatabaseAP.parserReplace(names.get(i), values.get(i), output);
            }
        }

        return output;
    }
}
