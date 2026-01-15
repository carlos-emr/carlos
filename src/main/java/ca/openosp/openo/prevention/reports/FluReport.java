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
import java.util.GregorianCalendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import ca.openosp.openo.commn.model.Demographic;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.demographic.data.DemographicData;
import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import ca.openosp.openo.prevention.PreventionData;
import ca.openosp.openo.prevention.pageUtil.PreventionReportDisplay;
import ca.openosp.openo.util.UtilDateUtilities;

/**
 * @author jay
 */
public class FluReport implements PreventionReport {
    private static Logger log = MiscUtils.getLogger();

    /**
     * Creates a new instance of FluReport
     */
    public FluReport() {
    }

    public Hashtable<String, Object> runReport(LoggedInInfo loggedInInfo, ArrayList<ArrayList<String>> list, Date asofDate) {
        int inList = 0;
        double done = 0;
        ArrayList<PreventionReportDisplay> returnReport = new ArrayList<PreventionReportDisplay>();

        for (int i = 0; i < list.size(); i++) {//for each  element in arraylist
            ArrayList<String> fieldList = list.get(i);
            Integer demo = Integer.valueOf(fieldList.get(0));

            ArrayList<Map<String, Object>> prevs = PreventionData.getPreventionData(loggedInInfo, "Flu", demo);
            PreventionData.addRemotePreventions(loggedInInfo, prevs, demo, "Flu", null);

            if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
                try {
                    ArrayList<HashMap<String, Object>> remotePreventions = PreventionData.getLinkedRemotePreventionData(loggedInInfo, "Flu", demo);
                    prevs.addAll(remotePreventions);

                    Collections.sort(prevs, new PreventionData.PreventionsComparator());
                } catch (Exception e) {
                    log.error("Error getting remote preventions.", e);
                }
            }

            ArrayList<Map<String, Object>> noFutureItems = removeFutureItems(prevs, asofDate);
            PreventionReportDisplay prd = new PreventionReportDisplay();
            prd.demographicNo = demo;
            prd.bonusStatus = "N";
            prd.billStatus = "N";

            Date[] begendDates = getStartEndDate(asofDate);
            Date beginingOfYear = begendDates[0];
            Date endOfYear = begendDates[1];

            Calendar cal = Calendar.getInstance();
            cal.setTime(asofDate);
            cal.add(Calendar.MONTH, -6);
            Date dueDate = cal.getTime();
            Date cutoffDate = dueDate;
            if (!isOfAge(loggedInInfo, demo.toString(), asofDate)) {
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
                boolean refused = false;
                if (h.get("refused") != null && ((String) h.get("refused")).equals("1")) {
                    refused = true;
                }

                String prevDateStr = (String) h.get("prevention_date");

                if (refused && noFutureItems.size() > 1) {
                    for (int pr = (noFutureItems.size() - 2); pr > -1; pr--) {
                        Map<String, Object> h2 = noFutureItems.get(pr);
                        if (h2.get("refused") != null && ((String) h2.get("refused")).equals("0")) {
                            prevDateStr = (String) h2.get("prevention_date");
                            pr = 0;
                        }
                    }
                }


                Date prevDate = null;
                try {
                    prevDate = formatter.parse(prevDateStr);
                } catch (Exception e) {
                    log.error("Error parsing prevention date: " + prevDateStr, e);
                }

                String numMonths = "------";
                if (prevDate != null) {
                    int num = UtilDateUtilities.getNumMonths(prevDate, asofDate);
                    numMonths = "" + num + " months";
                }

                if (prevDate != null && beginingOfYear.before(prevDate) && endOfYear.after(prevDate) && isOfAge(loggedInInfo, demo.toString(), asofDate)) {
                    if (refused) {
                        prd.billStatus = "Y";
                    } else {
                        prd.bonusStatus = "Y";
                        prd.billStatus = "Y";
                        done++;
                    }
                }

                if (prevDate != null && !refused && dueDate.after(prevDate) && cutoffDate.before(prevDate)) { // due
                    prd.rank = 2;
                    prd.lastDate = prevDateStr;
                    prd.state = "due";
                    prd.numMonths = numMonths;
                    prd.color = "yellow"; //FF00FF

                } else if (prevDate != null && !refused && cutoffDate.after(prevDate)) { // overdue
                    prd.rank = 2;
                    prd.lastDate = prevDateStr;
                    prd.state = "Overdue";
                    prd.numMonths = numMonths;
                    prd.color = "red"; //FF00FF

                } else if (refused) {  // recorded and refused
                    prd.rank = 3;
                    prd.lastDate = prevDateStr;
                    prd.state = "Refused";
                    prd.numMonths = numMonths;
                    prd.color = "orange"; //FF9933
                } else if (prevDate != null && (dueDate.before(prevDate) || dueDate.equals(prevDate))) {  // recorded done (prevDate >= dueDate)
                    prd.rank = 4;
                    prd.lastDate = prevDateStr;
                    prd.state = "Up to date";
                    prd.numMonths = numMonths;
                    prd.color = "green";
                } else {
                    log.error("Missed case : refused=" + refused + ", prevDate=" + prevDate + ", dueDate=" + dueDate + ", cutoffDate=" + cutoffDate);
                }
            }

            if (asofDate.before(endOfYear) && asofDate.after(beginingOfYear)) {
                letterProcessing(prd, cutoffDate);
            } else {
                EctMeasurementsDataBeanHandler measurementData = new EctMeasurementsDataBeanHandler(prd.demographicNo, "FLUF");
                Collection<EctMeasurementsDataBean> fluFollowupData = measurementData.getMeasurementsDataVector();
                if (fluFollowupData.size() > 0) {
                    EctMeasurementsDataBean fluData = fluFollowupData.iterator().next();
                    prd.lastFollowup = fluData.getDateObservedAsDate();
                    prd.lastFollupProcedure = fluData.getDataField();
                }
                prd.nextSuggestedProcedure = "----";
            }
            returnReport.add(prd);

        }
        String percentStr = "0";
        double eligible = list.size() - inList;
        if (eligible != 0) {
            double percentage = (done / eligible) * 100;
            percentStr = "" + Math.round(percentage);
        }

        Collections.sort(returnReport);

        Hashtable<String, Object> h = new Hashtable<String, Object>();

        h.put("up2date", "" + Math.round(done));
        h.put("percent", percentStr);
        h.put("returnReport", returnReport);
        h.put("inEligible", "" + inList);
        h.put("eformSearch", "Flu");
        h.put("followUpType", "FLUF");
        h.put("BillCode", "Q003A");
        return h;
    }

    boolean ineligible(Hashtable<String, String> h) {
        boolean ret = false;
        if (h.get("refused") != null && (h.get("refused")).equals("2")) {
            ret = true;
        }
        return ret;
    }


    boolean ineligible(ArrayList<Hashtable<String, String>> list) {
        for (int i = 0; i < list.size(); i++) {
            Hashtable<String, String> h = list.get(i);
            if (ineligible(h)) {
                return true;
            }
        }
        return false;
    }
    
    boolean isOfAge(LoggedInInfo loggedInInfo, String d, Date asofDate) {
        boolean isAge = true;
        DemographicData demoData = new DemographicData();
        Demographic demo = demoData.getDemographic(loggedInInfo, d);
        Date demoDOB = DemographicData.getDOBObj(demo);

        Calendar bonusEl = Calendar.getInstance();
        bonusEl.setTime(asofDate);
        int year = bonusEl.get(Calendar.YEAR);
        Calendar cal = new GregorianCalendar(year, Calendar.DECEMBER, 31);
        cal.add(Calendar.YEAR, -65);
        Date cutoff = cal.getTime();

        if (demoDOB != null && demoDOB.after(cutoff)) {
            isAge = false;
        }
        return isAge;
    }

    Date[] getStartEndDate(Date asofDate) {
        // if prevDate is in the previous year
        Calendar bonusEl = Calendar.getInstance();
        bonusEl.setTime(asofDate);
        Date[] retDates = new Date[2];
        Date endOfYear;
        Date beginingOfYear;
        int year = bonusEl.get(Calendar.YEAR);
        Calendar cal;
        if (bonusEl.get(Calendar.MONTH) == Calendar.JANUARY) {
            cal = new GregorianCalendar(year, Calendar.FEBRUARY, 1);
            endOfYear = cal.getTime();
            cal = new GregorianCalendar(year - 1, Calendar.AUGUST, 31);
            beginingOfYear = cal.getTime();
        } else {
            cal = new GregorianCalendar(year + 1, Calendar.FEBRUARY, 1);
            endOfYear = cal.getTime();
            cal = new GregorianCalendar(year, Calendar.AUGUST, 31);
            beginingOfYear = cal.getTime();
        }
        retDates[0] = beginingOfYear;
        retDates[1] = endOfYear;
        return retDates;
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

    //FLu is different then the others IT only has one letter and a phone call
    //If they don't have a FLu shot
    //When was The last contact method?
    //NO contact
    //Send letter
    //Before Cuttoff Date
    //SEnd Letter
    //Since Cuttoff Date
    //HAs it been 1 month
    //Suggest Phone call
    //Less than a month?
    //Do nothing but display a message of when letter was sent.

    //Measurement Type will be 1 per Prevention report, with the dataField holding method ie L1, L2, P1 (letter 1 , letter 2, phone call 1)
    private static final String LETTER1 = "L1";
    private static final String PHONE1 = "P1";

    private String letterProcessing(PreventionReportDisplay prd, Date cuttoffDate) {
        if (prd != null) {
            if ("No Info".equals(prd.state) || "due".equals(prd.state) || "Overdue".equals(prd.state)) {
                EctMeasurementsDataBeanHandler measurementData = new EctMeasurementsDataBeanHandler(prd.demographicNo, "FLUF");
                Collection<EctMeasurementsDataBean> fluFollowupData = measurementData.getMeasurementsDataVector();
                if (fluFollowupData.size() == 0) {
                    prd.nextSuggestedProcedure = LETTER1;
                    return LETTER1;
                } else {
                    EctMeasurementsDataBean fluData = fluFollowupData.iterator().next();
                    prd.lastFollowup = fluData.getDateObservedAsDate();
                    prd.lastFollupProcedure = fluData.getDataField();
                    if (fluData.getDateObservedAsDate().before(cuttoffDate)) {
                        prd.nextSuggestedProcedure = LETTER1;
                        return LETTER1;
                    } else {
                        Calendar today = Calendar.getInstance();
                        long num = UtilDateUtilities.getNumDays(fluData.getDateObservedAsDate(), today.getTime());
                        if (num >= 28 && !prd.lastFollupProcedure.equals(PHONE1)) {
                            prd.nextSuggestedProcedure = PHONE1;
                            return PHONE1;
                        } else {
                            prd.nextSuggestedProcedure = "----";
                        }
                    }
                }
            } else if ("Refused".equals(prd.state)) {
                EctMeasurementsDataBeanHandler measurementData = new EctMeasurementsDataBeanHandler(prd.demographicNo, "FLUF");
                Collection<EctMeasurementsDataBean> fluFollowupData = measurementData.getMeasurementsDataVector();
                if (fluFollowupData.size() > 0) {
                    EctMeasurementsDataBean fluData = fluFollowupData.iterator().next();
                    prd.lastFollowup = fluData.getDateObservedAsDate();
                    prd.lastFollupProcedure = fluData.getDataField();
                }
            } else if ("Ineligible".equals(prd.state)) {
                // Do nothing
            } else if ("Up to date".equals(prd.state)) {
                EctMeasurementsDataBeanHandler measurementDataHandler = new EctMeasurementsDataBeanHandler(prd.demographicNo, "FLUF");
                Collection<EctMeasurementsDataBean> followupData = measurementDataHandler.getMeasurementsDataVector();
                if (followupData.size() > 0) {
                    EctMeasurementsDataBean measurementData = followupData.iterator().next();
                    prd.lastFollowup = measurementData.getDateObservedAsDate();
                    prd.lastFollupProcedure = measurementData.getDataField();
                }
            } else {
                log.error("prd.state appears to be null or a missed case : " + prd.state);
            }
        }
        return null;
    }
}
