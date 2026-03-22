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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.collections4.KeyValue;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import io.github.carlos_emr.carlos.mds.data.ProviderData;

/**
 * Evaluates a clinical report by iterating over the patient population defined by a
 * {@link Denominator} and testing each patient against a primary {@link Numerator}
 * and up to 11 additional numerators. Collects pass/fail results, counts, percentages,
 * and optional measurement data for each patient.
 *
 * <p>Results are stored in an internal list of hashtables accessible via
 * {@link #getReportResultList()}, and summary statistics are available through
 * {@link #getNumeratorCount()}, {@link #getDenominatorCount()}, and
 * {@link #getPercentage()}.</p>
 *
 * @see Numerator
 * @see Denominator
 * @see ClinicalReportManager
 * @since 2006-06-17
 */
public class ReportEvaluator {

    /**
     * Creates a new instance of ReportEvaluator
     */
    int denominatorCount = 0;
    int numeratorCount = 0;
    Denominator denominator = null;
    Numerator numerator = null;
    Numerator numerator2 = null;
    Numerator[] numerators = new Numerator[11];

    private ArrayList<Hashtable<String, Object>> reportResultList = null;


    public ReportEvaluator() {
    }

    /**
     * Evaluates a report with a single numerator against the denominator population.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context
     * @param deno Denominator the patient population
     * @param numer Numerator the clinical criterion to evaluate
     */
    public void evaluate(LoggedInInfo loggedInInfo, Denominator deno, Numerator numer) {
        evaluate(loggedInInfo, deno, numer, null, null, true);
    }

    /**
     * Evaluates a report with a primary numerator and additional numerators.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context
     * @param deno Denominator the patient population
     * @param numer Numerator the primary criterion
     * @param numer2 Numerator[] additional numerators (all must pass for a positive result)
     */
    public void evaluate(LoggedInInfo loggedInInfo, Denominator deno, Numerator numer, Numerator[] numer2) {
        evaluate(loggedInInfo, deno, numer, numer2, null, true);
    }

    /**
     * Evaluates a report with additional measurement fields to include in results.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context
     * @param deno Denominator the patient population
     * @param numer Numerator the clinical criterion
     * @param additionalFields List&lt;KeyValue&gt; extra measurement fields to attach to results
     */
    public void evaluate(LoggedInInfo loggedInInfo, Denominator deno, Numerator numer, List<KeyValue> additionalFields) {
        evaluate(loggedInInfo, deno, numer, null, additionalFields, true);
    }

    /**
     * Full evaluation method. Iterates over the denominator patient list, evaluates
     * the primary numerator and all additional numerators for each patient, and
     * collects results with optional measurement data.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context
     * @param deno Denominator the patient population
     * @param numer Numerator the primary criterion
     * @param numers Numerator[] additional numerators (all must pass for a positive result)
     * @param additionalFields List&lt;KeyValue&gt; extra measurement fields to attach, or {@code null}
     * @param includeFalseResults boolean whether to include patients who fail the criteria
     */
    public void evaluate(LoggedInInfo loggedInInfo, Denominator deno, Numerator numer, Numerator[] numers, List<KeyValue> additionalFields, boolean includeFalseResults) {
        denominator = deno;
        numerator = numer;
        this.numerators = numers;
        List demoList = deno.getDenominatorList();
        denominatorCount = demoList.size();
        setReportResultList(new ArrayList<Hashtable<String, Object>>());
        for (int i = 0; i < demoList.size(); i++) {
            String demo = (String) demoList.get(i);
            boolean bool = numer.evaluate(loggedInInfo, demo);

            boolean bool2 = true;
            for (int x = 0; x < 11; x++) {
                if (numers[x] != null) {
                    boolean res = numers[x].evaluate(loggedInInfo, demo);
                    if (!res) {
                        bool2 = false;
                    }
                }
            }

            //Object obj = numer.getOutputValues();  // PROBLEM IS THAT THIS WILL ALWAYS HAVE A VALUE
            Hashtable<String, Object> h = new Hashtable<String, Object>();
            h.put("_demographic_no", demo);
            h.put("_report_result", Boolean.valueOf(bool && bool2));

            if (additionalFields != null) {
                for (KeyValue field : additionalFields) {
                    String key = (String) field.getKey();
                    String val = (String) field.getValue();

                    EctMeasurementsDataBeanHandler ect = new EctMeasurementsDataBeanHandler(Integer.valueOf(demo), val);
                    Collection<EctMeasurementsDataBean> v = ect.getMeasurementsDataVector();
                    //Execute for the value and attach it to the key in the hashtable
                    //Object obj =
                    if (v.iterator().hasNext()) {
                        h.put(key, v.iterator().next());
                    }

                }
            }


            if (includeFalseResults) {
                getReportResultList().add(h);
            } else {
                if (bool && bool2) {
                    getReportResultList().add(h);
                }
            }

//            if (obj != null){
//                getReportResultList().add(obj);
//            }
            if (bool && bool2) {
                numeratorCount++;
            }

        }

    }

    /**
     * Returns the total number of patients in the denominator set.
     *
     * @return int the denominator count
     */
    public int getDenominatorCount() {
        return denominatorCount;
    }

    /**
     * Returns the number of patients who passed all numerator criteria.
     *
     * @return int the numerator count
     */
    public int getNumeratorCount() {
        return numeratorCount;
    }

    /**
     * Calculates the percentage of patients who passed the numerator criteria.
     *
     * @return float the percentage (0-100), or 0 on division by zero
     */
    public float getPercentage() {
        float percentage = 0;
        try {
            percentage = ((float) getNumeratorCount() / (float) getDenominatorCount()) * 100;
        } catch (java.lang.ArithmeticException arithEx) {
            MiscUtils.getLogger().error("Error", arithEx);
            //request.setAttribute("divisionByZero",denominatorId);
            percentage = 0;
        }
        return percentage;
    }

    /**
     * Returns the percentage as a truncated integer.
     *
     * @return int the percentage as an integer (0-100)
     */
    public int getPercentageInt() {
        return Float.valueOf(getPercentage()).intValue();
    }


    /**
     * Generates a CSV-formatted string of the report summary including provider name
     * (if available), numerator count, denominator count, and percentage.
     *
     * @return String the CSV representation of the report summary
     */
    public String getCSV() {
        String csv = null;
        if (denominator.hasReplaceableValues()) {
            String providerNo = (String) denominator.getReplaceableValues().get("provider_no");
            csv = "'" + getProviderStringName(providerNo) + "','" + getNumeratorCount() + "','" + getDenominatorCount() + "','" + getPercentageInt() + "'";
        } else {
            csv = "'" + getNumeratorCount() + "','" + getDenominatorCount() + "','" + getPercentageInt() + "'";
        }

        return csv;
    }


    /**
     * Builds the display name for this report evaluation combining the numerator
     * name, denominator name, and any replaceable value details (e.g. provider name).
     *
     * @return String the composite report name
     */
    public String getName() {
        StringBuilder name = new StringBuilder();
        name.append(numerator.getNumeratorName());
        name.append("/");
        name.append(denominator.getDenominatorName());
        if (denominator.hasReplaceableValues()) {
            name.append(" (");
            String[] repKeys = denominator.getReplaceableKeys();
            Hashtable repVals = denominator.getReplaceableValues();
            for (int i = 0; i < repKeys.length; i++) {
                //provider_no:999998  if key is provider_no look up providers name
                MiscUtils.getLogger().debug("repKeys " + repKeys[i]);
                if (repKeys[i] != null && repKeys[i].equals("provider_no")) {
                    name.append("Provider: " + getProviderStringName("" + repVals.get(repKeys[i])));
                } else {
                    name.append(repKeys[i] + ":" + repVals.get(repKeys[i]));
                }
            }
            name.append(")");
        }

        return name.toString();
    }

    private String getProviderStringName(String providerNo) {
        return ProviderData.getProviderName(providerNo);
    }

    //private String getProvider

    /**
     * Returns the list of per-patient result hashtables from the evaluation.
     *
     * @return ArrayList&lt;Hashtable&lt;String, Object&gt;&gt; the result list
     */
    public ArrayList<Hashtable<String, Object>> getReportResultList() {
        return reportResultList;
    }

    public void setReportResultList(ArrayList<Hashtable<String, Object>> reportResultList) {
        this.reportResultList = reportResultList;
    }
}
