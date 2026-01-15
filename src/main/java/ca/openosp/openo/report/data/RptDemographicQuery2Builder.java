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

package ca.openosp.openo.report.data;

import ca.openosp.openo.messenger.util.MsgStringQuote;
import ca.openosp.openo.commn.dao.forms.FormsDao;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.prevention.reports.PreventionReportUtil;
import ca.openosp.openo.report.pageUtil.RptDemographicReport2Form;
import ca.openosp.openo.util.DateUtils;
import ca.openosp.openo.util.UtilDateUtilities;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RptDemographicQuery2Builder {

    int theWhereFlag;
    int theFirstFlag;
    StringBuilder stringBuffer = null;

    public void whereClause() {
        if (stringBuffer != null) {
            if (theWhereFlag == 0) {
                stringBuffer.append(" where ");
                theWhereFlag = 1;
            }
        }
    }

    public void firstClause() {
        if (theFirstFlag != 0) {
            stringBuffer.append(" and ");
            theFirstFlag = 1;
        }
    }

    public RptDemographicQuery2Builder() {
    }

    public ArrayList<ArrayList<String>> buildQuery(LoggedInInfo loggedInInfo, RptDemographicReport2Form frm) {
        return buildQuery(loggedInInfo, frm, null);
    }

    public ArrayList<ArrayList<String>> buildQuery(LoggedInInfo loggedInInfo, RptDemographicReport2Form frm, String asofRosterDate) {
        MiscUtils.getLogger().debug("in buildQuery");

        if (frm == null) {
            frm = new RptDemographicReport2Form();
        }
        String[] select = frm.getSelect();
        if (select == null || select.length == 0) {
            MiscUtils.getLogger().debug("No columns selected. Returning empty result.");
            return new ArrayList<>();
        }
        stringBuffer = new StringBuilder("select ");

        // Initialize parameters map for parameterized query
        Map<String, Object> params = new HashMap<>();

        String ageStyle = frm.getAgeStyle();
        String yearStyle = frm.getAge();
        String startYear = frm.getStartYear();
        String endYear = frm.getEndYear();
        String[] rosterStatus = frm.getRosterStatus();
        String[] patientStatus = frm.getPatientStatus();
        String[] providers = frm.getProviderNo();

        String firstName = frm.getFirstName();
        String lastName = frm.getLastName();
        String sex = frm.getSex();

        String orderBy = frm.getOrderBy();
        String limit = frm.getResultNum();

        String asofDate = frm.getAsofDate();
        String asofDateSql; // SQL representation (either CURRENT_DATE or :asofDate parameter)
        boolean useAsofDateParam = false; // Track whether :asofDate parameter will be used in the SQL

        if (asofDate == null || asofDate.trim().isEmpty()
                || UtilDateUtilities.getDateFromString(asofDate, "yyyy-MM-dd") == null) {
            asofDateSql = "CURRENT_DATE";
        } else {
            asofDateSql = ":asofDate";
            useAsofDateParam = true;
        }

        RptDemographicColumnNames demoCols = new RptDemographicColumnNames();

        MsgStringQuote s = new MsgStringQuote();
        if (firstName != null) {
            firstName = firstName.trim();
        }

        if (lastName != null) {
            lastName = lastName.trim();
        }

        if (sex != null) {
            sex = sex.trim();
        }

        theWhereFlag = 0;
        theFirstFlag = 0;

        boolean getprovider = false;
        for (int i = 0; i < select.length; i++) {
            if (select[i].equalsIgnoreCase("provider_name")) {
                stringBuffer.append(" concat(p.last_name,', ',p.first_name) " + select[i] + " ");
                getprovider = true;
                if (i < (select.length - 1)) {
                    stringBuffer.append(", ");
                }
                continue;
            }
            if (i == (select.length - 1)) {

                if (select[i].equalsIgnoreCase("ver")) {
                    stringBuffer.append(" CAST(d." + select[i] + " as CHAR) ");
                } else {
                    stringBuffer.append(" d." + select[i] + " ");
                }

            } else {


                if (select[i].equalsIgnoreCase("ver")) {
                    stringBuffer.append(" CAST(d." + select[i] + " as CHAR) " + ", ");
                } else {
                    stringBuffer.append(" d." + select[i] + ", ");
                }
            }

        }

        stringBuffer.append(" from demographic d ");
        if (getprovider) {
            stringBuffer.append(", provider p");
        }
        int yStyle = 0;
        try {
            yStyle = Integer.parseInt(yearStyle);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().error("Error:" + e);
        }

        MiscUtils.getLogger().debug("date style" + yStyle);
        switch (yStyle) {
            case 1:
                whereClause();
                if (ageStyle.equals("1")) {
                    params.put("startYear1", startYear);
                    stringBuffer.append(" ( ( YEAR(" + asofDateSql + ") -YEAR (DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(" + asofDateSql + ",5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'),5)) <  :startYear1 ) ");
                } else {
                    params.put("startYear1b", startYear);
                    stringBuffer.append(" ( YEAR(" + asofDateSql + ") - d.year_of_birth < :startYear1b  ) ");
                }
                theFirstFlag = 1;
                break;
            case 2:
                whereClause();
                //if (ageStyle.equals("1")){
                params.put("startYear2", startYear);
                stringBuffer.append(" ( ( YEAR(" + asofDateSql + ") -YEAR (DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(" + asofDateSql + ",5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'),5)) >  :startYear2 ) ");
                //}else{
                //   stringBuffer.append(" ( YEAR("+asofDateSql+") - year_of_birth > "+startYear+"  ) ");
                //}
                theFirstFlag = 1;
                break;
            case 3:
                whereClause();
                if (ageStyle.equals("1")) {
                    params.put("startYear3", startYear);
                    stringBuffer.append(" ( ( YEAR(" + asofDateSql + ") -YEAR (DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(" + asofDateSql + ",5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'),5)) =  :startYear3 ) ");
                } else {
                    params.put("startYear3b", startYear);
                    stringBuffer.append(" ( YEAR(" + asofDateSql + ") - d.year_of_birth = :startYear3b  ) ");
                }
                theFirstFlag = 1;
                break;
            case 4:
                whereClause();
                MiscUtils.getLogger().debug("age style " + ageStyle);
                if (!ageStyle.equals("2")) {
                    // stringBuffer.append(" ( ( YEAR("+asofDateSql+") -YEAR (DATE_FORMAT(CONCAT((year_of_birth), '-', (month_of_birth),'-',(date_of_birth)),'%Y-%m-%d'))) - (RIGHT("+asofDateSql+",5)<RIGHT(DATE_FORMAT(CONCAT((year_of_birth),'-',(month_of_birth),'-',(date_of_birth)),'%Y-%m-%d'),5)) >  "+startYear+" and ( YEAR("+asofDateSql+") -YEAR (DATE_FORMAT(CONCAT((year_of_birth), '-', (month_of_birth),'-',(date_of_birth)),'%Y-%m-%d'))) - (RIGHT("+asofDateSql+",5)<RIGHT(DATE_FORMAT(CONCAT((year_of_birth),'-',(month_of_birth),'-',(date_of_birth)),'%Y-%m-%d'),5)) <  "+endYear+"  ) ");
                    MiscUtils.getLogger().debug("VERIFYING INT" + startYear);
                    //check to see if its a number
                    if (verifyInt(startYear)) {
                        params.put("startYear4", startYear);
                        stringBuffer.append(" ( ( YEAR(" + asofDateSql + ") -YEAR (DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(" + asofDateSql + ",5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'),5)) >  :startYear4 ) ");
                    } else {
                        // Note: MySQL INTERVAL syntax cannot be parameterized, but getInterval() validates the input
                        String interval = getInterval(startYear);
                        stringBuffer.append(" ( date_sub(" + asofDateSql + ",interval " + interval + ") >= DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d')   ) ");
                    }
                    stringBuffer.append(" and ");
                    if (verifyInt(endYear)) {
                        params.put("endYear4", endYear);
                        stringBuffer.append(" ( ( YEAR(" + asofDateSql + ") -YEAR (DATE_FORMAT(CONCAT((d.year_of_birth), '-', (d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'))) - (RIGHT(" + asofDateSql + ",5)<RIGHT(DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d'),5)) <  :endYear4  ) ");
                    } else {
                        // Note: MySQL INTERVAL syntax cannot be parameterized, but getInterval() validates the input
                        String interval = getInterval(endYear);
                        stringBuffer.append(" ( date_sub(" + asofDateSql + ",interval " + interval + ") < DATE_FORMAT(CONCAT((d.year_of_birth),'-',(d.month_of_birth),'-',(d.date_of_birth)),'%Y-%m-%d')   ) ");
                    }
                } else {
                    params.put("startYear4b", startYear);
                    params.put("endYear4b", endYear);
                    stringBuffer.append(" ( YEAR(" + asofDateSql + ") - d.year_of_birth > :startYear4b  and YEAR(" + asofDateSql + ") - d.year_of_birth < :endYear4b  ) ");
                }
                theFirstFlag = 1;
                break;
        }

        // Only add asofDate param if it's actually used in the SQL (i.e., an age filter case was executed)
        if (useAsofDateParam && yStyle >= 1 && yStyle <= 4) {
            params.put("asofDate", asofDate);
        }

        if (rosterStatus != null) {
            whereClause();
            firstClause();
            stringBuffer.append(" ( ");
            for (int i = 0; i < rosterStatus.length; i++) {
                theFirstFlag = 1;
                String paramName = "rosterStatus" + i;
                params.put(paramName, rosterStatus[i]);
                if (i == (rosterStatus.length - 1)) {
                    stringBuffer.append(" d.roster_status = :" + paramName + " )");
                } else {
                    stringBuffer.append(" d.roster_status = :" + paramName + " or  ");
                }
            }
        }

        if (patientStatus != null) {
            whereClause();
            firstClause();
            stringBuffer.append(" ( ");
            for (int i = 0; i < patientStatus.length; i++) {
                theFirstFlag = 1;
                String paramName = "patientStatus" + i;
                params.put(paramName, patientStatus[i]);
                if (i == (patientStatus.length - 1)) {
                    stringBuffer.append(" d.patient_status = :" + paramName + " )");
                } else {
                    stringBuffer.append(" d.patient_status = :" + paramName + " or  ");
                }
            }
        }

        if (providers != null) {
            whereClause();
            firstClause();
            stringBuffer.append(" ( ");
            for (int i = 0; i < providers.length; i++) {
                theFirstFlag = 1;
                String paramName = "provider" + i;
                params.put(paramName, providers[i]);
                if (i == (providers.length - 1)) {
                    stringBuffer.append(" d.provider_no = :" + paramName + " )");
                } else {
                    stringBuffer.append(" d.provider_no = :" + paramName + " or  ");
                }
            }
        }

        if (lastName != null && lastName.length() != 0) {
            MiscUtils.getLogger().debug("last name = " + lastName + "<size = " + lastName.length());
            whereClause();
            firstClause();
            theFirstFlag = 1;
            stringBuffer.append(" ( ");
            params.put("lastName", lastName + "%");
            stringBuffer.append(" d.last_name like :lastName");
            stringBuffer.append(" ) ");
        }

        if (firstName != null && firstName.length() != 0) {
            whereClause();
            firstClause();
            theFirstFlag = 1;
            stringBuffer.append(" ( ");
            params.put("firstName", firstName + "%");
            stringBuffer.append(" d.first_name like :firstName");
            stringBuffer.append(" ) ");
        }

        yStyle = 0;
        try {
            yStyle = Integer.parseInt(sex);
        } catch (Exception e) {
            //empty
        }
        switch (yStyle) {
            case 1:
                whereClause();
                firstClause();
                stringBuffer.append(" ( d.sex =  'F'  )");
                theFirstFlag = 1;
                break;
            case 2:
                whereClause();
                firstClause();
                stringBuffer.append(" ( d.sex = 'M' )");
                theFirstFlag = 1;
                break;

        }

        //removed roster_status condition in place more complex check below

        if (getprovider) {
            whereClause();
            firstClause();
            stringBuffer.append(" ( d.provider_no = p.provider_no )");
            theFirstFlag = 1;
        }

        List<Integer> demoIds = frm.getDemographicIds();
        if (!demoIds.isEmpty()) {
            whereClause();
            firstClause();

            stringBuffer.append("(");
            boolean isFirst = true;
            int idIndex = 0;
            for (Integer i : demoIds) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    stringBuffer.append(" OR ");
                }
                String paramName = "demoId" + idIndex;
                params.put(paramName, i);
                stringBuffer.append("d.demographic_no = :" + paramName);
                idIndex++;
            }
            stringBuffer.append(")");
        }

        if (orderBy != null && orderBy.length() != 0) {
            if (!orderBy.equals("0")) {
                stringBuffer.append(" order by " + demoCols.getColumnName(orderBy) + " ");
            }
        }

        if (limit != null && limit.length() != 0) {
            if (!limit.equals("0")) {
                try {
                    int limitInt = Integer.parseInt(limit);
                    params.put("limitValue", limitInt);
                    stringBuffer.append(" limit :limitValue ");
                } catch (Exception u) {
                    MiscUtils.getLogger().debug("limit was not numeric >" + limit + "<");
                }
            }
        }

        MiscUtils.getLogger().debug("SEARCH SQL STATEMENT \n" + stringBuffer.toString());
        ArrayList<ArrayList<String>> searchedArray = new ArrayList<ArrayList<String>>();
        try {
            MiscUtils.getLogger().info(stringBuffer.toString());

            FormsDao dao = SpringUtils.getBean(FormsDao.class);
            for (Object[] o : dao.runParameterizedNativeQuery(stringBuffer.toString(), params)) {
                if (o == null) {
                    continue;
                }

                String demoNo = null;
                ArrayList<String> tempArr = new ArrayList<String>();
                for (int i = 0; i < select.length; i++) {
                    String fieldName = select[i];
                    String fieldValue = o[i] == null ? null : String.valueOf(o[i]);

                    tempArr.add(fieldValue);

                    if ("demographic_no".equals(fieldName)) {
                        demoNo = fieldValue;
                        MiscUtils.getLogger().debug("Demographic :" + demoNo + " is in the list");
                    }
                }

                // need to check if they were rostered at this point to this providers  (asofRosterDate is only set if this is being called from prevention reports)
                if (demoNo != null && asofRosterDate != null && providers != null && providers.length > 0) {
                    //Only checking the first doc.  Only one should be included for finding the cumulative bonus
                    try {
                        if (!PreventionReportUtil.wasRosteredToThisProvider(loggedInInfo, Integer.parseInt(demoNo), DateUtils.parseDate(asofRosterDate, null), providers[0])) {
                            MiscUtils.getLogger().info("Demographic :" + demoNo + " was not included in returned array because they were not rostered to " + providers[0] + " on " + asofRosterDate);
                            continue;
                        } else {
                            MiscUtils.getLogger().info("Demographic :" + demoNo + " was included in returned array because they were not rostered to " + providers[0] + " on " + asofRosterDate);
                        }
                    } catch (NumberFormatException e) {
                        MiscUtils.getLogger().error("Error", e);
                    } catch (ParseException e) {
                        MiscUtils.getLogger().error("Error", e);
                    }
                }

                searchedArray.add(tempArr);

            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return searchedArray;
    }

    boolean verifyInt(String str) {
        boolean verify = true;
        try {
            Integer.parseInt(str);
        } catch (Exception e) {
            verify = false;
        }
        return verify;
    }

    String getInterval(String startYear) {
        MiscUtils.getLogger().debug("in getInterval startYear " + startYear);
        String str = "";
        if (startYear.charAt(startYear.length() - 1) == 'm') {
            str = startYear.substring(0, (startYear.length() - 1)) + " month";
        }
        MiscUtils.getLogger().debug(str);
        return str;
    }
}
