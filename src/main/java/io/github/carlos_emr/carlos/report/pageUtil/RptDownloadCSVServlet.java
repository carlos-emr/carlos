/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.report.pageUtil;

import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.commn.dao.AntenatalReportDao;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.report.data.ReportLimitRefusal;
import io.github.carlos_emr.carlos.report.data.RptReportConfigData;
import io.github.carlos_emr.carlos.report.data.RptReportCreator;
import io.github.carlos_emr.carlos.report.data.RptReportItem;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public class RptDownloadCSVServlet extends HttpServlet {

    private static final Logger _logger = MiscUtils.getLogger();
    public static final int MAX_CSV_EXPORT_BYTES = 5 * 1024 * 1024;
    static final String LIMIT_REFUSAL_MESSAGE = "Report is too large or took too long; narrow the report filters";
    /**
     * Set on the request before each 413. The error page shows the status message only in its
     * developer block, so it reads this marker to say what the user can do (#5850). Other 413s in
     * CARLOS (uploads, attachments) are not reports and do not set it.
     */
    static final String REPORT_TOO_LARGE_ATTRIBUTE = "carlosReportTooLarge";
    private static final String SQL_AND = " and ";

    String DELIMETER = "\t";

    private record GeneratedCsv(String reportName, String content) { }

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null)
            return;
        GeneratedCsv report;
        try {
            report = request.getParameter("demoReport") != null ? demoReport(request) : formReport(request);
        } catch (ServletException e1) {
            if (refusedByLimit(request, response, e1)) {
                return;
            }
            _logger.error("RptDownloadCSVServlet service() - form report failed", e1);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate report");
            return;
        } catch (Exception e1) {
            if (refusedByLimit(request, response, e1)) {
                return;
            }
            _logger.error("RptDownloadCSVServlet service() - report generation failed", e1);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate report");
            return;
        }

        String in = report.content();
        if (in == null) {
            return;
        }
        byte[] bytes = in.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_CSV_EXPORT_BYTES) {
            request.setAttribute(REPORT_TOO_LARGE_ATTRIBUTE, Boolean.TRUE);
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Report is too large; narrow the report filters");
            return;
        }

        String filename = report.reportName().replaceAll("[\\r\\n]", "") + ".csv";
        try (OutputStream out = new BufferedOutputStream(response.getOutputStream())) {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            out.write(bytes); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- bounded UTF-8 CSV attachment with text/csv and nosniff headers; not HTML
        }
    }

    /**
     * A report refused by its row, size or time limit is not a server failure: answer 413 with
     * what the user can do, as the byte check in {@link #service} does, instead of a 500 (#5850).
     */
    private static boolean refusedByLimit(HttpServletRequest request, HttpServletResponse response, Exception failure)
            throws IOException {
        if (!ReportLimitRefusal.isRefusal(failure)) {
            return false;
        }
        _logger.warn("RptDownloadCSVServlet service() - report refused by its row, size or time limit");
        request.setAttribute(REPORT_TOO_LARGE_ATTRIBUTE, Boolean.TRUE);
        response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, LIMIT_REFUSAL_MESSAGE);
        return true;
    }

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    private GeneratedCsv formReport(HttpServletRequest request) throws ServletException {


        String SAVE_AS = "default";
        String reportId = request.getParameter("id") != null ? request.getParameter("id") : "0";
        // get form name
        String reportName;
        String in = "";
        try {
            reportName = (new RptReportItem()).getReportName(reportId);
            RptFormQuery formQuery = new RptFormQuery();
            ParameterizedSql psql = formQuery.getQueryStr(reportId, request);

            RptReportConfigData formConfig = new RptReportConfigData();
            Vector[] vecField = formConfig.getAllFieldNameValue(SAVE_AS, reportId);
            Vector vecFieldCaption = vecField[1];


            Vector vecFieldValue = (new RptReportCreator()).query(psql.getSql(), vecFieldCaption, psql.getParamsArray());

            StringWriter swr = new StringWriter();
            CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter('\t')
                .build();
            CSVPrinter csvp = new CSVPrinter(swr, format);

            for (int i = 0; i < vecFieldCaption.size(); i++) {
                csvp.print((String) vecFieldCaption.get(i));
            }

            for (int i = 0; i < vecFieldValue.size(); i++) {
                Properties prop = (Properties) vecFieldValue.get(i);
                csvp.println();
                for (int j = 0; j < vecFieldCaption.size(); j++) {
                    csvp.print(prop.getProperty((String) vecFieldCaption.get(j), ""));
                }
            }
            csvp.flush();
            in = swr.toString();


        } catch (Exception e1) {
            throw new ServletException("Unable to generate form report", e1);
        }
        return new GeneratedCsv(reportName, in);
    }

    private GeneratedCsv demoReport(HttpServletRequest request) throws Exception {
        return new DemographicCsvReport(request).generate();
    }

    /** Selected columns and bound predicates for one report data source. */
    private static final class ReportColumns {
        private final Properties captions = new Properties();
        private final java.util.StringJoiner selection = new java.util.StringJoiner(",");
        private final java.util.StringJoiner filter = new java.util.StringJoiner(SQL_AND);
        private final List<Object> parameters = new ArrayList<>();
        private boolean selected;
        private boolean filtered;
    }

    /** Request-owned phases for the legacy demographic/antenatal CSV combinations. */
    private static final class DemographicCsvReport {
        private static final String DEMOGRAPHIC_NUMBER = "demographic_no";
        private static final String DEMOGRAPHIC_PREFIX = "demographic.";
        private static final String EXTENSION_PREFIX = "demographicExt.";
        private static final String FROM_DEMOGRAPHIC = " from demographic where ";
        private static final String ORDER_BY = " order by demographic.last_name, demographic.first_name";
        private static final List<String> DEMOGRAPHIC_FIELDS = List.of("last_name", "first_name", "date_joined",
                "hin", "hc_type", "address", "city", "postal", "phone", "phone2", "email");

        private final HttpServletRequest request;
        private final ReportColumns demographicColumns = new ReportColumns();
        private final ReportColumns extensionColumns = new ReportColumns();
        private final ReportColumns antenatalColumns = new ReportColumns();
        private final String antenatalTable;
        private final java.util.Set<String> selectedDemographics = new java.util.HashSet<>();
        private final List<String> filterValues = new ArrayList<>();
        private final List<String> dateFormats = new ArrayList<>();
        private final Vector<String> fieldCaptions = new Vector<>();
        private final Vector<String> fieldNames = new Vector<>();
        private final List<String> selectedExtensionCaptions = new ArrayList<>();
        private final Properties extensionValues = new Properties();
        private Vector<Properties> fieldValues = new Vector<>();

        private DemographicCsvReport(HttpServletRequest request) {
            this.request = request;
            antenatalTable = "BCAR2007".equals(request.getParameter("bcartype")) ? "formBCAR2007" : "formBCAR";
            String[] captions = {"Last Name", "First Name", "Date Joined", "Health Ins.", "HC Type", "Address",
                    "City", "Postal Code", "Phone (H)", "Phone (W)", "Email"};
            for (int i = 0; i < DEMOGRAPHIC_FIELDS.size(); i++) {
                demographicColumns.captions.setProperty(DEMOGRAPHIC_FIELDS.get(i), captions[i]);
            }
            extensionColumns.captions.setProperty("prefer_language", "Preferred Language");
            String configuredExtensions = CarlosProperties.getInstance().getProperty("demographicExt");
            if (configuredExtensions != null) {
                for (String caption : configuredExtensions.split("\\|")) {
                    extensionColumns.captions.setProperty(caption.replace(' ', '_'), caption);
                }
            }
            antenatalColumns.captions.setProperty("c_EDD", "EDD");
            antenatalColumns.captions.setProperty("pg1_famPhy", "Family Physician");
            antenatalColumns.captions.setProperty("pg1_partnerName", "Partner Name");
            antenatalColumns.captions.setProperty("ga", "GA Today");
            antenatalColumns.captions.setProperty("b_primiparous", "Primiparous");
        }

        private GeneratedCsv generate() throws Exception {
            readSelections();
            readFilters();
            queryDemographics();
            queryDemographicExtensions();
            queryAntenatal();
            queryAllTables();
            return new GeneratedCsv("clientDatabaseReport", renderCsv());
        }

        private void readSelections() {
            Enumeration<String> names = request.getParameterNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                selectField(name);
                if (name.startsWith("value_")) {
                    String serial = name.substring("value_".length());
                    if (request.getParameter("filter_" + serial) != null) {
                        filterValues.add(request.getParameter(name));
                        dateFormats.add(request.getParameter("dateFormat_" + serial));
                    }
                }
            }
            for (String field : DEMOGRAPHIC_FIELDS) {
                if (selectedDemographics.contains(field)) {
                    demographicColumns.selection.add(DEMOGRAPHIC_PREFIX + field);
                }
            }
        }

        private void selectField(String name) {
            if (demographicColumns.captions.containsKey(name)) {
                demographicColumns.selected = true;
                selectedDemographics.add(name);
            }
            if (antenatalColumns.captions.containsKey(name)) {
                antenatalColumns.selected = true;
                if (!name.equals("ga") && !name.equals("b_primiparous")) {
                    antenatalColumns.selection.add(antenatalTable + "." + name);
                }
            }
            if (extensionColumns.captions.containsKey(name)) {
                extensionColumns.selected = true;
                extensionColumns.selection.add(EXTENSION_PREFIX + name);
            }
        }

        private void readFilters() throws Exception {
            for (int i = 0; i < filterValues.size(); i++) {
                ParameterizedSql filter = bindFilter(filterValues.get(i), dateFormats.get(i));
                String sql = filter.getSql();
                if (sql.contains(DEMOGRAPHIC_PREFIX)) {
                    demographicColumns.filtered = true;
                    demographicColumns.filter.add(sql);
                    demographicColumns.parameters.addAll(filter.getParams());
                }
                if (sql.contains(EXTENSION_PREFIX)) {
                    extensionColumns.filtered = true;
                    extensionColumns.filter.add(sql);
                    extensionColumns.parameters.addAll(filter.getParams());
                }
                if (sql.contains(antenatalTable + ".")) {
                    antenatalColumns.filtered = true;
                    ParameterizedSql antenatal = replaceBirthSummaryFilter(filter);
                    antenatalColumns.filter.add(antenatal.getSql());
                    antenatalColumns.parameters.addAll(antenatal.getParams());
                }
            }
        }

        private ParameterizedSql bindFilter(String value, String dateFormat) throws Exception {
            Vector<?> variables = RptReportCreator.getVarVec(value);
            Vector<String> values = new Vector<>();
            for (Object variable : variables) {
                String name = (String) variable;
                String parameter = request.getParameter(name);
                if (name.matches("startDate\\d|endDate\\d") && dateFormat.length() > 1) {
                    parameter = RptReportCreator.getDiffDateFormat(parameter, dateFormat, "yyyy-MM-dd");
                }
                values.add(parameter);
            }
            return RptReportCreator.getWhereValueClauseParameterized(value, values);
        }

        private ParameterizedSql replaceBirthSummaryFilter(ParameterizedSql filter) throws java.sql.SQLException {
            if (filter.getSql().indexOf("formBCBirthSumMo") <= 0) {
                return filter;
            }
            List<Integer> numbers = SpringUtils.getBean(AntenatalReportDao.class).birthSummaryDemographics();
            String identifiers = identifierList(numbers.stream().map(String::valueOf).toList());
            return new ParameterizedSql(" " + antenatalTable + ".demographic_no in (" + identifiers + ")", List.of());
        }

        private void queryDemographics() throws java.sql.SQLException {
            if (demographicColumns.selected && !antenatalColumns.selected && !extensionColumns.selected
                    && demographicColumns.filtered && !antenatalColumns.filtered && !extensionColumns.filtered) {
                String sql = "select " + demographicColumns.selection + FROM_DEMOGRAPHIC + demographicColumns.filter + ORDER_BY;
                addDemographicFields();
                fieldValues = new RptReportCreator().query(sql, fieldNames, demographicColumns.parameters.toArray());
            }
        }

        private void queryDemographicExtensions() throws java.sql.SQLException {
            if (!((demographicColumns.selected && !antenatalColumns.selected && extensionColumns.selected && !antenatalColumns.filtered)
                    || (!antenatalColumns.filtered && extensionColumns.filtered))) {
                return;
            }
            if (demographicColumns.selected && !antenatalColumns.selected && extensionColumns.selected && !extensionColumns.filtered) {
                queryDemographicsWithExtensions();
            }
            if (extensionColumns.filtered) {
                queryFilteredExtensions();
            }
        }

        private void queryDemographicsWithExtensions() throws java.sql.SQLException {
            fieldNames.add(DEMOGRAPHIC_NUMBER);
            String sql = "select demographic_no," + demographicColumns.selection + FROM_DEMOGRAPHIC + demographicColumns.filter + ORDER_BY;
            addDemographicFields();
            fieldValues = new RptReportCreator().query(sql, fieldNames, demographicColumns.parameters.toArray());
            fieldNames.remove(0);
            readExtensionValues(resultDemographics(), true);
        }

        private void queryFilteredExtensions() throws java.sql.SQLException {
            fieldNames.add(DEMOGRAPHIC_NUMBER);
            List<String> demographics = extensionDemographics();
            readExtensionValues(demographics, true);
            String sql = "select demographic.demographic_no," + demographicColumns.selection + FROM_DEMOGRAPHIC
                    + " demographic.demographic_no in (" + identifierList(demographics) + ") " + ORDER_BY;
            addDemographicFields();
            fieldValues = new RptReportCreator().query(sql, fieldNames);
            fieldNames.remove(0);
        }

        private List<String> extensionDemographics() throws java.sql.SQLException {
            String sql = "select distinct(demographic.demographic_no) from demographicExt, demographic where demographic.demographic_no=demographicExt.demographic_no ";
            String joined = RptReportCreator.joinPredicates(demographicColumns.filter.toString(), extensionColumns.filter.toString());
            if (!joined.isEmpty()) {
                sql += SQL_AND + joined + "  ";
            }
            List<Object> parameters = new ArrayList<>(demographicColumns.parameters);
            parameters.addAll(extensionColumns.parameters);
            return ConfiguredReportQuery.identifiers(sql, parameters.toArray()).stream().map(String::valueOf).toList();
        }

        private void queryAntenatal() throws java.sql.SQLException {
            if ((demographicColumns.selected && antenatalColumns.selected && !extensionColumns.selected && !extensionColumns.filtered)
                    || (!extensionColumns.selected && antenatalColumns.filtered && !extensionColumns.filtered)) {
                queryAntenatalRows(null);
            }
        }

        private void queryAllTables() throws java.sql.SQLException {
            if (!((demographicColumns.selected && antenatalColumns.selected && extensionColumns.selected) || (antenatalColumns.filtered && extensionColumns.filtered))) {
                return;
            }
            if (demographicColumns.selected && antenatalColumns.selected && extensionColumns.selected && !extensionColumns.filtered) {
                fieldNames.add(DEMOGRAPHIC_NUMBER);
                queryAntenatalRows(null);
                fieldNames.remove(0);
                readExtensionValues(resultDemographics(), false);
            }
            if (antenatalColumns.filtered && extensionColumns.filtered) {
                fieldNames.add(DEMOGRAPHIC_NUMBER);
                List<String> demographics = extensionDemographics();
                readExtensionValues(demographics, true);
                queryAntenatalRows(demographics);
                fieldNames.remove(0);
            }
        }

        private String antenatalIdentifiers() throws java.sql.SQLException {
            String joined = RptReportCreator.joinPredicates(demographicColumns.filter.toString(), antenatalColumns.filter.toString());
            String sql = "select max(ID) from " + antenatalTable + ", demographic where demographic.demographic_no="
                    + antenatalTable + ".demographic_no ";
            if (!joined.isEmpty()) {
                sql += SQL_AND + joined;
            }
            sql += " group by " + antenatalTable + ".demographic_no," + antenatalTable + ".formCreated ";
            List<Object> parameters = new ArrayList<>(demographicColumns.parameters);
            parameters.addAll(antenatalColumns.parameters);
            return identifierList(ConfiguredReportQuery.identifiers(sql, parameters.toArray()).stream().map(String::valueOf).toList());
        }

        private void queryAntenatalRows(List<String> demographics) throws java.sql.SQLException {
            String identifiers = antenatalIdentifiers();
            String selectedAntenatalColumns = antenatalColumns.selection.length() > 0 ? "," + antenatalColumns.selection : "";
            String sql = "select demographic.demographic_no," + demographicColumns.selection + selectedAntenatalColumns
                    + " from demographic," + antenatalTable + " where ";
            if (demographics != null) {
                sql += " demographic.demographic_no in (" + identifierList(demographics) + ") and ";
            }
            // Both IN lists contain only integer identifiers obtained from the bounded readers.
            sql += " " + antenatalTable + ".ID in (" + identifiers + ") and demographic.demographic_no="
                    + antenatalTable + ".demographic_no " + ORDER_BY;
            addDemographicFields();
            if (antenatalColumns.selected) {
                addFields(antenatalColumns.selection.toString(), antenatalTable + ".", antenatalColumns.captions);
            }
            fieldValues = new RptReportCreator().query(sql, fieldNames);
        }

        private void addDemographicFields() {
            addFields(demographicColumns.selection.toString(), DEMOGRAPHIC_PREFIX, demographicColumns.captions);
        }

        private void addFields(String selection, String prefix, Properties captions) {
            for (String column : selection.replaceAll(prefix, "").split(",")) {
                fieldCaptions.add(captions.getProperty(column.trim()));
                fieldNames.add(column.trim());
            }
        }

        private List<String> resultDemographics() {
            return fieldValues.stream().map(row -> row.getProperty(DEMOGRAPHIC_NUMBER)).toList();
        }

        private void readExtensionValues(List<String> demographics, boolean latestOnly) {
            for (String column : extensionColumns.selection.toString().replaceAll(EXTENSION_PREFIX, "").split(",")) {
                selectedExtensionCaptions.add(extensionColumns.captions.getProperty(column.trim()));
                if (!demographics.isEmpty()) {
                    List<Integer> numbers = demographics.stream().map(Integer::valueOf).toList();
                    for (var extension : SpringUtils.getBean(DemographicExtDao.class).findReportValues(column.trim(), numbers, latestOnly)) {
                        extensionValues.setProperty(extension.demographicNo() + column,
                                extension.value() == null ? "" : extension.value());
                    }
                }
            }
        }

        private static String identifierList(List<String> identifiers) {
            return identifiers.isEmpty() ? "0" : String.join(",", identifiers);
        }

        private String renderCsv() throws IOException {
            StringWriter text = new StringWriter();
            CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter('\t').get();
            try (CSVPrinter csv = new CSVPrinter(text, format)) {
                csv.print("id");
                for (String caption : fieldCaptions) {
                    csv.print(caption);
                }
                if (extensionColumns.selected) {
                    for (String caption : selectedExtensionCaptions) {
                        csv.print(caption);
                    }
                }
                for (int i = 0; i < fieldValues.size(); i++) {
                    renderRow(csv, fieldValues.get(i), i + 1);
                }
            }
            return text.toString();
        }

        private void renderRow(CSVPrinter csv, Properties row, int number) throws IOException {
            csv.println();
            csv.print(String.valueOf(number));
            for (String name : fieldNames) {
                csv.print(row.getProperty(name, ""));
            }
            if (extensionColumns.selected) {
                String demographic = row.getProperty(DEMOGRAPHIC_NUMBER);
                for (String caption : selectedExtensionCaptions) {
                    csv.print(extensionValues.getProperty(demographic + caption.replace(' ', '_'), ""));
                }
            }
        }
    }
}
