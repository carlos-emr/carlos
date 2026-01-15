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


package ca.openosp.openo.prevention.reports;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import ca.openosp.openo.prevention.PreventionData;
import ca.openosp.openo.prevention.pageUtil.PreventionReportDisplay;
import ca.openosp.openo.util.UtilDateUtilities;

/**
 * @author jay
 */
public class FOBTReport implements PreventionReport {
    private static Logger log = MiscUtils.getLogger();


    /**
     * Creates a new instance of FOBTReport
     */
    public FOBTReport() {
    }

    public Hashtable<String, Object> runReport(LoggedInInfo loggedInInfo, ArrayList<ArrayList<String>> list, Date asofDate) {

        int inList = 0;
        double done = 0, doneWithGrace = 0;
        ArrayList<PreventionReportDisplay> returnReport = new ArrayList<PreventionReportDisplay>();

        for (int i = 0; i < list.size(); i++) {//for each  element in arraylist
            ArrayList<String> fieldList = list.get(i);
            Integer demo = Integer.valueOf(fieldList.get(0));

            //search   prevention_date prevention_type  deleted   refused
            ArrayList<Map<String, Object>> prevs = PreventionData.getPreventionData(loggedInInfo, "FOBT", demo);
            PreventionData.addRemotePreventions(loggedInInfo, prevs, demo, "FOBT", null);
            ArrayList<Map<String, Object>> noFutureItems = removeFutureItems(prevs, asofDate);
            ArrayList<Map<String, Object>> colonoscopys = PreventionData.getPreventionData(loggedInInfo, "COLONOSCOPY", demo);
            PreventionData.addRemotePreventions(loggedInInfo, colonoscopys, demo, "COLONOSCOPY", null);
            PreventionReportDisplay prd = new PreventionReportDisplay();
            prd.demographicNo = demo;
            prd.bonusStatus = "N";
            prd.billStatus = "N";
            Date prevDate = null;
            if (ineligible(prevs) || colonoscopywith10(colonoscopys, asofDate)) {
                prd.rank = 5;
                prd.lastDate = "------";
                prd.state = "Ineligible";
                prd.numMonths = "------";
                prd.color = "grey";
                inList++;
            } else if (noFutureItems.size() == 0) {// no info
                prd.rank = 1;
                prd.lastDate = "------";
                prd.state = "No Info";
                prd.numMonths = "------";
                prd.color = "Magenta";
            } else {
                Map<String, Object> h = noFutureItems.get(noFutureItems.size() - 1);
                DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                String prevDateStr = (String) h.get("prevention_date");

                try {
                    prevDate = formatter.parse(prevDateStr);
                } catch (Exception e) {
                    log.error("Error parsing prevention date: " + prevDateStr, e);
                }
                boolean refused = false;
                if (h.get("refused") != null && ((String) h.get("refused")).equals("1")) {
                    refused = true;
                }

                Calendar cal = Calendar.getInstance();
                cal.setTime(asofDate);
                cal.add(Calendar.YEAR, -2);
                Date dueDate = cal.getTime();
                cal.add(Calendar.MONTH, -6);
                Date cutoffDate = cal.getTime();

                // if prevDate is less than as of date and greater than 2 years prior
                Calendar bonusEl = Calendar.getInstance();
                bonusEl.setTime(asofDate);
                bonusEl.add(Calendar.MONTH, -30);
                Date bonusStartDate = bonusEl.getTime();

                String result = PreventionData.getExtValue((String) h.get("id"), "result");

                if (prevDate != null && !refused && bonusStartDate.before(prevDate) && asofDate.after(prevDate) && !result.equalsIgnoreCase("pending")) {
                    prd.bonusStatus = "Y";
                    prd.billStatus = "Y";
                    done++;
                }

                String numMonths = "------";
                if (prevDate != null) {
                    int num = UtilDateUtilities.getNumMonths(prevDate, asofDate);
                    numMonths = "" + num + " months";
                }


                if (prevDate != null && !refused && dueDate.after(prevDate) && cutoffDate.before(prevDate)) { // due
                    prd.rank = 2;
                    prd.lastDate = prevDateStr;
                    prd.state = "due";
                    prd.numMonths = numMonths;
                    prd.color = "yellow"; //FF00FF
                    doneWithGrace++;

                } else if (prevDate != null && !refused && cutoffDate.after(prevDate)) { // overdue
                    prd.rank = 2;
                    prd.lastDate = prevDateStr;
                    prd.state = "Overdue";
                    prd.numMonths = numMonths;
                    prd.color = "red"; //FF00FF

                } else if (refused) {  // recorded and refused
                    prd.rank = 3;
                    prd.lastDate = "-----";
                    prd.state = "Refused";
                    prd.numMonths = numMonths;
                    prd.color = "orange"; //FF9933
                } else if (prevDate != null && dueDate.before(prevDate) && result.equalsIgnoreCase("pending")) {
                    prd.rank = 4;
                    prd.lastDate = prevDateStr;
                    prd.state = "Pending";
                    prd.numMonths = numMonths;
                    prd.color = "pink";

                } else if (prevDate != null && (dueDate.before(prevDate) || dueDate.equals(prevDate))) {  // recorded done (prevDate >= dueDate)
                    prd.rank = 4;
                    prd.lastDate = prevDateStr;
                    prd.state = "Up to date";
                    prd.numMonths = numMonths;
                    prd.color = "green";
                }
            }
            letterProcessing(prd, "FOBF", asofDate, prevDate);
            returnReport.add(prd);

        }
        String percentStr = "0";
        String percentWithGraceStr = "0";
        double eligible = list.size() - inList;
        if (eligible != 0) {
            double percentage = (done / eligible) * 100;
            double percentageWithGrace = (done + doneWithGrace) / eligible * 100;
            percentStr = "" + Math.round(percentage);
            percentWithGraceStr = "" + Math.round(percentageWithGrace);
        }

        Collections.sort(returnReport);

        Hashtable<String, Object> h = new Hashtable<String, Object>();

        h.put("up2date", "" + Math.round(done));
        h.put("percent", percentStr);
        h.put("percentWithGrace", percentWithGraceStr);
        h.put("returnReport", returnReport);
        h.put("inEligible", "" + inList);
        h.put("eformSearch", "FOBT");
        h.put("followUpType", "FOBF");
        h.put("BillCode", "Q005A");
        return h;
    }

    boolean ineligible(Map<String, Object> h) {
        boolean ret = false;
        if (h.get("refused") != null && ((String) h.get("refused")).equals("2")) {
            ret = true;
        }
        return ret;
    }

    boolean ineligible(ArrayList<Map<String, Object>> list) {
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> h = list.get(i);
            if (ineligible(h)) {
                return true;
            }
        }
        return false;
    }

    boolean colonoscopywith10(ArrayList<Map<String, Object>> list, Date asofDate) {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        cal.setTime(asofDate);
        cal.add(Calendar.YEAR, -10);
        Date tenyearcutoff = cal.getTime();
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> h = list.get(i);
            if (h.get("refused") != null && ((String) h.get("refused")).equals("0")) {

                String prevDateStr = (String) h.get("prevention_date");
                Date prevDate = null;
                try {
                    prevDate = formatter.parse(prevDateStr);
                } catch (Exception e) {
                    log.error("Error parsing prevention date: " + prevDateStr, e);
                }

                if (prevDate != null && tenyearcutoff.before(prevDate)) {
                    return true;
                }
            }
        }
        return false;
    }


    //TODO: THIS MAY NEED TO BE REFACTORED AT SOME POINT IF MAM and PAP are exactly the same
    //If they don't have a FOBT Test with guidelines
    //Get contact methods
    //NO contact
    //Send letter
    //Was last contact within a year ago
    //NO
    //Send Letter 1
    //Was contact within the last year and at least one month ago
    //Yes count it

    //No contacts qualify
    //send letter 1
    //One contact qualifies
    //send letter 2
    //Two contacts qualify
    //Phone call
    //Reached limit no contact suggested

    //Measurement Type will be 1 per Prevention report, with the dataField holding method ie L1, L2, P1 (letter 1 , letter 2, phone call 1)
    private static final String LETTER1 = "L1";
    private static final String LETTER2 = "L2";
    private static final String PHONE1 = "P1";
    private static final String CALLFU = "Follow Up";

    private String letterProcessing(PreventionReportDisplay prd, String measurementType, Date asofDate, Date prevDate) {
        if (prd != null) {
            boolean inclUpToDate = false;
            if (prd.state.equals("Up to date")) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(asofDate);
                cal.add(Calendar.YEAR, -2);
                Date dueDate = cal.getTime();
                cal.add(Calendar.MONTH, -6);
                Date cutoffDate = cal.getTime();

                if (prevDate != null && ((dueDate.after(prevDate) && cutoffDate.before(prevDate)) || cutoffDate.after(prevDate))) {
                    inclUpToDate = true;
                }
            }

            if (prd.state.equals("No Info") || prd.state.equals("due") || prd.state.equals("Overdue") || inclUpToDate) {
                EctMeasurementsDataBeanHandler measurementDataHandler = new EctMeasurementsDataBeanHandler(prd.demographicNo, measurementType);
                Collection<EctMeasurementsDataBean> followupData = measurementDataHandler.getMeasurementsDataVector();

                if (followupData.size() == 0) {
                    prd.nextSuggestedProcedure = LETTER1;
                    return LETTER1;
                } else {
                    Calendar oneyear = Calendar.getInstance();
                    oneyear.setTime(asofDate);
                    oneyear.add(Calendar.YEAR, -1);

                    Calendar onemonth = Calendar.getInstance();
                    onemonth.setTime(asofDate);
                    onemonth.add(Calendar.MONTH, -1);

                    Date observationDate = null;
                    int count = 0;
                    int index = 0;
                    EctMeasurementsDataBean measurementData = null;

                    @SuppressWarnings("unchecked")
                    Iterator<EctMeasurementsDataBean> iterator = followupData.iterator();

                    while (iterator.hasNext()) {
                        measurementData = iterator.next();
                        observationDate = measurementData.getDateObservedAsDate();

                        if (index == 0) {
                            prd.lastFollowup = observationDate;
                            prd.lastFollupProcedure = measurementData.getDataField();

                            if (measurementData.getDateObservedAsDate().before(oneyear.getTime())) {
                                prd.nextSuggestedProcedure = LETTER1;
                                return LETTER1;
                            }

                            if (prd.lastFollupProcedure.equals(PHONE1)) {
                                prd.nextSuggestedProcedure = "----";
                                return "----";
                            }
                        }

                        if (observationDate.before(onemonth.getTime()) && observationDate.after(oneyear.getTime())) {
                            ++count;
                        } else if (count > 1 && observationDate.after(oneyear.getTime())) {
                            ++count;
                        }
                        ++index;
                    }

                    switch (count) {
                        case 0:
                            prd.nextSuggestedProcedure = LETTER1;
                            break;
                        case 1:
                            prd.nextSuggestedProcedure = LETTER2;
                            break;
                        case 2:
                            prd.nextSuggestedProcedure = PHONE1;
                            break;
                        default:
                            prd.nextSuggestedProcedure = "----";
                    }
                    return prd.nextSuggestedProcedure;
                }
            } else if (prd.state.equals("Refused")) {
                prd.nextSuggestedProcedure = "----";
            } else if (prd.state.equals("Ineligible")) {
                prd.nextSuggestedProcedure = "----";
            } else if (prd.state.equals("Pending")) {
                prd.nextSuggestedProcedure = CALLFU;
            } else if (prd.state.equals("Up to date")) {
                prd.nextSuggestedProcedure = "----";
            } else {
                log.error("prd.state appears to be null or a missed case : " + prd.state);
            }
        }
        return null;
    }

    private ArrayList<Map<String, Object>> removeFutureItems(ArrayList<Map<String, Object>> list, Date asOfDate) {
        ArrayList<Map<String, Object>> noFutureItems = new ArrayList<Map<String, Object>>();
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> map = list.get(i);
            String prevDateStr = (String) map.get("prevention_date");
            Date prevDate = null;
            try {
                prevDate = formatter.parse(prevDateStr);
            } catch (Exception e) {
                log.error("Error parsing prevention date: " + prevDateStr, e);
            }

            if (prevDate != null && prevDate.before(asOfDate)) {
                noFutureItems.add(map);
            }
        }
        return noFutureItems;
    }

}
