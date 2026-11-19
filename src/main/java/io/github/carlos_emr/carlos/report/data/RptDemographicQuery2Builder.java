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

package io.github.carlos_emr.carlos.report.data;

import io.github.carlos_emr.carlos.messenger.util.MsgStringQuote;
import io.github.carlos_emr.carlos.commn.dao.forms.FormsDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.prevention.reports.PreventionReportUtil;
import io.github.carlos_emr.carlos.report.pageUtil.RptDemographicReport2Form;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
        // An "as of" roster report (#5877) widens its candidates to archived provider periods and
        // then checks each row against the roster history. The check needs the patient number
        // whatever columns were selected, and a row limit must count only the rows it keeps.
        boolean historicalRoster = asofRosterDate != null && providers != null && providers.length > 0;
        java.sql.Date rosterHistoryDate = null;
        if (historicalRoster) {
            java.util.Date parsed = UtilDateUtilities.getDateFromString(asofRosterDate, "yyyy-MM-dd");
            if (parsed != null) {
                rosterHistoryDate = new java.sql.Date(parsed.getTime());
                params.put("rosterHistoryDate", rosterHistoryDate);
            }
        }

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

        if (historicalRoster) {
            // Hidden last column for the roster-history check; never copied into a result row.
            stringBuffer.append(", d.demographic_no ");
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
            default:
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
                String providerPredicate = " d.provider_no = :" + paramName;
                if (historicalRoster) {
                    // An "as of" report asks who was rostered to the provider on that date, which
                    // the per-row history check below decides. Filtering on the patient's current
                    // provider here dropped patients who were the provider's then but have since
                    // moved (#5877), so also admit patients any archived period assigns to them.
                    // Only archived periods that could cover the date are candidates; the history check
                    // applies the exact rule. Without a readable date, every archived period is a candidate.
                    String archivePeriod = rosterHistoryDate == null ? ""
                            : " and da.roster_date <= :rosterHistoryDate"
                                    + " and (da.roster_termination_date is null or da.roster_termination_date >= :rosterHistoryDate)";
                    providerPredicate = " ( d.provider_no = :" + paramName
                            + " or d.demographic_no in (select da.demographic_no from demographicArchive da"
                            + " where da.provider_no = :" + paramName + archivePeriod + ") )";
                }
                if (i == (providers.length - 1)) {
                    stringBuffer.append(providerPredicate + " )");
                } else {
                    stringBuffer.append(providerPredicate + " or  ");
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
            default:
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

        Integer keptRowLimit = null;
        if (limit != null && limit.length() != 0) {
            if (!limit.equals("0")) {
                try {
                    int limitInt = Integer.parseInt(limit);
                    if (historicalRoster) {
                        // A SQL LIMIT would let rows the history check then drops use up the limit.
                        if (limitInt > 0) {
                            keptRowLimit = limitInt;
                        }
                    } else {
                        params.put("limitValue", limitInt);
                        stringBuffer.append(" limit :limitValue ");
                    }
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
                if (historicalRoster) {
                    Object rosterDemographicNo = o[select.length];
                    demoNo = rosterDemographicNo == null ? null : String.valueOf(rosterDemographicNo);
                    if (demoNo == null) {
                        // A row whose roster history cannot be checked is not reported for the date.
                        continue;
                    }
                    //Only checking the first doc.  Only one should be included for finding the cumulative bonus
                    try {
                        if (!PreventionReportUtil.wasRosteredToThisProvider(loggedInInfo, Integer.parseInt(demoNo), DateUtils.parseDate(asofRosterDate, null), providers[0])) {
                            // Patient and provider numbers stay out of the log: this runs for every candidate row.
                            MiscUtils.getLogger().debug("Report row left out: not rostered to the selected provider on the as-of date");
                            continue;
                        } else {
                            MiscUtils.getLogger().debug("Report row kept: rostered to the selected provider on the as-of date");
                        }
                    } catch (NumberFormatException | ParseException e) {
                        // An unreadable patient number or as-of date means the history cannot be
                        // checked; such a row is not reported for the date, so it cannot use up
                        // the row limit either. The exception text can carry the patient number.
                        MiscUtils.getLogger().error("Roster history check failed ({}); report row left out",
                                e.getClass().getSimpleName());
                        continue;
                    }
                }

                searchedArray.add(tempArr);
                if (keptRowLimit != null && searchedArray.size() >= keptRowLimit) {
                    break;
                }

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
