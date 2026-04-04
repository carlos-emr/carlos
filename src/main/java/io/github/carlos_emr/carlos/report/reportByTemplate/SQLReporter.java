/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.db.DBHandler;
import io.github.carlos_emr.carlos.report.data.RptResultStruct;
import io.github.carlos_emr.carlos.util.UtilMisc;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;


/**
 * @author rjonasz
 */
public class SQLReporter implements Reporter {

    /**
     * Creates a new instance of SQLReporter
     */
    public SQLReporter() {
    }

    public boolean generateReport(HttpServletRequest request) {
        String templateId = request.getParameter("templateId");
        ReportObject curReport = (new ReportManager()).getReportTemplateNoParam(templateId);
        Map parameterMap = request.getParameterMap();

        if (curReport.isSequence()) {
            return generateSequencedReport(request);
        }

        // Use the parameterized-SQL path when the report object supports it; this
        // prevents user-supplied parameter values from being interpolated directly
        // into the SQL text (SQL injection prevention).
        String[] parameterizedResult = curReport instanceof ReportObjectGeneric
                ? ((ReportObjectGeneric) curReport).getParameterizedSQL(parameterMap)
                : null;

        if (parameterizedResult == null || parameterizedResult[0] == null || parameterizedResult[0].trim().isEmpty()) {
            request.setAttribute("errormsg", "Error: Cannot find all parameters for the query.  Check the template.");
            request.setAttribute("templateid", templateId);
            return false;
        }

        String sql = parameterizedResult[0];
        // Extract the parameter values (indices 1..n)
        Object[] sqlParams = new Object[parameterizedResult.length - 1];
        System.arraycopy(parameterizedResult, 1, sqlParams, 0, sqlParams.length);

        String rsHtml = "An SQL query error has occured ";
        String csv = "";
        try (StringWriter swr = new StringWriter();
             ResultSet rs = DBHandler.GetPreSQL(sql, sqlParams)) {
            if (!rs.isBeforeFirst()) {
                rsHtml = "The query returned no results.";
            } else {
                rsHtml = RptResultStruct.getStructure2(rs);  //makes html from the result set
                CSVPrinter csvp = new CSVPrinter(swr, CSVFormat.DEFAULT);
                String[][] data = UtilMisc.getArrayFromResultSet(rs);
                for (String[] row : data) {
                    csvp.printRecord((Object[]) row);
                }
                csvp.flush();
                csv = swr.toString();
            }
        } catch (SQLException sqe) {
            rsHtml += sqe.getCause() != null ? sqe.getCause() : sqe.getMessage();
            MiscUtils.getLogger().error("Error", sqe);
        } catch (IOException e) {
            rsHtml = "Error: during creation of CSV : " + (e.getCause() != null ? e.getCause() : e.getMessage());
            MiscUtils.getLogger().error("Error", e);
        }

        request.getSession().setAttribute("csv", csv);
        request.setAttribute("csv", csv);
        request.setAttribute("sql", sql);
        request.setAttribute("reportobject", curReport);
        request.setAttribute("resultsethtml", rsHtml);

        return true;
    }

    public boolean generateSequencedReport(HttpServletRequest request) {
        String templateId = request.getParameter("templateId");
        ReportObject curReport = (new ReportManager()).getReportTemplateNoParam(templateId);
        Map parameterMap = request.getParameterMap();

        int x = 0;
        // Use the parameterized-SQL path when the report object supports it
        if (curReport instanceof ReportObjectGeneric) {
            ReportObjectGeneric genericReport = (ReportObjectGeneric) curReport;
            String[] parameterizedResult;
            while ((parameterizedResult = genericReport.getParameterizedSQL(x, parameterMap)) != null) {
                String sql = parameterizedResult[0];
                if (sql.isEmpty()) {
                    request.setAttribute("errormsg", "Error: Cannot find all parameters for the query.  Check the template.");
                    request.setAttribute("templateid", templateId);
                    return false;
                }

                Object[] sqlParams = new Object[parameterizedResult.length - 1];
                System.arraycopy(parameterizedResult, 1, sqlParams, 0, sqlParams.length);

                String rsHtml = "An SQL query error has occured ";
                String csv = "";
                try (StringWriter swr = new StringWriter();
                     ResultSet rs = DBHandler.GetPreSQL(sql, sqlParams)) {
                    if (!rs.isBeforeFirst()) {
                        rsHtml = sql + "<br/>The query returned no results.";
                    } else {
                        rsHtml = RptResultStruct.getStructure2(rs);  //makes html from the result set
                        CSVPrinter csvp = new CSVPrinter(swr, CSVFormat.DEFAULT);
                        String[][] data = UtilMisc.getArrayFromResultSet(rs);
                        for (String[] row : data) {
                            csvp.printRecord((Object[]) row);
                        }
                        csvp.flush();
                        csv = swr.toString();
                    }
                } catch (SQLException sqe) {
                    rsHtml += sqe.getCause() != null ? sqe.getCause() : sqe.getMessage();
                    MiscUtils.getLogger().error("Error", sqe);
                } catch (IOException e) {
                    rsHtml = "Error: during creation of CSV : " + (e.getCause() != null ? e.getCause() : e.getMessage());
                    MiscUtils.getLogger().error("Error", e);
                }

                request.setAttribute("csv-" + x, csv);
                request.setAttribute("sql-" + x, sql);
                request.setAttribute("resultsethtml-" + x, rsHtml);
                x++;
            }
        } else {
            // Fallback for non-generic report objects
            String sql = null;
            while ((sql = curReport.getPreparedSQL(x, parameterMap)) != null) {
                if (sql.isEmpty()) {
                    request.setAttribute("errormsg", "Error: Cannot find all parameters for the query.  Check the template.");
                    request.setAttribute("templateid", templateId);
                    return false;
                }

                String rsHtml = "An SQL query error has occured ";
                String csv = "";
                try (StringWriter swr = new StringWriter();
                    ResultSet rs = DBHandler.GetSQL(sql) ) {
                    if (!rs.isBeforeFirst()) {
                        rsHtml = sql + "<br/>The query returned no results.";
                    } else {
                        rsHtml = RptResultStruct.getStructure2(rs);  //makes html from the result set
                        CSVPrinter csvp = new CSVPrinter(swr, CSVFormat.DEFAULT);
                        String[][] data = UtilMisc.getArrayFromResultSet(rs);
                        for (String[] row : data) {
                            csvp.printRecord((Object[]) row);
                        }
                        csvp.flush();
                        csv = swr.toString();
                    }
                } catch (SQLException sqe) {
                    rsHtml += sqe.getCause() != null ? sqe.getCause() : sqe.getMessage();
                    MiscUtils.getLogger().error("Error", sqe);
                } catch (IOException e) {
                    rsHtml = "Error: during creation of CSV : " + (e.getCause() != null ? e.getCause() : e.getMessage());
                    MiscUtils.getLogger().error("Error", e);
                }

                request.setAttribute("csv-" + x, csv);
                request.setAttribute("sql-" + x, sql);
                request.setAttribute("resultsethtml-" + x, rsHtml);
                x++;
            }
        }

        request.setAttribute("sequenceLength", x);
        request.setAttribute("reportobject", curReport);

        return true;
    }

}